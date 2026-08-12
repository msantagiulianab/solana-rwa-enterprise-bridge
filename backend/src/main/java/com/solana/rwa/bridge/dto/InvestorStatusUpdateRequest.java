package com.solana.rwa.bridge.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.solana.rwa.bridge.entity.KycStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

/**
 * Request payload to update an investor's KYC status (APPROVE / REJECT).
 *
 * @param kycStatus target KYC status
 */
@Getter
@Builder
public class InvestorStatusUpdateRequest {

    @NotNull(message = "kycStatus must not be null")
    private final KycStatus kycStatus;

    @JsonCreator
    public InvestorStatusUpdateRequest(@JsonProperty("kycStatus") KycStatus kycStatus) {
        this.kycStatus = kycStatus;
    }
}