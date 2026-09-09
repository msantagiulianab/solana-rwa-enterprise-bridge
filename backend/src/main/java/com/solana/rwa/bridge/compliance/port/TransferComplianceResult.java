package com.solana.rwa.bridge.compliance.port;

import java.time.Instant;

/**
 * Immutable, pure-Java outcome of a transfer compliance evaluation.
 *
 * @param status      the compliance decision
 * @param reason      authority + reason code (never {@code null})
 * @param referenceId correlation id persisted off-chain for auditability
 * @param evaluatedAt instant at which the evaluation was produced
 */
public record TransferComplianceResult(
        TransferComplianceStatus status,
        TransferComplianceReason reason,
        String referenceId,
        Instant evaluatedAt
) {
}
