package com.solana.rwa.bridge.repository;

import com.solana.rwa.bridge.entity.TransferHookAuditLog;
import com.solana.rwa.bridge.entity.TransferHookAuditStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence access for the immutable {@link TransferHookAuditLog} ledger.
 */
public interface TransferHookAuditLogRepository extends JpaRepository<TransferHookAuditLog, UUID> {

    List<TransferHookAuditLog> findByMintAddress(String mintAddress);

    List<TransferHookAuditLog> findBySourceWallet(String sourceWallet);

    List<TransferHookAuditLog> findByDestinationWallet(String destinationWallet);

    List<TransferHookAuditLog> findByComplianceStatus(TransferHookAuditStatus complianceStatus);

    List<TransferHookAuditLog> findByCreatedAtAfter(Instant createdAt);
}
