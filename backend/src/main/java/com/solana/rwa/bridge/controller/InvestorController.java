package com.solana.rwa.bridge.controller;

import com.solana.rwa.bridge.dto.InvestorRegistrationRequest;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.service.InvestorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for investor registration and KYC status management.
 */
@RestController
@RequestMapping("/api/investors")
@RequiredArgsConstructor
public class InvestorController {

    private final InvestorService investorService;

    @GetMapping
    public List<Investor> getAllInvestors() {
        return investorService.findAll();
    }

    /**
     * POST /api/investors — registers a new investor with default KYC PENDING.
     */
    @PostMapping
    public Investor register(@Valid @RequestBody InvestorRegistrationRequest request) {
        return investorService.register(request);
    }
}