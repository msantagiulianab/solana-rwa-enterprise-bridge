package com.solana.rwa.bridge.simulation.controller;

import com.solana.rwa.bridge.simulation.dto.SimulationResultDto;
import com.solana.rwa.bridge.simulation.dto.SimulationSubmitRequest;
import com.solana.rwa.bridge.simulation.service.TransactionSimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for the institutional pre-flight transaction rehearsal engine.
 *
 * <p>Accepts a raw base64 wire transaction, rehearses it against the Solana RPC
 * node via {@code simulateTransaction}, and returns the extracted compute units,
 * program logs, and recommended compute unit limit before any funds are
 * committed or broadcast.
 */
@RestController
@RequestMapping("/api/v1/settlement")
@RequiredArgsConstructor
public class TransactionSimulationController {

    private final TransactionSimulationService simulationService;

    /**
     * POST /api/v1/settlement/simulate — dry-runs the supplied transaction.
     *
     * @param request base64-encoded serialized wire transaction
     * @return the structured successful simulation result
     */
    @PostMapping("/simulate")
    public ResponseEntity<SimulationResultDto> simulate(
            @Valid @RequestBody SimulationSubmitRequest request) {
        return ResponseEntity.ok(simulationService.simulate(request.encodedTransaction()));
    }
}
