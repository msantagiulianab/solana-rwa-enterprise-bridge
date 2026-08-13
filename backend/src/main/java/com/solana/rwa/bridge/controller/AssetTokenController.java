package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import com.solana.rwa.bridge.repository.AuditLogRepository;
import com.solana.rwa.bridge.service.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class AssetTokenController {

    public static final String ACTION_TOKENIZE_ASSET = "TOKENIZE_ASSET";

    /**
     * Tokenization is an off-chain registry operation with no investor wallet in
     * scope, so the audit trail attributes the event to the Solana system program
     * address as a fixed treasury/sentinel wallet.
     */
    public static final String SYSTEM_TREASURY_WALLET = "11111111111111111111111111111111";

    private final TokenService tokenService;
    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public List<AssetToken> getAllTokens() {
        return tokenService.findAll();
    }

    /**
     * POST /api/tokens — registers a new asset token off-chain and writes an
     * immutable audit log entry for the successful tokenization.
     */
    @PostMapping
    public AssetToken createToken(@Valid @RequestBody AssetTokenRegistrationRequest request) {
        AssetToken token = tokenService.create(request);

        auditLogRepository.save(AuditLog.builder()
                .walletAddress(SYSTEM_TREASURY_WALLET)
                .action(ACTION_TOKENIZE_ASSET)
                .status(AuditLogStatus.APPROVED)
                .reason("Asset tokenized: " + token.getAssetName())
                .build());

        return token;
    }

    @GetMapping("/{id}")
    public AssetToken getTokenById(@PathVariable UUID id) {
        return tokenService.findById(id);
    }
}