package com.solana.rwa.bridge.maritime.exception;

import java.util.UUID;

/**
 * Thrown when a canal transit settlement operation references a settlement
 * identifier that does not exist in the persistence store.
 */
public class CanalTransitSettlementNotFoundException extends RuntimeException {

    public CanalTransitSettlementNotFoundException(UUID id) {
        super("Canal transit settlement not found for id: " + id);
    }
}
