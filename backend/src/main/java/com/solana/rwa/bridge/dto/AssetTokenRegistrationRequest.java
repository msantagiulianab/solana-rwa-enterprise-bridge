package com.solana.rwa.bridge.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Request payload to tokenize a new real-world asset.
 *
 * @param assetName    human-readable asset name
 * @param valuationUsd USD valuation, must be greater than zero
 */
@Getter
@Builder
public class AssetTokenRegistrationRequest {

    @NotBlank(message = "assetName must not be blank")
    private final String assetName;

    @NotNull(message = "valuationUsd must not be null")
    @DecimalMin(value = "0.01", message = "valuationUsd must be greater than 0")
    private final BigDecimal valuationUsd;
}