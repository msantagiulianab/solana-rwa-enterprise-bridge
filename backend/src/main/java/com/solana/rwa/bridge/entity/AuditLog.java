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
 * Immutable audit trail of every transaction attempt (approved or blocked),
 * in accordance with the enterprise compliance rules.
 */
@Entity
@Table(name = "audit_logs",
        indexes = @Index(name = "idx_audit_logs_wallet_address", columnList = "wallet_address"))
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_address", nullable = false, length = 44)
    private String walletAddress;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AuditLogStatus status;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @Builder
    public AuditLog(String walletAddress, String action, AuditLogStatus status, String reason, Instant timestamp) {
        this.walletAddress = walletAddress;
        this.action = action;
        this.status = status;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    @PrePersist
    void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }
}
