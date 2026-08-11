package com.solana.rwa.bridge.entity;

/**
 * KYC/AML verification status of an investor, persisted as a string enum.
 */
public enum KycStatus {
    PENDING,
    VERIFIED,
    REJECTED,
    FLAGGED_SANCTION
}
