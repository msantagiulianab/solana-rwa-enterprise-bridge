package com.solana.rwa.bridge.maritime.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solana.rwa.bridge.config.ApiKeyAuthInterceptor;
import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;
import com.solana.rwa.bridge.maritime.domain.TransitSettlementStatus;
import com.solana.rwa.bridge.maritime.dto.BillOfLadingResponse;
import com.solana.rwa.bridge.maritime.dto.CanalTransitSettlementResponse;
import com.solana.rwa.bridge.maritime.dto.ContainerConsignmentResponse;
import com.solana.rwa.bridge.maritime.dto.SettlementEvaluationResponse;
import com.solana.rwa.bridge.maritime.exception.MaritimeComplianceException;
import com.solana.rwa.bridge.maritime.port.ClearanceReasonCode;
import com.solana.rwa.bridge.maritime.port.MaritimeClearanceResult;
import com.solana.rwa.bridge.maritime.service.MaritimeSettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc web-layer tests for {@link MaritimeSettlementController}.
 *
 * <p>Defines the authenticated maritime REST contract:
 * <ul>
 *   <li>{@code POST /api/v1/maritime/bills-of-lading} → 201 (register an eBL)</li>
 *   <li>{@code POST /api/v1/maritime/settlements/{id}/evaluate} → 200 CLEARED / 422 fail-closed</li>
 *   <li>{@code POST /api/v1/maritime/settlements/{id}/execute} → 200 (atomic SPL settlement)</li>
 *   <li>{@code GET /api/v1/maritime/settlements/{id}} and {@code GET /api/v1/maritime/bills-of-lading/{id}} → 200 reads</li>
 * </ul>
 * Every mutating route is gated by the {@code X-API-Key} interceptor (missing key → 401).
 * The settlement service is mocked; no live Solana RPC call is made.
 */
@WebMvcTest(MaritimeSettlementController.class)
@Import(ApiKeyAuthInterceptor.class)
@ActiveProfiles("test")
class MaritimeSettlementControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY = "test-api-key";
    private static final UUID BILL_OF_LADING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SETTLEMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CONSIGNMENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MaritimeSettlementService maritimeSettlementService;

    @Test
    void registerBillOfLading_missingApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/maritime/bills-of-lading")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing or invalid API key"));
    }

    @Test
    void registerBillOfLading_validRequest_returns201() throws Exception {
        when(maritimeSettlementService.registerBillOfLading(any()))
                .thenReturn(billOfLadingResponse());

        mockMvc.perform(post("/api/v1/maritime/bills-of-lading")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(BILL_OF_LADING_ID.toString()))
                .andExpect(jsonPath("$.blNumber").value("BL-2026-0001"))
                .andExpect(jsonPath("$.clearanceStatus").value("PENDING"))
                .andExpect(jsonPath("$.consignments[0].containerNumber").value("CONT-001"))
                .andExpect(jsonPath("$.consignments[0].declaredValueUsd").value(12500.50));

        verify(maritimeSettlementService).registerBillOfLading(any());
    }

    @Test
    void registerBillOfLading_blankBlNumber_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/maritime/bills-of-lading")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload("", "IMO1234567", "MSC")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("blNumber")));
    }

    @Test
    void evaluateSettlement_missingApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/maritime/settlements/{id}/evaluate", SETTLEMENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing or invalid API key"));
    }

    @Test
    void evaluateSettlement_cleared_returns200() throws Exception {
        when(maritimeSettlementService.evaluateSettlement(SETTLEMENT_ID))
                .thenReturn(new SettlementEvaluationResponse(
                        SETTLEMENT_ID, ClearanceStatus.CLEARED, null, null, "CERT-001"));

        mockMvc.perform(post("/api/v1/maritime/settlements/{id}/evaluate", SETTLEMENT_ID)
                        .header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value(SETTLEMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("CLEARED"))
                .andExpect(jsonPath("$.referenceId").value("CERT-001"));
    }

    @Test
    void evaluateSettlement_sanctionsFlag_returns422() throws Exception {
        MaritimeClearanceResult sanctioned = new MaritimeClearanceResult(
                ClearanceStatus.SANCTIONED,
                new ClearanceReasonCode("OFAC", "SDN_MATCH"),
                "CASE-OFAC-001", null, Instant.parse("2026-09-03T10:00:00Z"));
        when(maritimeSettlementService.evaluateSettlement(SETTLEMENT_ID))
                .thenThrow(new MaritimeComplianceException(sanctioned));

        mockMvc.perform(post("/api/v1/maritime/settlements/{id}/evaluate", SETTLEMENT_ID)
                        .header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message", containsString("SANCTIONED")))
                .andExpect(jsonPath("$.message", containsString("OFAC")));
    }

    @Test
    void executeSettlement_valid_returns200() throws Exception {
        when(maritimeSettlementService.executeSettlement(SETTLEMENT_ID))
                .thenReturn(settlementResponse(TransitSettlementStatus.SETTLED));

        mockMvc.perform(post("/api/v1/maritime/settlements/{id}/execute", SETTLEMENT_ID)
                        .header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SETTLEMENT_ID.toString()))
                .andExpect(jsonPath("$.billOfLadingId").value(BILL_OF_LADING_ID.toString()))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.finalityState").value("CONFIRMED"));
    }

    @Test
    void getSettlement_returns200() throws Exception {
        when(maritimeSettlementService.getSettlement(SETTLEMENT_ID))
                .thenReturn(settlementResponse(TransitSettlementStatus.SETTLED));

        mockMvc.perform(get("/api/v1/maritime/settlements/{id}", SETTLEMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SETTLEMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.finalityState").value("CONFIRMED"));
    }

    @Test
    void getBillOfLading_returns200() throws Exception {
        when(maritimeSettlementService.getBillOfLading(BILL_OF_LADING_ID))
                .thenReturn(billOfLadingResponse());

        mockMvc.perform(get("/api/v1/maritime/bills-of-lading/{id}", BILL_OF_LADING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BILL_OF_LADING_ID.toString()))
                .andExpect(jsonPath("$.blNumber").value("BL-2026-0001"));
    }

    private BillOfLadingResponse billOfLadingResponse() {
        return new BillOfLadingResponse(
                BILL_OF_LADING_ID,
                "BL-2026-0001",
                "IMO1234567",
                "MSC",
                "PACTB",
                "USNYC",
                WALLET,
                ClearanceStatus.PENDING,
                List.of(new ContainerConsignmentResponse(
                        CONSIGNMENT_ID,
                        "CONT-001",
                        new BigDecimal("12500.50"))));
    }

    private CanalTransitSettlementResponse settlementResponse(TransitSettlementStatus status) {
        return new CanalTransitSettlementResponse(
                SETTLEMENT_ID,
                BILL_OF_LADING_ID,
                status,
                null,
                SettlementStatus.CONFIRMED);
    }

    private String registerPayload() throws JsonProcessingException {
        return registerPayload("BL-2026-0001", "IMO1234567", "MSC");
    }

    private String registerPayload(String blNumber, String vesselImo, String carrierId)
            throws JsonProcessingException {
        Map<String, Object> consignment = new LinkedHashMap<>();
        consignment.put("containerNumber", "CONT-001");
        consignment.put("declaredValueUsd", new BigDecimal("12500.50"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("blNumber", blNumber);
        body.put("vesselImo", vesselImo);
        body.put("carrierId", carrierId);
        body.put("originPort", "PACTB");
        body.put("destinationPort", "USNYC");
        body.put("consigneeWallet", WALLET);
        body.put("consignments", List.of(consignment));

        return objectMapper.writeValueAsString(body);
    }
}
