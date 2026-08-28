package com.solana.rwa.bridge.compliance.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable settlement-proof audit record emitted by the compliance export
 * engine.
 *
 * <p>Captures the off-chain KYC/OFAC decision together with the cryptographic
 * Solana settlement proof for the on-chain transaction attempt (priority fee,
 * compute budget, slot, recent blockhash, and transaction signature).
 */
@Getter
@Builder
public class AuditExportRecordDto {

    /** Stable event identifier. */
    private final UUID eventId;

    /** Instant at which the settlement attempt was evaluated (ISO-8601). */
    private final Instant timestamp;

    /** Off-chain identifier of the tokenized real-world asset. */
    private final String assetId;

    /** Investor's Solana wallet, base58-encoded. */
    private final String investorWallet;

    /** True when the investor passed KYC verification. */
    private final boolean kycVerified;

    /** True when the investor passed OFAC/sanctions screening. */
    private final boolean ofacPassed;

    /** Transaction execution status: SUCCESS, FAILED_COMPLIANCE, or FAILED_RPC. */
    private final String status;

    /** Priority fee paid in micro-lamports per compute unit. */
    private final long computeUnitPriceMicroLamports;

    /** Compute-unit budget limit requested for the transaction. */
    private final int computeUnitLimit;

    /** Base58 transaction signature; null when no transaction was broadcast. */
    private final String solanaTransactionSignature;

    /** Solana slot at which the transaction landed; null when not confirmed. */
    private final Long slot;

    /** Recent blockhash the transaction was bound to; null when not broadcast. */
    private final String blockhash;
}
