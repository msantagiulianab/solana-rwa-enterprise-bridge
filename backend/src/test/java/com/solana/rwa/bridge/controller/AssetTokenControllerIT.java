package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for {@link AssetTokenController}.
 */
@WebMvcTest(AssetTokenController.class)
class AssetTokenControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @Test
    void createToken_returns200AndTokenWhenValid() throws Exception {
        AssetToken token = AssetToken.builder()
                .assetName("Prime Manhattan Office Fund")
                .valuationUsd(new BigDecimal("125000000.00"))
                .complianceStatus(AssetTokenComplianceStatus.NON_COMPLIANT)
                .build();

        when(tokenService.create(any(AssetTokenRegistrationRequest.class))).thenReturn(token);

        mockMvc.perform(post("/api/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "Prime Manhattan Office Fund",
                                  "valuationUsd": 125000000.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetName").value("Prime Manhattan Office Fund"))
                .andExpect(jsonPath("$.complianceStatus").value("NON_COMPLIANT"));
    }

    @Test
    void createToken_returns400WhenAssetNameBlank() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "",
                                  "valuationUsd": 100.00
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createToken_returns400WhenValuationMissing() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "Prime Manhattan Office Fund"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createToken_returns400WhenValuationNotPositive() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetName": "Prime Manhattan Office Fund",
                                  "valuationUsd": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}