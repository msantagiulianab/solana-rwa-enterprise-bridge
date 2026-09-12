package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.config.ApiKeyAuthInterceptor;
import com.solana.rwa.bridge.dto.ClawbackRequest;
import com.solana.rwa.bridge.dto.ClawbackResult;
import com.solana.rwa.bridge.service.TokenClawbackService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc web-layer tests for {@link ComplianceClawbackController}.
 *
 * <p>Defines the authenticated clawback REST contract:
 * <ul>
 *   <li>{@code POST /api/v1/compliance/clawback} → 200 with the broadcast signature</li>
 *   <li>missing/invalid {@code X-API-Key} → 401 (no service interaction)</li>
 *   <li>invalid/blank payload fields and malformed bodies → 400 (no service interaction)</li>
 * </ul>
 * The {@link TokenClawbackService} is mocked; no live Solana RPC call is made.
 */
@WebMvcTest(ComplianceClawbackController.class)
@Import(ApiKeyAuthInterceptor.class)
@ActiveProfiles("test")
class ComplianceClawbackControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY = "test-api-key";
    private static final String MINT = "MNTabcdefghijkmnpqrstuvwxyz123456789";
    private static final String SOURCE = "SRCabcdefghijkmnpqrstuvwxyz123456789";
    private static final String DEST = "DSTabcdefghijkmnpqrstuvwxyz123456789";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenClawbackService tokenClawbackService;

    @Test
    void clawback_returns200AndDelegatesToService() throws Exception {
        ClawbackResult result = new ClawbackResult(
                "tx-signature", "CLAWBACK", MINT, SOURCE, DEST, 1_000_000L,
                Instant.parse("2026-09-12T12:00:00Z"));
        when(tokenClawbackService.clawback(any(ClawbackRequest.class), eq("idem-clawback-0001")))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/compliance/clawback")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature").value("tx-signature"))
                .andExpect(jsonPath("$.action").value("CLAWBACK"))
                .andExpect(jsonPath("$.mintAddress").value(MINT))
                .andExpect(jsonPath("$.sourceTokenAccount").value(SOURCE))
                .andExpect(jsonPath("$.destinationTokenAccount").value(DEST))
                .andExpect(jsonPath("$.amount").value(1_000_000))
                .andExpect(jsonPath("$.executedAt").exists());

        ArgumentCaptor<ClawbackRequest> captor = ArgumentCaptor.forClass(ClawbackRequest.class);
        verify(tokenClawbackService).clawback(captor.capture(), eq("idem-clawback-0001"));

        ClawbackRequest mapped = captor.getValue();
        assertThat(mapped.mintAddress()).isEqualTo(MINT);
        assertThat(mapped.sourceTokenAccount()).isEqualTo(SOURCE);
        assertThat(mapped.destinationTokenAccount()).isEqualTo(DEST);
        assertThat(mapped.amount()).isEqualTo(1_000_000L);
        assertThat(mapped.reason()).isEqualTo("Regulatory breach - clawback");
    }

    @Test
    void clawback_returns401WhenApiKeyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/clawback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tokenClawbackService);
    }

    @Test
    void clawback_returns401WhenApiKeyInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/clawback")
                        .header(API_KEY_HEADER, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tokenClawbackService);
    }

    @Test
    void clawback_returns400WhenMintAddressBlank() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/clawback")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mintAddress": "",
                                  "sourceTokenAccount": "%s",
                                  "destinationTokenAccount": "%s",
                                  "amount": 1000000,
                                  "reason": "Regulatory breach - clawback",
                                  "idempotencyKey": "idem-clawback-0001"
                                }
                                """.formatted(SOURCE, DEST)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("mintAddress")));

        verifyNoInteractions(tokenClawbackService);
    }

    @Test
    void clawback_returns400WhenSourceAddressInvalidFormat() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/clawback")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mintAddress": "%s",
                                  "sourceTokenAccount": "NOT_A_SOLANA_ADDRESS_0",
                                  "destinationTokenAccount": "%s",
                                  "amount": 1000000,
                                  "reason": "Regulatory breach - clawback",
                                  "idempotencyKey": "idem-clawback-0001"
                                }
                                """.formatted(MINT, DEST)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("sourceTokenAccount")));

        verifyNoInteractions(tokenClawbackService);
    }


    @Test
    void clawback_returns400WhenAmountNotPositive() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/clawback")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mintAddress": "%s",
                                  "sourceTokenAccount": "%s",
                                  "destinationTokenAccount": "%s",
                                  "amount": 0,
                                  "reason": "Regulatory breach - clawback",
                                  "idempotencyKey": "idem-clawback-0001"
                                }
                                """.formatted(MINT, SOURCE, DEST)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("amount")));

        verifyNoInteractions(tokenClawbackService);
    }

    @Test
    void clawback_returns400WhenIdempotencyKeyBlank() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/clawback")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mintAddress": "%s",
                                  "sourceTokenAccount": "%s",
                                  "destinationTokenAccount": "%s",
                                  "amount": 1000000,
                                  "reason": "Regulatory breach - clawback",
                                  "idempotencyKey": "   "
                                }
                                """.formatted(MINT, SOURCE, DEST)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("idempotencyKey")));

        verifyNoInteractions(tokenClawbackService);
    }

    @Test
    void clawback_returns400WhenBodyMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/clawback")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(tokenClawbackService);
    }

    private String validPayload() {
        return """
                {
                  "mintAddress": "%s",
                  "sourceTokenAccount": "%s",
                  "destinationTokenAccount": "%s",
                  "amount": 1000000,
                  "reason": "Regulatory breach - clawback",
                  "idempotencyKey": "idem-clawback-0001"
                }
                """.formatted(MINT, SOURCE, DEST);
    }
}

