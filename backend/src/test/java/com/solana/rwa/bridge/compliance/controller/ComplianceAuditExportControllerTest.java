package com.solana.rwa.bridge.compliance.controller;

import com.solana.rwa.bridge.compliance.dto.AuditExportRecordDto;
import com.solana.rwa.bridge.compliance.exporter.CsvAuditExporter;
import com.solana.rwa.bridge.compliance.exporter.JsonAuditExporter;
import com.solana.rwa.bridge.compliance.service.AuditExportService;
import com.solana.rwa.bridge.config.ApiKeyAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc web-layer tests for {@link ComplianceAuditExportController}.
 *
 * <p>The audit query service and the two format exporters are mocked so the
 * tests can verify request binding, HTTP streaming headers, structured 400
 * validation failures, and correct delegation to the matching exporter.
 */
@WebMvcTest(ComplianceAuditExportController.class)
@Import(ApiKeyAuthInterceptor.class)
@ActiveProfiles("test")
class ComplianceAuditExportControllerTest {

    private static final String CSV_FILENAME_PATTERN =
            "attachment; filename=\"audit-export-\\d{8}T\\d{6}Z\\.csv\"";
    private static final String JSON_FILENAME_PATTERN =
            "attachment; filename=\"audit-export-\\d{8}T\\d{6}Z\\.json\"";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditExportService auditExportService;

    @MockitoBean
    private CsvAuditExporter csvAuditExporter;

    @MockitoBean
    private JsonAuditExporter jsonAuditExporter;

    @Test
    void exportCsv_returns200WithCsvHeadersAndAttachmentFilename() throws Exception {
        byte[] csvBody = "EventId,Timestamp\r\n00000000-0000-0000-0000-000000000001,2025-06-15T12:00:00Z\r\n"
                .getBytes(StandardCharsets.UTF_8);

        when(auditExportService.query(anyList(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(record()));
        when(csvAuditExporter.export(anyList())).thenReturn(csvBody);

        mockMvc.perform(get("/api/v1/compliance/audit-logs/export")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        matchesPattern(CSV_FILENAME_PATTERN)))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(content().bytes(csvBody));

        verify(auditExportService).query(anyList(), isNull(), isNull(), isNull(), isNull());
        verify(csvAuditExporter).export(anyList());
        verifyNoInteractions(jsonAuditExporter);
    }

    @Test
    void exportJson_returns200WithJsonContentTypeAndAttachmentFilename() throws Exception {
        byte[] jsonBody = "[]".getBytes(StandardCharsets.UTF_8);

        when(auditExportService.query(anyList(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(record()));
        when(jsonAuditExporter.export(anyList())).thenReturn(jsonBody);

        mockMvc.perform(get("/api/v1/compliance/audit-logs/export")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        matchesPattern(JSON_FILENAME_PATTERN)))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(content().bytes(jsonBody));

        verify(auditExportService).query(anyList(), isNull(), isNull(), isNull(), isNull());
        verify(jsonAuditExporter).export(anyList());
        verifyNoInteractions(csvAuditExporter);
    }


    @Test
    void export_returns400WhenFormatIsUnsupported() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/audit-logs/export")
                        .param("format", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        "Unsupported export format 'xml'. Allowed formats: csv, json"));

        verifyNoInteractions(auditExportService, csvAuditExporter, jsonAuditExporter);
    }

    @Test
    void export_returns400WhenFormatIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/audit-logs/export"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Missing required parameter 'format'. Allowed formats: csv, json"));

        verifyNoInteractions(auditExportService, csvAuditExporter, jsonAuditExporter);
    }

    @Test
    void export_returns400WhenStartDateIsNotIso8601() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/audit-logs/export")
                        .param("format", "csv")
                        .param("startDate", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Invalid startDate 'not-a-date': must be a valid ISO-8601 instant"));

        verifyNoInteractions(auditExportService, csvAuditExporter, jsonAuditExporter);
    }

    @Test
    void export_returns400WhenEndDateIsNotIso8601() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/audit-logs/export")
                        .param("format", "csv")
                        .param("endDate", "also-not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Invalid endDate 'also-not-a-date': must be a valid ISO-8601 instant"));

        verifyNoInteractions(auditExportService, csvAuditExporter, jsonAuditExporter);
    }

    @Test
    void export_delegatesFiltersAndSerializationToMatchingExporter() throws Exception {
        Instant start = Instant.parse("2025-06-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-30T23:59:59Z");
        byte[] csvBody = "EventId,Timestamp\r\n".getBytes(StandardCharsets.UTF_8);

        when(auditExportService.query(anyList(), eq(start), eq(end), eq("ASSET-1"), eq("SUCCESS")))
                .thenReturn(List.of(record()));
        when(csvAuditExporter.export(anyList())).thenReturn(csvBody);

        mockMvc.perform(get("/api/v1/compliance/audit-logs/export")
                        .param("format", "csv")
                        .param("startDate", "2025-06-01T00:00:00Z")
                        .param("endDate", "2025-06-30T23:59:59Z")
                        .param("assetId", "ASSET-1")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk());

        verify(auditExportService).query(anyList(), eq(start), eq(end), eq("ASSET-1"), eq("SUCCESS"));
        verify(csvAuditExporter).export(anyList());
        verifyNoInteractions(jsonAuditExporter);
    }

    private AuditExportRecordDto record() {
        return AuditExportRecordDto.builder()
                .eventId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .timestamp(Instant.parse("2025-06-15T12:00:00Z"))
                .assetId("ASSET-1")
                .investorWallet("7XeXLabcDEFghijkmnpqrstuvwxyz23456789")
                .kycVerified(true)
                .ofacPassed(true)
                .status("SUCCESS")
                .computeUnitPriceMicroLamports(5_000L)
                .computeUnitLimit(10_000)
                .solanaTransactionSignature("5Kg...signature")
                .slot(123_456L)
                .blockhash("6Fg...blockhash")
                .build();
    }

}
