package com.solana.rwa.bridge.repository;

import com.solana.rwa.bridge.entity.FinalityOutboxEntry;
import com.solana.rwa.bridge.entity.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence access for {@link FinalityOutboxEntry} outbox records.
 */
public interface FinalityOutboxRepository extends JpaRepository<FinalityOutboxEntry, UUID> {

    /**
     * Returns outbox rows that are due for a finality poll: still
     * {@link SettlementStatus#CONFIRMED}, not yet observed at the finalized
     * commitment, and whose next-poll time has arrived.
     *
     * <p>{@code coalesce(commitmentLevel, 'CONFIRMED') <> 'FINALIZED'} keeps
     * null commitment levels pollable while guaranteeing a finalized record is
     * never re-selected (idempotency).
     */
    @Query("""
            select e from FinalityOutboxEntry e
            where e.status = :status
              and coalesce(e.commitmentLevel, 'CONFIRMED') <> 'FINALIZED'
              and e.nextPollAt <= :now
            order by e.nextPollAt asc
            """)
    List<FinalityOutboxEntry> findDueForPolling(@Param("status") SettlementStatus status,
                                                @Param("now") Instant now);
}
