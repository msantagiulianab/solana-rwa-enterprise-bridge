package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.compliance.adapter.out.simulation.SimulatedTransferComplianceAdapter;
import com.solana.rwa.bridge.dto.TokenTransferRequest;
import com.solana.rwa.bridge.dto.TokenTransferResult;
import com.solana.rwa.bridge.entity.TransferHookAuditLog;
import com.solana.rwa.bridge.entity.TransferHookAuditStatus;
import com.solana.rwa.bridge.exception.ComplianceViolationException;
import com.solana.rwa.bridge.repository.TransferHookAuditLogRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import com.solana.rwa.bridge.solana.AccountMeta;
import com.solana.rwa.bridge.solana.Base58Codec;
import com.solana.rwa.bridge.solana.SolanaInstruction;
import com.solana.rwa.bridge.solana.SolanaKeypairService;
import com.solana.rwa.bridge.solana.SolanaPdaUtil;
import com.solana.rwa.bridge.solana.Token2022Program;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Integration test for the compliance-gated Token-2022 transfer hook execution
 * path.
 *
 * <p>Boots the full Spring context (H2, Flyway, real keypair/serializer beans)
 * with the {@link SolanaRpcAdapter} mocked so no live Devnet traffic occurs.
 * Verifies end-to-end that a compliant transfer appends the transfer hook
 * {@code extra-account-metas} validation PDA and persists a {@code CLEARED}
 * audit row carrying the broadcast signature, while a blocked decision aborts
 * before any broadcast and persists a {@code BLOCKED} audit row with a null
 * transaction signature.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "solana.transfer-hook.program-id="
        + Token2022Program.TOKEN_2022_PROGRAM_ID)
class TransferHookIT {

    private static final String HOOK_PROGRAM_ID = Token2022Program.TOKEN_2022_PROGRAM_ID;
    private static final String BLOCKHASH = "11111111111111111111111111111111";

    @Autowired
    private TokenTransferService tokenTransferService;

    @Autowired
    private TransferHookAuditLogRepository transferHookAuditLogRepository;

    @MockitoBean
    private SolanaRpcAdapter rpcAdapter;

    private final SolanaKeypairService keypairService = new SolanaKeypairService("");

    @BeforeEach
    void setUp() {
        transferHookAuditLogRepository.deleteAll();
    }

    @Test
    void transfer_compliantBroadcastsAndWritesClearedAuditLog() {
        TokenTransferRequest request = compliantRequest();
        when(rpcAdapter.getLatestBlockhash()).thenReturn(new LatestBlockhash(BLOCKHASH, 1234L));
        when(rpcAdapter.sendTransaction(anyString())).thenReturn("tx-signature");

        TokenTransferResult result = tokenTransferService.transfer(request);

        assertThat(result.signature()).isEqualTo("tx-signature");
        assertThat(result.complianceStatus()).isEqualTo("APPROVED");
        verify(rpcAdapter).sendTransaction(anyString());

        List<TransferHookAuditLog> logs = transferHookAuditLogRepository.findByMintAddress(request.assetMintAddress());
        assertThat(logs).hasSize(1);

        TransferHookAuditLog log = logs.get(0);
        assertThat(log.getComplianceStatus()).isEqualTo(TransferHookAuditStatus.CLEARED);
        assertThat(log.getTransactionSignature()).isEqualTo("tx-signature");
        assertThat(log.getSourceWallet()).isEqualTo(request.sourceWallet());
        assertThat(log.getDestinationWallet()).isEqualTo(request.destinationWallet());
        assertThat(log.getAmount()).isEqualTo(request.amount());
    }

    @Test
    void transfer_blockedDestination_throwsAndWritesBlockedAuditLogWithoutBroadcast() {
        TokenTransferRequest request = sanctionedRequest();

        assertThatThrownBy(() -> tokenTransferService.transfer(request))
                .isInstanceOf(ComplianceViolationException.class)
                .hasMessageContaining("SANCTIONED_DESTINATION");

        // Fail-closed: zero Devnet RPC calls may be emitted on a blocked decision.
        verifyNoInteractions(rpcAdapter);

        List<TransferHookAuditLog> logs = transferHookAuditLogRepository.findByMintAddress(request.assetMintAddress());
        assertThat(logs).hasSize(1);

        TransferHookAuditLog log = logs.get(0);
        assertThat(log.getComplianceStatus()).isEqualTo(TransferHookAuditStatus.BLOCKED);
        assertThat(log.getTransactionSignature()).isNull();
        assertThat(log.getDestinationWallet()).isEqualTo(SimulatedTransferComplianceAdapter.SANCTIONED_DESTINATION_WALLET);
    }

    @Test
    void buildTransferChecked_appendsExtraAccountMetasValidationPda() {
        TokenTransferRequest request = compliantRequest();

        SolanaInstruction instruction = tokenTransferService.buildTransferChecked(request);

        // 4 base accounts (source, mint, destination, authority) + 1 hook account.
        assertThat(instruction.accounts()).hasSize(5);

        AccountMeta validation = instruction.accounts().get(4);
        assertThat(validation.signer()).isFalse();
        assertThat(validation.writable()).isFalse();

        byte[] mint = Base58Codec.decode(request.assetMintAddress());
        byte[] hookProgram = Base58Codec.decode(HOOK_PROGRAM_ID);
        byte[] expected = SolanaPdaUtil.findProgramAddress(
                List.of(Token2022Program.EXTRA_ACCOUNT_METAS_SEED, mint), hookProgram).address();
        assertThat(validation.pubkey()).isEqualTo(expected);
    }

    private TokenTransferRequest compliantRequest() {
        return new TokenTransferRequest(
                key("source-wallet"), key("destination-wallet"),
                key("source-ata"), key("destination-ata"), key("mint"), 1_000_000L);
    }

    private TokenTransferRequest sanctionedRequest() {
        return new TokenTransferRequest(
                key("source-wallet"), SimulatedTransferComplianceAdapter.SANCTIONED_DESTINATION_WALLET,
                key("source-ata"), key("destination-ata"), key("mint"), 1_000_000L);
    }

    private String key(String material) {
        return keypairService.fromSeed(keypairService.deriveSeed(material)).getPublicKeyBase58();
    }
}
