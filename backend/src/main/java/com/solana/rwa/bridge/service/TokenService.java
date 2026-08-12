package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.repository.AssetTokenRepository;
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

    public List<AssetToken> findAll() {
        return assetTokenRepository.findAll();
    }

    public AssetToken findById(UUID id) {
        return assetTokenRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Asset token not found for id: " + id));
    }

    /**
     * Registers a new asset token off-chain. Tokens start NON_COMPLIANT and
     * remain unminted (no {@code mintAddress}) until compliance + RPC mint pass.
     */
    @Transactional
    public AssetToken create(AssetTokenRegistrationRequest request) {
        return assetTokenRepository.save(AssetToken.builder()
                .assetName(request.getAssetName())
                .valuationUsd(request.getValuationUsd())
                .complianceStatus(AssetTokenComplianceStatus.NON_COMPLIANT)
                .build());
    }
}
