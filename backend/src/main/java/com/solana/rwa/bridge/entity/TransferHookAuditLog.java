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
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, append-only audit trail of every transfer-hook compliance
 * evaluation ({@code CLEARED} or {@code BLOCKED}).
 *
 * <p>The ledger is write-once: {@code created_at} is set on persist and is
 * non-updatable, and there is no {@code updated_at} column. A {@code BLOCKED}
 * evaluation never broadcasts a transaction, so its
 * {@code transaction_signature} remains {@code null} — mirroring the nullable
 * column contract in the V5 Flyway migration.
 */
@Entity
@Table(name = "transfer_hook_audit_logs",
        indexes = {
                @Index(name = "idx_transfer_hook_audit_logs_mint_address", columnList = "mint_address"),
                @Index(name = "idx_transfer_hook_audit_logs_source_wallet", columnList = "source_wallet"),
                @Index(name = "idx_transfer_hook_audit_logs_destination_wallet", columnList = "destination_wallet"),
                @Index(name = "idx_transfer_hook_audit_logs_created_at", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class TransferHookAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_signature", length = 88)
    private String transactionSignature;

    @Column(name = "mint_address", nullable = false, length = 44)
    private String mintAddress;

    @Column(name = "source_wallet", nullable = false, length = 44)
    private String sourceWallet;

    @Column(name = "destination_wallet", nullable = false, length = 44)
    private String destinationWallet;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "compliance_status", nullable = false, length = 16)
    private TransferHookAuditStatus complianceStatus;

    @Column(name = "reason_code", length = 255)
    private String reasonCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public TransferHookAuditLog(String transactionSignature, String mintAddress, String sourceWallet,
                                String destinationWallet, long amount, TransferHookAuditStatus complianceStatus,
                                String reasonCode, Instant createdAt) {
        this.transactionSignature = transactionSignature;
        this.mintAddress = mintAddress;
        this.sourceWallet = sourceWallet;
        this.destinationWallet = destinationWallet;
        this.amount = amount;
        this.complianceStatus = complianceStatus;
        this.reasonCode = reasonCode;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
