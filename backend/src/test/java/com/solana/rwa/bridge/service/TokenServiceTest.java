package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.repository.AssetTokenRepository;
import com.solana.rwa.bridge.repository.InvestorRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.AccountInfo;
import com.solana.rwa.bridge.solana.SolanaMintService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link TokenService}.
 *
 * <p>The Solana mint creation is delegated to {@link SolanaMintService} and
 * mocked here, keeping these tests fully offline. The pre-flight compliance
 * gate must block minting for unregistered, KYC-rejected, sanctions-flagged,
 * pending, or off-chain-absent issuers, and must only mint when cleared. The
 * tokenization flow must also persist a PENDING settlement state before
 * dispatching the mint and honor idempotency-key replays without re-broadcast.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String MINT = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String SYSTEM_OWNER = "11111111111111111111111111111111";
    private static final String IDEMPOTENCY_KEY = "idem-tokenize-0001";

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
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();
    }

    private AssetTokenRegistrationRequest requestWithIdempotencyKey(String idempotencyKey) {
        return AssetTokenRegistrationRequest.builder()
                .assetName("Prime Manhattan Office Fund")
                .valuationUsd(new BigDecimal("125000000.00"))
                .issuerWalletAddress(WALLET)
                .idempotencyKey(idempotencyKey)
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
    void create_persistsPendingBeforeMintAndConfirmsWithMintAddressOnSuccess() {
        stubVerifiedIssuerOnChain();
        when(solanaMintService.createMint()).thenReturn(MINT);
        List<String> settlementStatuses = new ArrayList<>();
        List<String> mintAddresses = new ArrayList<>();
        when(assetTokenRepository.save(any(AssetToken.class)))
                .thenAnswer(invocation -> {
                    AssetToken saved = invocation.getArgument(0);
                    settlementStatuses.add(saved.getSettlementStatus() == null
                            ? null : saved.getSettlementStatus().name());
                    mintAddresses.add(saved.getMintAddress());
                    return saved;
                });

        AssetToken token = tokenService.create(request());

        assertThat(token.getMintAddress()).isEqualTo(MINT);
        assertThat(token.getAssetName()).isEqualTo("Prime Manhattan Office Fund");
        assertThat(token.getComplianceStatus()).isEqualTo(AssetTokenComplianceStatus.COMPLIANT);
        assertThat(token.getSettlementStatus()).isEqualTo(SettlementStatus.CONFIRMED);

        // The PENDING record is persisted BEFORE the RPC dispatch, and only then
        // is the settled record persisted with the on-chain mint address.
        InOrder inOrder = inOrder(assetTokenRepository, solanaMintService);
        inOrder.verify(assetTokenRepository).save(any(AssetToken.class));
        inOrder.verify(solanaMintService).createMint();
        inOrder.verify(assetTokenRepository).save(any(AssetToken.class));

        assertThat(settlementStatuses).containsExactly("PENDING", "CONFIRMED");
        assertThat(mintAddresses).containsExactly(null, MINT);
    }

    @Test
    void create_returnsExistingAssetWithoutReBroadcastingWhenIdempotencyKeyReplayed() {
        stubVerifiedIssuerOnChain();
        AssetToken existing = AssetToken.builder()
                .assetName("Already tokenized")
                .valuationUsd(new BigDecimal("100.00"))
                .mintAddress(MINT)
                .complianceStatus(AssetTokenComplianceStatus.COMPLIANT)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .settlementStatus(SettlementStatus.CONFIRMED)
                .build();
        when(assetTokenRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        AssetToken result = tokenService.create(request());

        assertThat(result).isSameAs(existing);
        verify(solanaMintService, never()).createMint();
        verify(assetTokenRepository, never()).save(any(AssetToken.class));
    }

    @Test
    void create_marksSettlementFailedAndRethrowsWhenMintFails() {
        stubVerifiedIssuerOnChain();
        List<String> settlementStatuses = new ArrayList<>();
        when(assetTokenRepository.save(any(AssetToken.class)))
                .thenAnswer(invocation -> {
                    AssetToken saved = invocation.getArgument(0);
                    settlementStatuses.add(saved.getSettlementStatus() == null
                            ? null : saved.getSettlementStatus().name());
                    return saved;
                });
        ResponseStatusException mintFailure = new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Solana Devnet Mint Error: boom");
        when(solanaMintService.createMint()).thenThrow(mintFailure);

        assertThatThrownBy(() -> tokenService.create(request())).isSameAs(mintFailure);

        assertThat(settlementStatuses).containsExactly("PENDING", "FAILED");
    }

    @Test
    void create_rejectsBlankIdempotencyKey() {
        assertThatThrownBy(() -> tokenService.create(requestWithIdempotencyKey("   ")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Idempotency key is required");
                });

        verifyNoInteractions(investorRepository, solanaRpcAdapter, solanaMintService, assetTokenRepository);
    }

    @Test
    void create_rejectsOversizedIdempotencyKey() {
        assertThatThrownBy(() -> tokenService.create(requestWithIdempotencyKey("a".repeat(256))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("must not exceed 255");
                });

        verifyNoInteractions(investorRepository, solanaRpcAdapter, solanaMintService, assetTokenRepository);
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