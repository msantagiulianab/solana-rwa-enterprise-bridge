package com.solana.rwa.bridge.dto;

import com.solana.rwa.bridge.entity.KycStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation unit tests for the compliance and investor DTOs.
 */
class ComplianceDtosValidationTest {

    private static final String VALID_WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String VALID_MINT = "MNTabcdefghijkmnpqrstuvwxyz123456789";

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ---------------------------------------------------------------
    // ComplianceCheckRequest
    // ---------------------------------------------------------------

    @Test
    void complianceCheckRequest_acceptsValidRequest() {
        ComplianceCheckRequest request = ComplianceCheckRequest.builder()
                .walletAddress(VALID_WALLET)
                .assetMintAddress(VALID_MINT)
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void complianceCheckRequest_rejectsBlankWalletAddress() {
        ComplianceCheckRequest request = ComplianceCheckRequest.builder()
                .walletAddress("")
                .assetMintAddress(VALID_MINT)
                .build();

        Set<ConstraintViolation<ComplianceCheckRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("walletAddress"));
    }

    @Test
    void complianceCheckRequest_rejectsInvalidWalletAddressFormat() {
        ComplianceCheckRequest request = ComplianceCheckRequest.builder()
                .walletAddress("NOT_A_SOLANA_ADDRESS_0")
                .assetMintAddress(VALID_MINT)
                .build();

        Set<ConstraintViolation<ComplianceCheckRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("walletAddress"));
    }

    @Test
    void complianceCheckRequest_rejectsBlankAssetMintAddress() {
        ComplianceCheckRequest request = ComplianceCheckRequest.builder()
                .walletAddress(VALID_WALLET)
                .assetMintAddress("   ")
                .build();

        Set<ConstraintViolation<ComplianceCheckRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("assetMintAddress"));
    }

    // ---------------------------------------------------------------
    // InvestorRegistrationRequest
    // ---------------------------------------------------------------

    @Test
    void investorRegistrationRequest_acceptsValidRequest() {
        InvestorRegistrationRequest request = InvestorRegistrationRequest.builder()
                .walletAddress(VALID_WALLET)
                .country("US")
                .kycStatus(KycStatus.PENDING)
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void investorRegistrationRequest_rejectsBlankCountry() {
        InvestorRegistrationRequest request = InvestorRegistrationRequest.builder()
                .walletAddress(VALID_WALLET)
                .country("")
                .kycStatus(KycStatus.PENDING)
                .build();

        Set<ConstraintViolation<InvestorRegistrationRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("country"));
    }

    @Test
    void investorRegistrationRequest_rejectsNullKycStatus() {
        InvestorRegistrationRequest request = InvestorRegistrationRequest.builder()
                .walletAddress(VALID_WALLET)
                .country("US")
                .kycStatus(null)
                .build();

        Set<ConstraintViolation<InvestorRegistrationRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("kycStatus"));
    }
}
