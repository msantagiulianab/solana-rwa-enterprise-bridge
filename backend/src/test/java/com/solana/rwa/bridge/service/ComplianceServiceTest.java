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
import com.solana.rwa.bridge.rpc.dto.AccountInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link ComplianceService} business rules.
 *
 * <p>Every eligibility check MUST produce an audit log entry (APPROVED or
 * BLOCKED) regardless of the outcome. Once the off-chain KYC/asset checks
 * pass, the on-chain Solana RPC layer is consulted (mocked here, never a
 * live Devnet call during the build).
 */
@ExtendWith(MockitoExtension.class)
class ComplianceServiceTest {

    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String MINT = "MNTabcdefghijkmnpqrstuvwxyz123456789";
    private static final String SYSTEM_OWNER = "11111111111111111111111111111111";

    @Mock
    private InvestorRepository investorRepository;

    @Mock
    private AssetTokenRepository assetTokenRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private SolanaRpcAdapter solanaRpcAdapter;

    @InjectMocks
    private ComplianceService complianceService;

    private Investor investor(KycStatus status) {
        return Investor.builder()
                .fullName("Test Investor")
                .email("test@example.com")
                .walletAddress(WALLET)
                .kycStatus(status)
                .country("US")
                .build();
    }

    private AssetToken token(AssetTokenComplianceStatus status) {
        return AssetToken.builder()
                .assetName("Prime Manhattan Office Fund")
                .valuationUsd(new BigDecimal("125000000.00"))
                .mintAddress(MINT)
                .complianceStatus(status)
                .build();
    }

    private AccountInfo onChainAccount() {
        return new AccountInfo(SYSTEM_OWNER, 5_000_000L, false, 80L);
    }

    // ------------------------------------------------------------------
    // Business rules
    // ------------------------------------------------------------------

    @Test
    void verifyEligibility_approvesWhenInvestorVerifiedAndAssetCompliant() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(assetTokenRepository.findByMintAddress(MINT))
                .thenReturn(Optional.of(token(AssetTokenComplianceStatus.COMPLIANT)));
        when(solanaRpcAdapter.getAccountInfo(WALLET)).thenReturn(onChainAccount());

        ComplianceCheckResponse response = complianceService.verifyEligibility(WALLET, MINT);

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getReason()).isEqualTo("Investor KYC verified and asset compliant");
        assertThat(response.getInvestorStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(response.getAssetStatus()).isEqualTo(AssetTokenComplianceStatus.COMPLIANT);
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void verifyEligibility_blocksWhenInvestorNotRegistered() {
        when(investorRepository.findByWalletAddress(WALLET)).thenReturn(Optional.empty());

        ComplianceCheckResponse response = complianceService.verifyEligibility(WALLET, MINT);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReason()).isEqualTo("Investor not registered");
        assertThat(response.getInvestorStatus()).isNull();
        assertThat(response.getAssetStatus()).isNull();
        verifyNoInteractions(assetTokenRepository);
    }

    @Test
    void verifyEligibility_blocksWhenInvestorRejected() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.REJECTED)));

        ComplianceCheckResponse response = complianceService.verifyEligibility(WALLET, MINT);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReason()).isEqualTo("Investor KYC status is REJECTED");
        assertThat(response.getInvestorStatus()).isEqualTo(KycStatus.REJECTED);
        verifyNoInteractions(assetTokenRepository);
    }

    @Test
    void verifyEligibility_blocksWhenInvestorFlaggedForSanctions() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.FLAGGED_SANCTION)));

        ComplianceCheckResponse response = complianceService.verifyEligibility(WALLET, MINT);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReason()).isEqualTo("Investor is flagged for sanctions screening");
        assertThat(response.getInvestorStatus()).isEqualTo(KycStatus.FLAGGED_SANCTION);
        verifyNoInteractions(assetTokenRepository);
    }

    @Test
    void verifyEligibility_blocksWhenInvestorKycPending() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.PENDING)));

        ComplianceCheckResponse response = complianceService.verifyEligibility(WALLET, MINT);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReason()).isEqualTo("Investor KYC verification is not complete (status: PENDING)");
        assertThat(response.getInvestorStatus()).isEqualTo(KycStatus.PENDING);
        verifyNoInteractions(assetTokenRepository);
    }

    @Test
    void verifyEligibility_blocksWhenAssetNotRegistered() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(assetTokenRepository.findByMintAddress(MINT)).thenReturn(Optional.empty());

        ComplianceCheckResponse response = complianceService.verifyEligibility(WALLET, MINT);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReason()).isEqualTo("Asset token not registered");
        assertThat(response.getInvestorStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(response.getAssetStatus()).isNull();
    }

    @Test
    void verifyEligibility_blocksWhenAssetNonCompliant() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(assetTokenRepository.findByMintAddress(MINT))
                .thenReturn(Optional.of(token(AssetTokenComplianceStatus.NON_COMPLIANT)));

        ComplianceCheckResponse response = complianceService.verifyEligibility(WALLET, MINT);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReason()).isEqualTo("Asset token is not compliant (status: NON_COMPLIANT)");
        assertThat(response.getInvestorStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(response.getAssetStatus()).isEqualTo(AssetTokenComplianceStatus.NON_COMPLIANT);
    }

    // ------------------------------------------------------------------
    // Mandatory audit logging on EVERY check (approved or blocked)
    // ------------------------------------------------------------------

    @Test
    void verifyEligibility_writesApprovedAuditLogForApprovedCheck() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(assetTokenRepository.findByMintAddress(MINT))
                .thenReturn(Optional.of(token(AssetTokenComplianceStatus.COMPLIANT)));
        when(solanaRpcAdapter.getAccountInfo(WALLET)).thenReturn(onChainAccount());

        complianceService.verifyEligibility(WALLET, MINT);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog logged = captor.getValue();
        assertThat(logged.getWalletAddress()).isEqualTo(WALLET);
        assertThat(logged.getAction()).isEqualTo("CHECK_ELIGIBILITY");
        assertThat(logged.getStatus()).isEqualTo(AuditLogStatus.APPROVED);
        assertThat(logged.getReason()).isEqualTo("Investor KYC verified and asset compliant");
        assertThat(logged.getTimestamp()).isNotNull();
    }

    @Test
    void verifyEligibility_writesBlockedAuditLogForBlockedCheck() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.REJECTED)));

        complianceService.verifyEligibility(WALLET, MINT);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog logged = captor.getValue();
        assertThat(logged.getWalletAddress()).isEqualTo(WALLET);
        assertThat(logged.getAction()).isEqualTo("CHECK_ELIGIBILITY");
        assertThat(logged.getStatus()).isEqualTo(AuditLogStatus.BLOCKED);
        assertThat(logged.getReason()).isEqualTo("Investor KYC status is REJECTED");
        assertThat(logged.getTimestamp()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Compliance history retrieval
    // ------------------------------------------------------------------

    @Test
    void getAuditLogs_returnsComplianceHistoryForRegisteredInvestor() {
        AuditLog log = AuditLog.builder()
                .walletAddress(WALLET)
                .action("CHECK_ELIGIBILITY")
                .status(AuditLogStatus.BLOCKED)
                .reason("Investor not registered")
                .timestamp(Instant.now())
                .build();
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(auditLogRepository.findByWalletAddress(WALLET)).thenReturn(List.of(log));

        List<AuditLog> history = complianceService.getAuditLogs(WALLET);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getStatus()).isEqualTo(AuditLogStatus.BLOCKED);
        verify(auditLogRepository).findByWalletAddress(WALLET);
    }

    @Test
    void getAuditLogs_throwsInvestorNotFoundWhenWalletNotRegistered() {
        when(investorRepository.findByWalletAddress(WALLET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> complianceService.getAuditLogs(WALLET))
                .isInstanceOf(InvestorNotFoundException.class)
                .hasMessageContaining(WALLET);
        verifyNoInteractions(auditLogRepository);
    }

    // ------------------------------------------------------------------
    // On-chain RPC gatekeeping (after off-chain checks pass)
    // ------------------------------------------------------------------

    @Test
    void verifyEligibility_blocksWhenWalletDoesNotExistOnChain() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(assetTokenRepository.findByMintAddress(MINT))
                .thenReturn(Optional.of(token(AssetTokenComplianceStatus.COMPLIANT)));
        when(solanaRpcAdapter.getAccountInfo(WALLET)).thenReturn(new AccountInfo(null, 0L, false, 0L));

        ComplianceCheckResponse response = complianceService.verifyEligibility(WALLET, MINT);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReason()).isEqualTo("Wallet does not exist on Solana chain");
        assertThat(response.getInvestorStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(response.getAssetStatus()).isEqualTo(AssetTokenComplianceStatus.COMPLIANT);
    }

    @Test
    void verifyEligibility_blocksWhenSolanaRpcUnavailable() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(assetTokenRepository.findByMintAddress(MINT))
                .thenReturn(Optional.of(token(AssetTokenComplianceStatus.COMPLIANT)));
        when(solanaRpcAdapter.getAccountInfo(WALLET))
                .thenThrow(new SolanaRpcException("getAccountInfo", new RuntimeException("Read timed out")));

        ComplianceCheckResponse response = complianceService.verifyEligibility(WALLET, MINT);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReason()).isEqualTo("Solana RPC unavailable - on-chain verification failed");
        assertThat(response.getInvestorStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(response.getAssetStatus()).isEqualTo(AssetTokenComplianceStatus.COMPLIANT);
    }

    @Test
    void verifyEligibility_doesNotCallRpcWhenInvestorRejected() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.REJECTED)));

        complianceService.verifyEligibility(WALLET, MINT);

        verifyNoInteractions(solanaRpcAdapter);
    }

    @Test
    void verifyEligibility_doesNotCallRpcWhenAssetNonCompliant() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(assetTokenRepository.findByMintAddress(MINT))
                .thenReturn(Optional.of(token(AssetTokenComplianceStatus.NON_COMPLIANT)));

        complianceService.verifyEligibility(WALLET, MINT);

        verifyNoInteractions(solanaRpcAdapter);
    }
}
