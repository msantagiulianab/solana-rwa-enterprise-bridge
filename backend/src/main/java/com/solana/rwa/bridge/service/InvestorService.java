package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.InvestorRegistrationRequest;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.repository.InvestorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and KYC status management for off-chain investor records.
 */
@Service
@RequiredArgsConstructor
public class InvestorService {

    private final InvestorRepository investorRepository;

    /**
     * Registers a new investor or updates the KYC status of an existing one,
     * keyed by the unique wallet address.
     */
    @Transactional
    public Investor registerOrUpdate(InvestorRegistrationRequest request) {
        return investorRepository.findByWalletAddress(request.getWalletAddress())
                .map(existing -> {
                    existing.setKycStatus(request.getKycStatus());
                    existing.setCountry(request.getCountry());
                    return investorRepository.save(existing);
                })
                .orElseGet(() -> investorRepository.save(Investor.builder()
                        .walletAddress(request.getWalletAddress())
                        .country(request.getCountry())
                        .kycStatus(request.getKycStatus())
                        .build()));
    }
}
