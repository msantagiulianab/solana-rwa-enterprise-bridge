package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.exception.AssetTokenNotFoundException;
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
                .orElseThrow(() -> new AssetTokenNotFoundException(id.toString(), true));
    }

    @Transactional
    public AssetToken create(AssetTokenRegistrationRequest request) {
        AssetToken token = AssetToken.builder()
                .assetName(request.getAssetName())
                .valuationUsd(request.getValuationUsd())
                .complianceStatus(AssetTokenComplianceStatus.NON_COMPLIANT)
                .build();
        return assetTokenRepository.save(token);
    }
}
