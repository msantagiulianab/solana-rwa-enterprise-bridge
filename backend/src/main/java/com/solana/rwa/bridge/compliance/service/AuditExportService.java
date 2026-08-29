package com.solana.rwa.bridge.compliance.service;

import com.solana.rwa.bridge.compliance.dto.AuditExportRecordDto;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Query/aggregation service over the immutable settlement-proof audit ledger.
 *
 * <p>{@link #export(Instant, Instant, String, String)} loads the actual
 * immutable {@link AuditLog} records from the persisted
 * {@link AuditLogRepository}, maps them to {@link AuditExportRecordDto}, and
 * then applies optional inclusive ISO-8601 date-range, asset-id, and execution
 * status filters. The service is fail-safe: an empty ledger or null/blank
 * filters never throw and yield an empty (never null) result when nothing
 * matches.
 */
@Service
@RequiredArgsConstructor
public class AuditExportService {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED_COMPLIANCE = "FAILED_COMPLIANCE";
    public static final String STATUS_FAILED_RPC = "FAILED_RPC";

    private final AuditLogRepository auditLogRepository;

    /**
     * Exports the persisted immutable audit ledger, filtered by the supplied
     * criteria.
     *
     * @param startDate inclusive lower bound on record timestamp; null = unbounded
     * @param endDate   inclusive upper bound on record timestamp; null = unbounded
     * @param assetId   exact asset id match; null/blank = unbounded
     * @param status    exact execution status match; null/blank = unbounded
     * @return filtered settlement-proof records, or an empty immutable list
     */
    public List<AuditExportRecordDto> export(Instant startDate,
                                             Instant endDate,
                                             String assetId,
                                             String status) {
        List<AuditExportRecordDto> records = auditLogRepository.findAll().stream()
                .map(this::toRecord)
                .toList();
        return query(records, startDate, endDate, assetId, status);
    }

    /**
     * Filters audit records by the supplied criteria.
     *
     * @param records   audit ledger records to filter (null is treated as empty)
     * @param startDate inclusive lower bound on record timestamp; null = unbounded
     * @param endDate   inclusive upper bound on record timestamp; null = unbounded
     * @param assetId   exact asset id match; null/blank = unbounded
     * @param status    exact execution status match; null/blank = unbounded
     * @return filtered records in original order, or an empty immutable list
     */
    public List<AuditExportRecordDto> query(List<AuditExportRecordDto> records,
                                            Instant startDate,
                                            Instant endDate,
                                            String assetId,
                                            String status) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        Stream<AuditExportRecordDto> stream = records.stream();

        if (startDate != null) {
            stream = stream.filter(record -> record.getTimestamp() != null
                    && !record.getTimestamp().isBefore(startDate));
        }
        if (endDate != null) {
            stream = stream.filter(record -> record.getTimestamp() != null
                    && !record.getTimestamp().isAfter(endDate));
        }

        String assetFilter = normalize(assetId);
        if (assetFilter != null) {
            stream = stream.filter(record -> assetFilter.equals(record.getAssetId()));
        }

        String statusFilter = normalize(status);
        if (statusFilter != null) {
            stream = stream.filter(record -> statusFilter.equals(record.getStatus()));
        }

        return stream.toList();
    }

    private AuditExportRecordDto toRecord(AuditLog log) {
        return AuditExportRecordDto.builder()
                .eventId(log.getId())
                .timestamp(log.getTimestamp())
                .assetId(log.getAssetId())
                .investorWallet(log.getWalletAddress())
                .kycVerified(Boolean.TRUE.equals(log.getKycVerified()))
                .ofacPassed(Boolean.TRUE.equals(log.getOfacPassed()))
                .status(log.getSettlementStatus())
                .computeUnitPriceMicroLamports(log.getComputeUnitPriceMicroLamports() == null
                        ? 0L : log.getComputeUnitPriceMicroLamports())
                .computeUnitLimit(log.getComputeUnitLimit() == null
                        ? 0 : log.getComputeUnitLimit())
                .solanaTransactionSignature(log.getSolanaTransactionSignature())
                .slot(log.getSlot())
                .blockhash(log.getBlockhash())
                .build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
