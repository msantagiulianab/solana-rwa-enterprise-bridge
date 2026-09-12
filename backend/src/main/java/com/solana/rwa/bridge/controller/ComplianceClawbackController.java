package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.dto.ClawbackRequest;
import com.solana.rwa.bridge.dto.ClawbackRequestDto;
import com.solana.rwa.bridge.dto.ClawbackResponseDto;
import com.solana.rwa.bridge.service.TokenClawbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for executing a Token-2022 permanent-delegate clawback.
 *
 * <p>The route is a mutating {@code POST} and is therefore gated by the
 * {@code X-API-Key} interceptor registered in {@code WebConfig}. Payloads are
 * Bean-validated ({@code @Valid}) before the request is mapped onto the
 * service-layer {@link TokenClawbackService}.
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
public class ComplianceClawbackController {

    private final TokenClawbackService tokenClawbackService;

    /**
     * POST /api/v1/compliance/clawback — recovers tokens from a source account
     * using the mint's Permanent Delegate authority.
     */
    @PostMapping("/clawback")
    public ClawbackResponseDto clawback(@Valid @RequestBody ClawbackRequestDto request) {
        ClawbackRequest serviceRequest = new ClawbackRequest(
                request.mintAddress(),
                request.sourceTokenAccount(),
                request.destinationTokenAccount(),
                request.amount(),
                request.reason());

        return ClawbackResponseDto.from(
                tokenClawbackService.clawback(serviceRequest, request.idempotencyKey()));
    }
}
