package com.solana.rwa.bridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable outbox row consumed by the finality confirmation worker.
 *
 * <p>A row is enqueued when an on-chain settlement is dispatched and reaches
 * {@link SettlementStatus#CONFIRMED}. The background worker polls due rows and
 * advances them to {@link SettlementStatus#FINALIZED} once the transaction
 * signature is observed at the finalized commitment, or to
 * {@link SettlementStatus#FAILED}/{@link SettlementStatus#EXPIRED} on a
 * transaction error or a timeout.
 *
 * <p>Polling bookkeeping ({@code pollAttempts}, {@code nextPollAt},
 * {@code commitmentLevel}, {@code errorMessage}) lives here — never on the
 * immutable {@link AuditLog} ledger or the {@link AssetToken} source of truth.
 */
@Entity
@Table(name = "finality_outbox",
        indexes = {
                @Index(name = "idx_settlements_outbox_polling",
                        columnList = "status, commitment_level, next_poll_at"),
                @Index(name = "idx_finality_outbox_next_poll_at", columnList = "next_poll_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class FinalityOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "asset_token_id")
    private UUID assetTokenId;

    @Column(name = "idempotency_key", unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "solana_transaction_signature", length = 88)
    private String solanaTransactionSignature;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SettlementStatus status;

    @Column(name = "commitment_level", length = 32)
    private String commitmentLevel;

    @Column(name = "poll_attempts", nullable = false)
    private int pollAttempts;

    @Column(name = "max_poll_attempts", nullable = false)
    private int maxPollAttempts = 30;

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Column(name = "next_poll_at")
    private Instant nextPollAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public FinalityOutboxEntry(UUID assetTokenId, String idempotencyKey, String solanaTransactionSignature,
                               SettlementStatus status, String commitmentLevel, int pollAttempts,
                               int maxPollAttempts, Instant lastPolledAt, Instant nextPollAt,
                               String errorMessage, Instant settledAt) {
        this.assetTokenId = assetTokenId;
        this.idempotencyKey = idempotencyKey;
        this.solanaTransactionSignature = solanaTransactionSignature;
        this.status = status;
        this.commitmentLevel = commitmentLevel;
        this.pollAttempts = pollAttempts;
        this.maxPollAttempts = maxPollAttempts > 0 ? maxPollAttempts : 30;
        this.lastPolledAt = lastPolledAt;
        this.nextPollAt = nextPollAt;
        this.errorMessage = errorMessage;
        this.settledAt = settledAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
