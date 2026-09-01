package com.solana.rwa.bridge.maritime.repository;

import com.solana.rwa.bridge.maritime.domain.BillOfLading;
import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link BillOfLading} electronic Bills of Lading.
 */
public interface BillOfLadingRepository extends JpaRepository<BillOfLading, UUID> {

    Optional<BillOfLading> findByBlNumber(String blNumber);

    List<BillOfLading> findByVesselImo(String vesselImo);

    List<BillOfLading> findByClearanceStatus(ClearanceStatus clearanceStatus);
}
