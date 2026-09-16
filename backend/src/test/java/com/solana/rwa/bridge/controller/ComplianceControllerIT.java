package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.compliance.port.TransferComplianceReason;
import com.solana.rwa.bridge.compliance.port.TransferComplianceResult;
import com.solana.rwa.bridge.compliance.port.TransferComplianceStatus;
import com.solana.rwa.bridge.dto.ComplianceCheckResponse;
import com.solana.rwa.bridge.dto.TokenTransferRequest;
import com.solana.rwa.bridge.dto.TokenTransferResult;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.entity.TransferHookAuditLog;
import com.solana.rwa.bridge.entity.TransferHookAuditStatus;
import com.solana.rwa.bridge.exception.ComplianceViolationException;
import com.solana.rwa.bridge.exception.InvestorNotFoundException;
import com.solana.rwa.bridge.repository.TransferHookAuditLogRepository;
import com.solana.rwa.bridge.service.ComplianceService;
import com.solana.rwa.bridge.service.TokenTransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.solana.rwa.bridge.config.ApiKeyAuthInterceptor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private static final String SOURCE_WALLET = "SRCabcdefghijkmnpqrstuvwxyz123456789";
    private static final String DEST_WALLET = "DSTabcdefghijkmnpqrstuvwxyz123456789";
    private static final String SOURCE_TOKEN_ACCOUNT = "SATAabcdefghijkmnpqrstuvwxyz12345678";
    private static final String DEST_TOKEN_ACCOUNT = "DATAabcdefghijkmnpqrstuvwxyz12345678";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplianceService complianceService;

    @MockitoBean
    private TransferHookAuditLogRepository transferHookAuditLogRepository;

    @MockitoBean
    private TokenTransferService tokenTransferService;

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

    @Test
    void getTransferHookAuditLogs_returns200AndList() throws Exception {
        TransferHookAuditLog log = TransferHookAuditLog.builder()
                .transactionSignature("mock-tx-signature")
                .mintAddress(MINT)
                .sourceWallet(WALLET)
                .destinationWallet("DSTabcdefghijkmnpqrstuvwxyz123456789")
                .amount(250)
                .complianceStatus(TransferHookAuditStatus.CLEARED)
                .reasonCode("KYC_PASSED")
                .createdAt(Instant.parse("2026-09-16T10:00:00Z"))
                .build();
        when(transferHookAuditLogRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/compliance/transfer-hook-audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionSignature").value("mock-tx-signature"))
                .andExpect(jsonPath("$[0].mintAddress").value(MINT))
                .andExpect(jsonPath("$[0].sourceWallet").value(WALLET))
                .andExpect(jsonPath("$[0].destinationWallet").value("DSTabcdefghijkmnpqrstuvwxyz123456789"))
                .andExpect(jsonPath("$[0].amount").value(250))
                .andExpect(jsonPath("$[0].complianceStatus").value("CLEARED"))
                .andExpect(jsonPath("$[0].reasonCode").value("KYC_PASSED"))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void executeTransfer_compliant_returns200() throws Exception {
        TokenTransferResult result = new TokenTransferResult(
                "tx-signature", "APPROVED", "ref-transfer",
                Instant.parse("2026-09-16T10:00:00Z"));
        when(tokenTransferService.transfer(any(TokenTransferRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/compliance/transfer")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransferPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature").value("tx-signature"))
                .andExpect(jsonPath("$.complianceStatus").value("APPROVED"))
                .andExpect(jsonPath("$.referenceId").value("ref-transfer"))
                .andExpect(jsonPath("$.evaluatedAt").exists());
    }

    @Test
    void executeTransfer_sanctionedOrBlocked_returns422() throws Exception {
        TransferComplianceResult blocked = new TransferComplianceResult(
                TransferComplianceStatus.BLOCKED,
                new TransferComplianceReason("OFAC", "SANCTIONED_DESTINATION"),
                "ref-blocked", Instant.parse("2026-09-16T10:00:00Z"));
        when(tokenTransferService.transfer(any(TokenTransferRequest.class)))
                .thenThrow(new ComplianceViolationException(blocked));

        mockMvc.perform(post("/api/v1/compliance/transfer")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransferPayload()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void executeTransfer_missingApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransferPayload()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tokenTransferService);
    }

    private String validTransferPayload() {
        return """
                {
                  "sourceWallet": "%s",
                  "destinationWallet": "%s",
                  "sourceTokenAccount": "%s",
                  "destinationTokenAccount": "%s",
                  "assetMintAddress": "%s",
                  "amount": 1000000
                }
                """.formatted(SOURCE_WALLET, DEST_WALLET, SOURCE_TOKEN_ACCOUNT,
                DEST_TOKEN_ACCOUNT, MINT);
    }
}