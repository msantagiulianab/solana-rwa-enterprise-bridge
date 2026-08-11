package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.ComplianceCheckResponse;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.exception.InvestorNotFoundException;
import com.solana.rwa.bridge.repository.AssetTokenRepository;
import com.solana.rwa.bridge.repository.AuditLogRepository;
import com.solana.rwa.bridge.repository.InvestorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Off-chain compliance gatekeeper. Performs the eligibility check BEFORE any
 * Solana RPC dispatch and writes an immutable audit log entry for EVERY check
 * (approved or blocked), per the enterprise auditability rule.
 */
@Service
@RequiredArgsConstructor
public class ComplianceService {

    public static final String ACTION_CHECK_ELIGIBILITY = "CHECK_ELIGIBILITY";

    private final InvestorRepository investorRepository;
    private final AssetTokenRepository assetTokenRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * Evaluates whether an investor is eligible to transact with an asset token.
     *
     * <p>Decision matrix:
     * <ul>
     *   <li>BLOCKED — investor not registered, REJECTED, FLAGGED_SANCTION, or not VERIFIED</li>
     *   <li>BLOCKED — asset token not registered or NON_COMPLIANT</li>
     *   <li>APPROVED — investor VERIFIED and asset COMPLIANT</li>
     * </ul>
     */
    @Transactional
    public ComplianceCheckResponse verifyEligibility(String walletAddress, String assetMintAddress) {
        Investor investor = investorRepository.findByWalletAddress(walletAddress).orElse(null);

        if (investor == null) {
            return auditAndRespond(walletAddress, false,
                    "Investor not registered", null, null);
        }
        if (investor.getKycStatus() == KycStatus.REJECTED) {
            return auditAndRespond(walletAddress, false,
                    "Investor KYC status is REJECTED",
                    KycStatus.REJECTED, null);
        }
        if (investor.getKycStatus() == KycStatus.FLAGGED_SANCTION) {
            return auditAndRespond(walletAddress, false,
                    "Investor is flagged for sanctions screening",
                    KycStatus.FLAGGED_SANCTION, null);
        }
        if (investor.getKycStatus() != KycStatus.VERIFIED) {
            return auditAndRespond(walletAddress, false,
                    "Investor KYC verification is not complete (status: " + investor.getKycStatus() + ")",
                    investor.getKycStatus(), null);
        }

        AssetToken token = assetTokenRepository.findByMintAddress(assetMintAddress).orElse(null);
        if (token == null) {
            return auditAndRespond(walletAddress, false,
                    "Asset token not registered",
                    KycStatus.VERIFIED, null);
        }
        if (token.getComplianceStatus() != AssetTokenComplianceStatus.COMPLIANT) {
            return auditAndRespond(walletAddress, false,
                    "Asset token is not compliant (status: " + token.getComplianceStatus() + ")",
                    KycStatus.VERIFIED, token.getComplianceStatus());
        }

        return auditAndRespond(walletAddress, true,
                "Investor KYC verified and asset compliant",
                KycStatus.VERIFIED, AssetTokenComplianceStatus.COMPLIANT);
    }

    /**
     * Retrieves the immutable compliance history for a registered investor.
     *
     * @throws InvestorNotFoundException if the wallet address has no investor record
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogs(String walletAddress) {
        if (investorRepository.findByWalletAddress(walletAddress).isEmpty()) {
            throw new InvestorNotFoundException(walletAddress);
        }
        return auditLogRepository.findByWalletAddress(walletAddress);
    }

    private ComplianceCheckResponse auditAndRespond(String walletAddress, boolean allowed,
                                                    String reason, KycStatus investorStatus,
                                                    AssetTokenComplianceStatus assetStatus) {
        Instant now = Instant.now();

        auditLogRepository.save(AuditLog.builder()
                .walletAddress(walletAddress)
                .action(ACTION_CHECK_ELIGIBILITY)
                .status(allowed ? AuditLogStatus.APPROVED : AuditLogStatus.BLOCKED)
                .reason(reason)
                .timestamp(now)
                .build());

        return ComplianceCheckResponse.builder()
                .allowed(allowed)
                .reason(reason)
                .investorStatus(investorStatus)
                .assetStatus(assetStatus)
                .timestamp(now)
                .build();
    }
}
