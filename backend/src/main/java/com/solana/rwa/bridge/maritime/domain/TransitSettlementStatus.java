package com.solana.rwa.bridge.maritime.domain;

/**
 * Off-chain lifecycle of a Panama Canal transit settlement.
 *
 * <p>This is an additive {@link jakarta.persistence.EnumType#STRING} enum backed
 * by an unconstrained {@code varchar} column.
 */
public enum TransitSettlementStatus {

    /** Settlement record created; awaiting clearance and escrow funding. */
    INITIALIZED,

    /** Escrow account funded with the SPL settlement token. */
    ESCROW_FUNDED,

    /** Maritime clearance passed; settlement is ready to execute. */
    CLEARED,

    /** Settlement executed; off-chain record finalized. */
    SETTLED,

    /** Settlement failed (fail-closed); no broadcast occurred. */
    FAILED
}
