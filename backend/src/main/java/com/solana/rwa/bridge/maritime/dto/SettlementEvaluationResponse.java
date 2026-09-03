package com.solana.rwa.bridge.maritime.dto;

import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;

import java.util.UUID;

/**
 * Result of a maritime clearance evaluation for a canal transit settlement.
 *
 * @param settlementId settlement identifier under evaluation
 * @param status       clearance decision
 * @param authority    issuing maritime authority (null for CLEARED)
 * @param reasonCode   authority-specific reason code (null for CLEARED)
 * @param referenceId  external case / audit reference
 */
public record SettlementEvaluationResponse(
        UUID settlementId,
        ClearanceStatus status,
        String authority,
        String reasonCode,
        String referenceId) {
}
