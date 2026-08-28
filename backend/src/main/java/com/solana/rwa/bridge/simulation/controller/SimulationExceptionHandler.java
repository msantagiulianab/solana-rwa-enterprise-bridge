package com.solana.rwa.bridge.simulation.controller;

import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.simulation.exception.SimulationExecutionException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps pre-flight simulation failures into fail-closed REST error responses.
 *
 * <p>A reverted dry-run carries a structured Solana error (instruction index,
 * custom program error code, execution logs) and is surfaced as {@code 422
 * Unprocessable Entity}. An upstream RPC transport outage is surfaced as
 * {@code 502 Bad Gateway} so callers never mistake an unavailable node for a
 * successful rehearsal.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SimulationExceptionHandler {

    @ExceptionHandler(SimulationExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleSimulationExecution(SimulationExecutionException ex) {
        if (ex.getCause() instanceof SolanaRpcException) {
            return build(HttpStatus.BAD_GATEWAY,
                    "Transaction simulation could not be completed: upstream RPC node is unavailable (fail-closed)");
        }
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return build(status, message, null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message,
                                                      SimulationExecutionException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (ex != null) {
            putIfNotNull(body, "errorType", ex.getErrorType());
            putIfNotNull(body, "instructionIndex", ex.getInstructionIndex());
            putIfNotNull(body, "programError", ex.getProgramError());
            putIfNotNull(body, "programErrorCode", ex.getProgramErrorCode());
            putIfNotNull(body, "unitsConsumed", ex.getUnitsConsumed());
            putIfNotNull(body, "logs", ex.getLogs());
        }
        return ResponseEntity.status(status).body(body);
    }

    private void putIfNotNull(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
