package com.solana.rwa.bridge.exception;

/**
 * Thrown when an operation requires a registered investor, but no investor
 * record exists for the requested wallet address.
 */
public class InvestorNotFoundException extends RuntimeException {

    public InvestorNotFoundException(String walletAddress) {
        super("Investor not registered for wallet address: " + walletAddress);
    }
}
