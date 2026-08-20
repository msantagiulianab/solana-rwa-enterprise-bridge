package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.repository.AssetTokenRepository;
import com.solana.rwa.bridge.repository.InvestorRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.AccountInfo;
import com.solana.rwa.bridge.solana.SolanaMintService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link TokenService}.
 *
 * <p>The Solana mint creation is delegated to {@link SolanaMintService} and
 * mocked here, keeping these tests fully offline. The pre-flight compliance
 * gate must block minting for unregistered, KYC-rejected, sanctions-flagged,
 * pending, or off-chain-absent issuers, and must only mint when cleared.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String MINT = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String SYSTEM_OWNER = "11111111111111111111111111111111";

    @Mock
    private AssetTokenRepository assetTokenRepository;

    @Mock
    private SolanaMintService solanaMintService;

    @Mock
    private InvestorRepository investorRepository;

    @Mock
    private SolanaRpcAdapter solanaRpcAdapter;

    @InjectMocks
    private TokenService tokenService;

    private AssetTokenRegistrationRequest request() {
        return AssetTokenRegistrationRequest.builder()
                .assetName("Prime Manhattan Office Fund")
                .valuationUsd(new BigDecimal("125000000.00"))
                .issuerWalletAddress(WALLET)
                .build();
    }

    private Investor investor(KycStatus status) {
        return Investor.builder()
                .fullName("Test Investor")
                .email("test@example.com")
                .walletAddress(WALLET)
                .kycStatus(status)
                .country("US")
                .build();
    }

    private void stubVerifiedIssuerOnChain() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(solanaRpcAdapter.getAccountInfo(WALLET))
                .thenReturn(new AccountInfo(SYSTEM_OWNER, 5_000_000L, false, 80L));
    }

    @Test
    void create_persistsAssetWithOnChainMintAddressWhenIssuerCompliant() {
        stubVerifiedIssuerOnChain();
        when(solanaMintService.createMint()).thenReturn(MINT);
        when(assetTokenRepository.save(any(AssetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssetToken token = tokenService.create(request());

        assertThat(token.getMintAddress()).isEqualTo(MINT);
        assertThat(token.getAssetName()).isEqualTo("Prime Manhattan Office Fund");
        assertThat(token.getComplianceStatus()).isEqualTo(AssetTokenComplianceStatus.COMPLIANT);

        verify(solanaMintService).createMint();
        verify(assetTokenRepository).save(any(AssetToken.class));
    }

    @Test
    void create_blocksMintWhenIssuerNotRegistered() {
        when(investorRepository.findByWalletAddress(WALLET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.create(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(rse.getReason()).contains("Investor not registered");
                });

        verifyNoInteractions(solanaMintService);
        verifyNoInteractions(solanaRpcAdapter);
        verifyNoInteractions(assetTokenRepository);
    }

    @Test
    void create_blocksMintWhenIssuerRejected() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.REJECTED)));

        assertThatThrownBy(() -> tokenService.create(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(rse.getReason()).contains("REJECTED");
                });

        verifyNoInteractions(solanaMintService);
        verifyNoInteractions(solanaRpcAdapter);
        verifyNoInteractions(assetTokenRepository);
    }

    @Test
    void create_blocksMintWhenIssuerFlaggedForSanctions() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.FLAGGED_SANCTION)));

        assertThatThrownBy(() -> tokenService.create(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(rse.getReason()).contains("sanctions screening");
                });

        verifyNoInteractions(solanaMintService);
        verifyNoInteractions(solanaRpcAdapter);
        verifyNoInteractions(assetTokenRepository);
    }

    @Test
    void create_blocksMintWhenIssuerKycPending() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.PENDING)));

        assertThatThrownBy(() -> tokenService.create(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(rse.getReason()).contains("not complete");
                });

        verifyNoInteractions(solanaMintService);
        verifyNoInteractions(solanaRpcAdapter);
        verifyNoInteractions(assetTokenRepository);
    }

    @Test
    void create_blocksMintWhenIssuerWalletAbsentOnChain() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(solanaRpcAdapter.getAccountInfo(WALLET))
                .thenReturn(new AccountInfo(null, 0L, false, 0L));

        assertThatThrownBy(() -> tokenService.create(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(rse.getReason()).contains("does not exist on Solana chain");
                });

        verifyNoInteractions(solanaMintService);
        verifyNoInteractions(assetTokenRepository);
    }

    @Test
    void create_blocksMintWhenRpcUnavailable() {
        when(investorRepository.findByWalletAddress(WALLET))
                .thenReturn(Optional.of(investor(KycStatus.VERIFIED)));
        when(solanaRpcAdapter.getAccountInfo(WALLET))
                .thenThrow(new SolanaRpcException("getAccountInfo", new RuntimeException("Read timed out")));

        assertThatThrownBy(() -> tokenService.create(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(rse.getReason()).contains("RPC unavailable");
                });

        verifyNoInteractions(solanaMintService);
        verifyNoInteractions(assetTokenRepository);
    }
}