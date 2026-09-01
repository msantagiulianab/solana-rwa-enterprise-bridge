package com.solana.rwa.bridge.maritime.repository;

import com.solana.rwa.bridge.entity.FinalityOutboxEntry;
import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.maritime.domain.BillOfLading;
import com.solana.rwa.bridge.maritime.domain.CanalTransitSettlement;
import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;
import com.solana.rwa.bridge.maritime.domain.ContainerConsignment;
import com.solana.rwa.bridge.maritime.domain.TransitSettlementStatus;
import com.solana.rwa.bridge.repository.FinalityOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA repository integration tests for the maritime domain against the V4
 * Flyway migration (H2, PostgreSQL mode). Verifies schema defaults + timestamps,
 * cascade persist and orphan removal of consignments, settlement-to-outbox
 * linkage, and unique constraints.
 */
@DataJpaTest
@ActiveProfiles("test")
class MaritimeRepositoryIT {

    private static final String SHIPPER = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String CONSIGNEE = "5kYxLabcDEFghijkmnpqrstuvwxyz2345678";

    @Autowired
    private BillOfLadingRepository billOfLadingRepository;

    @Autowired
    private ContainerConsignmentRepository containerConsignmentRepository;

    @Autowired
    private CanalTransitSettlementRepository canalTransitSettlementRepository;

    @Autowired
    private FinalityOutboxRepository finalityOutboxRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private BillOfLading bol(String blNumber) {
        return BillOfLading.builder()
                .blNumber(blNumber)
                .carrierCode("MSC")
                .vesselImo("IMO1234567")
                .portOfLoading("PACTB")
                .portOfDischarge("USNYC")
                .shipperWallet(SHIPPER)
                .consigneeWallet(CONSIGNEE)
                .declaredValueUsd(new BigDecimal("100000.00"))
                .cargoDescription("Containerized machinery")
                .build();
    }


    @Test
    void save_persistsBillOfLadingWithDefaultsAndTimestamps() {
        BillOfLading saved = billOfLadingRepository.save(bol("BL-2026-0001"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getClearanceStatus()).isEqualTo(ClearanceStatus.PENDING);
        assertThat(saved.getTokenMintAddress()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getConsignments()).isEmpty();
    }

    @Test
    void save_cascadesConsignmentsAndEnforcesOrphanRemoval() {
        BillOfLading bol = bol("BL-2026-0002");
        bol.addConsignment(ContainerConsignment.builder()
                .containerNumber("CONT-001")
                .sealNumber("SEAL-001")
                .grossWeightKg(new BigDecimal("24000.00"))
                .isHazardous(false)
                .build());

        BillOfLading saved = billOfLadingRepository.saveAndFlush(bol);
        UUID bolId = saved.getId();

        assertThat(saved.getConsignments()).hasSize(1);
        assertThat(containerConsignmentRepository.findByBillOfLadingId(bolId)).hasSize(1);

        testEntityManager.clear();

        BillOfLading reloaded = billOfLadingRepository.findById(bolId).orElseThrow();
        assertThat(reloaded.getConsignments()).hasSize(1);

        reloaded.getConsignments().clear();
        billOfLadingRepository.saveAndFlush(reloaded);

        assertThat(containerConsignmentRepository.findByBillOfLadingId(bolId)).isEmpty();
    }

    @Test
    void settlement_linksToFinalityOutboxAndPersistsDefaults() {
        BillOfLading saved = billOfLadingRepository.saveAndFlush(bol("BL-2026-0003"));

        FinalityOutboxEntry outbox = finalityOutboxRepository.saveAndFlush(FinalityOutboxEntry.builder()
                .idempotencyKey("maritime-settle-REF-001")
                .status(SettlementStatus.CONFIRMED)
                .commitmentLevel("CONFIRMED")
                .build());

        CanalTransitSettlement settlement = canalTransitSettlementRepository.saveAndFlush(
                CanalTransitSettlement.builder()
                        .billOfLading(saved)
                        .transitBookingReference("REF-001")
                        .transitFeeUsd(new BigDecimal("2500.00"))
                        .settlementTokenMint("MINT-ADDR")
                        .escrowAccount("ESCROW-ADDR")
                        .status(TransitSettlementStatus.INITIALIZED)
                        .outboxEntryId(outbox.getId())
                        .build());

        assertThat(settlement.getId()).isNotNull();
        assertThat(settlement.getStatus()).isEqualTo(TransitSettlementStatus.INITIALIZED);
        assertThat(settlement.getOutboxEntryId()).isEqualTo(outbox.getId());
        assertThat(settlement.getCreatedAt()).isNotNull();
        assertThat(settlement.getUpdatedAt()).isNotNull();
        assertThat(settlement.getSettledAt()).isNull();

        assertThat(canalTransitSettlementRepository.findByTransitBookingReference("REF-001"))
                .isPresent();
    }

    @Test
    void findByVesselImoAndClearanceStatus_returnMatchingRows() {
        billOfLadingRepository.saveAndFlush(bol("BL-2026-0004"));

        assertThat(billOfLadingRepository.findByVesselImo("IMO1234567")).hasSize(1);
        assertThat(billOfLadingRepository.findByClearanceStatus(ClearanceStatus.PENDING)).hasSize(1);
        assertThat(billOfLadingRepository.findByClearanceStatus(ClearanceStatus.CLEARED)).isEmpty();
    }

    @Test
    void uniqueConstraints_enforcedForBlNumber() {
        billOfLadingRepository.saveAndFlush(bol("BL-DUP-0001"));

        assertThatThrownBy(() -> billOfLadingRepository.saveAndFlush(bol("BL-DUP-0001")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

