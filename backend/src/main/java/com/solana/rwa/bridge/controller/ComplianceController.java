package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.dto.ComplianceCheckRequest;
import com.solana.rwa.bridge.dto.ComplianceCheckResponse;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.service.ComplianceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for the compliance gatekeeper.
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;

    /**
     * POST /api/v1/compliance/check — evaluates investor eligibility off-chain.
     */
    @PostMapping("/check")
    public ComplianceCheckResponse check(@Valid @RequestBody ComplianceCheckRequest request) {
        return complianceService.verifyEligibility(
                request.getWalletAddress(), request.getAssetMintAddress());
    }

    /**
     * GET /api/v1/compliance/audit-logs/{walletAddress} — immutable compliance history.
     */
    @GetMapping("/audit-logs/{walletAddress}")
    public List<AuditLog> auditLogs(@PathVariable String walletAddress) {
        return complianceService.getAuditLogs(walletAddress);
    }
}
