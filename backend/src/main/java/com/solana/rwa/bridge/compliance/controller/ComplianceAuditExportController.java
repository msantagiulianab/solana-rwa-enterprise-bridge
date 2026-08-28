package com.solana.rwa.bridge.compliance.controller;

import com.solana.rwa.bridge.compliance.dto.AuditExportRecordDto;
import com.solana.rwa.bridge.compliance.exporter.CsvAuditExporter;
import com.solana.rwa.bridge.compliance.exporter.JsonAuditExporter;
import com.solana.rwa.bridge.compliance.service.AuditExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Streaming REST endpoint for the enterprise compliance and settlement-proof
 * audit export engine.
 *
 * <p>Binds optional ISO-8601 date-range, asset-id, and execution-status filters,
 * delegates querying to {@link AuditExportService}, then streams the filtered
 * records through the matching deterministic {@code csv} or {@code json}
 * exporter as a download attachment.
 */
@RestController
@RequestMapping("/api/v1/compliance/audit-logs")
@RequiredArgsConstructor
public class ComplianceAuditExportController {

    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv");
    private static final DateTimeFormatter EXPORT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final AuditExportService auditExportService;
    private final CsvAuditExporter csvAuditExporter;
    private final JsonAuditExporter jsonAuditExporter;

    /**
     * GET /api/v1/compliance/audit-logs/export?format=csv|json
     *
     * <p>Fail-closed validation rejects missing/unsupported formats and
     * non-ISO-8601 date bounds with a structured 400 before any querying or
     * serialization occurs.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "assetId", required = false) String assetId,
            @RequestParam(value = "status", required = false) String status) {

        String resolvedFormat = resolveFormat(format);
        Instant start = parseInstant(startDate, "startDate");
        Instant end = parseInstant(endDate, "endDate");

        // The immutable settlement-proof ledger source is wired in a later
        // persistence phase; the query pipeline is still exercised here against
        // the current (empty) in-memory ledger so the streaming contract and
        // deterministic exporters remain fully testable end to end.
        List<AuditExportRecordDto> records = auditExportService.query(
                List.of(), start, end, assetId, status);

        if ("csv".equals(resolvedFormat)) {
            return stream(csvAuditExporter.export(records), CSV_MEDIA_TYPE, "csv");
        }
        return stream(jsonAuditExporter.export(records), MediaType.APPLICATION_JSON, "json");
    }

    private String resolveFormat(String format) {
        if (format == null || format.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing required parameter 'format'. Allowed formats: csv, json");
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        if (!"csv".equals(normalized) && !"json".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported export format '" + format + "'. Allowed formats: csv, json");
        }
        return normalized;
    }

    private Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid " + field + " '" + value + "': must be a valid ISO-8601 instant");
        }
    }

    private ResponseEntity<byte[]> stream(byte[] body, MediaType mediaType, String extension) {
        String filename = "audit-export-" + EXPORT_TIMESTAMP.format(Instant.now()) + "." + extension;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .cacheControl(CacheControl.noCache())
                .body(body);
    }
}
