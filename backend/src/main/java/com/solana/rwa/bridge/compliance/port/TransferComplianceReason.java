package com.solana.rwa.bridge.compliance.port;

/**
 * Immutable, pure-Java reason code for a transfer compliance decision.
 *
 * @param authority the compliance authority that issued the decision (e.g.
 *                  {@code COMPLIANCE}, {@code KYC}, {@code OFAC})
 * @param code      the authority-specific reason code (e.g. {@code APPROVED},
 *                  {@code SANCTIONED_DESTINATION}, {@code INVALID_AMOUNT})
 */
public record TransferComplianceReason(String authority, String code) {
}
