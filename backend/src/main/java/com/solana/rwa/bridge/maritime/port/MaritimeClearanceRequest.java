package com.solana.rwa.bridge.maritime.port;

import java.math.BigDecimal;

/**
 * Immutable, pure-Java request for a maritime clearance evaluation.
 *
 * @param billOfLadingNumber eBL document reference (unique)
 * @param containerNumber    container / consignment identifier
 * @param sealNumber         customs seal number
 * @param grossWeightKg      container gross weight in kilograms
 * @param hazardous          whether the consignment is hazardous cargo
 * @param vesselImo          IMO number of the vessel
 * @param carrierCode        carrier / operator identifier
 * @param portOfLoading      origin port (UN/LOCODE or port code)
 * @param portOfDischarge    destination port (UN/LOCODE or port code)
 * @param consigneeWallet    Solana base58 wallet of the consignee for DvP settlement
 */
public record MaritimeClearanceRequest(
        String billOfLadingNumber,
        String containerNumber,
        String sealNumber,
        BigDecimal grossWeightKg,
        boolean hazardous,
        String vesselImo,
        String carrierCode,
        String portOfLoading,
        String portOfDischarge,
        String consigneeWallet
) {
}
