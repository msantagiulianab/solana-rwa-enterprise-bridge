package com.solana.rwa.bridge.maritime.controller;

import com.solana.rwa.bridge.maritime.dto.BillOfLadingResponse;
import com.solana.rwa.bridge.maritime.dto.CanalTransitSettlementResponse;
import com.solana.rwa.bridge.maritime.dto.RegisterBillOfLadingRequest;
import com.solana.rwa.bridge.maritime.dto.SettlementEvaluationResponse;
import com.solana.rwa.bridge.maritime.service.MaritimeSettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authenticated REST endpoints for the maritime Delivery-vs-Payment (DvP)
 * settlement domain.
 *
 * <p>Mutating routes ({@code POST}) are gated by the {@code X-API-Key}
 * interceptor; read routes ({@code GET}) are deliberately public for the
 * settlement/ledger viewers. All payloads are validated with Jakarta Bean
 * Validation and responses are typed to response DTOs.
 */
@RestController
@RequestMapping("/api/v1/maritime")
@RequiredArgsConstructor
public class MaritimeSettlementController {

    private final MaritimeSettlementService maritimeSettlementService;

    /**
     * POST /api/v1/maritime/bills-of-lading — registers an eBL + consignments.
     */
    @PostMapping("/bills-of-lading")
    public ResponseEntity<BillOfLadingResponse> registerBillOfLading(
            @Valid @RequestBody RegisterBillOfLadingRequest request) {
        BillOfLadingResponse response = maritimeSettlementService.registerBillOfLading(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/maritime/settlements/{id}/evaluate — runs maritime clearance.
     */
    @PostMapping("/settlements/{id}/evaluate")
    public ResponseEntity<SettlementEvaluationResponse> evaluateSettlement(@PathVariable UUID id) {
        return ResponseEntity.ok(maritimeSettlementService.evaluateSettlement(id));
    }

    /**
     * POST /api/v1/maritime/settlements/{id}/execute — performs atomic SPL settlement.
     */
    @PostMapping("/settlements/{id}/execute")
    public ResponseEntity<CanalTransitSettlementResponse> executeSettlement(@PathVariable UUID id) {
        return ResponseEntity.ok(maritimeSettlementService.executeSettlement(id));
    }

    /**
     * GET /api/v1/maritime/settlements/{id} — reads settlement + finality state.
     */
    @GetMapping("/settlements/{id}")
    public ResponseEntity<CanalTransitSettlementResponse> getSettlement(@PathVariable UUID id) {
        return ResponseEntity.ok(maritimeSettlementService.getSettlement(id));
    }

    /**
     * GET /api/v1/maritime/bills-of-lading/{id} — reads a registered eBL.
     */
    @GetMapping("/bills-of-lading/{id}")
    public ResponseEntity<BillOfLadingResponse> getBillOfLading(@PathVariable UUID id) {
        return ResponseEntity.ok(maritimeSettlementService.getBillOfLading(id));
    }
}
