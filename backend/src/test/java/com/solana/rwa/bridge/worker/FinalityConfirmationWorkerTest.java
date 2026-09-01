package com.solana.rwa.bridge.worker;

import com.solana.rwa.bridge.entity.FinalityOutboxEntry;
import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.repository.FinalityOutboxRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.SignatureStatusResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FinalityConfirmationWorker}: finality state transitions,
 * exponential backoff, fail-closed timeout handling, and idempotency.
 */
@ExtendWith(MockitoExtension.class)
class FinalityConfirmationWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final String SIGNATURE = "5KtD3WZkYw8QxG9mN1vB2cX7pL4sR6aH9uJ3fE5gQ7iT2oY8kL1zM4nP6oA5bC3dF7eG9hI2jK4lM6nO8pQ1rS3tU";
    private static final String FINALIZED_SIGNATURE = "4JwC2xYvZr9wPyH7kU2aD5fT3mR8sN1gV6bX4cL9jQ5pO7iK0uM3nA8eE6gH2jK4lM6nO8pQ1rS3tU";

    @Mock
    private FinalityOutboxRepository repository;

    @Mock
    private SolanaRpcAdapter rpcAdapter;

    private FinalityConfirmationWorker worker;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        worker = new FinalityConfirmationWorker(repository, rpcAdapter, clock, 2000L, 60000L, 100);
    }

    private FinalityOutboxEntry confirmedEntry(String signature) {
        return FinalityOutboxEntry.builder()
                .solanaTransactionSignature(signature)
                .status(SettlementStatus.CONFIRMED)
                .commitmentLevel("CONFIRMED")
                .nextPollAt(NOW.minusSeconds(1))
                .build();
    }

    // -- tests --

    @Test
    void processDueEntries_upgradesConfirmedToFinalizedOnFinalizedResponse() {
        FinalityOutboxEntry entry = confirmedEntry(SIGNATURE);
        when(repository.findDueForPolling(SettlementStatus.CONFIRMED, NOW)).thenReturn(List.of(entry));
        when(rpcAdapter.getSignatureStatuses(List.of(SIGNATURE)))
                .thenReturn(List.of(new SignatureStatusResult(SIGNATURE, 10L, null, "finalized", null)));

        int processed = worker.processDueEntries();

        assertThat(processed).isEqualTo(1);
        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.FINALIZED);
        assertThat(entry.getCommitmentLevel()).isEqualTo("FINALIZED");
        assertThat(entry.getSettledAt()).isEqualTo(NOW);
        assertThat(entry.getNextPollAt()).isNull();
    }

    @Test
    void processDueEntries_appliesBackoffOnTransientRpcFailure() {
        FinalityOutboxEntry entry = confirmedEntry(SIGNATURE);
        when(repository.findDueForPolling(SettlementStatus.CONFIRMED, NOW)).thenReturn(List.of(entry));
        when(rpcAdapter.getSignatureStatuses(List.of(SIGNATURE)))
                .thenThrow(new SolanaRpcException("Solana node unreachable"));

        int processed = worker.processDueEntries();

        assertThat(processed).isEqualTo(1);
        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(entry.getPollAttempts()).isEqualTo(1);
        assertThat(entry.getNextPollAt()).isEqualTo(NOW.plusMillis(2000));
        assertThat(entry.getErrorMessage()).isEqualTo("Solana node unreachable");
    }

    @Test
    void applyTransition_incrementsRetryAndComputesBackoffOnConfirmed() {
        FinalityOutboxEntry entry = confirmedEntry(SIGNATURE);

        worker.applyTransition(entry, new SignatureStatusResult(SIGNATURE, 10L, 31L, "confirmed", null), NOW);

        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(entry.getPollAttempts()).isEqualTo(1);
        assertThat(entry.getCommitmentLevel()).isEqualTo("CONFIRMED");
        assertThat(entry.getNextPollAt()).isEqualTo(NOW.plusMillis(2000));
    }

    @Test
    void applyTransition_transitionsToFailedOnTransactionError() {
        FinalityOutboxEntry entry = confirmedEntry(SIGNATURE);
        Object err = Map.of("InstructionError", List.of(0, "Custom"));

        worker.applyTransition(entry, new SignatureStatusResult(SIGNATURE, 10L, null, "finalized", err), NOW);

        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(entry.getErrorMessage()).contains("InstructionError");
        assertThat(entry.getNextPollAt()).isNull();
    }

    @Test
    void applyTransition_transitionsToExpiredAfterMaxAttempts() {
        FinalityOutboxEntry entry = confirmedEntry(SIGNATURE);
        entry.setPollAttempts(29);

        worker.applyTransition(entry, new SignatureStatusResult(SIGNATURE, 10L, null, "confirmed", null), NOW);

        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.EXPIRED);
        assertThat(entry.getPollAttempts()).isEqualTo(30);
        assertThat(entry.getErrorMessage()).contains("timed out");
        assertThat(entry.getNextPollAt()).isNull();
    }

    @Test
    void processDueEntries_failsClosedWhenSignatureIsMissing() {
        FinalityOutboxEntry entry = FinalityOutboxEntry.builder()
                .status(SettlementStatus.CONFIRMED)
                .commitmentLevel("CONFIRMED")
                .nextPollAt(NOW.minusSeconds(1))
                .build();
        when(repository.findDueForPolling(SettlementStatus.CONFIRMED, NOW)).thenReturn(List.of(entry));

        int processed = worker.processDueEntries();

        assertThat(processed).isEqualTo(1);
        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(entry.getErrorMessage()).contains("Missing transaction signature");
    }

    @Test
    void processDueEntries_doesNotReprocessFinalizedRecords() {
        FinalityOutboxEntry finalized = FinalityOutboxEntry.builder()
                .solanaTransactionSignature(FINALIZED_SIGNATURE)
                .status(SettlementStatus.FINALIZED)
                .commitmentLevel("FINALIZED")
                .build();
        FinalityOutboxEntry confirmed = confirmedEntry(SIGNATURE);
        when(repository.findDueForPolling(SettlementStatus.CONFIRMED, NOW))
                .thenReturn(List.of(finalized, confirmed));
        when(rpcAdapter.getSignatureStatuses(List.of(SIGNATURE)))
                .thenReturn(List.of(new SignatureStatusResult(SIGNATURE, 10L, null, "finalized", null)));

        int processed = worker.processDueEntries();

        assertThat(processed).isEqualTo(1);
        assertThat(finalized.getStatus()).isEqualTo(SettlementStatus.FINALIZED);
        assertThat(finalized.getPollAttempts()).isZero();
        assertThat(confirmed.getStatus()).isEqualTo(SettlementStatus.FINALIZED);
        verify(rpcAdapter).getSignatureStatuses(List.of(SIGNATURE));
    }

    @Test
    void backoffDelayMs_producesExponentialScheduleUpToCap() {
        assertThat(FinalityConfirmationWorker.backoffDelayMs(1, 2000, 60000)).isEqualTo(2000);
        assertThat(FinalityConfirmationWorker.backoffDelayMs(2, 2000, 60000)).isEqualTo(4000);
        assertThat(FinalityConfirmationWorker.backoffDelayMs(3, 2000, 60000)).isEqualTo(8000);
        assertThat(FinalityConfirmationWorker.backoffDelayMs(100, 2000, 60000)).isEqualTo(60000);
    }
}
