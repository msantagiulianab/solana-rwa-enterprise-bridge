package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.InvestorRegistrationRequest;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.repository.InvestorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InvestorService {

    private final InvestorRepository investorRepository;

    @Transactional(readOnly = true)
    public List<Investor> findAll() {
        return investorRepository.findAll();
    }

    /**
     * Registers a new investor. Defaults KYC to PENDING.
     */
    @Transactional
    public Investor register(InvestorRegistrationRequest request) {
        return investorRepository.save(Investor.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .walletAddress(request.getSolanaAddress())
                .kycStatus(KycStatus.PENDING)
                .build());
    }
}