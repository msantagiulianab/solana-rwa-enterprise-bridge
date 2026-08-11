package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.dto.InvestorRegistrationRequest;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.service.InvestorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for investor registration and KYC status management.
 */
@RestController
@RequestMapping("/api/v1/investors")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class InvestorController {

    private final InvestorService investorService;

    /**
     * POST /api/v1/investors — registers a new investor or updates KYC status.
     */
    @PostMapping
    public Investor register(@Valid @RequestBody InvestorRegistrationRequest request) {
        return investorService.registerOrUpdate(request);
    }
}
