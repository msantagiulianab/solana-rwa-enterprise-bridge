package com.solana.rwa.bridge.compliance.port;

/**
 * Hexagonal outbound port for pre-broadcast transfer compliance.
 *
 * <p>Owned by the application core and implemented by sandbox and (future)
 * production adapters. The transfer execution path depends only on this port —
 * never on KYC/AML/OFAC transport details. The interface and its
 * request/result records import no Spring framework classes (pure Java 21).
 */
public interface TransferCompliancePort {

    /**
     * Evaluates a secondary-market token transfer against off-chain compliance
     * rules. Returns a typed {@link TransferComplianceResult}; a
     * {@link TransferComplianceStatus#BLOCKED} decision is a fail-closed signal
     * that the transaction must never be broadcast.
     *
     * @param request the transfer under evaluation
     * @return the compliance decision with reason code
     */
    TransferComplianceResult evaluateTransfer(TransferComplianceRequest request);
}
