package com.solana.rwa.bridge.maritime.exception;

import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;
import com.solana.rwa.bridge.maritime.port.MaritimeClearanceResult;

/**
 * Thrown when a maritime clearance evaluation fails-closed (customs hold,
 * sanctions flag, or unverified carrier). Signals that settlement must NOT
 * proceed: no outbox row is created and no token broadcast is dispatched.
 */
public class MaritimeComplianceException extends RuntimeException {

    private final MaritimeClearanceResult clearanceResult;

    public MaritimeComplianceException(MaritimeClearanceResult clearanceResult) {
        super(buildMessage(clearanceResult));
        this.clearanceResult = clearanceResult;
    }

    public MaritimeClearanceResult getClearanceResult() {
        return clearanceResult;
    }

    public ClearanceStatus getClearanceStatus() {
        return clearanceResult != null ? clearanceResult.status() : null;
    }

    private static String buildMessage(MaritimeClearanceResult result) {
        if (result == null) {
            return "Maritime compliance check failed";
        }
        if (result.reasonCode() != null) {
            return "Maritime clearance rejected: " + result.status()
                    + " (" + result.reasonCode().authority() + " " + result.reasonCode().code() + ")";
        }
        return "Maritime clearance rejected: " + result.status();
    }
}
