package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.InvestorRegistrationRequest;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.exception.InvestorNotFoundException;
import com.solana.rwa.bridge.repository.InvestorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    /**
     * Returns a single investor by UUID.
     */
    @Transactional(readOnly = true)
    public Investor findById(UUID id) {
        return investorRepository.findById(id)
                .orElseThrow(() -> new InvestorNotFoundException(id));
    }

    /**
     * Updates the investor KYC status (APPROVE / REJECT).
     */
    @Transactional
    public Investor updateStatus(UUID id, KycStatus kycStatus) {
        Investor investor = findById(id);
        investor.setKycStatus(kycStatus);
        return investorRepository.save(investor);
    }
}
