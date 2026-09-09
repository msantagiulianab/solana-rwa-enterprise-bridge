package com.solana.rwa.bridge.compliance.port;

/**
 * Outcome of a pre-broadcast transfer compliance evaluation.
 */
public enum TransferComplianceStatus {
    /** Transfer is eligible to be dispatched on-chain. */
    APPROVED,
    /** Transfer is blocked; the transaction must never be broadcast. */
    BLOCKED
}
