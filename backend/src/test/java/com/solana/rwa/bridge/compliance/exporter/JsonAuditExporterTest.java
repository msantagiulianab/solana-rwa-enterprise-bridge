package com.solana.rwa.bridge.compliance.exporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solana.rwa.bridge.compliance.dto.AuditExportRecordDto;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the deterministic JSON audit exporter.
 *
 * <p>Verifies the stable field schema and ordering, ISO-8601 timestamps,
 * explicit {@code null} serialization for optional settlement proofs, and the
 * empty-array behaviour for zero records.
 */
class JsonAuditExporterTest {

    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant TIMESTAMP = Instant.parse("2025-06-15T12:00:00Z");

    private final JsonAuditExporter exporter = new JsonAuditExporter();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Test
    void export_returnsEmptyJsonArrayForEmptyList() {
        assertThat(new String(exporter.export(List.of()), StandardCharsets.UTF_8))
                .isEqualTo("[]");
    }

    @Test
    void export_producesDeterministicSchemaInCanonicalFieldOrder() {
        String json = new String(
                exporter.export(List.of(baseRecord().build())),
                StandardCharsets.UTF_8);

        String expected = "["
                + "{\"eventId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"timestamp\":\"2025-06-15T12:00:00Z\","
                + "\"assetId\":\"ASSET-1\","
                + "\"investorWallet\":\"" + WALLET + "\","
                + "\"kycVerified\":true,"
                + "\"ofacPassed\":true,"
                + "\"status\":\"SUCCESS\","
                + "\"computeUnitPriceMicroLamports\":5000,"
                + "\"computeUnitLimit\":10000,"
                + "\"solanaTransactionSignature\":\"5Kg...signature\","
                + "\"slot\":123456,"
                + "\"blockhash\":\"6Fg...blockhash\"}"
                + "]";

        assertThat(json).isEqualTo(expected);
    }

    @Test
    void export_serializesNullOptionalFieldsAsJsonNull() throws Exception {
        String json = new String(
                exporter.export(List.of(baseRecord()
                        .solanaTransactionSignature(null)
                        .slot(null)
                        .blockhash(null)
                        .build())),
                StandardCharsets.UTF_8);

        JsonNode record = objectMapper.readTree(json).get(0);

        assertThat(record.get("solanaTransactionSignature").isNull()).isTrue();
        assertThat(record.get("slot").isNull()).isTrue();
        assertThat(record.get("blockhash").isNull()).isTrue();
    }

    @Test
    void export_usesIso8601Timestamps() throws Exception {
        String json = new String(
                exporter.export(List.of(baseRecord().build())),
                StandardCharsets.UTF_8);

        JsonNode timestamp = objectMapper.readTree(json).get(0).get("timestamp");

        assertThat(timestamp.isTextual()).isTrue();
        assertThat(timestamp.asText()).isEqualTo("2025-06-15T12:00:00Z");
    }
}
