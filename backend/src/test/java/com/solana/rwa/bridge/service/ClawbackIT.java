package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.ClawbackRequest;
import com.solana.rwa.bridge.dto.ClawbackResult;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import com.solana.rwa.bridge.repository.AuditLogRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test for the permanent-delegate clawback execution path.
 *
 * <p>Boots the full Spring context (H2, Flyway, real keypair/serializer beans)
 * with the {@link SolanaRpcAdapter} mocked so no live Devnet traffic occurs.
 * Verifies end-to-end that an approved clawback broadcasts via
 * {@code sendTransaction} and persists an immutable {@code CLAWBACK} audit row
 * carrying the client idempotency key and full request metadata, and that the
 * {@code TransferChecked} instruction appends the transfer-hook
 * {@code extra-account-metas} validation PDA.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "solana.transfer-hook.program-id="
        + Token2022Program.TOKEN_2022_PROGRAM_ID)
class ClawbackIT {

    private static final String HOOK_PROGRAM_ID = Token2022Program.TOKEN_2022_PROGRAM_ID;
    private static final String BLOCKHASH = "11111111111111111111111111111111";
    private static final String IDEMPOTENCY_KEY = "idem-clawback-0001";

    @Autowired
    private TokenClawbackService tokenClawbackService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private SolanaRpcAdapter rpcAdapter;

    private final SolanaKeypairService keypairService = new SolanaKeypairService("");

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    void clawback_approvedPersistsAuditLogAndBroadcasts() {
        ClawbackRequest request = request();
        when(rpcAdapter.getLatestBlockhash()).thenReturn(new LatestBlockhash(BLOCKHASH, 1234L));
        when(rpcAdapter.sendTransaction(anyString())).thenReturn("tx-signature");

        ClawbackResult result = tokenClawbackService.clawback(request, IDEMPOTENCY_KEY);

        assertThat(result.signature()).isEqualTo("tx-signature");
        assertThat(result.action()).isEqualTo(TokenClawbackService.ACTION_CLAWBACK);
        verify(rpcAdapter).sendTransaction(anyString());

        List<AuditLog> logs = auditLogRepository.findByWalletAddressAndAction(
                request.sourceTokenAccount(), TokenClawbackService.ACTION_CLAWBACK);
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo(TokenClawbackService.ACTION_CLAWBACK);
        assertThat(log.getStatus()).isEqualTo(AuditLogStatus.APPROVED);
        assertThat(log.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(log.getWalletAddress()).isEqualTo(request.sourceTokenAccount());
        assertThat(log.getAssetId()).isEqualTo(request.mintAddress());
        assertThat(log.getReason()).isEqualTo(request.reason());
        assertThat(log.getSolanaTransactionSignature()).isEqualTo("tx-signature");
        assertThat(log.getTimestamp()).isNotNull();
    }

    @Test
    void buildTransferChecked_appendsExtraAccountMetasValidationPda() {
        ClawbackRequest request = request();

        SolanaInstruction instruction = tokenClawbackService.buildTransferChecked(request);

        // 4 base accounts (source, mint, destination, authority) + 1 hook account.
        assertThat(instruction.accounts()).hasSize(5);

        AccountMeta validation = instruction.accounts().get(4);
        assertThat(validation.signer()).isFalse();
        assertThat(validation.writable()).isFalse();

        byte[] mint = Base58Codec.decode(request.mintAddress());
        byte[] hookProgram = Base58Codec.decode(HOOK_PROGRAM_ID);
        byte[] expected = SolanaPdaUtil.findProgramAddress(
                List.of(Token2022Program.EXTRA_ACCOUNT_METAS_SEED, mint), hookProgram).address();
        assertThat(validation.pubkey()).isEqualTo(expected);
    }

    private ClawbackRequest request() {
        return new ClawbackRequest(
                key("mint"), key("source-ata"), key("destination-ata"),
                1_000_000L, "Regulatory breach - clawback");
    }

    private String key(String material) {
        return keypairService.fromSeed(keypairService.deriveSeed(material)).getPublicKeyBase58();
    }
}
