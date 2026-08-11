package com.solana.rwa.bridge.repository;

import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link Investor} records.
 */
public interface InvestorRepository extends JpaRepository<Investor, UUID> {

    Optional<Investor> findByWalletAddress(String walletAddress);

    List<Investor> findByKycStatus(KycStatus kycStatus);

    boolean existsByWalletAddress(String walletAddress);

    long countByKycStatus(KycStatus kycStatus);
}
