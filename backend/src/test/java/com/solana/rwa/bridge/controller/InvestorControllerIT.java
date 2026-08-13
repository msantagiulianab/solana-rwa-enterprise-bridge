package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.repository.AuditLogRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @MockitoBean
    private AuditLogRepository auditLogRepository;

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

    @Test
    void updateStatus_returns200AndWritesAuditLogWhenVerified() throws Exception {
        Investor verified = Investor.builder()
                .fullName("Alice Johnson")
                .email("alice@example.com")
                .walletAddress(WALLET)
                .kycStatus(KycStatus.VERIFIED)
                .build();
        when(investorService.updateStatus(any(), any(KycStatus.class))).thenReturn(verified);

        mockMvc.perform(patch("/api/investors/{id}/status", "00000000-0000-0000-0000-000000000001")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kycStatus": "VERIFIED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));

        verify(auditLogRepository).save(any());
    }

    @Test
    void updateStatus_doesNotWriteAuditLogWhenRejected() throws Exception {
        Investor rejected = Investor.builder()
                .fullName("Alice Johnson")
                .email("alice@example.com")
                .walletAddress(WALLET)
                .kycStatus(KycStatus.REJECTED)
                .build();
        when(investorService.updateStatus(any(), any(KycStatus.class))).thenReturn(rejected);

        mockMvc.perform(patch("/api/investors/{id}/status", "00000000-0000-0000-0000-000000000001")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kycStatus": "REJECTED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("REJECTED"));

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void updateStatus_returns401WhenApiKeyMissing() throws Exception {
        mockMvc.perform(patch("/api/investors/{id}/status", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kycStatus": "VERIFIED"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
