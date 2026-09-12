package com.solana.rwa.bridge.repository;

import com.solana.rwa.bridge.entity.TransferHookAuditLog;
import com.solana.rwa.bridge.entity.TransferHookAuditStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA repository integration tests for {@link TransferHookAuditLogRepository}
 * (H2, PostgreSQL mode). Exercises the V5 Flyway schema and the immutable
 * transfer-hook compliance audit ledger.
 */
@DataJpaTest
@ActiveProfiles("test")
class TransferHookAuditLogRepositoryIT {

    @Autowired
    private TransferHookAuditLogRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    private TransferHookAuditLog auditLog(TransferHookAuditStatus status, String transactionSignature) {
        return auditLog(status, transactionSignature, null);
    }

    private TransferHookAuditLog auditLog(TransferHookAuditStatus status, String transactionSignature,
                                          Instant createdAt) {
        return TransferHookAuditLog.builder()
                .transactionSignature(transactionSignature)
                .mintAddress("MINT-WALLET-1")
                .sourceWallet("SOURCE-WALLET-1")
                .destinationWallet("DESTINATION-WALLET-1")
                .amount(1_000_000L)
                .complianceStatus(status)
                .reasonCode("COMPLIANCE:APPROVED")
                .createdAt(createdAt)
                .build();
    }

    @Test
    void save_persistsAuditLogWithGeneratedUuidAndCreatedAt() {
        TransferHookAuditLog saved = repository.save(
                auditLog(TransferHookAuditStatus.CLEARED, "5Kg...signature"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getMintAddress()).isEqualTo("MINT-WALLET-1");
        assertThat(saved.getAmount()).isEqualTo(1_000_000L);
        assertThat(saved.getComplianceStatus()).isEqualTo(TransferHookAuditStatus.CLEARED);
    }

    @Test
    void save_blockedEvaluationPersistsNullTransactionSignature() {
        TransferHookAuditLog saved = repository.save(
                auditLog(TransferHookAuditStatus.BLOCKED, null));

        // Blocked evaluations never broadcast, so the signature column stays null.
        assertThat(saved.getTransactionSignature()).isNull();
        assertThat(saved.getComplianceStatus()).isEqualTo(TransferHookAuditStatus.BLOCKED);
    }

    @Test
    void save_clearedEvaluationPersistsTransactionSignature() {
        TransferHookAuditLog saved = repository.save(
                auditLog(TransferHookAuditStatus.CLEARED, "5Kg...signature"));

        assertThat(saved.getTransactionSignature()).isEqualTo("5Kg...signature");
    }

    @Test
    void findByMintAddress_returnsLogsForMint() {
        testEntityManager.persistAndFlush(auditLog(TransferHookAuditStatus.CLEARED, "sig-1"));
        testEntityManager.persistAndFlush(auditLog(TransferHookAuditStatus.BLOCKED, null));

        List<TransferHookAuditLog> logs = repository.findByMintAddress("MINT-WALLET-1");

        assertThat(logs).hasSize(2);
        assertThat(logs)
                .extracting(TransferHookAuditLog::getComplianceStatus)
                .containsExactlyInAnyOrder(TransferHookAuditStatus.CLEARED, TransferHookAuditStatus.BLOCKED);
    }

    @Test
    void findBySourceWallet_returnsLogsForSource() {
        testEntityManager.persistAndFlush(auditLog(TransferHookAuditStatus.CLEARED, "sig-1"));
        testEntityManager.persistAndFlush(auditLog(TransferHookAuditStatus.CLEARED, "sig-2"));

        List<TransferHookAuditLog> logs = repository.findBySourceWallet("SOURCE-WALLET-1");

        assertThat(logs).hasSize(2);
    }

    @Test
    void findByDestinationWallet_returnsLogsForDestination() {
        testEntityManager.persistAndFlush(auditLog(TransferHookAuditStatus.CLEARED, "sig-1"));

        List<TransferHookAuditLog> logs = repository.findByDestinationWallet("DESTINATION-WALLET-1");

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getTransactionSignature()).isEqualTo("sig-1");
    }

    @Test
    void findByComplianceStatus_returnsOnlyMatchingStatus() {
        testEntityManager.persistAndFlush(auditLog(TransferHookAuditStatus.CLEARED, "sig-1"));
        testEntityManager.persistAndFlush(auditLog(TransferHookAuditStatus.CLEARED, "sig-2"));
        testEntityManager.persistAndFlush(auditLog(TransferHookAuditStatus.BLOCKED, null));

        List<TransferHookAuditLog> cleared = repository.findByComplianceStatus(TransferHookAuditStatus.CLEARED);
        List<TransferHookAuditLog> blocked = repository.findByComplianceStatus(TransferHookAuditStatus.BLOCKED);

        assertThat(cleared).hasSize(2);
        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).getTransactionSignature()).isNull();
    }

    @Test
    void findByCreatedAtAfter_returnsLogsAfterInstant() {
        Instant cutoff = Instant.parse("2025-06-01T00:00:00Z");

        TransferHookAuditLog older = auditLog(
                TransferHookAuditStatus.CLEARED, "sig-old", cutoff.plusSeconds(1));
        testEntityManager.persistAndFlush(older);

        TransferHookAuditLog newer = auditLog(
                TransferHookAuditStatus.CLEARED, "sig-new", cutoff.plusSeconds(2));
        testEntityManager.persistAndFlush(newer);

        List<TransferHookAuditLog> afterCutoff = repository.findByCreatedAtAfter(cutoff);
        List<TransferHookAuditLog> afterAll = repository.findByCreatedAtAfter(cutoff.plusSeconds(3));

        assertThat(afterCutoff).hasSize(2);
        assertThat(afterAll).isEmpty();
    }

    @Test
    void nullMintAddress_violatesNotNullConstraint() {
        TransferHookAuditLog invalid = auditLog(TransferHookAuditStatus.CLEARED, "sig-1");
        invalid.setMintAddress(null);

        assertThatThrownBy(() -> repository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flywaySchema_createsTransferHookAuditLogsTable() {
        Number count = (Number) testEntityManager.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM transfer_hook_audit_logs")
                .getSingleResult();

        assertThat(count.longValue()).isZero();
    }

}
