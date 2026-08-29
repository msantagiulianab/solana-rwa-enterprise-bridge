package com.solana.rwa.bridge.repository;

import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA repository integration tests for {@link AuditLogRepository} (H2, PostgreSQL mode).
 */
@DataJpaTest
@ActiveProfiles("test")
class AuditLogRepositoryIT {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private AuditLog auditLog(String walletAddress, String action, AuditLogStatus status, String reason) {
        return AuditLog.builder()
                .walletAddress(walletAddress)
                .action(action)
                .status(status)
                .reason(reason)
                .timestamp(Instant.now())
                .build();
    }

    @Test
    void save_persistsAuditLogWithGeneratedUuid() {
        AuditLog saved = auditLogRepository.save(
                auditLog("WALLET1", "MINT_TOKEN", AuditLogStatus.APPROVED, "KYC verified"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(AuditLogStatus.APPROVED);
    }

    @Test
    void findByWalletAddress_returnsLogsForWallet() {
        testEntityManager.persistAndFlush(auditLog("WALLET1", "MINT_TOKEN", AuditLogStatus.APPROVED, "ok"));
        testEntityManager.persistAndFlush(auditLog("WALLET1", "TRANSFER_TOKEN", AuditLogStatus.BLOCKED, "AML flag"));
        testEntityManager.persistAndFlush(auditLog("WALLET2", "MINT_TOKEN", AuditLogStatus.APPROVED, "ok"));

        List<AuditLog> wallet1Logs = auditLogRepository.findByWalletAddress("WALLET1");

        assertThat(wallet1Logs).hasSize(2);
        assertThat(wallet1Logs)
                .extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder("MINT_TOKEN", "TRANSFER_TOKEN");
    }

    @Test
    void findByWalletAddressAndStatus_returnsFilteredLogs() {
        testEntityManager.persistAndFlush(auditLog("WALLET1", "MINT_TOKEN", AuditLogStatus.APPROVED, "ok"));
        testEntityManager.persistAndFlush(auditLog("WALLET1", "TRANSFER_TOKEN", AuditLogStatus.BLOCKED, "AML flag"));

        List<AuditLog> blocked = auditLogRepository.findByWalletAddressAndStatus("WALLET1", AuditLogStatus.BLOCKED);

        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).getAction()).isEqualTo("TRANSFER_TOKEN");
        assertThat(blocked.get(0).getReason()).isEqualTo("AML flag");
    }

    @Test
    void findByWalletAddressAndAction_returnsMatchingLogs() {
        testEntityManager.persistAndFlush(auditLog("WALLET1", "MINT_TOKEN", AuditLogStatus.APPROVED, "ok"));
        testEntityManager.persistAndFlush(auditLog("WALLET1", "TRANSFER_TOKEN", AuditLogStatus.BLOCKED, "AML flag"));

        List<AuditLog> mintLogs = auditLogRepository.findByWalletAddressAndAction("WALLET1", "MINT_TOKEN");

        assertThat(mintLogs).hasSize(1);
        assertThat(mintLogs.get(0).getStatus()).isEqualTo(AuditLogStatus.APPROVED);
    }

    @Test
    void findByTimestampAfter_returnsLogsAfterInstant() {
        Instant cutoff = Instant.parse("2025-06-01T00:00:00Z");

        AuditLog older = auditLog("WALLET1", "MINT_TOKEN", AuditLogStatus.APPROVED, "ok");
        older.setTimestamp(cutoff.plusSeconds(1));
        testEntityManager.persistAndFlush(older);

        AuditLog newer = auditLog("WALLET2", "MINT_TOKEN", AuditLogStatus.BLOCKED, "late flag");
        newer.setTimestamp(cutoff.plusSeconds(2));
        testEntityManager.persistAndFlush(newer);

        List<AuditLog> afterCutoff = auditLogRepository.findByTimestampAfter(cutoff);
        List<AuditLog> afterAllLogs = auditLogRepository.findByTimestampAfter(cutoff.plusSeconds(3));

        assertThat(afterCutoff).isNotEmpty();
        assertThat(afterAllLogs).isEmpty();
    }

    @Test
    void findFirstByWalletAddressOrderByTimestampDesc_returnsMostRecentLog() {
        AuditLog older = auditLog("WALLET1", "MINT_TOKEN", AuditLogStatus.APPROVED, "first");
        older.setTimestamp(Instant.now().minusSeconds(60));
        testEntityManager.persistAndFlush(older);

        AuditLog newer = auditLog("WALLET1", "TRANSFER_TOKEN", AuditLogStatus.BLOCKED, "latest");
        newer.setTimestamp(Instant.now());
        testEntityManager.persistAndFlush(newer);

        Optional<AuditLog> mostRecent = auditLogRepository
                .findFirstByWalletAddressOrderByTimestampDesc("WALLET1");

        assertThat(mostRecent).isPresent();
        assertThat(mostRecent.get().getAction()).isEqualTo("TRANSFER_TOKEN");
        assertThat(mostRecent.get().getStatus()).isEqualTo(AuditLogStatus.BLOCKED);
    }

    @Test
    void save_persistsSettlementMetadataColumns() {
        AuditLog log = auditLog("WALLET1", "TOKENIZE_ASSET", AuditLogStatus.APPROVED, "minted");
        log.setIdempotencyKey("idem-settlement-1");
        log.setAssetId("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
        log.setKycVerified(true);
        log.setOfacPassed(true);
        log.setSettlementStatus("SUCCESS");
        log.setComputeUnitPriceMicroLamports(5_000L);
        log.setComputeUnitLimit(10_000);
        log.setSolanaTransactionSignature("5Kg...signature");
        log.setSlot(123_456L);
        log.setBlockhash("6Fg...blockhash");

        AuditLog saved = auditLogRepository.save(log);

        assertThat(saved.getIdempotencyKey()).isEqualTo("idem-settlement-1");
        assertThat(saved.getAssetId()).isEqualTo("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
        assertThat(saved.getKycVerified()).isTrue();
        assertThat(saved.getOfacPassed()).isTrue();
        assertThat(saved.getSettlementStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getComputeUnitPriceMicroLamports()).isEqualTo(5_000L);
        assertThat(saved.getComputeUnitLimit()).isEqualTo(10_000);
        assertThat(saved.getSolanaTransactionSignature()).isEqualTo("5Kg...signature");
        assertThat(saved.getSlot()).isEqualTo(123_456L);
        assertThat(saved.getBlockhash()).isEqualTo("6Fg...blockhash");
    }
}
