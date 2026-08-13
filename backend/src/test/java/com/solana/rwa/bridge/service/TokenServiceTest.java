package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.repository.AssetTokenRepository;
import com.solana.rwa.bridge.solana.SolanaMintService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link TokenService}.
 *
 * <p>The Solana mint creation is delegated to {@link SolanaMintService} and
 * mocked here, keeping these tests fully offline. The returned mint address
 * must be persisted on the asset and surfaced in the response entity.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String MINT = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";

    @Mock
    private AssetTokenRepository assetTokenRepository;

    @Mock
    private SolanaMintService solanaMintService;

    @InjectMocks
    private TokenService tokenService;

    @Test
    void create_persistsAssetWithOnChainMintAddress() {
        AssetTokenRegistrationRequest request = AssetTokenRegistrationRequest.builder()
                .assetName("Prime Manhattan Office Fund")
                .valuationUsd(new BigDecimal("125000000.00"))
                .build();

        when(solanaMintService.createMint()).thenReturn(MINT);
        when(assetTokenRepository.save(any(AssetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssetToken token = tokenService.create(request);

        assertThat(token.getMintAddress()).isEqualTo(MINT);
        assertThat(token.getAssetName()).isEqualTo("Prime Manhattan Office Fund");
        assertThat(token.getComplianceStatus()).isEqualTo(AssetTokenComplianceStatus.NON_COMPLIANT);

        verify(solanaMintService).createMint();
        verify(assetTokenRepository).save(any(AssetToken.class));
    }
}