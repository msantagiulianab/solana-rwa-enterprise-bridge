package com.solana.rwa.bridge.dto;

import java.time.Instant;

/**
 * Result of a compliance-gated Token-2022 transfer broadcast.
 *
 * @param signature        base58 transaction signature (populated only on success)
 * @param complianceStatus final compliance status of the dispatched transfer
 * @param referenceId      compliance correlation id persisted off-chain
 * @param evaluatedAt      instant at which compliance was evaluated
 */
public record TokenTransferResult(
        String signature,
        String complianceStatus,
        String referenceId,
        Instant evaluatedAt
) {
}
