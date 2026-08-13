package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.dto.InvestorRegistrationRequest;
import com.solana.rwa.bridge.dto.InvestorStatusUpdateRequest;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.repository.AuditLogRepository;
import com.solana.rwa.bridge.service.InvestorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for investor registration and KYC status management.
 */
@RestController
@RequestMapping("/api/investors")
@RequiredArgsConstructor
public class InvestorController {

    public static final String ACTION_KYC_VERIFIED = "KYC_VERIFIED";

    private final InvestorService investorService;
    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public List<Investor> getAllInvestors() {
        return investorService.findAll();
    }

    /**
     * GET /api/investors/{id} — returns a single investor by UUID.
     */
    @GetMapping("/{id}")
    public Investor getInvestorById(@PathVariable UUID id) {
        return investorService.findById(id);
    }

    /**
     * PATCH /api/investors/{id}/status — updates KYC status (APPROVE / REJECT).
     *
     * <p>When the resulting status is {@link KycStatus#VERIFIED}, an immutable
     * {@link AuditLog} entry (action {@code KYC_VERIFIED}) is written for the
     * investor's wallet.
     */
    @PatchMapping("/{id}/status")
    public Investor updateStatus(@PathVariable UUID id,
                                 @Valid @RequestBody InvestorStatusUpdateRequest request) {
        Investor investor = investorService.updateStatus(id, request.getKycStatus());

        if (investor.getKycStatus() == KycStatus.VERIFIED) {
            auditLogRepository.save(AuditLog.builder()
                    .walletAddress(investor.getWalletAddress())
                    .action(ACTION_KYC_VERIFIED)
                    .status(AuditLogStatus.APPROVED)
                    .reason("Investor KYC verified: " + investor.getFullName())
                    .build());
        }

        return investor;
    }

    /**
     * POST /api/investors — registers a new investor with default KYC PENDING.
     */
    @PostMapping
    public Investor register(@Valid @RequestBody InvestorRegistrationRequest request) {
        return investorService.register(request);
    }
}