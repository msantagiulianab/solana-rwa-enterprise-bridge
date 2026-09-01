package com.solana.rwa.bridge.maritime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single container consignment attached to a {@link BillOfLading}.
 *
 * <p>Holds per-container customs metadata (container/seal numbers, gross
 * weight, hazardous flag, customs status) consumed by the maritime clearance
 * evaluation.
 */
@Entity
@Table(name = "container_consignments")
@Getter
@Setter
@NoArgsConstructor
public class ContainerConsignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_of_lading_id", nullable = false)
    private BillOfLading billOfLading;

    @Column(name = "container_number", nullable = false, length = 255)
    private String containerNumber;

    @Column(name = "seal_number", nullable = false, length = 255)
    private String sealNumber;

    @Column(name = "gross_weight_kg", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossWeightKg;

    @Column(name = "is_hazardous", nullable = false)
    private boolean isHazardous;

    @Column(name = "customs_status", nullable = false, length = 32)
    private String customsStatus = "PENDING";

    @Builder
    public ContainerConsignment(String containerNumber, String sealNumber,
                                BigDecimal grossWeightKg, boolean isHazardous,
                                String customsStatus) {
        this.containerNumber = containerNumber;
        this.sealNumber = sealNumber;
        this.grossWeightKg = grossWeightKg;
        this.isHazardous = isHazardous;
        this.customsStatus = customsStatus != null ? customsStatus : "PENDING";
    }
}
