package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{id}")
    public AssetToken getTokenById(@PathVariable UUID id) {
        return tokenService.findById(id);
    }
}