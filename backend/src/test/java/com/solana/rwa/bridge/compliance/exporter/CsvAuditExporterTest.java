package com.solana.rwa.bridge.compliance.exporter;

import com.solana.rwa.bridge.compliance.dto.AuditExportRecordDto;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the RFC-4180 deterministic CSV exporter.
 *
 * <p>Verifies the canonical column header, exact column ordering, RFC-4180
 * quoting/escaping for fields containing commas, double quotes, and newlines,
 * and the empty-field representation used for nullable settlement proofs.
 */
class CsvAuditExporterTest {

    private static final String CANONICAL_HEADER =
            "EventId,Timestamp,AssetId,InvestorWallet,KYC_Verified,OFAC_Passed,Status,CU_Price_MicroLamports,CU_Limit,Signature,Slot,Blockhash";
    private static final String CRLF = "\r\n";
    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant TIMESTAMP = Instant.parse("2025-06-15T12:00:00Z");

    private final CsvAuditExporter exporter = new CsvAuditExporter();

    private AuditExportRecordDto.AuditExportRecordDtoBuilder baseRecord() {
        return AuditExportRecordDto.builder()
                .eventId(EVENT_ID)
                .timestamp(TIMESTAMP)
                .assetId("ASSET-1")
                .investorWallet(WALLET)
                .kycVerified(true)
                .ofacPassed(true)
                .status("SUCCESS")
                .computeUnitPriceMicroLamports(5_000L)
                .computeUnitLimit(10_000)
                .solanaTransactionSignature("5Kg...signature")
                .slot(123_456L)
                .blockhash("6Fg...blockhash");
    }

    private String export(AuditExportRecordDto record) {
        return new String(exporter.export(List.of(record)), StandardCharsets.UTF_8);
    }

    @Test
    void export_writesCanonicalHeaderOnlyForEmptyList() {
        assertThat(new String(exporter.export(List.of()), StandardCharsets.UTF_8))
                .isEqualTo(CANONICAL_HEADER + CRLF);
    }

    @Test
    void export_writesFullRecordInCanonicalColumnOrder() {
        String csv = export(baseRecord().build());

        String expected = CANONICAL_HEADER + CRLF
                + "00000000-0000-0000-0000-000000000001,"
                + "2025-06-15T12:00:00Z,"
                + "ASSET-1,"
                + WALLET + ","
                + "true,true,SUCCESS,5000,10000,"
                + "5Kg...signature,123456,6Fg...blockhash"
                + CRLF;

        assertThat(csv).isEqualTo(expected);
    }

    @Test
    void export_escapesFieldContainingComma() {
        String csv = export(baseRecord().assetId("ASSET,1").build());

        assertThat(csv).contains("\"ASSET,1\"");
    }

    @Test
    void export_escapesFieldContainingDoubleQuote() {
        String csv = export(baseRecord().assetId("ASSET\"1").build());

        assertThat(csv).contains("\"ASSET\"\"1\"");
    }

    @Test
    void export_escapesFieldContainingNewline() {
        String csv = export(baseRecord().assetId("ASSET\n1").build());

        assertThat(csv).contains("\"ASSET\n1\"");
    }

    @Test
    void export_representsNullFieldsAsEmptyColumns() {
        String csv = export(baseRecord()
                .solanaTransactionSignature(null)
                .slot(null)
                .blockhash(null)
                .build());

        String expected = CANONICAL_HEADER + CRLF
                + "00000000-0000-0000-0000-000000000001,"
                + "2025-06-15T12:00:00Z,"
                + "ASSET-1,"
                + WALLET + ","
                + "true,true,SUCCESS,5000,10000,,,"
                + CRLF;

        assertThat(csv).isEqualTo(expected);
    }
}
