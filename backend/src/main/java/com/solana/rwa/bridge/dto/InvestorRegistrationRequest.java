package com.solana.rwa.bridge.dto;

import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.validation.ValidSolanaAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

/**
 * Request payload to register an investor or update their KYC status.
 *
 * @param walletAddress investor's Solana wallet (base58, 32-44 chars)
 * @param country       ISO 3166-1 alpha-2 country code
 * @param kycStatus     current KYC/AML verification status
 */
@Getter
@Builder
public class InvestorRegistrationRequest {

    @NotBlank(message = "walletAddress must not be blank")
    @ValidSolanaAddress
    private final String walletAddress;

    @NotBlank(message = "country must not be blank")
    @Size(min = 2, max = 2, message = "country must be a 2-letter ISO country code")
    private final String country;

    @NotNull(message = "kycStatus must not be null")
    private final KycStatus kycStatus;
}
