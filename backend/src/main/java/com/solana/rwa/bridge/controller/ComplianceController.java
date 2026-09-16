package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.dto.ComplianceCheckRequest;
import com.solana.rwa.bridge.dto.ComplianceCheckResponse;
import com.solana.rwa.bridge.dto.TokenTransferRequest;
import com.solana.rwa.bridge.dto.TokenTransferResult;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.TransferHookAuditLog;
import com.solana.rwa.bridge.repository.TransferHookAuditLogRepository;
import com.solana.rwa.bridge.service.ComplianceService;
import com.solana.rwa.bridge.service.TokenTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
    private final TransferHookAuditLogRepository transferHookAuditLogRepository;
    private final TokenTransferService tokenTransferService;

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

    /**
     * GET /api/v1/compliance/transfer-hook-audit-logs — immutable transfer-hook
     * compliance ledger, newest first.
     */
    @GetMapping("/transfer-hook-audit-logs")
    public List<TransferHookAuditLog> transferHookAuditLogs() {
        return transferHookAuditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * POST /api/v1/compliance/transfer — executes a compliance-gated Token-2022
     * secondary-market transfer. A blocked decision aborts with 422 before any
     * RPC bytes are emitted; the route is gated by the {@code X-API-Key}
     * interceptor registered in {@code WebConfig}.
     */
    @PostMapping("/transfer")
    public TokenTransferResult transfer(@Valid @RequestBody TokenTransferRequest request) {
        return tokenTransferService.transfer(request);
    }
}
