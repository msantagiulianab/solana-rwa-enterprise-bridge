package com.solana.rwa.bridge.dto;

import com.solana.rwa.bridge.validation.ValidSolanaAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request payload for a permanent-delegate clawback exposed over the REST API.
 *
 * <p>Carries the same clawback target as {@link ClawbackRequest} plus a
 * client-supplied {@code idempotencyKey} that guards against duplicate RPC
 * broadcasts on network retries. Every field is Bean-validated before the
 * request reaches the service layer.
 *
 * @param mintAddress             base58 mint address of the asset token
 * @param sourceTokenAccount      base58 token account whose funds are clawed back
 * @param destinationTokenAccount base58 token account receiving the recovered funds
 * @param amount                  clawback amount in base (smallest) units, must be positive
 * @param reason                  regulatory justification recorded immutably
 * @param idempotencyKey          client-supplied unique key guarding duplicate broadcasts
 */
public record ClawbackRequestDto(
        @NotBlank(message = "mintAddress must not be blank")
        @ValidSolanaAddress(message = "mintAddress must be a valid Solana address")
        String mintAddress,

        @NotBlank(message = "sourceTokenAccount must not be blank")
        @ValidSolanaAddress(message = "sourceTokenAccount must be a valid Solana address")
        String sourceTokenAccount,

        @NotBlank(message = "destinationTokenAccount must not be blank")
        @ValidSolanaAddress(message = "destinationTokenAccount must be a valid Solana address")
        String destinationTokenAccount,

        @Positive(message = "amount must be positive")
        long amount,

        @NotBlank(message = "reason must not be blank")
        @Size(max = 1000, message = "reason must not exceed 1000 characters")
        String reason,

        @NotBlank(message = "idempotencyKey must not be blank")
        @Size(max = 255, message = "idempotencyKey must not exceed 255 characters")
        String idempotencyKey
) {
}
