package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.ClawbackRequest;
import com.solana.rwa.bridge.dto.ClawbackResult;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.repository.AuditLogRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import com.solana.rwa.bridge.solana.AccountMeta;
import com.solana.rwa.bridge.solana.Base58Codec;
import com.solana.rwa.bridge.solana.SolanaInstruction;
import com.solana.rwa.bridge.solana.SolanaKeypair;
import com.solana.rwa.bridge.solana.SolanaKeypairService;
import com.solana.rwa.bridge.solana.SolanaPdaUtil;
import com.solana.rwa.bridge.solana.SolanaTransactionSerializer;
import com.solana.rwa.bridge.solana.Token2022Program;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for the permanent-delegate {@link TokenClawbackService}
 * (no live RPC, no Spring context).
 *
 * <p>The clawback path must assemble a Token-2022 {@code TransferChecked}
 * instruction whose signing authority is the Permanent Delegate — the enterprise
 * fee-payer derived from {@link SolanaKeypairService} — never the source account
 * owner, and must append the transfer-hook {@code extra-account-metas} validation
 * PDA. A successful broadcast is recorded immutably via
 * {@link AuditLogRepository#save} with {@code action = CLAWBACK}.
 */
@ExtendWith(MockitoExtension.class)
class TokenClawbackServiceTest {

    private static final String BLOCKHASH = "11111111111111111111111111111111";

    @Mock
    private SolanaRpcAdapter rpcAdapter;

    @Mock
    private SolanaKeypairService keypairService;

    @Mock
    private SolanaTransactionSerializer transactionSerializer;

    @Mock
    private AuditLogRepository auditLogRepository;

    private TokenClawbackService service;

    private final SolanaKeypairService keypairFactory = new SolanaKeypairService("");

    @BeforeEach
    void setUp() {
        service = new TokenClawbackService(rpcAdapter, keypairService, transactionSerializer,
                auditLogRepository, key("transfer-hook-program"));
    }


    @Test
    void buildTransferChecked_assemblesInstructionWithPermanentDelegateAuthority() {
        SolanaKeypair delegate = keypair("permanent-delegate");
        when(keypairService.resolveKeypair()).thenReturn(delegate);

        ClawbackRequest request = request();
        SolanaInstruction instruction = service.buildTransferChecked(request);

        assertThat(instruction.programId())
                .isEqualTo(Base58Codec.decode(Token2022Program.TOKEN_2022_PROGRAM_ID));

        List<AccountMeta> accounts = instruction.accounts();
        assertThat(accounts).hasSize(5);

        // Source token account: writable, non-signer (owner is bypassed).
        assertThat(accounts.get(0).pubkey()).isEqualTo(Base58Codec.decode(request.sourceTokenAccount()));
        assertThat(accounts.get(0).signer()).isFalse();
        assertThat(accounts.get(0).writable()).isTrue();

        // Mint: readonly, non-signer.
        assertThat(accounts.get(1).pubkey()).isEqualTo(Base58Codec.decode(request.mintAddress()));
        assertThat(accounts.get(1).signer()).isFalse();
        assertThat(accounts.get(1).writable()).isFalse();

        // Destination token account: writable, non-signer.
        assertThat(accounts.get(2).pubkey()).isEqualTo(Base58Codec.decode(request.destinationTokenAccount()));
        assertThat(accounts.get(2).signer()).isFalse();
        assertThat(accounts.get(2).writable()).isTrue();

        // The signing authority is the permanent delegate, never the source owner.
        assertThat(accounts.get(3).pubkey()).isEqualTo(delegate.getPublicKeyBytes());
        assertThat(accounts.get(3).signer()).isTrue();
        assertThat(accounts.get(3).writable()).isFalse();

        // Transfer-hook extra-account-metas PDA: readonly, non-signer.
        byte[] expectedValidation = SolanaPdaUtil.findProgramAddress(
                List.of(Token2022Program.EXTRA_ACCOUNT_METAS_SEED, Base58Codec.decode(request.mintAddress())),
                Base58Codec.decode(key("transfer-hook-program"))).address();
        assertThat(accounts.get(4).pubkey()).isEqualTo(expectedValidation);
        assertThat(accounts.get(4).signer()).isFalse();
        assertThat(accounts.get(4).writable()).isFalse();

        // TransferChecked data: discriminator 12 + u64 little-endian amount + decimals.
        byte[] data = instruction.data();
        assertThat(data).hasSize(10);
        assertThat(data[0] & 0xFF).isEqualTo(Token2022Program.TRANSFER_CHECKED_DISCRIMINATOR);
        assertThat(readU64(data, 1)).isEqualTo(request.amount());
        assertThat(data[9] & 0xFF).isEqualTo(TokenClawbackService.DEFAULT_DECIMALS);
    }


    @Test
    void clawback_broadcastsAndPersistsClawbackAuditLog() {
        SolanaKeypair delegate = keypair("permanent-delegate");
        when(keypairService.resolveKeypair()).thenReturn(delegate);
        when(rpcAdapter.getLatestBlockhash()).thenReturn(new LatestBlockhash(BLOCKHASH, 1234L));
        when(transactionSerializer.serializeAndSign(any(), anyString(), anyList())).thenReturn("signed-tx");
        when(rpcAdapter.sendTransaction("signed-tx")).thenReturn("tx-signature");

        ClawbackRequest request = request();
        ClawbackResult result = service.clawback(request, "idem-clawback-0001");

        assertThat(result.signature()).isEqualTo("tx-signature");
        assertThat(result.action()).isEqualTo(TokenClawbackService.ACTION_CLAWBACK);
        assertThat(result.mintAddress()).isEqualTo(request.mintAddress());
        assertThat(result.sourceTokenAccount()).isEqualTo(request.sourceTokenAccount());
        assertThat(result.destinationTokenAccount()).isEqualTo(request.destinationTokenAccount());
        assertThat(result.amount()).isEqualTo(request.amount());

        // The transaction is signed by the permanent delegate alone (never the source owner).
        ArgumentCaptor<List> signersCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionSerializer).serializeAndSign(any(), anyString(), signersCaptor.capture());
        assertThat(signersCaptor.getValue()).hasSize(1);
        assertThat(((SolanaKeypair) signersCaptor.getValue().get(0)).getPublicKeyBase58())
                .isEqualTo(delegate.getPublicKeyBase58());

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog saved = auditCaptor.getValue();
        assertThat(saved.getAction()).isEqualTo(TokenClawbackService.ACTION_CLAWBACK);
        assertThat(saved.getStatus()).isEqualTo(AuditLogStatus.APPROVED);
        assertThat(saved.getReason()).isEqualTo(request.reason());
        assertThat(saved.getWalletAddress()).isEqualTo(request.sourceTokenAccount());
        assertThat(saved.getAssetId()).isEqualTo(request.mintAddress());
        assertThat(saved.getSolanaTransactionSignature()).isEqualTo("tx-signature");
        assertThat(saved.getIdempotencyKey()).isEqualTo("idem-clawback-0001");

        verify(rpcAdapter).sendTransaction("signed-tx");
    }

    @Test
    void clawback_retriesWhenBlockhashIsStale() {
        SolanaKeypair delegate = keypair("permanent-delegate");
        when(keypairService.resolveKeypair()).thenReturn(delegate);
        when(rpcAdapter.getLatestBlockhash()).thenReturn(new LatestBlockhash(BLOCKHASH, 1234L));
        when(transactionSerializer.serializeAndSign(any(), anyString(), anyList())).thenReturn("signed-tx");
        when(rpcAdapter.sendTransaction("signed-tx"))
                .thenThrow(new SolanaRpcException(
                        "Solana RPC call 'sendTransaction' failed: JSON-RPC error -32002 (Blockhash not found)"))
                .thenReturn("tx-signature");

        ClawbackResult result = service.clawback(request(), "idem-clawback-0001");

        assertThat(result.signature()).isEqualTo("tx-signature");
        verify(rpcAdapter, times(2)).getLatestBlockhash();
        verify(rpcAdapter, times(2)).sendTransaction("signed-tx");
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    private ClawbackRequest request() {
        return new ClawbackRequest(
                key("mint"), key("source-ata"), key("destination-ata"),
                1_000_000L, "Regulatory breach - clawback");
    }

    private SolanaKeypair keypair(String material) {
        return keypairFactory.fromSeed(keypairFactory.deriveSeed(material));
    }

    private String key(String material) {
        return keypair(material).getPublicKeyBase58();
    }

    private long readU64(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= ((long) (data[offset + i] & 0xFF)) << (8 * i);
        }
        return value;
    }
}
