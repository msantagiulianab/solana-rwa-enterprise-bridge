package com.solana.rwa.bridge.maritime.adapter.out.simulation;

import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;
import com.solana.rwa.bridge.maritime.port.MaritimeClearanceRequest;
import com.solana.rwa.bridge.maritime.port.MaritimeClearanceResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the deterministic 4-scenario simulation matrix in
 * {@link SimulatedMaritimeClearanceAdapter}.
 */
class SimulatedMaritimeClearanceAdapterTest {

    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";

    private final SimulatedMaritimeClearanceAdapter adapter = new SimulatedMaritimeClearanceAdapter();

    private MaritimeClearanceRequest request(String vesselImo, String carrierCode,
                                             String consigneeWallet, String containerNumber,
                                             String sealNumber) {
        return new MaritimeClearanceRequest(
                "BL-2026-0001",
                containerNumber,
                sealNumber,
                new BigDecimal("24000.00"),
                false,
                vesselImo,
                carrierCode,
                "PACTB",
                "USNYC",
                consigneeWallet);
    }

    @Test
    void evaluateClearance_clearsValidTransit() {
        MaritimeClearanceResult result = adapter.evaluateClearance(
                request("IMO1234567", "MSC", WALLET, "CONT-001", "SEAL-001"));

        assertThat(result.status()).isEqualTo(ClearanceStatus.CLEARED);
        assertThat(result.reasonCode()).isEqualTo(SimulatedMaritimeClearanceAdapter.NONE_CLEARED);
        assertThat(result.referenceId()).startsWith("CLEARANCE-CERT-");
        assertThat(result.transitPermitToken()).startsWith("TRANSIT-PERMIT-");
        assertThat(result.evaluatedAt()).isNotNull();
    }

    @Test
    void evaluateClearance_flagsSanctionedVessel() {
        MaritimeClearanceResult result = adapter.evaluateClearance(
                request(SimulatedMaritimeClearanceAdapter.SANCTIONED_VESSEL_IMO, "MSC",
                        WALLET, "CONT-001", "SEAL-001"));

        assertThat(result.status()).isEqualTo(ClearanceStatus.SANCTIONED);
        assertThat(result.reasonCode()).isEqualTo(SimulatedMaritimeClearanceAdapter.OFAC_SANCTIONS_MATCH);
        assertThat(result.transitPermitToken()).isNull();
    }

    @Test
    void evaluateClearance_flagsSanctionedConsignee() {
        MaritimeClearanceResult result = adapter.evaluateClearance(
                request("IMO1234567", "MSC",
                        SimulatedMaritimeClearanceAdapter.SANCTIONED_CONSIGNEE_WALLET,
                        "CONT-001", "SEAL-001"));

        assertThat(result.status()).isEqualTo(ClearanceStatus.SANCTIONED);
        assertThat(result.reasonCode().authority()).isEqualTo("OFAC");
        assertThat(result.transitPermitToken()).isNull();
    }

    @Test
    void evaluateClearance_holdsCustomsForHoldSeal() {
        MaritimeClearanceResult result = adapter.evaluateClearance(
                request("IMO1234567", "MSC", WALLET, "CONT-001", "HOLD-0001"));

        assertThat(result.status()).isEqualTo(ClearanceStatus.HELD_CUSTOMS);
        assertThat(result.reasonCode()).isEqualTo(SimulatedMaritimeClearanceAdapter.ANA_SIGA_CUSTOMS_HOLD);
        assertThat(result.transitPermitToken()).isNull();
    }

    @Test
    void evaluateClearance_holdsCustomsForHoldContainer() {
        MaritimeClearanceResult result = adapter.evaluateClearance(
                request("IMO1234567", "MSC", WALLET,
                        SimulatedMaritimeClearanceAdapter.HELD_CONTAINER_NUMBER, "SEAL-001"));

        assertThat(result.status()).isEqualTo(ClearanceStatus.HELD_CUSTOMS);
        assertThat(result.reasonCode().authority()).isEqualTo("ANA_SIGA");
        assertThat(result.transitPermitToken()).isNull();
    }

    @Test
    void evaluateClearance_rejectsUnverifiedCarrier() {
        MaritimeClearanceResult result = adapter.evaluateClearance(
                request("IMO1234567", "UNVERIFIED-CARRIER-X", WALLET, "CONT-001", "SEAL-001"));

        assertThat(result.status()).isEqualTo(ClearanceStatus.REJECTED);
        assertThat(result.reasonCode()).isEqualTo(SimulatedMaritimeClearanceAdapter.ACP_VUMPA_UNVERIFIED_CARRIER);
        assertThat(result.transitPermitToken()).isNull();
    }
}
