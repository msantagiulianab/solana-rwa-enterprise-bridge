package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.ComplianceCheckResponse;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.exception.InvestorNotFoundException;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.repository.AssetTokenRepository;
import com.solana.rwa.bridge.repository.AuditLogRepository;
import com.solana.rwa.bridge.repository.InvestorRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Off-chain compliance gatekeeper. Performs the eligibility check BEFORE any
 * Solana RPC dispatch and writes an immutable audit log entry for EVERY check
 * (approved or blocked), per the enterprise auditability rule.
 *
 * <p>Once the off-chain KYC/asset checks pass, the on-chain wallet existence
 * is verified through the {@link SolanaRpcAdapter} (fail-closed: an RPC outage
 * blocks the decision rather than silently approving).
 */
@Service
@RequiredArgsConstructor
public class ComplianceService {

    public static final String ACTION_CHECK_ELIGIBILITY = "CHECK_ELIGIBILITY";

    private final InvestorRepository investorRepository;
    private final AssetTokenRepository assetTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final SolanaRpcAdapter solanaRpcAdapter;

    /**
     * Evaluates whether an investor is eligible to transact with an asset token.
     *
     * <p>Decision matrix:
     * <ul>
     *   <li>BLOCKED — investor not registered, REJECTED, FLAGGED_SANCTION, or not VERIFIED</li>
     *   <li>BLOCKED — asset token not registered or NON_COMPLIANT</li>
     *   <li>BLOCKED — wallet does not exist on-chain, or the RPC layer is unavailable</li>
     *   <li>APPROVED — investor VERIFIED, asset COMPLIANT, and wallet exists on-chain</li>
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

        // On-chain verification: confirm the investor's wallet actually exists on
        // Solana Devnet BEFORE approving. RPC failures fail closed (BLOCKED), never
        // silently approve. SolanaRpcException is caught so a network outage is
        // surfaced as a compliance decision rather than a 500.
        try {
            if (!solanaRpcAdapter.getAccountInfo(walletAddress).exists()) {
                return auditAndRespond(walletAddress, false,
                        "Wallet does not exist on Solana chain",
                        KycStatus.VERIFIED, AssetTokenComplianceStatus.COMPLIANT);
            }
        } catch (SolanaRpcException ex) {
            return auditAndRespond(walletAddress, false,
                    "Solana RPC unavailable - on-chain verification failed",
                    KycStatus.VERIFIED, AssetTokenComplianceStatus.COMPLIANT);
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
