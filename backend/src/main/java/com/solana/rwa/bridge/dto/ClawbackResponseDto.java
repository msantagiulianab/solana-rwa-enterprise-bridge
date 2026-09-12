package com.solana.rwa.bridge.dto;

import java.time.Instant;

/**
 * Response payload for a permanent-delegate clawback broadcast.
 *
 * @param signature              base58 transaction signature of the clawback broadcast
 * @param action                 the audit action performed (always {@code CLAWBACK})
 * @param mintAddress            base58 mint address of the clawed-back asset
 * @param sourceTokenAccount     base58 source token account
 * @param destinationTokenAccount base58 destination token account
 * @param amount                 clawback amount in base (smallest) units
 * @param executedAt             instant the clawback was broadcast and audit-logged
 */
public record ClawbackResponseDto(
        String signature,
        String action,
        String mintAddress,
        String sourceTokenAccount,
        String destinationTokenAccount,
        long amount,
        Instant executedAt
) {

    /**
     * Maps the service-layer {@link ClawbackResult} into the REST response contract.
     */
    public static ClawbackResponseDto from(ClawbackResult result) {
        return new ClawbackResponseDto(
                result.signature(),
                result.action(),
                result.mintAddress(),
                result.sourceTokenAccount(),
                result.destinationTokenAccount(),
                result.amount(),
                result.executedAt());
    }
}
