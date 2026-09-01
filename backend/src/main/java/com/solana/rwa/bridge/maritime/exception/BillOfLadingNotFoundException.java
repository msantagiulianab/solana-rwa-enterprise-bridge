package com.solana.rwa.bridge.maritime.exception;

import java.util.UUID;

/**
 * Thrown when an operation requires a registered Bill of Lading, but no record
 * exists for the requested identifier.
 */
public class BillOfLadingNotFoundException extends RuntimeException {

    public BillOfLadingNotFoundException(UUID id) {
        super("Bill of lading not found for id: " + id);
    }

    public BillOfLadingNotFoundException(String blNumber) {
        super("Bill of lading not found for number: " + blNumber);
    }
}
