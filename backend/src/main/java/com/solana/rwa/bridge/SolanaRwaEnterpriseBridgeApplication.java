package com.solana.rwa.bridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Solana RWA Enterprise Bridge backend.
 *
 * <p>Bootstraps the Spring Boot application context, wiring off-chain services
 * (compliance, audit, persistence) with the Solana Devnet RPC layer.
 */
@SpringBootApplication
public class SolanaRwaEnterpriseBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SolanaRwaEnterpriseBridgeApplication.class, args);
    }
}
