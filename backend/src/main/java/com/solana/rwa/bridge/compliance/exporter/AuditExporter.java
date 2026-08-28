package com.solana.rwa.bridge.compliance.exporter;

import com.solana.rwa.bridge.compliance.dto.AuditExportRecordDto;

import java.util.List;

/**
 * Serializes immutable settlement-proof audit records to a deterministic byte
 * stream for compliance export.
 *
 * <p>Implementations are stateless and must produce byte-for-byte identical
 * output for equivalent inputs so auditors can verify ledger integrity.
 */
public interface AuditExporter {

    /**
     * Exports the supplied audit records to a deterministic byte representation.
     *
     * @param records records to export; null or empty yields the format's
     *                canonical empty representation (never null)
     * @return encoded export bytes
     */
    byte[] export(List<AuditExportRecordDto> records);
}
