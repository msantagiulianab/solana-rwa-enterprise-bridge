package com.solana.rwa.bridge.maritime.dto;

import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.maritime.domain.TransitSettlementStatus;

import java.util.UUID;

/**
 * Response projection for a Panama Canal transit settlement and its on-chain
 * finality state.
 *
 * @param id                   settlement identifier
 * @param billOfLadingId       linked electronic Bill of Lading
 * @param status               off-chain settlement lifecycle status
 * @param transactionSignature Solana transaction signature (null until broadcast)
 * @param finalityState        on-chain finality state (outbox state)
 */
public record CanalTransitSettlementResponse(
        UUID id,
        UUID billOfLadingId,
        TransitSettlementStatus status,
        String transactionSignature,
        SettlementStatus finalityState) {
}
