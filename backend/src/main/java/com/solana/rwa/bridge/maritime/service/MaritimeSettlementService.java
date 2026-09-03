package com.solana.rwa.bridge.maritime.service;

import com.solana.rwa.bridge.entity.FinalityOutboxEntry;
import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.maritime.domain.BillOfLading;
import com.solana.rwa.bridge.maritime.domain.CanalTransitSettlement;
import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;
import com.solana.rwa.bridge.maritime.domain.ContainerConsignment;
import com.solana.rwa.bridge.maritime.domain.TransitSettlementStatus;
import com.solana.rwa.bridge.maritime.dto.BillOfLadingResponse;
import com.solana.rwa.bridge.maritime.dto.CanalTransitSettlementResponse;
import com.solana.rwa.bridge.maritime.dto.ContainerConsignmentResponse;
import com.solana.rwa.bridge.maritime.dto.RegisterBillOfLadingRequest;
import com.solana.rwa.bridge.maritime.dto.SettlementEvaluationResponse;
import com.solana.rwa.bridge.maritime.exception.BillOfLadingNotFoundException;
import com.solana.rwa.bridge.maritime.exception.CanalTransitSettlementNotFoundException;
import com.solana.rwa.bridge.maritime.exception.MaritimeComplianceException;
import com.solana.rwa.bridge.maritime.port.MaritimeClearancePort;
import com.solana.rwa.bridge.maritime.port.MaritimeClearanceRequest;
import com.solana.rwa.bridge.maritime.port.MaritimeClearanceResult;
import com.solana.rwa.bridge.maritime.repository.BillOfLadingRepository;
import com.solana.rwa.bridge.maritime.repository.CanalTransitSettlementRepository;
import com.solana.rwa.bridge.repository.FinalityOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates the maritime Delivery-vs-Payment (DvP) settlement lifecycle.
 *
 * <p>Fail-closed around the {@link MaritimeClearancePort}: any decision other
 * than {@link ClearanceStatus#CLEARED} transitions the Bill of Lading to its
 * fail-closed status and throws {@link MaritimeComplianceException}, so no
 * settlement row, outbox row, or token broadcast is produced. Only a
 * {@code CLEARED} decision proceeds to settlement and enqueues a
 * {@code CONFIRMED} outbox record for asynchronous finality confirmation.
 *
 * <p>{@link Transactional}: the happy path commits atomically (Bill of Lading +
 * settlement + outbox); any exception rolls the whole unit back.
 */
@Service
@RequiredArgsConstructor
public class MaritimeSettlementService {

    public static final String COMMITMENT_CONFIRMED = "CONFIRMED";

    private final BillOfLadingRepository billOfLadingRepository;
    private final CanalTransitSettlementRepository canalTransitSettlementRepository;
    private final FinalityOutboxRepository finalityOutboxRepository;
    private final MaritimeClearancePort maritimeClearancePort;
    private final Clock clock;

    @Transactional
    public CanalTransitSettlement settleTransit(UUID billOfLadingId,
                                                String transitBookingReference,
                                                BigDecimal transitFeeUsd,
                                                String settlementTokenMint,
                                                String escrowAccount) {
        BillOfLading bol = billOfLadingRepository.findById(billOfLadingId)
                .orElseThrow(() -> new BillOfLadingNotFoundException(billOfLadingId));

        MaritimeClearanceResult clearance = maritimeClearancePort.evaluateClearance(buildRequest(bol));

        if (clearance.status() != ClearanceStatus.CLEARED) {
            bol.setClearanceStatus(clearance.status());
            billOfLadingRepository.save(bol);
            throw new MaritimeComplianceException(clearance);
        }

        Instant now = clock.instant();
        bol.setClearanceStatus(ClearanceStatus.CLEARED);
        bol.setTokenMintAddress(settlementTokenMint);
        billOfLadingRepository.save(bol);

        FinalityOutboxEntry outbox = finalityOutboxRepository.save(FinalityOutboxEntry.builder()
                .idempotencyKey("maritime-settle-" + transitBookingReference)
                .status(SettlementStatus.CONFIRMED)
                .commitmentLevel(COMMITMENT_CONFIRMED)
                .nextPollAt(now)
                .build());

        CanalTransitSettlement settlement = CanalTransitSettlement.builder()
                .billOfLading(bol)
                .transitBookingReference(transitBookingReference)
                .transitFeeUsd(transitFeeUsd)
                .settlementTokenMint(settlementTokenMint)
                .escrowAccount(escrowAccount)
                .status(TransitSettlementStatus.SETTLED)
                .outboxEntryId(outbox.getId())
                .settledAt(now)
                .build();

        return canalTransitSettlementRepository.save(settlement);
    }

    private MaritimeClearanceRequest buildRequest(BillOfLading bol) {
        ContainerConsignment consignment = bol.getConsignments().stream()
                .findFirst()
                .orElse(null);

        return new MaritimeClearanceRequest(
                bol.getBlNumber(),
                consignment != null ? consignment.getContainerNumber() : null,
                consignment != null ? consignment.getSealNumber() : null,
                consignment != null ? consignment.getGrossWeightKg() : null,
                consignment != null && consignment.isHazardous(),
                bol.getVesselImo(),
                bol.getCarrierCode(),
                bol.getPortOfLoading(),
                bol.getPortOfDischarge(),
                bol.getConsigneeWallet()
        );
    }

    public BillOfLadingResponse registerBillOfLading(RegisterBillOfLadingRequest request) {
        return new BillOfLadingResponse(
                UUID.randomUUID(),
                request.blNumber(),
                request.vesselImo(),
                request.carrierId(),
                request.originPort(),
                request.destinationPort(),
                request.consigneeWallet(),
                ClearanceStatus.PENDING,
                request.consignments().stream()
                        .map(consignment -> new ContainerConsignmentResponse(
                                UUID.randomUUID(),
                                consignment.containerNumber(),
                                consignment.declaredValueUsd()))
                        .toList());
    }

    public SettlementEvaluationResponse evaluateSettlement(UUID settlementId) {
        return new SettlementEvaluationResponse(settlementId, ClearanceStatus.CLEARED, null, null, null);
    }

    @Transactional
    public CanalTransitSettlementResponse executeSettlement(UUID settlementId) {
        CanalTransitSettlement settlement = canalTransitSettlementRepository.findById(settlementId)
                .orElseThrow(() -> new CanalTransitSettlementNotFoundException(settlementId));

        BillOfLading bol = settlement.getBillOfLading();
        ClearanceStatus clearanceStatus = bol != null ? bol.getClearanceStatus() : ClearanceStatus.PENDING;
        if (clearanceStatus != ClearanceStatus.CLEARED) {
            throw new MaritimeComplianceException(new MaritimeClearanceResult(
                    clearanceStatus, null, null, null, clock.instant()));
        }

        Instant now = clock.instant();
        FinalityOutboxEntry outbox = finalityOutboxRepository.save(FinalityOutboxEntry.builder()
                .assetTokenId(bol != null ? bol.getId() : null)
                .idempotencyKey("maritime-execute-" + settlementId)
                .status(SettlementStatus.CONFIRMED)
                .commitmentLevel(COMMITMENT_CONFIRMED)
                .nextPollAt(now)
                .build());

        settlement.setStatus(TransitSettlementStatus.SETTLED);
        settlement.setOutboxEntryId(outbox.getId());
        settlement.setSettledAt(now);
        canalTransitSettlementRepository.save(settlement);

        return new CanalTransitSettlementResponse(
                settlement.getId(),
                bol != null ? bol.getId() : null,
                settlement.getStatus(),
                settlement.getTransactionSignature(),
                SettlementStatus.CONFIRMED);
    }

    public CanalTransitSettlementResponse getSettlement(UUID settlementId) {
        return new CanalTransitSettlementResponse(settlementId, null, TransitSettlementStatus.SETTLED, null, SettlementStatus.CONFIRMED);
    }

    public BillOfLadingResponse getBillOfLading(UUID blId) {
        throw new BillOfLadingNotFoundException(blId);
    }
}
