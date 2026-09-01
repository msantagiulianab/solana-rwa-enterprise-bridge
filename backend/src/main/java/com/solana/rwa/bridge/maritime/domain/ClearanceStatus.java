package com.solana.rwa.bridge.maritime.domain;

/**
 * Maritime clearance decision for an electronic Bill of Lading transit.
 *
 * <p>This is an additive {@link jakarta.persistence.EnumType#STRING} enum backed
 * by an unconstrained {@code varchar} column, so appending values never requires
 * a schema change.
 */
public enum ClearanceStatus {

    /** Awaiting an external maritime clearance evaluation. */
    PENDING,

    /** Cleared by all external authorities; the only settlement-permitting state. */
    CLEARED,

    /** Parked by customs (ANA SIGA); no settlement may proceed. */
    HELD_CUSTOMS,

    /** Blocked by sanctions screening (OFAC). */
    SANCTIONED,

    /** Rejected by the maritime authority (ACP VUMPA), e.g. unverified carrier. */
    REJECTED
}
