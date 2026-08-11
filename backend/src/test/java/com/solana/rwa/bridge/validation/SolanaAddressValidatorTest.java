package com.solana.rwa.bridge.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the base58 {@link ValidSolanaAddress} constraint.
 */
class SolanaAddressValidatorTest {

    private final SolanaAddressValidator validator = new SolanaAddressValidator();
    private final ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);

    @Test
    void isValid_acceptsValidBase58SolanaAddress() {
        assertThat(validator.isValid("7XeXLabcDEFghijkmnpqrstuvwxyz23456789", context)).isTrue();
        assertThat(validator.isValid("MNTabcdefghijkmnpqrstuvwxyz123456789", context)).isTrue();
    }

    @Test
    void isValid_rejectsForbiddenBase58Characters() {
        // '0', 'O', 'I', 'l' are not part of the base58 alphabet.
        assertThat(validator.isValid("01".repeat(20), context)).isFalse();
        assertThat(validator.isValid("O1".repeat(20), context)).isFalse();
        assertThat(validator.isValid("I1".repeat(20), context)).isFalse();
        assertThat(validator.isValid("l1".repeat(20), context)).isFalse();
    }

    @Test
    void isValid_rejectsAddressTooShort() {
        assertThat(validator.isValid("7XeXLabcDE", context)).isFalse();
    }

    @Test
    void isValid_rejectsAddressTooLong() {
        assertThat(validator.isValid("1".repeat(45), context)).isFalse();
    }

    @Test
    void isValid_allowsNullBecauseBlankHandledByNotBlank() {
        assertThat(validator.isValid(null, context)).isTrue();
    }
}
