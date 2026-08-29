package com.solana.rwa.bridge.entity;

/**
 * Off-chain settlement lifecycle of an on-chain mint attempt.
 *
 * <p>{@link #PENDING} is persisted <em>before</em> the Solana RPC broadcast so a
 * retried/failed dispatch can always be correlated to a durable off-chain row;
 * {@link #CONFIRMED} marks a successful mint; {@link #FAILED} marks an attempt
 * whose RPC dispatch aborted.
 */
public enum SettlementStatus {
    PENDING,
    CONFIRMED,
    FAILED
}
