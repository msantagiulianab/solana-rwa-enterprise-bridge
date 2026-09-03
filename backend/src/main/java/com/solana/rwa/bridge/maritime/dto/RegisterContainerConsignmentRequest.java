package com.solana.rwa.bridge.maritime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request payload for a single container consignment attached to an eBL.
 *
 * @param containerNumber  container / consignment identifier
 * @param declaredValueUsd declared value of the container's cargo in USD
 */
public record RegisterContainerConsignmentRequest(
        @NotBlank(message = "containerNumber must not be blank") String containerNumber,
        @NotNull(message = "declaredValueUsd must not be null")
        @Positive(message = "declaredValueUsd must be positive")
        BigDecimal declaredValueUsd) {
}
