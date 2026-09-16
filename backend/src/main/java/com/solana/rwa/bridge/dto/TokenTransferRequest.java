package com.solana.rwa.bridge.dto;

import com.solana.rwa.bridge.validation.ValidSolanaAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for a Token-2022 secondary-market transfer executed through
 * the compliance-gated transfer hook path.
 *
 * <p>The owner wallets drive off-chain compliance screening, while the token
 * accounts drive the on-chain {@code TransferChecked} instruction.
 *
 * @param sourceWallet            base58 owner wallet of the sender
 * @param destinationWallet       base58 owner wallet of the recipient
 * @param sourceTokenAccount      base58 source token account
 * @param destinationTokenAccount base58 destination token account
 * @param assetMintAddress        base58 mint address of the asset token
 * @param amount                  transfer amount in base (smallest) units
 * @param decimals                token decimals (0 triggers the RWA default of 6)
 */
public record TokenTransferRequest(
        @NotBlank(message = "sourceWallet must not be blank")
        @ValidSolanaAddress(message = "sourceWallet must be a valid Solana address")
        String sourceWallet,

        @NotBlank(message = "destinationWallet must not be blank")
        @ValidSolanaAddress(message = "destinationWallet must be a valid Solana address")
        String destinationWallet,

        @NotBlank(message = "sourceTokenAccount must not be blank")
        @ValidSolanaAddress(message = "sourceTokenAccount must be a valid Solana address")
        String sourceTokenAccount,

        @NotBlank(message = "destinationTokenAccount must not be blank")
        @ValidSolanaAddress(message = "destinationTokenAccount must be a valid Solana address")
        String destinationTokenAccount,

        @NotBlank(message = "assetMintAddress must not be blank")
        @ValidSolanaAddress(message = "assetMintAddress must be a valid Solana address")
        String assetMintAddress,

        @Positive(message = "amount must be positive")
        long amount,

        int decimals
) {

    public TokenTransferRequest {
        if (decimals < 0 || decimals > 9) {
            throw new IllegalArgumentException("decimals must be between 0 and 9");
        }
    }

    /**
     * Convenience constructor using the enterprise RWA default of 6 decimals.
     */
    public TokenTransferRequest(String sourceWallet, String destinationWallet,
                                String sourceTokenAccount, String destinationTokenAccount,
                                String assetMintAddress, long amount) {
        this(sourceWallet, destinationWallet, sourceTokenAccount, destinationTokenAccount,
                assetMintAddress, amount, 6);
    }
}

