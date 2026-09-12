package com.solana.rwa.bridge.entity;

/**
 * Outcome of a transfer-hook compliance evaluation, persisted as a string enum
 * in the immutable {@code transfer_hook_audit_logs} ledger.
 *
 * <p>Uses the transfer-hook domain vocabulary ({@code CLEARED}/{@code BLOCKED})
 * rather than the SPI's {@code APPROVED}/{@code BLOCKED} so the audit ledger
 * records the fail-closed clearing decision of the on-chain hook.
 */
public enum TransferHookAuditStatus {
    /** Evaluation passed; the transfer is eligible to be dispatched on-chain. */
    CLEARED,
    /** Evaluation blocked the transfer; no broadcast occurred. */
    BLOCKED
}
