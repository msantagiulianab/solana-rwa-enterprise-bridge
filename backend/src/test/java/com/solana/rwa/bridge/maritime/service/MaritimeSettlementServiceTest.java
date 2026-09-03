package com.solana.rwa.bridge.maritime.service;

import com.solana.rwa.bridge.entity.FinalityOutboxEntry;
import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.maritime.adapter.out.simulation.SimulatedMaritimeClearanceAdapter;
import com.solana.rwa.bridge.maritime.domain.BillOfLading;
import com.solana.rwa.bridge.maritime.domain.CanalTransitSettlement;
import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;
import com.solana.rwa.bridge.maritime.domain.ContainerConsignment;
import com.solana.rwa.bridge.maritime.domain.TransitSettlementStatus;
import com.solana.rwa.bridge.maritime.dto.CanalTransitSettlementResponse;
import com.solana.rwa.bridge.maritime.exception.BillOfLadingNotFoundException;
import com.solana.rwa.bridge.maritime.exception.CanalTransitSettlementNotFoundException;
import com.solana.rwa.bridge.maritime.exception.MaritimeComplianceException;
import com.solana.rwa.bridge.maritime.port.ClearanceReasonCode;
import com.solana.rwa.bridge.maritime.port.MaritimeClearancePort;
import com.solana.rwa.bridge.maritime.port.MaritimeClearanceResult;
import com.solana.rwa.bridge.maritime.repository.BillOfLadingRepository;
import com.solana.rwa.bridge.maritime.repository.CanalTransitSettlementRepository;
import com.solana.rwa.bridge.repository.FinalityOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link MaritimeSettlementService}: happy-path
 * settlement + outbox enqueue, and fail-closed behavior on customs hold,
 * sanctions, and unverified carrier.
 *
 * <p>The service depends only on the {@link MaritimeClearancePort} and JPA
 * repositories — never on a Solana RPC/token service — so a non-cleared path
 * structurally performs zero token actions. The assertions below additionally
 * verify that no outbox or settlement row is created on fail-closed paths.
 */
@ExtendWith(MockitoExtension.class)
class MaritimeSettlementServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final UUID BOL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SETTLEMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";

    @Mock
    private BillOfLadingRepository billOfLadingRepository;

    @Mock
    private CanalTransitSettlementRepository canalTransitSettlementRepository;

    @Mock
    private FinalityOutboxRepository finalityOutboxRepository;

    @Mock
    private MaritimeClearancePort maritimeClearancePort;

    @Mock
    private Clock clock;

    @InjectMocks
    private MaritimeSettlementService service;

    private BillOfLading bol(String vesselImo, String carrierCode, String consigneeWallet,
                             String containerNumber, String sealNumber) {
        BillOfLading bol = BillOfLading.builder()
                .blNumber("BL-2026-0001")
                .carrierCode(carrierCode)
                .vesselImo(vesselImo)
                .portOfLoading("PACTB")
                .portOfDischarge("USNYC")
                .shipperWallet(WALLET)
                .consigneeWallet(consigneeWallet)
                .declaredValueUsd(new BigDecimal("100000.00"))
                .cargoDescription("Containerized machinery")
                .build();
        bol.addConsignment(ContainerConsignment.builder()
                .containerNumber(containerNumber)
                .sealNumber(sealNumber)
                .grossWeightKg(new BigDecimal("24000.00"))
                .isHazardous(false)
                .build());
        return bol;
    }

    private MaritimeClearanceResult result(ClearanceStatus status) {
        ClearanceReasonCode reasonCode = status == ClearanceStatus.CLEARED
                ? SimulatedMaritimeClearanceAdapter.NONE_CLEARED
                : new ClearanceReasonCode("TEST", status.name());
        return new MaritimeClearanceResult(status, reasonCode, "REF-1",
                status == ClearanceStatus.CLEARED ? "PERMIT-1" : null, NOW);
    }


    @Test
    void settleTransit_clearsSettlesAndEnqueuesOutbox() {
        BillOfLading bol = bol("IMO1234567", "MSC", WALLET, "CONT-001", "SEAL-001");
        when(clock.instant()).thenReturn(NOW);
        when(billOfLadingRepository.findById(BOL_ID)).thenReturn(Optional.of(bol));
        when(maritimeClearancePort.evaluateClearance(any())).thenReturn(result(ClearanceStatus.CLEARED));
        when(finalityOutboxRepository.save(any(FinalityOutboxEntry.class))).thenAnswer(inv -> {
            FinalityOutboxEntry entry = inv.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });
        when(canalTransitSettlementRepository.save(any(CanalTransitSettlement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CanalTransitSettlement settlement = service.settleTransit(
                BOL_ID, "TRANSIT-REF-001", new BigDecimal("2500.00"), "MINT-ADDR", "ESCROW-ADDR");

        assertThat(settlement.getStatus()).isEqualTo(TransitSettlementStatus.SETTLED);
        assertThat(settlement.getOutboxEntryId()).isNotNull();
        assertThat(bol.getClearanceStatus()).isEqualTo(ClearanceStatus.CLEARED);
        assertThat(bol.getTokenMintAddress()).isEqualTo("MINT-ADDR");

        ArgumentCaptor<FinalityOutboxEntry> outboxCaptor = ArgumentCaptor.forClass(FinalityOutboxEntry.class);
        verify(finalityOutboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(outboxCaptor.getValue().getCommitmentLevel()).isEqualTo("CONFIRMED");
    }

    @Test
    void settleTransit_failsClosedOnCustomsHold() {
        BillOfLading bol = bol("IMO1234567", "MSC", WALLET, "CONT-001", "HOLD-0001");
        when(billOfLadingRepository.findById(BOL_ID)).thenReturn(Optional.of(bol));
        when(maritimeClearancePort.evaluateClearance(any())).thenReturn(result(ClearanceStatus.HELD_CUSTOMS));

        assertThatThrownBy(() -> service.settleTransit(BOL_ID, "TRANSIT-REF-002",
                new BigDecimal("2500.00"), "MINT-ADDR", "ESCROW-ADDR"))
                .isInstanceOf(MaritimeComplianceException.class)
                .satisfies(ex -> assertThat(((MaritimeComplianceException) ex).getClearanceStatus())
                        .isEqualTo(ClearanceStatus.HELD_CUSTOMS));

        assertThat(bol.getClearanceStatus()).isEqualTo(ClearanceStatus.HELD_CUSTOMS);
        verify(finalityOutboxRepository, never()).save(any());
        verify(canalTransitSettlementRepository, never()).save(any());
    }

    @Test
    void settleTransit_failsClosedOnSanctions() {
        BillOfLading bol = bol(SimulatedMaritimeClearanceAdapter.SANCTIONED_VESSEL_IMO,
                "MSC", WALLET, "CONT-001", "SEAL-001");
        when(billOfLadingRepository.findById(BOL_ID)).thenReturn(Optional.of(bol));
        when(maritimeClearancePort.evaluateClearance(any())).thenReturn(result(ClearanceStatus.SANCTIONED));

        assertThatThrownBy(() -> service.settleTransit(BOL_ID, "TRANSIT-REF-003",
                new BigDecimal("2500.00"), "MINT-ADDR", "ESCROW-ADDR"))
                .isInstanceOf(MaritimeComplianceException.class)
                .satisfies(ex -> assertThat(((MaritimeComplianceException) ex).getClearanceStatus())
                        .isEqualTo(ClearanceStatus.SANCTIONED));

        assertThat(bol.getClearanceStatus()).isEqualTo(ClearanceStatus.SANCTIONED);
        verify(finalityOutboxRepository, never()).save(any());
        verify(canalTransitSettlementRepository, never()).save(any());
    }

    @Test
    void settleTransit_failsClosedOnUnverifiedCarrier() {
        BillOfLading bol = bol("IMO1234567", "UNVERIFIED-CARRIER-X", WALLET, "CONT-001", "SEAL-001");
        when(billOfLadingRepository.findById(BOL_ID)).thenReturn(Optional.of(bol));
        when(maritimeClearancePort.evaluateClearance(any())).thenReturn(result(ClearanceStatus.REJECTED));

        assertThatThrownBy(() -> service.settleTransit(BOL_ID, "TRANSIT-REF-004",
                new BigDecimal("2500.00"), "MINT-ADDR", "ESCROW-ADDR"))
                .isInstanceOf(MaritimeComplianceException.class)
                .satisfies(ex -> assertThat(((MaritimeComplianceException) ex).getClearanceStatus())
                        .isEqualTo(ClearanceStatus.REJECTED));

        assertThat(bol.getClearanceStatus()).isEqualTo(ClearanceStatus.REJECTED);
        verify(finalityOutboxRepository, never()).save(any());
        verify(canalTransitSettlementRepository, never()).save(any());
    }

    @Test
    void settleTransit_throwsWhenBillOfLadingMissing() {
        when(billOfLadingRepository.findById(BOL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.settleTransit(BOL_ID, "TRANSIT-REF-005",
                new BigDecimal("2500.00"), "MINT-ADDR", "ESCROW-ADDR"))
                .isInstanceOf(BillOfLadingNotFoundException.class);

        verify(maritimeClearancePort, never()).evaluateClearance(any());
        verify(finalityOutboxRepository, never()).save(any());
        verify(canalTransitSettlementRepository, never()).save(any());
    }

    private CanalTransitSettlement clearedSettlement() {
        BillOfLading bol = bol("IMO1234567", "MSC", WALLET, "CONT-001", "SEAL-001");
        bol.setId(BOL_ID);
        bol.setClearanceStatus(ClearanceStatus.CLEARED);

        CanalTransitSettlement settlement = CanalTransitSettlement.builder()
                .billOfLading(bol)
                .transitBookingReference("TRANSIT-REF-001")
                .transitFeeUsd(new BigDecimal("2500.00"))
                .settlementTokenMint("MINT-ADDR")
                .escrowAccount("ESCROW-ADDR")
                .status(TransitSettlementStatus.CLEARED)
                .build();
        settlement.setId(SETTLEMENT_ID);
        return settlement;
    }

    @Test
    void executeSettlement_whenCleared_enqueuesOutboxAndReturnsResponse() {
        CanalTransitSettlement settlement = clearedSettlement();
        when(clock.instant()).thenReturn(NOW);
        when(canalTransitSettlementRepository.findById(SETTLEMENT_ID)).thenReturn(Optional.of(settlement));
        when(finalityOutboxRepository.save(any(FinalityOutboxEntry.class))).thenAnswer(inv -> {
            FinalityOutboxEntry entry = inv.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });
        when(canalTransitSettlementRepository.save(any(CanalTransitSettlement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CanalTransitSettlementResponse response = service.executeSettlement(SETTLEMENT_ID);

        assertThat(response.id()).isEqualTo(SETTLEMENT_ID);
        assertThat(response.billOfLadingId()).isEqualTo(BOL_ID);
        assertThat(response.status()).isEqualTo(TransitSettlementStatus.SETTLED);
        assertThat(response.finalityState()).isEqualTo(SettlementStatus.CONFIRMED);

        ArgumentCaptor<FinalityOutboxEntry> outboxCaptor = ArgumentCaptor.forClass(FinalityOutboxEntry.class);
        verify(finalityOutboxRepository, times(1)).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(outboxCaptor.getValue().getAssetTokenId()).isEqualTo(BOL_ID);
        assertThat(outboxCaptor.getValue().getIdempotencyKey()).isNotBlank();
    }

    @Test
    void executeSettlement_throwsNotFoundWhenMissing() {
        when(canalTransitSettlementRepository.findById(SETTLEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.executeSettlement(SETTLEMENT_ID))
                .isInstanceOf(CanalTransitSettlementNotFoundException.class);

        verify(finalityOutboxRepository, never()).save(any());
        verify(canalTransitSettlementRepository, never()).save(any());
    }

    @Test
    void executeSettlement_failsClosedOnNonCleared() {
        CanalTransitSettlement settlement = clearedSettlement();
        settlement.getBillOfLading().setClearanceStatus(ClearanceStatus.SANCTIONED);
        when(canalTransitSettlementRepository.findById(SETTLEMENT_ID)).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> service.executeSettlement(SETTLEMENT_ID))
                .isInstanceOf(MaritimeComplianceException.class)
                .satisfies(ex -> assertThat(((MaritimeComplianceException) ex).getClearanceStatus())
                        .isEqualTo(ClearanceStatus.SANCTIONED));

        verify(finalityOutboxRepository, never()).save(any());
        verify(canalTransitSettlementRepository, never()).save(any());
    }

}

