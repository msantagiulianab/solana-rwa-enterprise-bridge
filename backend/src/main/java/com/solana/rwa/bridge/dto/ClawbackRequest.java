package com.solana.rwa.bridge.dto;

/**
 * Request payload for a permanent-delegate clawback of a Token-2022 asset.
 *
 * <p>A clawback bypasses the source token account owner and is authorized solely
 * by the mint's Permanent Delegate (the enterprise oversight wallet), enabling a
 * compliance officer to recover assets after an off-chain regulatory breach.
 *
 * @param mintAddress            base58 mint address of the asset token
 * @param sourceTokenAccount     base58 token account whose funds are clawed back
 * @param destinationTokenAccount base58 token account receiving the recovered funds
 * @param amount                 clawback amount in base (smallest) units
 * @param reason                 regulatory justification recorded immutably on-chain
 */
public record ClawbackRequest(
        String mintAddress,
        String sourceTokenAccount,
        String destinationTokenAccount,
        long amount,
        String reason
) {
}
