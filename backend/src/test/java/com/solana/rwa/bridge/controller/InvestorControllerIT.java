package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.service.InvestorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for {@link InvestorController}.
 *
 * <p>Verifies investor registration/update routing, Bean Validation (400),
 * and the persisted investor payload returned on success.
 */
@WebMvcTest(InvestorController.class)
class InvestorControllerIT {

    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvestorService investorService;

    @Test
    void register_returns200AndInvestorWhenValid() throws Exception {
        Investor investor = Investor.builder()
                .walletAddress(WALLET)
                .country("US")
                .kycStatus(KycStatus.PENDING)
                .build();
        when(investorService.registerOrUpdate(any())).thenReturn(investor);

        mockMvc.perform(post("/api/v1/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "%s",
                                  "country": "US",
                                  "kycStatus": "PENDING"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletAddress").value(WALLET))
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.kycStatus").value("PENDING"));
    }

    @Test
    void register_returns200AndUpdatedInvestorWhenExisting() throws Exception {
        Investor investor = Investor.builder()
                .walletAddress(WALLET)
                .country("CA")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        when(investorService.registerOrUpdate(any())).thenReturn(investor);

        mockMvc.perform(post("/api/v1/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "%s",
                                  "country": "CA",
                                  "kycStatus": "VERIFIED"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletAddress").value(WALLET))
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));
    }

    @Test
    void register_returns400WhenWalletAddressBlank() throws Exception {
        mockMvc.perform(post("/api/v1/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "",
                                  "country": "US",
                                  "kycStatus": "PENDING"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400WhenCountryBlank() throws Exception {
        mockMvc.perform(post("/api/v1/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "%s",
                                  "country": "",
                                  "kycStatus": "PENDING"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400WhenKycStatusNull() throws Exception {
        mockMvc.perform(post("/api/v1/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "%s",
                                  "country": "US",
                                  "kycStatus": null
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400WhenKycStatusInvalidEnum() throws Exception {
        mockMvc.perform(post("/api/v1/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "%s",
                                  "country": "US",
                                  "kycStatus": "NOT_A_REAL_STATUS"
                                }
                                """.formatted(WALLET)))
                .andExpect(status().isBadRequest());
    }
}
