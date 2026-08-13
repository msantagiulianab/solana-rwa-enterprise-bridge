package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.exception.AssetTokenNotFoundException;
import com.solana.rwa.bridge.repository.AssetTokenRepository;
import com.solana.rwa.bridge.solana.SolanaMintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TokenService {

    private final AssetTokenRepository assetTokenRepository;
    private final SolanaMintService solanaMintService;

    public List<AssetToken> findAll() {
        return assetTokenRepository.findAll();
    }

    public AssetToken findById(UUID id) {
        return assetTokenRepository.findById(id)
                .orElseThrow(() -> new AssetTokenNotFoundException(id.toString(), true));
    }

    @Transactional
    public AssetToken create(AssetTokenRegistrationRequest request) {
        // Issue the real on-chain SPL mint BEFORE persisting the off-chain
        // registry record. A failed RPC call aborts the tokenization rather
        // than leaving an asset without a verifiable mint address.
        String mintAddress = solanaMintService.createMint();

        AssetToken token = AssetToken.builder()
                .assetName(request.getAssetName())
                .valuationUsd(request.getValuationUsd())
                .mintAddress(mintAddress)
                .complianceStatus(AssetTokenComplianceStatus.NON_COMPLIANT)
                .build();
        return assetTokenRepository.save(token);
    }
}
