package com.solana.rwa.bridge.maritime.port;

import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;

import java.time.Instant;

/**
 * Immutable, pure-Java outcome of a maritime clearance evaluation.
 *
 * @param status            the clearance decision
 * @param reasonCode        external authority + reason code (null for CLEARED)
 * @param referenceId       external case / clearance certificate id
 * @param transitPermitToken transit permit token (populated only on CLEARED)
 * @param evaluatedAt       instant at which the evaluation was produced
 */
public record MaritimeClearanceResult(
        ClearanceStatus status,
        ClearanceReasonCode reasonCode,
        String referenceId,
        String transitPermitToken,
        Instant evaluatedAt
) {
}
