package com.solana.rwa.bridge.simulation.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.solana.rwa.bridge.simulation.dto.RpcSimulationResponseDto;

import java.util.Iterator;
import java.util.List;

/**
 * Thrown when a pre-flight transaction simulation cannot be safely completed,
 * or when the simulated transaction reverts on-chain.
 *
 * <p>For reverted executions the exception carries a structured parse of the
 * Solana error node (for example {@code {"InstructionError":[0,{"Custom":1}]}})
 * together with the execution logs and consumed compute units, so callers can
 * fail closed with precise diagnostics instead of a raw JSON blob.
 */
public class SimulationExecutionException extends RuntimeException {

    private final List<String> logs;
    private final Long unitsConsumed;
    private final String errorType;
    private final Integer instructionIndex;
    private final String programError;
    private final Integer programErrorCode;

    public SimulationExecutionException(String message) {
        this(message, null, null, null, null, null, null);
    }

    public SimulationExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.logs = null;
        this.unitsConsumed = null;
        this.errorType = null;
        this.instructionIndex = null;
        this.programError = null;
        this.programErrorCode = null;
    }

    private SimulationExecutionException(String message, List<String> logs, Long unitsConsumed,
                                         String errorType, Integer instructionIndex,
                                         String programError, Integer programErrorCode) {
        super(message);
        this.logs = logs;
        this.unitsConsumed = unitsConsumed;
        this.errorType = errorType;
        this.instructionIndex = instructionIndex;
        this.programError = programError;
        this.programErrorCode = programErrorCode;
    }

    /**
     * Parses a raw simulation outcome into a structured, fail-closed exception.
     *
     * @param value raw {@code simulateTransaction} outcome (possibly null)
     */
    public static SimulationExecutionException from(RpcSimulationResponseDto.SimulationValue value) {
        List<String> logs = value == null ? null : value.logs();
        Long unitsConsumed = value == null ? null : value.unitsConsumed();
        JsonNode err = value == null ? null : value.err();

        if (err == null || err.isNull() || !err.isObject()) {
            String detail = (err == null || err.isNull()) ? "unknown error"
                    : err.isTextual() ? err.asText() : err.toString();
            return new SimulationExecutionException("Transaction simulation reverted: " + detail,
                    logs, unitsConsumed, null, null, null, null);
        }

        ParsedError parsed = parseError(err);
        return new SimulationExecutionException(parsed.message(), logs, unitsConsumed,
                parsed.errorType(), parsed.instructionIndex(), parsed.programError(),
                parsed.programErrorCode());
    }

    private static ParsedError parseError(JsonNode err) {
        JsonNode instructionError = err.get("InstructionError");
        if (instructionError != null && instructionError.isArray() && instructionError.size() >= 2) {
            JsonNode indexNode = instructionError.get(0);
            Integer index = indexNode != null && indexNode.isNumber() ? indexNode.intValue() : null;

            JsonNode detail = instructionError.get(1);
            String programError = null;
            Integer programErrorCode = null;
            if (detail != null && detail.isObject()) {
                Iterator<String> names = detail.fieldNames();
                if (names.hasNext()) {
                    programError = names.next();
                    JsonNode codeNode = detail.get(programError);
                    if (codeNode != null && codeNode.isNumber()) {
                        programErrorCode = codeNode.intValue();
                    }
                }
            }

            StringBuilder message = new StringBuilder("Transaction simulation reverted: InstructionError");
            if (index != null) {
                message.append(" at instruction ").append(index);
            }
            if (programError != null) {
                message.append(" (").append(programError);
                if (programErrorCode != null) {
                    message.append(' ').append(programErrorCode);
                }
                message.append(')');
            }
            return new ParsedError(message.toString(), "InstructionError", index, programError, programErrorCode);
        }

        String errorType = firstFieldName(err);
        return new ParsedError("Transaction simulation reverted: " + errorType,
                errorType, null, null, null);
    }

    private static String firstFieldName(JsonNode node) {
        Iterator<String> names = node.fieldNames();
        return names.hasNext() ? names.next() : "unknown error";
    }

    public List<String> getLogs() {
        return logs;
    }

    public Long getUnitsConsumed() {
        return unitsConsumed;
    }

    public String getErrorType() {
        return errorType;
    }

    public Integer getInstructionIndex() {
        return instructionIndex;
    }

    public String getProgramError() {
        return programError;
    }

    public Integer getProgramErrorCode() {
        return programErrorCode;
    }

    private record ParsedError(String message, String errorType, Integer instructionIndex,
                               String programError, Integer programErrorCode) {
    }
}
