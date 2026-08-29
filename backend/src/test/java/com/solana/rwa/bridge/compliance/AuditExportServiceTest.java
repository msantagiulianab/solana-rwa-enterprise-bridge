package com.solana.rwa.bridge.compliance;

import com.solana.rwa.bridge.compliance.dto.AuditExportRecordDto;
import com.solana.rwa.bridge.compliance.service.AuditExportService;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import com.solana.rwa.bridge.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditExportService} query/aggregation rules and the
 * repository-backed {@code export} wiring.
 *
 * <p>Verifies immutable settlement-proof audit records can be filtered by
 * inclusive ISO-8601 date range, {@code assetId}, and transaction execution
 * status ({@code SUCCESS}, {@code FAILED_COMPLIANCE}, {@code FAILED_RPC}),
 * that empty result sets never surface null-pointer regressions, and that
 * {@code export} maps the persisted {@link AuditLog} ledger into DTOs.
 */
@ExtendWith(MockitoExtension.class)
class AuditExportServiceTest {

    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED_COMPLIANCE = "FAILED_COMPLIANCE";
    private static final String FAILED_RPC = "FAILED_RPC";

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditExportService service;

    private AuditExportRecordDto record(String assetId, Instant timestamp, String status) {
        return AuditExportRecordDto.builder()
                .eventId(UUID.randomUUID())
                .timestamp(timestamp)
                .assetId(assetId)
                .investorWallet(WALLET)
                .kycVerified(true)
                .ofacPassed(true)
                .status(status)
                .computeUnitPriceMicroLamports(5_000L)
                .computeUnitLimit(10_000)
                .solanaTransactionSignature("5Kg...signature")
                .slot(123_456L)
                .blockhash("6Fg...blockhash")
                .build();
    }

    @Test
    void query_filtersByInclusiveIso8601DateRange() {
        Instant start = Instant.parse("2025-06-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-30T23:59:59Z");

        AuditExportRecordDto atStart = record("ASSET-1", Instant.parse("2025-06-01T00:00:00Z"), SUCCESS);
        AuditExportRecordDto inside = record("ASSET-1", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS);
        AuditExportRecordDto atEnd = record("ASSET-1", Instant.parse("2025-06-30T23:59:59Z"), SUCCESS);
        AuditExportRecordDto before = record("ASSET-1", Instant.parse("2025-05-31T23:59:59Z"), SUCCESS);
        AuditExportRecordDto after = record("ASSET-1", Instant.parse("2025-07-01T00:00:00Z"), SUCCESS);

        List<AuditExportRecordDto> result = service.query(
                List.of(before, atStart, inside, atEnd, after), start, end, null, null);

        assertThat(result).containsExactly(atStart, inside, atEnd);
    }

    @Test
    void query_filtersByAssetId() {
        AuditExportRecordDto assetA = record("ASSET-A", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS);
        AuditExportRecordDto assetB = record("ASSET-B", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS);

        List<AuditExportRecordDto> result = service.query(
                List.of(assetA, assetB), null, null, "ASSET-A", null);

        assertThat(result).containsExactly(assetA);
    }

    @Test
    void query_filtersByExecutionStatus() {
        AuditExportRecordDto success = record("ASSET-1", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS);
        AuditExportRecordDto failedCompliance = record("ASSET-1", Instant.parse("2025-06-15T12:00:00Z"), FAILED_COMPLIANCE);
        AuditExportRecordDto failedRpc = record("ASSET-1", Instant.parse("2025-06-15T12:00:00Z"), FAILED_RPC);
        List<AuditExportRecordDto> all = List.of(success, failedCompliance, failedRpc);

        assertThat(service.query(all, null, null, null, SUCCESS)).containsExactly(success);
        assertThat(service.query(all, null, null, null, FAILED_COMPLIANCE)).containsExactly(failedCompliance);
        assertThat(service.query(all, null, null, null, FAILED_RPC)).containsExactly(failedRpc);
    }

    @Test
    void query_composesDateAssetAndStatusFilters() {
        AuditExportRecordDto match = record("ASSET-A", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS);
        AuditExportRecordDto wrongAsset = record("ASSET-B", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS);
        AuditExportRecordDto wrongStatus = record("ASSET-A", Instant.parse("2025-06-15T12:00:00Z"), FAILED_COMPLIANCE);
        AuditExportRecordDto wrongDate = record("ASSET-A", Instant.parse("2025-07-15T12:00:00Z"), SUCCESS);

        List<AuditExportRecordDto> result = service.query(
                List.of(match, wrongAsset, wrongStatus, wrongDate),
                Instant.parse("2025-06-01T00:00:00Z"),
                Instant.parse("2025-06-30T23:59:59Z"),
                "ASSET-A",
                SUCCESS);

        assertThat(result).containsExactly(match);
    }

    @Test
    void query_returnsAllRecordsWhenNoFiltersProvided() {
        AuditExportRecordDto first = record("ASSET-A", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS);
        AuditExportRecordDto second = record("ASSET-B", Instant.parse("2025-06-16T12:00:00Z"), FAILED_COMPLIANCE);

        assertThat(service.query(List.of(first, second), null, null, null, null))
                .containsExactly(first, second);
    }

    @Test
    void query_returnsEmptyListWhenNothingMatches() {
        AuditExportRecordDto record = record("ASSET-A", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS);

        assertThat(service.query(List.of(record),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z"),
                null, null)).isEmpty();

        assertThat(service.query(List.of(record), null, null, "UNKNOWN-ASSET", null)).isEmpty();
        assertThat(service.query(List.of(record), null, null, null, "UNKNOWN_STATUS")).isEmpty();
    }

    @Test
    void query_returnsEmptyListForEmptyInput() {
        assertThat(service.query(List.of(), null, null, null, null)).isEmpty();
        assertThat(service.query(null, null, null, null, null)).isEmpty();
    }

    @Test
    void query_ignoresBlankAssetIdAndStatusFilters() {
        AuditExportRecordDto record = record("ASSET-A", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS);

        assertThat(service.query(List.of(record), null, null, "  ", "  "))
                .containsExactly(record);
    }

    @Test
    void dto_capturesCryptographicSettlementProofs() {
        UUID eventId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2025-06-15T12:00:00Z");

        AuditExportRecordDto record = AuditExportRecordDto.builder()
                .eventId(eventId)
                .timestamp(timestamp)
                .assetId("ASSET-1")
                .investorWallet(WALLET)
                .kycVerified(true)
                .ofacPassed(true)
                .status(SUCCESS)
                .computeUnitPriceMicroLamports(12_500L)
                .computeUnitLimit(200_000)
                .solanaTransactionSignature("5Kg...signature")
                .slot(987_654L)
                .blockhash("6Fg...blockhash")
                .build();

        assertThat(record.getEventId()).isEqualTo(eventId);
        assertThat(record.getTimestamp()).isEqualTo(timestamp);
        assertThat(record.getAssetId()).isEqualTo("ASSET-1");
        assertThat(record.getInvestorWallet()).isEqualTo(WALLET);
        assertThat(record.isKycVerified()).isTrue();
        assertThat(record.isOfacPassed()).isTrue();
        assertThat(record.getStatus()).isEqualTo(SUCCESS);
        assertThat(record.getComputeUnitPriceMicroLamports()).isEqualTo(12_500L);
        assertThat(record.getComputeUnitLimit()).isEqualTo(200_000);
        assertThat(record.getSolanaTransactionSignature()).isEqualTo("5Kg...signature");
        assertThat(record.getSlot()).isEqualTo(987_654L);
        assertThat(record.getBlockhash()).isEqualTo("6Fg...blockhash");
    }

    @Test
    void export_mapsPersistedLedgerToSettlementProofRecords() {
        UUID eventId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2025-06-15T12:00:00Z");
        AuditLog log = auditLog("ASSET-1", timestamp, SUCCESS, "5Kg...signature");
        log.setId(eventId);
        when(auditLogRepository.findAll()).thenReturn(List.of(log));

        List<AuditExportRecordDto> result = service.export(null, null, null, null);

        assertThat(result).hasSize(1);
        AuditExportRecordDto record = result.get(0);
        assertThat(record.getEventId()).isEqualTo(eventId);
        assertThat(record.getTimestamp()).isEqualTo(timestamp);
        assertThat(record.getAssetId()).isEqualTo("ASSET-1");
        assertThat(record.getInvestorWallet()).isEqualTo(WALLET);
        assertThat(record.isKycVerified()).isTrue();
        assertThat(record.isOfacPassed()).isTrue();
        assertThat(record.getStatus()).isEqualTo(SUCCESS);
        assertThat(record.getComputeUnitPriceMicroLamports()).isEqualTo(5_000L);
        assertThat(record.getComputeUnitLimit()).isEqualTo(10_000);
        assertThat(record.getSolanaTransactionSignature()).isEqualTo("5Kg...signature");
        assertThat(record.getSlot()).isEqualTo(123_456L);
        assertThat(record.getBlockhash()).isEqualTo("6Fg...blockhash");

        verify(auditLogRepository).findAll();
    }

    @Test
    void export_coalescesNullSettlementMetadataToSafeDefaults() {
        AuditLog legacy = AuditLog.builder()
                .walletAddress(WALLET)
                .action("CHECK_ELIGIBILITY")
                .status(AuditLogStatus.APPROVED)
                .reason("Investor KYC verified and asset compliant")
                .timestamp(Instant.parse("2025-06-15T12:00:00Z"))
                .build();
        when(auditLogRepository.findAll()).thenReturn(List.of(legacy));

        List<AuditExportRecordDto> result = service.export(null, null, null, null);

        assertThat(result).hasSize(1);
        AuditExportRecordDto record = result.get(0);
        assertThat(record.isKycVerified()).isFalse();
        assertThat(record.isOfacPassed()).isFalse();
        assertThat(record.getComputeUnitPriceMicroLamports()).isZero();
        assertThat(record.getComputeUnitLimit()).isZero();
        assertThat(record.getStatus()).isNull();
        assertThat(record.getAssetId()).isNull();
        assertThat(record.getSolanaTransactionSignature()).isNull();
        assertThat(record.getSlot()).isNull();
        assertThat(record.getBlockhash()).isNull();
    }

    @Test
    void export_returnsEmptyListWhenLedgerEmpty() {
        when(auditLogRepository.findAll()).thenReturn(List.of());

        assertThat(service.export(null, null, null, null)).isEmpty();
    }

    @Test
    void export_appliesFiltersToPersistedLedger() {
        Instant start = Instant.parse("2025-06-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-30T23:59:59Z");
        AuditLog match = auditLog("ASSET-A", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS, "sigA");
        AuditLog wrongAsset = auditLog("ASSET-B", Instant.parse("2025-06-15T12:00:00Z"), SUCCESS, "sigB");
        AuditLog wrongDate = auditLog("ASSET-A", Instant.parse("2025-07-15T12:00:00Z"), SUCCESS, "sigC");
        when(auditLogRepository.findAll()).thenReturn(List.of(match, wrongAsset, wrongDate));

        List<AuditExportRecordDto> result = service.export(start, end, "ASSET-A", SUCCESS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAssetId()).isEqualTo("ASSET-A");
        assertThat(result.get(0).getSolanaTransactionSignature()).isEqualTo("sigA");
    }

    private AuditLog auditLog(String assetId, Instant timestamp, String settlementStatus, String signature) {
        return AuditLog.builder()
                .walletAddress(WALLET)
                .action("TOKENIZE_ASSET")
                .status(AuditLogStatus.APPROVED)
                .reason("Asset tokenized")
                .timestamp(timestamp)
                .idempotencyKey("idem-" + assetId)
                .assetId(assetId)
                .kycVerified(true)
                .ofacPassed(true)
                .settlementStatus(settlementStatus)
                .computeUnitPriceMicroLamports(5_000L)
                .computeUnitLimit(10_000)
                .solanaTransactionSignature(signature)
                .slot(123_456L)
                .blockhash("6Fg...blockhash")
                .build();
    }
}
