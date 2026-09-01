package com.solana.rwa.bridge.maritime.port;

/**
 * Immutable, pure-Java external authority reason code for a clearance decision.
 *
 * @param authority the issuing maritime authority (e.g. {@code OFAC},
 *                  {@code ANA_SIGA}, {@code ACP_VUMPA}, or {@code NONE})
 * @param code      the authority-specific reason code (e.g. {@code SDN_MATCH},
 *                  {@code CUSTOMS_HOLD}, {@code UNVERIFIED_CARRIER}, {@code CLEARED})
 */
public record ClearanceReasonCode(String authority, String code) {
}
