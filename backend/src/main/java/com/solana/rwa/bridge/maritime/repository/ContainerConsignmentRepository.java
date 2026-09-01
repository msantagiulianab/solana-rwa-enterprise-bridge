package com.solana.rwa.bridge.maritime.repository;

import com.solana.rwa.bridge.maritime.domain.ContainerConsignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence access for {@link ContainerConsignment} rows.
 */
public interface ContainerConsignmentRepository extends JpaRepository<ContainerConsignment, UUID> {

    List<ContainerConsignment> findByBillOfLadingId(UUID billOfLadingId);
}
