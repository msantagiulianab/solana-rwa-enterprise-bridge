package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.dto.ComplianceCheckResponse;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.exception.InvestorNotFoundException;
import com.solana.rwa.bridge.service.ComplianceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.solana.rwa.bridge.config.ApiKeyAuthInterceptor;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for {@link ComplianceController}.
 *
 * <p>The service layer is mocked; no RPC or persistence is touched. Verifies
 * routing, validation (400), successful checks (200), 404 handling, and the
 * X-API-Key authentication gate (401) enforced on mutating routes.
 */
@WebMvcTest(ComplianceController.class)
@Import(ApiKeyAuthInterceptor.class)
@ActiveProfiles("test")
class ComplianceControllerIT {

    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String MINT = "MNTabcdefghijkmnpqrstuvwxyz123456789";
    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ComplianceService complianceService;

    @Test
    void check_returns200AndAllowedWhenEligible() throws Exception {
        ComplianceCheckResponse response = ComplianceCheckResponse.builder()
                .allowed(true)
                .reason("Investor KYC verified and asset compliant")
                .investorStatus(KycStatus.VERIFIED)
                .assetStatus(AssetTokenComplianceStatus.COMPLIANT)
                .timestamp(Instant.now())
                .build();
        when(complianceService.verifyEligibility(WALLET, MINT)).thenReturn(response);

        mockMvc.perform(post("/api/v1/compliance/check")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "%s",
                                  "assetMintAddress": "%s"
                                }
                                """.formatted(WALLET, MINT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value("Investor KYC verified and asset compliant"))
                .andExpect(jsonPath("$.investorStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.assetStatus").value("COMPLIANT"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void check_returns200AndBlockedWhenNotEligible() throws Exception {
        ComplianceCheckResponse response = ComplianceCheckResponse.builder()
                .allowed(false)
                .reason("Investor KYC status is REJECTED")
                .investorStatus(KycStatus.REJECTED)
                .timestamp(Instant.now())
                .build();
        when(complianceService.verifyEligibility(WALLET, MINT)).thenReturn(response);

        mockMvc.perform(post("/api/v1/compliance/check")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "%s",
                                  "assetMintAddress": "%s"
                                }
                                """.formatted(WALLET, MINT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("Investor KYC status is REJECTED"))
                .andExpect(jsonPath("$.investorStatus").value("REJECTED"));
    }

    @Test
    void check_returns401WhenApiKeyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "%s",
                                  "assetMintAddress": "%s"
                                }
                                """.formatted(WALLET, MINT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void check_returns401WhenApiKeyInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/check")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "%s",
                                  "assetMintAddress": "%s"
                                }
                                """.formatted(WALLET, MINT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void check_returns400WhenWalletAddressBlank() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/check")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "",
                                  "assetMintAddress": "%s"
                                }
                                """.formatted(MINT)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void check_returns400WhenWalletAddressInvalidFormat() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/check")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "NOT_A_SOLANA_ADDRESS_0",
                                  "assetMintAddress": "%s"
                                }
                                """.formatted(MINT)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void check_returns400WhenAssetMintAddressBlank() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/check")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "%s",
                                  "assetMintAddress": " "
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void check_returns400WhenBodyMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/check")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditLogs_returns200WithHistoryForRegisteredInvestor() throws Exception {
        when(complianceService.getAuditLogs(WALLET))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/compliance/audit-logs/{walletAddress}", WALLET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void auditLogs_returns404WhenInvestorNotRegistered() throws Exception {
        when(complianceService.getAuditLogs(anyString()))
                .thenThrow(new InvestorNotFoundException("7XeXLabcDEFghijkmnpqrstuvwxyz23456789"));

        mockMvc.perform(get("/api/v1/compliance/audit-logs/{walletAddress}", WALLET))
                .andExpect(status().isNotFound());
    }
}