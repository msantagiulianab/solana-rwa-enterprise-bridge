package com.solana.rwa.bridge.maritime.dto;

import com.solana.rwa.bridge.maritime.domain.ClearanceStatus;

import java.util.List;
import java.util.UUID;

/**
 * Response projection for a registered electronic Bill of Lading.
 *
 * @param id               eBL identifier
 * @param blNumber         unique eBL document reference
 * @param vesselImo        IMO number of the vessel
 * @param carrierId        carrier / operator identifier
 * @param originPort       origin port
 * @param destinationPort  destination port
 * @param consigneeWallet  Solana base58 wallet of the consignee
 * @param clearanceStatus  maritime clearance / transit status
 * @param consignments     container consignments
 */
public record BillOfLadingResponse(
        UUID id,
        String blNumber,
        String vesselImo,
        String carrierId,
        String originPort,
        String destinationPort,
        String consigneeWallet,
        ClearanceStatus clearanceStatus,
        List<ContainerConsignmentResponse> consignments) {
}
