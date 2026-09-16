package com.solana.rwa.bridge.compliance.adapter.out.simulation;

import com.solana.rwa.bridge.compliance.port.TransferComplianceRequest;
import com.solana.rwa.bridge.compliance.port.TransferComplianceResult;
import com.solana.rwa.bridge.compliance.port.TransferComplianceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the deterministic transfer-compliance simulation matrix in
 * {@link SimulatedTransferComplianceAdapter}.
 */
class SimulatedTransferComplianceAdapterTest {

    private static final String BASE58_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String MINT = "MNTabcdefghijkmnpqrstuvwxyz123456789";

    private final SimulatedTransferComplianceAdapter adapter =
            new SimulatedTransferComplianceAdapter();

    @Test
    void sanctionedDestinationWallet_isValidBase58SolanaAddress() {
        String sanctioned = SimulatedTransferComplianceAdapter.SANCTIONED_DESTINATION_WALLET;

        assertThat(sanctioned.length()).isBetween(32, 44);
        boolean allBase58 = true;
        for (int i = 0; i < sanctioned.length(); i++) {
            if (BASE58_ALPHABET.indexOf(sanctioned.charAt(i)) < 0) {
                allBase58 = false;
                break;
            }
        }
        assertThat(allBase58).isTrue();
    }

    @Test
    void evaluateTransfer_blocksSanctionedDestination() {
        TransferComplianceResult result = adapter.evaluateTransfer(new TransferComplianceRequest(
                WALLET, SimulatedTransferComplianceAdapter.SANCTIONED_DESTINATION_WALLET,
                MINT, 1_000_000L));

        assertThat(result.status()).isEqualTo(TransferComplianceStatus.BLOCKED);
        assertThat(result.reason())
                .isEqualTo(SimulatedTransferComplianceAdapter.SANCTIONED_DESTINATION_REASON);
        assertThat(result.referenceId()).isEqualTo("TRANSFER-" + MINT);
        assertThat(result.evaluatedAt()).isNotNull();
    }

    @Test
    void evaluateTransfer_approvesNonSanctionedDestination() {
        TransferComplianceResult result = adapter.evaluateTransfer(new TransferComplianceRequest(
                WALLET, WALLET, MINT, 1_000_000L));

        assertThat(result.status()).isEqualTo(TransferComplianceStatus.APPROVED);
        assertThat(result.reason())
                .isEqualTo(SimulatedTransferComplianceAdapter.APPROVED_REASON);
        assertThat(result.referenceId()).isEqualTo("TRANSFER-" + MINT);
    }

    @Test
    void evaluateTransfer_blocksNonPositiveAmount() {
        TransferComplianceResult result = adapter.evaluateTransfer(new TransferComplianceRequest(
                WALLET, WALLET, MINT, 0L));

        assertThat(result.status()).isEqualTo(TransferComplianceStatus.BLOCKED);
        assertThat(result.reason())
                .isEqualTo(SimulatedTransferComplianceAdapter.INVALID_AMOUNT_REASON);
    }
}
