package com.solana.rwa.bridge.maritime.adapter.out.simulation;

import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;
import com.solana.rwa.bridge.maritime.port.ClearanceReasonCode;
import com.solana.rwa.bridge.maritime.port.MaritimeClearancePort;
import com.solana.rwa.bridge.maritime.port.MaritimeClearanceRequest;
import com.solana.rwa.bridge.maritime.port.MaritimeClearanceResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, in-process simulation of external maritime authorities.
 *
 * <p>Backs the {@link MaritimeClearancePort} boundary with four deterministic
 * rules — no live network calls, no SOAP/REST SDK. This adapter is the sandbox
 * stand-in for the post-seed institutional connectors (ACP VUMPA, ANA SIGA,
 * AMP, OFAC).
 *
 * <p>Evaluation order (first match wins):
 * <ol>
 *   <li>Vessel IMO {@code IMO9999999} or a blacklisted consignee wallet →
 *       {@link ClearanceStatus#SANCTIONED} (OFAC).</li>
 *   <li>Seal number starting with {@code HOLD} or container
 *       {@code CONT-HOLD-001} → {@link ClearanceStatus#HELD_CUSTOMS}
 *       (ANA SIGA).</li>
 *   <li>Carrier code starting with {@code UNVERIFIED} →
 *       {@link ClearanceStatus#REJECTED} (ACP VUMPA).</li>
 *   <li>Otherwise → {@link ClearanceStatus#CLEARED} with a clearance
 *       certificate id and transit permit token.</li>
 * </ol>
 */
@Component
public class SimulatedMaritimeClearanceAdapter implements MaritimeClearancePort {

    public static final String SANCTIONED_VESSEL_IMO = "IMO9999999";
    public static final String HELD_CONTAINER_NUMBER = "CONT-HOLD-001";
    public static final String SEAL_HOLD_PREFIX = "HOLD";
    public static final String UNVERIFIED_CARRIER_PREFIX = "UNVERIFIED";

    public static final ClearanceReasonCode OFAC_SANCTIONS_MATCH =
            new ClearanceReasonCode("OFAC", "SDN_MATCH");
    public static final ClearanceReasonCode ANA_SIGA_CUSTOMS_HOLD =
            new ClearanceReasonCode("ANA_SIGA", "CUSTOMS_HOLD");
    public static final ClearanceReasonCode ACP_VUMPA_UNVERIFIED_CARRIER =
            new ClearanceReasonCode("ACP_VUMPA", "UNVERIFIED_CARRIER");
    public static final ClearanceReasonCode NONE_CLEARED =
            new ClearanceReasonCode("NONE", "CLEARED");

    /**
     * Deterministic blacklist of consignee wallets used by the OFAC rule.
     */
    public static final String SANCTIONED_CONSIGNEE_WALLET = "BLACKLISTED_CONSIGNEE_WALLET";

    private static final Set<String> SANCTIONED_CONSIGNEE_WALLETS =
            Set.of(SANCTIONED_CONSIGNEE_WALLET);

    @Override
    public MaritimeClearanceResult evaluateClearance(MaritimeClearanceRequest request) {
        Instant now = Instant.now();

        if (isSanctioned(request)) {
            return new MaritimeClearanceResult(ClearanceStatus.SANCTIONED, OFAC_SANCTIONS_MATCH,
                    reference("OFAC", request), null, now);
        }
        if (isCustomsHold(request)) {
            return new MaritimeClearanceResult(ClearanceStatus.HELD_CUSTOMS, ANA_SIGA_CUSTOMS_HOLD,
                    reference("ANA-SIGA", request), null, now);
        }
        if (isUnverifiedCarrier(request)) {
            return new MaritimeClearanceResult(ClearanceStatus.REJECTED, ACP_VUMPA_UNVERIFIED_CARRIER,
                    reference("ACP-VUMPA", request), null, now);
        }

        return new MaritimeClearanceResult(ClearanceStatus.CLEARED, NONE_CLEARED,
                reference("CLEARANCE-CERT", request), reference("TRANSIT-PERMIT", request), now);
    }

    private boolean isSanctioned(MaritimeClearanceRequest request) {
        return SANCTIONED_VESSEL_IMO.equals(request.vesselImo())
                || (request.consigneeWallet() != null
                    && SANCTIONED_CONSIGNEE_WALLETS.contains(request.consigneeWallet()));
    }

    private boolean isCustomsHold(MaritimeClearanceRequest request) {
        return (request.sealNumber() != null
                    && request.sealNumber().toUpperCase(Locale.ROOT).startsWith(SEAL_HOLD_PREFIX))
                || HELD_CONTAINER_NUMBER.equals(request.containerNumber());
    }

    private boolean isUnverifiedCarrier(MaritimeClearanceRequest request) {
        return request.carrierCode() != null
                && request.carrierCode().toUpperCase(Locale.ROOT).startsWith(UNVERIFIED_CARRIER_PREFIX);
    }

    private static String reference(String prefix, MaritimeClearanceRequest request) {
        String bl = request.billOfLadingNumber() != null ? request.billOfLadingNumber() : "UNKNOWN";
        return prefix + "-" + bl;
    }
}
