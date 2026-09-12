package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.compliance.port.TransferCompliancePort;
import com.solana.rwa.bridge.compliance.port.TransferComplianceReason;
import com.solana.rwa.bridge.compliance.port.TransferComplianceResult;
import com.solana.rwa.bridge.compliance.port.TransferComplianceStatus;
import com.solana.rwa.bridge.dto.TokenTransferRequest;
import com.solana.rwa.bridge.dto.TokenTransferResult;
import com.solana.rwa.bridge.entity.TransferHookAuditLog;
import com.solana.rwa.bridge.entity.TransferHookAuditStatus;
import com.solana.rwa.bridge.exception.ComplianceViolationException;
import com.solana.rwa.bridge.repository.TransferHookAuditLogRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import com.solana.rwa.bridge.solana.SolanaKeypairService;
import com.solana.rwa.bridge.solana.SolanaTransactionSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for the compliance-gated {@link TokenTransferService}
 * audit persistence (no live RPC, no Spring context).
 *
 * <p>Every transfer evaluation — {@code CLEARED} or {@code BLOCKED} — must be
 * recorded immutably via {@link TransferHookAuditLogRepository#save}. A blocked
 * decision is written with a {@code null} transaction signature and aborts with
 * {@link ComplianceViolationException} (HTTP 422 upstream) before any Solana
 * RPC interaction, keeping the transfer hook path fail-closed.
 */
@ExtendWith(MockitoExtension.class)
class TokenTransferServiceTest {

    private static final String BLOCKHASH = "11111111111111111111111111111111";

    @Mock
    private TransferCompliancePort transferCompliancePort;

    @Mock
    private SolanaRpcAdapter solanaRpcAdapter;

    @Mock
    private SolanaKeypairService keypairService;

    @Mock
    private SolanaTransactionSerializer transactionSerializer;

    @Mock
    private TransferHookAuditLogRepository transferHookAuditLogRepository;

    private TokenTransferService service;

    private final SolanaKeypairService keypairFactory = new SolanaKeypairService("");

    @BeforeEach
    void setUp() {
        service = new TokenTransferService(transferCompliancePort, solanaRpcAdapter, keypairService,
                transactionSerializer, transferHookAuditLogRepository, programId());
    }

    @Test
    void transfer_clearedPersistsAuditLogWithSignatureAndBroadcasts() {
        Instant evaluatedAt = Instant.parse("2026-09-12T10:00:00Z");
        TokenTransferRequest request = request();

        when(transferCompliancePort.evaluateTransfer(any()))
                .thenReturn(new TransferComplianceResult(TransferComplianceStatus.APPROVED,
                        new TransferComplianceReason("COMPLIANCE", "APPROVED"),
                        "ref-cleared", evaluatedAt));
        when(keypairService.resolveKeypair())
                .thenReturn(keypairFactory.fromSeed(keypairFactory.deriveSeed("authority")));
        when(solanaRpcAdapter.getLatestBlockhash()).thenReturn(new LatestBlockhash(BLOCKHASH, 1234L));
        when(transactionSerializer.serializeAndSign(any(), anyString(), anyList()))
                .thenReturn("signed-tx");
        when(solanaRpcAdapter.sendTransaction("signed-tx")).thenReturn("tx-signature");

        TokenTransferResult result = service.transfer(request);

        assertThat(result.signature()).isEqualTo("tx-signature");
        assertThat(result.complianceStatus()).isEqualTo("APPROVED");

        ArgumentCaptor<TransferHookAuditLog> captor = ArgumentCaptor.forClass(TransferHookAuditLog.class);
        verify(transferHookAuditLogRepository).save(captor.capture());

        TransferHookAuditLog saved = captor.getValue();
        assertThat(saved.getTransactionSignature()).isEqualTo("tx-signature");
        assertThat(saved.getComplianceStatus()).isEqualTo(TransferHookAuditStatus.CLEARED);
        assertThat(saved.getMintAddress()).isEqualTo(request.assetMintAddress());
        assertThat(saved.getSourceWallet()).isEqualTo(request.sourceWallet());
        assertThat(saved.getDestinationWallet()).isEqualTo(request.destinationWallet());
        assertThat(saved.getAmount()).isEqualTo(1_000_000L);
        assertThat(saved.getReasonCode()).isEqualTo("COMPLIANCE:APPROVED");

        verify(solanaRpcAdapter).sendTransaction("signed-tx");
    }

    @Test
    void transfer_blockedPersistsAuditLogWithNullSignatureAndThrowsWithoutBroadcast() {
        Instant evaluatedAt = Instant.parse("2026-09-12T10:00:00Z");
        TokenTransferRequest request = request();

        when(transferCompliancePort.evaluateTransfer(any()))
                .thenReturn(new TransferComplianceResult(TransferComplianceStatus.BLOCKED,
                        new TransferComplianceReason("COMPLIANCE", "SANCTIONED_DESTINATION"),
                        "ref-blocked", evaluatedAt));

        assertThatThrownBy(() -> service.transfer(request))
                .isInstanceOf(ComplianceViolationException.class)
                .hasMessageContaining("SANCTIONED_DESTINATION");

        ArgumentCaptor<TransferHookAuditLog> captor = ArgumentCaptor.forClass(TransferHookAuditLog.class);
        verify(transferHookAuditLogRepository).save(captor.capture());

        TransferHookAuditLog saved = captor.getValue();
        assertThat(saved.getTransactionSignature()).isNull();
        assertThat(saved.getComplianceStatus()).isEqualTo(TransferHookAuditStatus.BLOCKED);
        assertThat(saved.getReasonCode()).isEqualTo("COMPLIANCE:SANCTIONED_DESTINATION");

        // Fail-closed: no Solana RPC bytes may ever be emitted on a blocked decision.
        verifyNoInteractions(solanaRpcAdapter);
    }

    private TokenTransferRequest request() {
        return new TokenTransferRequest(
                key("source-wallet"), key("destination-wallet"),
                key("source-ata"), key("destination-ata"), key("mint"), 1_000_000L);
    }

    private String programId() {
        return key("transfer-hook-program");
    }

    private String key(String material) {
        return keypairFactory.fromSeed(keypairFactory.deriveSeed(material)).getPublicKeyBase58();
    }
}
