package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.exception.InvestorNotFoundException;
import com.solana.rwa.bridge.service.InvestorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for {@link InvestorController}.
 *
 * <p>Verifies investor registration routing, Bean Validation (400),
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
                .fullName("Alice Johnson")
                .email("alice@example.com")
                .walletAddress(WALLET)
                .kycStatus(KycStatus.PENDING)
                .build();
        when(investorService.register(any())).thenReturn(investor);

        mockMvc.perform(post("/api/investors")
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
    void register_returns400WhenFullNameBlank() throws Exception {
        mockMvc.perform(post("/api/investors")
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
    void getInvestorById_returns200WhenFound() throws Exception {
        Investor investor = Investor.builder()
                .fullName("Alice Johnson")
                .email("alice@example.com")
                .walletAddress(WALLET)
                .kycStatus(KycStatus.PENDING)
                .build();
        when(investorService.findById(any(UUID.class))).thenReturn(investor);

        mockMvc.perform(get("/api/investors/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice Johnson"))
                .andExpect(jsonPath("$.kycStatus").value("PENDING"));
    }

    @Test
    void getInvestorById_returns404WhenMissing() throws Exception {
        when(investorService.findById(any(UUID.class)))
                .thenThrow(new InvestorNotFoundException(UUID.randomUUID()));

        mockMvc.perform(get("/api/investors/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_returns200AndUpdatedInvestor() throws Exception {
        Investor updated = Investor.builder()
                .fullName("Alice Johnson")
                .email("alice@example.com")
                .walletAddress(WALLET)
                .kycStatus(KycStatus.VERIFIED)
                .build();
        when(investorService.updateStatus(any(UUID.class), any(KycStatus.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/investors/00000000-0000-0000-0000-000000000001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kycStatus": "VERIFIED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));
    }

    @Test
    void updateStatus_returns400WhenStatusMissing() throws Exception {
        mockMvc.perform(patch("/api/investors/00000000-0000-0000-0000-000000000001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
