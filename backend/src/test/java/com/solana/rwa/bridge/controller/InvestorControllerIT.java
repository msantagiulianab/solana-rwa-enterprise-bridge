package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.service.InvestorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.solana.rwa.bridge.config.ApiKeyAuthInterceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for {@link InvestorController}.
 *
 * <p>Verifies investor registration routing, Bean Validation (400), the
 * X-API-Key authentication gate (401), and the persisted investor payload
 * returned on success.
 */
@WebMvcTest(InvestorController.class)
@Import(ApiKeyAuthInterceptor.class)
@ActiveProfiles("test")
class InvestorControllerIT {

    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestorService investorService;

    @Test
    void register_returns200AndInvestorWhenValid() throws Exception {
        Investor investor = Investor.builder()
                .fullName("Alice Johnson")
                .email("alice@example.com")
                .walletAddress(WALLET)
                .kycStatus(KycStatus.PENDING)
                .build();
        when(investorService.register(any())).thenReturn(investor);

        mockMvc.perform(post("/api/investors")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Alice Johnson",
                                  "email": "alice@example.com",
                                  "solanaAddress": "%s"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice Johnson"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.walletAddress").value(WALLET));
    }

    @Test
    void register_returns401WhenApiKeyMissing() throws Exception {
        mockMvc.perform(post("/api/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Alice Johnson",
                                  "email": "alice@example.com",
                                  "solanaAddress": "%s"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_returns401WhenApiKeyInvalid() throws Exception {
        mockMvc.perform(post("/api/investors")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Alice Johnson",
                                  "email": "alice@example.com",
                                  "solanaAddress": "%s"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_returns400WhenFullNameBlank() throws Exception {
        mockMvc.perform(post("/api/investors")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "email": "alice@example.com",
                                  "solanaAddress": "%s"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400WhenEmailBlank() throws Exception {
        mockMvc.perform(post("/api/investors")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Alice",
                                  "email": "",
                                  "solanaAddress": "%s"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400WhenSolanaAddressBlank() throws Exception {
        mockMvc.perform(post("/api/investors")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Alice",
                                  "email": "alice@example.com",
                                  "solanaAddress": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400WhenSolanaAddressInvalid() throws Exception {
        mockMvc.perform(post("/api/investors")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Alice",
                                  "email": "alice@example.com",
                                  "solanaAddress": "NOT_A_VALID_ADDRESS"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}