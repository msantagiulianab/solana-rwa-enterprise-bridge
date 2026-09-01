package com.solana.rwa.bridge.maritime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Panama Canal transit settlement for a {@link BillOfLading}.
 *
 * <p>Captures the transit fee, SPL settlement token mint, escrow account, and
 * the settlement lifecycle. Once cleared, the settlement is linked to a
 * {@code finality_outbox} row (via {@code outboxEntryId}) for asynchronous
 * on-chain finality confirmation.
 */
@Entity
@Table(name = "canal_transit_settlements")
@Getter
@Setter
@NoArgsConstructor
public class CanalTransitSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_of_lading_id", nullable = false)
    private BillOfLading billOfLading;

    @Column(name = "transit_booking_reference", nullable = false, unique = true, length = 255)
    private String transitBookingReference;

    @Column(name = "transit_fee_usd", nullable = false, precision = 18, scale = 2)
    private BigDecimal transitFeeUsd;

    @Column(name = "settlement_token_mint", nullable = false, length = 44)
    private String settlementTokenMint;

    @Column(name = "escrow_account", nullable = false, length = 44)
    private String escrowAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransitSettlementStatus status = TransitSettlementStatus.INITIALIZED;

    @Column(name = "transaction_signature", length = 88)
    private String transactionSignature;

    @Column(name = "outbox_entry_id")
    private UUID outboxEntryId;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public CanalTransitSettlement(BillOfLading billOfLading, String transitBookingReference,
                                  BigDecimal transitFeeUsd, String settlementTokenMint,
                                  String escrowAccount, TransitSettlementStatus status,
                                  String transactionSignature, UUID outboxEntryId,
                                  Instant settledAt) {
        this.billOfLading = billOfLading;
        this.transitBookingReference = transitBookingReference;
        this.transitFeeUsd = transitFeeUsd;
        this.settlementTokenMint = settlementTokenMint;
        this.escrowAccount = escrowAccount;
        this.status = status != null ? status : TransitSettlementStatus.INITIALIZED;
        this.transactionSignature = transactionSignature;
        this.outboxEntryId = outboxEntryId;
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
