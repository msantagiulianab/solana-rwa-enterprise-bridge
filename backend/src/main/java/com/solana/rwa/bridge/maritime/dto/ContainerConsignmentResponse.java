package com.solana.rwa.bridge.maritime.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response projection for a container consignment.
 *
 * @param id               consignment identifier
 * @param containerNumber  container / consignment identifier
 * @param declaredValueUsd declared value of the container's cargo in USD
 */
public record ContainerConsignmentResponse(
        UUID id,
        String containerNumber,
        BigDecimal declaredValueUsd) {
}
