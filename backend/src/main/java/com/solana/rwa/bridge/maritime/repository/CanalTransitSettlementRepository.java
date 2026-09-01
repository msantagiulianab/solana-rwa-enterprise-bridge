package com.solana.rwa.bridge.maritime.repository;

import com.solana.rwa.bridge.maritime.domain.CanalTransitSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link CanalTransitSettlement} rows.
 */
public interface CanalTransitSettlementRepository extends JpaRepository<CanalTransitSettlement, UUID> {

    Optional<CanalTransitSettlement> findByTransitBookingReference(String transitBookingReference);
}
