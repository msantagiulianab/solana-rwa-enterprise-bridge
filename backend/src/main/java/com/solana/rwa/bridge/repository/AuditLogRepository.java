package com.solana.rwa.bridge.repository;

import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for the immutable {@link AuditLog} trail.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByWalletAddress(String walletAddress);

    List<AuditLog> findByWalletAddressAndStatus(String walletAddress, AuditLogStatus status);

    List<AuditLog> findByWalletAddressAndAction(String walletAddress, String action);

    List<AuditLog> findByTimestampAfter(Instant timestamp);

    Optional<AuditLog> findFirstByWalletAddressOrderByTimestampDesc(String walletAddress);
}
