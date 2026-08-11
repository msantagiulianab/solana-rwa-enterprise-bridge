package com.solana.rwa.bridge.dto;

import com.solana.rwa.bridge.validation.ValidSolanaAddress;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

/**
 * Request payload to register an investor via the frontend form.
 *
 * @param fullName       investor's full name
 * @param email          investor's email address
 * @param solanaAddress  investor's Solana wallet (base58, 32-44 chars)
 */
@Getter
@Builder
public class InvestorRegistrationRequest {

    @NotBlank(message = "fullName must not be blank")
    private final String fullName;

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be a valid email address")
    private final String email;

    @NotBlank(message = "solanaAddress must not be blank")
    @ValidSolanaAddress
    private final String solanaAddress;
}