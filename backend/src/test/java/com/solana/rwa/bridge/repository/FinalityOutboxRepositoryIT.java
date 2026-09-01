package com.solana.rwa.bridge.repository;

import com.solana.rwa.bridge.entity.FinalityOutboxEntry;
import com.solana.rwa.bridge.entity.SettlementStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA repository integration tests for {@link FinalityOutboxRepository} against
 * the V3 Flyway migration (H2, PostgreSQL mode).
 */
@DataJpaTest
@ActiveProfiles("test")
class FinalityOutboxRepositoryIT {

    @Autowired
    private FinalityOutboxRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void save_persistsOutboxEntryWithDefaultsAndTimestamps() {
        FinalityOutboxEntry entry = FinalityOutboxEntry.builder()
                .solanaTransactionSignature("sig123")
                .status(SettlementStatus.CONFIRMED)
                .commitmentLevel("CONFIRMED")
                .build();

        FinalityOutboxEntry saved = repository.save(entry);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getPollAttempts()).isZero();
        assertThat(saved.getMaxPollAttempts()).isEqualTo(30);
    }

    @Test
    void findDueForPolling_returnsOnlyDueConfirmedRecords() {
        Instant now = Instant.now();

        FinalityOutboxEntry due = testEntityManager.persistAndFlush(entry(
                SettlementStatus.CONFIRMED, "CONFIRMED", now.minusSeconds(10)));
        testEntityManager.persistAndFlush(entry(
                SettlementStatus.CONFIRMED, "CONFIRMED", now.plusSeconds(10))); // future: not due
        testEntityManager.persistAndFlush(entry(
                SettlementStatus.FINALIZED, "FINALIZED", now.minusSeconds(10))); // terminal: excluded
        testEntityManager.persistAndFlush(entry(
                SettlementStatus.EXPIRED, "CONFIRMED", now.minusSeconds(10))); // terminal: excluded

        List<FinalityOutboxEntry> result = repository.findDueForPolling(SettlementStatus.CONFIRMED, now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(due.getId());
    }

    private FinalityOutboxEntry entry(SettlementStatus status, String commitmentLevel, Instant nextPollAt) {
        return FinalityOutboxEntry.builder()
                .solanaTransactionSignature("sig-" + status.name() + "-" + nextPollAt.toEpochMilli())
                .status(status)
                .commitmentLevel(commitmentLevel)
                .nextPollAt(nextPollAt)
                .build();
    }
}
