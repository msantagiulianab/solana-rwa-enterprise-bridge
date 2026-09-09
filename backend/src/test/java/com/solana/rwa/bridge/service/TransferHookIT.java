package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.compliance.adapter.out.simulation.SimulatedTransferComplianceAdapter;
import com.solana.rwa.bridge.dto.TokenTransferRequest;
import com.solana.rwa.bridge.dto.TokenTransferResult;
import com.solana.rwa.bridge.exception.ComplianceViolationException;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import com.solana.rwa.bridge.solana.AccountMeta;
import com.solana.rwa.bridge.solana.Base58Codec;
import com.solana.rwa.bridge.solana.SolanaInstruction;
import com.solana.rwa.bridge.solana.SolanaKeypairService;
import com.solana.rwa.bridge.solana.SolanaPdaUtil;
import com.solana.rwa.bridge.solana.Token2022Program;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test for the compliance-gated Token-2022 transfer hook execution
 * path.
 *
 * <p>Boots the full Spring context (H2, Flyway, real keypair/serializer beans)
 * with the {@link SolanaRpcAdapter} mocked so no live Devnet traffic occurs.
 * Verifies that a blocked compliance decision aborts before any broadcast while
 * a compliant transfer appends the transfer hook {@code extra-account-metas}
 * validation PDA to the {@code TransferChecked} instruction.
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

    @MockitoBean
    private SolanaRpcAdapter rpcAdapter;

    private final SolanaKeypairService keypairService = new SolanaKeypairService("");

    @Test
    void transfer_compliantBroadcastsAndReturnsSignature() {
        when(rpcAdapter.getLatestBlockhash()).thenReturn(new LatestBlockhash(BLOCKHASH, 1234L));
        when(rpcAdapter.sendTransaction(anyString())).thenReturn("tx-signature");

        TokenTransferResult result = tokenTransferService.transfer(compliantRequest());

        assertThat(result.signature()).isEqualTo("tx-signature");
        assertThat(result.complianceStatus()).isEqualTo("APPROVED");
        verify(rpcAdapter).sendTransaction(anyString());
    }

    @Test
    void transfer_blockedDestination_throwsAndNeverBroadcasts() {
        assertThatThrownBy(() -> tokenTransferService.transfer(sanctionedRequest()))
                .isInstanceOf(ComplianceViolationException.class)
                .hasMessageContaining("SANCTIONED_DESTINATION");

        // Fail-closed: no RPC bytes may be emitted on a blocked decision.
        verify(rpcAdapter, never()).sendTransaction(anyString());
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
