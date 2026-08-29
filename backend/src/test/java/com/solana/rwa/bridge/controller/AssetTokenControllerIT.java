package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.compliance.service.AuditExportService;
import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.repository.AuditLogRepository;
import com.solana.rwa.bridge.service.TokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.solana.rwa.bridge.config.ApiKeyAuthInterceptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for {@link AssetTokenController}.
 */
@WebMvcTest(AssetTokenController.class)
@Import(ApiKeyAuthInterceptor.class)
@ActiveProfiles("test")
class AssetTokenControllerIT {

    private static final String API_KEY = "test-api-key";
    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String IDEMPOTENCY_KEY = "idem-tokenize-0001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @Test
    void createToken_returns200AndTokenWhenValid() throws Exception {
        AssetToken token = AssetToken.builder()
                .assetName("Prime Manhattan Office Fund")
                .valuationUsd(new BigDecimal("125000000.00"))
                .mintAddress("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
                .complianceStatus(AssetTokenComplianceStatus.NON_COMPLIANT)
                .build();

        when(tokenService.create(any(AssetTokenRegistrationRequest.class))).thenReturn(token);

        mockMvc.perform(post("/api/tokens")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "Prime Manhattan Office Fund",
                                  "valuationUsd": 125000000.00,
                                  "issuerWalletAddress": "%s",
                                  "idempotencyKey": "%s"
                                }
                                """.formatted(WALLET, IDEMPOTENCY_KEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetName").value("Prime Manhattan Office Fund"))
                .andExpect(jsonPath("$.mintAddress").value("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"))
                .andExpect(jsonPath("$.complianceStatus").value("NON_COMPLIANT"));

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(logCaptor.capture());
        AuditLog captured = logCaptor.getValue();
        assertThat(captured.getWalletAddress())
                .isEqualTo("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
        assertThat(captured.getAction()).isEqualTo(AssetTokenController.ACTION_TOKENIZE_ASSET);
        assertThat(captured.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(captured.getAssetId()).isEqualTo("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
        assertThat(captured.getKycVerified()).isTrue();
        assertThat(captured.getOfacPassed()).isTrue();
        assertThat(captured.getSettlementStatus()).isEqualTo(AuditExportService.STATUS_SUCCESS);
    }

    @Test
    void createToken_returns400WhenIdempotencyKeyBlank() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "Prime Manhattan Office Fund",
                                  "valuationUsd": 125000000.00,
                                  "issuerWalletAddress": "%s",
                                  "idempotencyKey": "  "
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createToken_returns400WhenAssetNameBlank() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "",
                                  "valuationUsd": 100.00,
                                  "issuerWalletAddress": "%s"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createToken_returns400WhenIssuerWalletAddressBlank() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "Prime Manhattan Office Fund",
                                  "valuationUsd": 125000000.00,
                                  "issuerWalletAddress": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createToken_returns400WhenIssuerWalletAddressInvalid() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "Prime Manhattan Office Fund",
                                  "valuationUsd": 125000000.00,
                                  "issuerWalletAddress": "NOT_A_SOLANA_ADDRESS_0"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createToken_returns400WhenValuationMissing() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "Prime Manhattan Office Fund",
                                  "issuerWalletAddress": "%s"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createToken_returns400WhenValuationNotPositive() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "Prime Manhattan Office Fund",
                                  "valuationUsd": 0,
                                  "issuerWalletAddress": "%s"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isBadRequest());
    }
}