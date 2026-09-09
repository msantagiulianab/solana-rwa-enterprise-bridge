package com.solana.rwa.bridge.exception;

import com.solana.rwa.bridge.compliance.port.TransferComplianceResult;

/**
 * Thrown when the off-chain transfer compliance SPI blocks a secondary-market
 * transfer. Mapped to {@code 422 Unprocessable Entity} by
 * {@link com.solana.rwa.bridge.controller.GlobalExceptionHandler}; no
 * transaction bytes are ever broadcast on this path.
 */
public class ComplianceViolationException extends RuntimeException {

    private final TransferComplianceResult complianceResult;

    public ComplianceViolationException(TransferComplianceResult complianceResult) {
        super(buildMessage(complianceResult));
        this.complianceResult = complianceResult;
    }

    public TransferComplianceResult getComplianceResult() {
        return complianceResult;
    }

    private static String buildMessage(TransferComplianceResult result) {
        if (result == null) {
            return "Transfer blocked by compliance";
        }
        String code = result.reason() == null ? "UNKNOWN" : result.reason().code();
        return "Transfer blocked by compliance: " + code;
    }
}
