package com.solana.rwa.bridge.solana;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline unit tests for {@link ComputeBudgetInstruction}, verifying the exact
 * Compute Budget instruction encodings used by the Solana runtime.
 *
 * <p>The Compute Budget program references no accounts, so the returned
 * {@link SolanaInstruction} must carry an empty account list; the program id
 * itself is resolved by the transaction serializer as a readonly, non-signer
 * account during message compilation.
 */
class ComputeBudgetInstructionTest {

    private static final String COMPUTE_BUDGET_PROGRAM_ID =
            "ComputeBudget111111111111111111111111111111";

    @Test
    void setComputeUnitPrice_targetsComputeBudgetProgramWithNoAccounts() {
        SolanaInstruction instruction = ComputeBudgetInstruction.setComputeUnitPrice(10_000L);

        assertThat(instruction.programId()).hasSize(32);
        assertThat(Base58Codec.encode(instruction.programId()))
                .isEqualTo(COMPUTE_BUDGET_PROGRAM_ID);
        assertThat(instruction.accounts()).isEmpty();
    }

    @Test
    void setComputeUnitPrice_encodesDiscriminatorAndUnsignedLittleEndianU64() {
        SolanaInstruction instruction = ComputeBudgetInstruction.setComputeUnitPrice(10_000L);

        // 0x03 discriminator + 10_000 as an 8-byte little-endian u64 (0x2710).
        assertThat(instruction.data()).isEqualTo(new byte[] {
                0x03,
                0x10, 0x27, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        });
    }

    @Test
    void setComputeUnitPrice_encodesMaxValueAsUnsignedLittleEndian() {
        SolanaInstruction instruction =
                ComputeBudgetInstruction.setComputeUnitPrice(Long.MAX_VALUE);

        // Long.MAX_VALUE (0x7FFF_FFFF_FFFF_FFFF) as unsigned little-endian u64
        // proves the high bit is emitted last, not sign-extended.
        assertThat(instruction.data()).isEqualTo(new byte[] {
                0x03,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F
        });
    }

    @Test
    void setComputeUnitPrice_rejectsNegativeMicroLamports() {
        assertThatThrownBy(() -> ComputeBudgetInstruction.setComputeUnitPrice(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setComputeUnitLimit_targetsComputeBudgetProgramWithNoAccounts() {
        SolanaInstruction instruction = ComputeBudgetInstruction.setComputeUnitLimit(10_000);

        assertThat(instruction.programId()).hasSize(32);
        assertThat(Base58Codec.encode(instruction.programId()))
                .isEqualTo(COMPUTE_BUDGET_PROGRAM_ID);
        assertThat(instruction.accounts()).isEmpty();
    }

    @Test
    void setComputeUnitLimit_encodesDiscriminatorAndUnsignedLittleEndianU32() {
        SolanaInstruction instruction = ComputeBudgetInstruction.setComputeUnitLimit(10_000);

        // 0x02 discriminator + 10_000 as a 4-byte little-endian u32 (0x2710).
        assertThat(instruction.data()).isEqualTo(new byte[] {
                0x02,
                0x10, 0x27, 0x00, 0x00
        });
    }

    @Test
    void setComputeUnitLimit_rejectsNegativeUnits() {
        assertThatThrownBy(() -> ComputeBudgetInstruction.setComputeUnitLimit(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
