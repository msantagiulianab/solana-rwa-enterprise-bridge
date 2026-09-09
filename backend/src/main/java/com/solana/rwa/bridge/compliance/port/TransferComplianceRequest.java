package com.solana.rwa.bridge.compliance.port;

/**
 * Immutable, pure-Java request for a transfer compliance evaluation.
 *
 * @param sourceWallet      Solana base58 owner wallet of the source token account
 * @param destinationWallet Solana base58 owner wallet of the destination token account
 * @param assetMintAddress  Solana base58 mint address of the asset token
 * @param amount            transfer amount in base (smallest) units
 */
public record TransferComplianceRequest(
        String sourceWallet,
        String destinationWallet,
        String assetMintAddress,
        long amount
) {
}
