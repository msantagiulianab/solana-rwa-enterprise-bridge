package com.solana.rwa.bridge.maritime.port;

/**
 * Hexagonal outbound port for external maritime/institutional clearance.
 *
 * <p>Owned by the application core and implemented by sandbox and (future)
 * production adapters. The settlement domain depends only on this port — never
 * on ACP VUMPA, ANA SIGA, AMP, or OFAC transport details. The interface and its
 * request/result records import no Spring framework classes (pure Java 21).
 */
public interface MaritimeClearancePort {

    /**
     * Deterministically evaluates a Panama Canal transit against external
     * maritime authorities. Returns a typed {@link MaritimeClearanceResult};
     * never throws for a domain rejection (only for a transport/infrastructure
     * fault, which the sandbox adapter does not produce).
     *
     * @param request the transit/consignment under evaluation
     * @return the clearance decision with reason code and (on success) the
     *         clearance certificate id and transit permit token
     */
    MaritimeClearanceResult evaluateClearance(MaritimeClearanceRequest request);
}
