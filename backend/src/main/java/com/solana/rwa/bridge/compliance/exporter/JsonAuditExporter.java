package com.solana.rwa.bridge.compliance.exporter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.solana.rwa.bridge.compliance.dto.AuditExportRecordDto;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Deterministic JSON exporter for the settlement-proof audit ledger.
 *
 * <p>Serializes each record with a stable, schema-compliant field ordering,
 * ISO-8601 timestamps, and explicit {@code null} members for optional
 * settlement proofs. Zero records produce the canonical {@code []} array.
 */
@Component
public class JsonAuditExporter implements AuditExporter {

    private static final String EMPTY_ARRAY = "[]";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] export(List<AuditExportRecordDto> records) {
        if (records == null || records.isEmpty()) {
            return EMPTY_ARRAY.getBytes(StandardCharsets.UTF_8);
        }

        ArrayNode array = objectMapper.createArrayNode();
        for (AuditExportRecordDto record : records) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("eventId", record.getEventId() == null ? null : record.getEventId().toString());
            node.put("timestamp", record.getTimestamp() == null ? null : record.getTimestamp().toString());
            node.put("assetId", record.getAssetId());
            node.put("investorWallet", record.getInvestorWallet());
            node.put("kycVerified", record.isKycVerified());
            node.put("ofacPassed", record.isOfacPassed());
            node.put("status", record.getStatus());
            node.put("computeUnitPriceMicroLamports", record.getComputeUnitPriceMicroLamports());
            node.put("computeUnitLimit", record.getComputeUnitLimit());
            node.put("solanaTransactionSignature", record.getSolanaTransactionSignature());
            node.put("slot", record.getSlot());
            node.put("blockhash", record.getBlockhash());
            array.add(node);
        }

        try {
            return objectMapper.writeValueAsBytes(array);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit export to JSON", e);
        }
    }
}
