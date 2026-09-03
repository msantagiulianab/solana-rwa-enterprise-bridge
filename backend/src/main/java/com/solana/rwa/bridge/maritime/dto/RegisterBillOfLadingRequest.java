package com.solana.rwa.bridge.maritime.dto;

import com.solana.rwa.bridge.validation.ValidSolanaAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request payload to register an electronic Bill of Lading (eBL) together with
 * its container consignments.
 *
 * @param blNumber        unique eBL document reference
 * @param vesselImo       IMO number of the vessel
 * @param carrierId       carrier / operator identifier
 * @param originPort      origin port (UN/LOCODE or port code)
 * @param destinationPort destination port (UN/LOCODE or port code)
 * @param consigneeWallet Solana base58 wallet of the consignee for DvP settlement
 * @param consignments    container consignments attached to this eBL
 */
public record RegisterBillOfLadingRequest(
        @NotBlank(message = "blNumber must not be blank") String blNumber,
        @NotBlank(message = "vesselImo must not be blank") String vesselImo,
        String carrierId,
        String originPort,
        String destinationPort,
        @NotBlank(message = "consigneeWallet must not be blank")
        @ValidSolanaAddress(message = "consigneeWallet must be a valid Solana address")
        String consigneeWallet,
        @NotEmpty(message = "consignments must not be empty")
        @Valid List<RegisterContainerConsignmentRequest> consignments) {
}
