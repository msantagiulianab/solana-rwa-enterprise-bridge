package com.solana.rwa.bridge.dto;

import com.solana.rwa.bridge.validation.ValidSolanaAddress;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Request payload to tokenize a new real-world asset.
 *
 * @param assetName           human-readable asset name
 * @param valuationUsd        USD valuation, must be greater than zero
 * @param issuerWalletAddress issuer's Solana wallet (base58, 32-44 chars)
 */
@Getter
@Builder
public class AssetTokenRegistrationRequest {

    @NotBlank(message = "assetName must not be blank")
    private final String assetName;

    @NotNull(message = "valuationUsd must not be null")
    @DecimalMin(value = "0.01", message = "valuationUsd must be greater than 0")
    private final BigDecimal valuationUsd;

    @NotBlank(message = "issuerWalletAddress must not be blank")
    @ValidSolanaAddress(message = "issuerWalletAddress must be a valid Solana address")
    private final String issuerWalletAddress;
}