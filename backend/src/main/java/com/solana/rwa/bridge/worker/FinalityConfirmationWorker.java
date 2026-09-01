package com.solana.rwa.bridge.worker;

import com.solana.rwa.bridge.entity.FinalityOutboxEntry;
import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.repository.FinalityOutboxRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.SignatureStatusResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Background daemon that advances durable outbox records from
 * {@link SettlementStatus#CONFIRMED} to {@link SettlementStatus#FINALIZED}
 * (or {@link SettlementStatus#FAILED}/{@link SettlementStatus#EXPIRED}) by
 * polling on-chain transaction finality via
 * {@link SolanaRpcAdapter#getSignatureStatuses(List)}.
 *
 * <p>The scheduled entry point doubles as the transaction boundary: it is
 * {@code @Scheduled} (so the container invokes it through the proxy) and
 * {@code @Transactional} (so every poll cycle commits atomically). Only
 * transient RPC transport failures are handled inline (retry + backoff); any
 * other exception propagates to roll the cycle back and let the scheduler retry
 * on the next interval.
 */
@Slf4j
@Component
public class FinalityConfirmationWorker {

    public static final String COMMITMENT_FINALIZED = "FINALIZED";

    private final FinalityOutboxRepository repository;
    private final SolanaRpcAdapter rpcAdapter;
    private final Clock clock;
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final int batchSize;

    public FinalityConfirmationWorker(
            FinalityOutboxRepository repository,
            SolanaRpcAdapter rpcAdapter,
            Clock clock,
            @Value("${solana.outbox.base-backoff-ms:2000}") long baseBackoffMs,
            @Value("${solana.outbox.max-backoff-ms:60000}") long maxBackoffMs,
            @Value("${solana.outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.rpcAdapter = rpcAdapter;
        this.clock = clock;
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.batchSize = batchSize;
    }

    /**
     * Polls due outbox rows and advances their finality state. Scheduled entry
     * point and transactional boundary of the cycle.
     *
     * @return number of due records processed this cycle
     */
    @Scheduled(fixedDelayString = "${solana.outbox.poll-interval-ms:5000}")
    @Transactional
    public int processDueEntries() {
        Instant now = clock.instant();
        List<FinalityOutboxEntry> due = repository.findDueForPolling(SettlementStatus.CONFIRMED, now);
        if (due.isEmpty()) {
            return 0;
        }

        int processed = 0;
        int chunkSize = Math.max(1, batchSize);
        for (int offset = 0; offset < due.size(); offset += chunkSize) {
            int end = Math.min(offset + chunkSize, due.size());
            processed += processBatch(due.subList(offset, end), now);
        }
        return processed;
    }

    private int processBatch(List<FinalityOutboxEntry> batch, Instant now) {
        // Idempotency guard: only CONFIRMED records are eligible. A record that
        // already reached a terminal state is skipped, never re-processed.
        List<FinalityOutboxEntry> eligible = batch.stream()
                .filter(entry -> entry.getStatus() == SettlementStatus.CONFIRMED)
                .toList();
        if (eligible.isEmpty()) {
            return 0;
        }

        List<String> signatures = eligible.stream()
                .map(FinalityOutboxEntry::getSolanaTransactionSignature)
                .filter(sig -> sig != null && !sig.isBlank())
                .toList();

        if (signatures.isEmpty()) {
            // Fail closed: without a signature the record can never be confirmed.
            for (FinalityOutboxEntry entry : eligible) {
                entry.setLastPolledAt(now);
                entry.setStatus(SettlementStatus.FAILED);
                entry.setErrorMessage("Missing transaction signature; finality cannot be confirmed");
                entry.setNextPollAt(null);
            }
            return eligible.size();
        }

        List<SignatureStatusResult> statuses;
        try {
            statuses = rpcAdapter.getSignatureStatuses(signatures);
        } catch (SolanaRpcException ex) {
            applyTransientFailure(eligible, ex.getMessage(), now);
            return eligible.size();
        }

        Map<String, SignatureStatusResult> bySignature = statuses.stream()
                .filter(result -> result.signature() != null)
                .collect(Collectors.toMap(
                        SignatureStatusResult::signature,
                        Function.identity(),
                        (first, second) -> first));

        for (FinalityOutboxEntry entry : eligible) {
            String signature = entry.getSolanaTransactionSignature();
            if (signature == null || signature.isBlank()) {
                entry.setStatus(SettlementStatus.FAILED);
                entry.setErrorMessage("Missing transaction signature; finality cannot be confirmed");
                entry.setNextPollAt(null);
                continue;
            }
            applyTransition(entry, bySignature.get(signature), now);
        }
        return eligible.size();
    }

    // -- transition helpers --

    /**
     * Applies the fail-closed state transition for a single record based on its
     * on-chain signature status.
     */
    void applyTransition(FinalityOutboxEntry entry, SignatureStatusResult result, Instant now) {
        entry.setLastPolledAt(now);

        if (result != null && result.hasError()) {
            entry.setStatus(SettlementStatus.FAILED);
            entry.setErrorMessage(sanitizeError(result.err()));
            entry.setNextPollAt(null);
            return;
        }

        if (result != null && result.isFinalized()) {
            entry.setStatus(SettlementStatus.FINALIZED);
            entry.setCommitmentLevel(COMMITMENT_FINALIZED);
            entry.setSettledAt(now);
            entry.setErrorMessage(null);
            entry.setNextPollAt(null);
            return;
        }

        if (result != null && result.confirmationStatus() != null) {
            entry.setCommitmentLevel(result.confirmationStatus().toUpperCase());
        }

        int attempts = entry.getPollAttempts() + 1;
        entry.setPollAttempts(attempts);
        int maxAttempts = entry.getMaxPollAttempts() > 0 ? entry.getMaxPollAttempts() : 30;
        if (attempts >= maxAttempts) {
            entry.setStatus(SettlementStatus.EXPIRED);
            entry.setErrorMessage("Finality confirmation timed out after " + attempts + " poll attempt(s)");
            entry.setNextPollAt(null);
        } else {
            entry.setNextPollAt(nextPollAt(attempts, now));
        }
    }

    /**
     * Applies a transient transport failure to every eligible record: increment
     * the retry counter, schedule the next backoff, and fail-closed to
     * {@code EXPIRED} once the per-record max attempts is exhausted.
     */
    void applyTransientFailure(List<FinalityOutboxEntry> entries, String errorMessage, Instant now) {
        for (FinalityOutboxEntry entry : entries) {
            entry.setLastPolledAt(now);
            int attempts = entry.getPollAttempts() + 1;
            entry.setPollAttempts(attempts);
            int maxAttempts = entry.getMaxPollAttempts() > 0 ? entry.getMaxPollAttempts() : 30;
            if (attempts >= maxAttempts) {
                entry.setStatus(SettlementStatus.EXPIRED);
                entry.setErrorMessage(sanitizeError(errorMessage));
                entry.setNextPollAt(null);
            } else {
                entry.setErrorMessage(sanitizeError(errorMessage));
                entry.setNextPollAt(nextPollAt(attempts, now));
            }
        }
    }

    Instant nextPollAt(int pollAttempts, Instant now) {
        return now.plusMillis(backoffDelayMs(pollAttempts));
    }

    long backoffDelayMs(int pollAttempts) {
        return backoffDelayMs(pollAttempts, baseBackoffMs, maxBackoffMs);
    }

    /**
     * Exponential backoff schedule: 2s, 4s, 8s, ... capped at {@code maxBackoffMs}.
     */
    static long backoffDelayMs(int pollAttempts, long baseBackoffMs, long maxBackoffMs) {
        int attempts = Math.max(pollAttempts, 1);
        long exponent = Math.min((long) attempts - 1, 30L);
        long delay = baseBackoffMs;
        if (exponent > 0) {
            delay = baseBackoffMs * (1L << exponent);
        }
        if (delay < 0 || delay > maxBackoffMs) {
            delay = maxBackoffMs;
        }
        return delay;
    }

    private static String sanitizeError(Object err) {
        if (err == null) {
            return null;
        }
        String text = String.valueOf(err);
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
