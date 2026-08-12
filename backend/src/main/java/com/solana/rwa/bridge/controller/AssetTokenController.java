package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
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

    private final TokenService tokenService;

    @GetMapping
    public List<AssetToken> getAllTokens() {
        return tokenService.findAll();
    }

    /**
     * POST /api/tokens — registers a new asset token off-chain.
     */
    @PostMapping
    public AssetToken createToken(@Valid @RequestBody AssetTokenRegistrationRequest request) {
        return tokenService.create(request);
    }

    @GetMapping("/{id}")
    public AssetToken getTokenById(@PathVariable UUID id) {
        return tokenService.findById(id);
    }
}