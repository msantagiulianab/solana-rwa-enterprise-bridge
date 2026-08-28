package com.solana.rwa.bridge.simulation.service;

import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.simulation.dto.RpcSimulationResponseDto;
import com.solana.rwa.bridge.simulation.dto.SimulationResultDto;
import com.solana.rwa.bridge.simulation.exception.SimulationExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Pre-flight transaction rehearsal engine.
 *
 * <p>Rehearses a raw base64 wire transaction against the Solana RPC node using
 * {@code simulateTransaction} before any funds are committed or broadcast.
 * The service extracts the exact consumed compute units and program logs, and
 * recommends a compute unit limit padded with a {@value #COMPUTE_UNIT_SAFETY_MARGIN}
 * safety margin so the later broadcast never lands short on budget.
 *
 * <p>Every failure path is fail-closed: a transport outage, a null/malformed
 * response, or a reverted dry-run is surfaced as a structured
 * {@link SimulationExecutionException} — never as a successful rehearsal.
 */
@Service
@RequiredArgsConstructor
public class TransactionSimulationService {

    private static final double COMPUTE_UNIT_SAFETY_MARGIN = 0.15;

    private final SolanaRpcAdapter solanaRpcAdapter;

    /**
     * Rehearses the supplied transaction without broadcasting it.
     *
     * @param encodedTransaction base64-encoded serialized wire transaction
     * @return the structured successful simulation result
     * @throws SimulationExecutionException on blank input, transport failure,
     *         null/malformed response, or a reverted simulated execution
     */
    public SimulationResultDto simulate(String encodedTransaction) {
        if (encodedTransaction == null || encodedTransaction.isBlank()) {
            throw new SimulationExecutionException("Encoded transaction must not be blank");
        }

        try {
            RpcSimulationResponseDto.SimulationValue value =
                    solanaRpcAdapter.simulateTransaction(encodedTransaction);
            if (value == null) {
                throw new SimulationExecutionException("Transaction simulation returned no result");
            }
            if (value.hasError()) {
                throw SimulationExecutionException.from(value);
            }
            if (value.unitsConsumed() == null) {
                throw new SimulationExecutionException("Transaction simulation returned no compute units");
            }
            return new SimulationResultDto(true, value.unitsConsumed(), value.logs(), null,
                    recommendedComputeUnitLimit(value.unitsConsumed()));
        } catch (SolanaRpcException ex) {
            throw new SimulationExecutionException("Transaction simulation failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Pads a measured compute-unit consumption with the safety margin so a
     * subsequent broadcast has headroom for scheduling variance.
     *
     * @param unitsConsumed measured compute units from the rehearsal
     * @return recommended compute unit limit (rounded up)
     */
    public static long recommendedComputeUnitLimit(long unitsConsumed) {
        return (long) Math.ceil(unitsConsumed * (1.0 + COMPUTE_UNIT_SAFETY_MARGIN));
    }
}
