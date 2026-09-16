package com.solana.rwa.bridge.compliance.adapter.out.simulation;

import com.solana.rwa.bridge.compliance.port.TransferCompliancePort;
import com.solana.rwa.bridge.compliance.port.TransferComplianceReason;
import com.solana.rwa.bridge.compliance.port.TransferComplianceRequest;
import com.solana.rwa.bridge.compliance.port.TransferComplianceResult;
import com.solana.rwa.bridge.compliance.port.TransferComplianceStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Deterministic, in-process simulation of the off-chain transfer compliance
 * engine (KYC/AML + sanctions screening).
 *
 * <p>Backs the {@link TransferCompliancePort} boundary with deterministic rules
 * — no live network calls. This adapter is the sandbox stand-in for the
 * post-seed institutional screening connectors.
 *
 * <p>Evaluation order (first match wins):
 * <ol>
 *   <li>A non-positive transfer amount → {@link TransferComplianceStatus#BLOCKED}
 *       ({@code INVALID_AMOUNT}).</li>
 *   <li>A sanctioned destination wallet → {@link TransferComplianceStatus#BLOCKED}
 *       ({@code SANCTIONED_DESTINATION}).</li>
 *   <li>Otherwise → {@link TransferComplianceStatus#APPROVED}.</li>
 * </ol>
 */
@Component
public class SimulatedTransferComplianceAdapter implements TransferCompliancePort {

    public static final String SANCTIONED_DESTINATION_WALLET = "11111111111111111111111111111111";

    public static final TransferComplianceReason APPROVED_REASON =
            new TransferComplianceReason("COMPLIANCE", "APPROVED");
    public static final TransferComplianceReason SANCTIONED_DESTINATION_REASON =
            new TransferComplianceReason("COMPLIANCE", "SANCTIONED_DESTINATION");
    public static final TransferComplianceReason INVALID_AMOUNT_REASON =
            new TransferComplianceReason("COMPLIANCE", "INVALID_AMOUNT");

    private static final Set<String> SANCTIONED_DESTINATION_WALLETS =
            Set.of(SANCTIONED_DESTINATION_WALLET);

    @Override
    public TransferComplianceResult evaluateTransfer(TransferComplianceRequest request) {
        Instant now = Instant.now();

        if (request.amount() <= 0) {
            return new TransferComplianceResult(TransferComplianceStatus.BLOCKED,
                    INVALID_AMOUNT_REASON, reference(request), now);
        }
        if (request.destinationWallet() != null
                && SANCTIONED_DESTINATION_WALLETS.contains(request.destinationWallet())) {
            return new TransferComplianceResult(TransferComplianceStatus.BLOCKED,
                    SANCTIONED_DESTINATION_REASON, reference(request), now);
        }
        return new TransferComplianceResult(TransferComplianceStatus.APPROVED,
                APPROVED_REASON, reference(request), now);
    }

    private static String reference(TransferComplianceRequest request) {
        String mint = request.assetMintAddress() != null ? request.assetMintAddress() : "UNKNOWN";
        return "TRANSFER-" + mint;
    }
}
