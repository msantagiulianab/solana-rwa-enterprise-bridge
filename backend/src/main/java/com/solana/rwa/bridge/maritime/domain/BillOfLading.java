package com.solana.rwa.bridge.maritime.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Electronic Bill of Lading (eBL) — the root aggregate of the maritime domain.
 *
 * <p>Captures the carrier/vessel, origin/destination ports, shipper and
 * consignee wallets, declared cargo value, and the maritime clearance decision.
 * Owns a cascade of {@link ContainerConsignment} rows; {@code tokenMintAddress}
 * is populated only after a cleared settlement.
 */
@Entity
@Table(name = "bills_of_lading",
        indexes = {
                @Index(name = "idx_bills_of_lading_vessel_imo", columnList = "vessel_imo"),
                @Index(name = "idx_bills_of_lading_bl_number", columnList = "bl_number"),
                @Index(name = "idx_bills_of_lading_clearance_status", columnList = "clearance_status")
        })
@Getter
@Setter
@NoArgsConstructor
public class BillOfLading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bl_number", nullable = false, unique = true, length = 255)
    private String blNumber;

    @Column(name = "carrier_code", nullable = false, length = 255)
    private String carrierCode;

    @Column(name = "vessel_imo", nullable = false, length = 255)
    private String vesselImo;

    @Column(name = "port_of_loading", nullable = false, length = 255)
    private String portOfLoading;

    @Column(name = "port_of_discharge", nullable = false, length = 255)
    private String portOfDischarge;

    @Column(name = "shipper_wallet", nullable = false, length = 44)
    private String shipperWallet;

    @Column(name = "consignee_wallet", nullable = false, length = 44)
    private String consigneeWallet;

    @Column(name = "declared_value_usd", nullable = false, precision = 18, scale = 2)
    private BigDecimal declaredValueUsd;

    @Column(name = "cargo_description", nullable = false, columnDefinition = "text")
    private String cargoDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "clearance_status", nullable = false, length = 32)
    private ClearanceStatus clearanceStatus = ClearanceStatus.PENDING;

    @Column(name = "token_mint_address", length = 44)
    private String tokenMintAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "billOfLading", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContainerConsignment> consignments = new ArrayList<>();

    @Builder
    public BillOfLading(String blNumber, String carrierCode, String vesselImo,
                        String portOfLoading, String portOfDischarge,
                        String shipperWallet, String consigneeWallet,
                        BigDecimal declaredValueUsd, String cargoDescription,
                        ClearanceStatus clearanceStatus, String tokenMintAddress) {
        this.blNumber = blNumber;
        this.carrierCode = carrierCode;
        this.vesselImo = vesselImo;
        this.portOfLoading = portOfLoading;
        this.portOfDischarge = portOfDischarge;
        this.shipperWallet = shipperWallet;
        this.consigneeWallet = consigneeWallet;
        this.declaredValueUsd = declaredValueUsd;
        this.cargoDescription = cargoDescription;
        this.clearanceStatus = clearanceStatus != null ? clearanceStatus : ClearanceStatus.PENDING;
        this.tokenMintAddress = tokenMintAddress;
    }

    /**
     * Adds a consignment, maintaining the bidirectional {@code billOfLading}
     * link required by the owning side of the relationship.
     */
    public void addConsignment(ContainerConsignment consignment) {
        consignment.setBillOfLading(this);
        this.consignments.add(consignment);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
