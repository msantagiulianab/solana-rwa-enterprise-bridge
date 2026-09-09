package com.solana.rwa.bridge.solana;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Token-2022 mint-extension wire format, account sizing and
 * program-derived-address derivation introduced in Week 3.
 *
 * <p>Pure, offline assertions — no Spring context, no RPC.
 */
class Token2022MintExtensionTest {

    @Test
    void mintAccountSize_calculatesCanonicalToken2022Sizes() {
        // 165 base + 1 account-type byte + one TLV entry (2 + 2 + value).
        assertThat(Token2022Program.permanentDelegateMintSize()).isEqualTo(202);
        assertThat(Token2022Program.transferHookMintSize()).isEqualTo(234);
        assertThat(Token2022Program.permanentDelegateAndTransferHookMintSize()).isEqualTo(270);
    }

    @Test
    void initializePermanentDelegateData_encodesDiscriminatorAndDelegate() {
        byte[] delegate = deterministicKey("delegate");

        byte[] data = Token2022InstructionBuilder.buildInitializePermanentDelegateData(delegate);

        assertThat(data).hasSize(33);
        assertThat(data[0] & 0xFF).isEqualTo(35); // InitializePermanentDelegate
        assertThat(Arrays.copyOfRange(data, 1, 33)).isEqualTo(delegate);
    }

    @Test
    void initializeTransferHookData_encodesExtensionAndSubInstruction() {
        byte[] authority = deterministicKey("authority");
        byte[] hookProgram = deterministicKey("hook-program");

        byte[] data = Token2022InstructionBuilder.buildInitializeTransferHookData(authority, hookProgram);

        assertThat(data).hasSize(66);
        assertThat(data[0] & 0xFF).isEqualTo(36); // TransferHookExtension
        assertThat(data[1] & 0xFF).isZero();       // TransferHookInstruction::Initialize
        assertThat(Arrays.copyOfRange(data, 2, 34)).isEqualTo(authority);
        assertThat(Arrays.copyOfRange(data, 34, 66)).isEqualTo(hookProgram);
    }

    @Test
    void transferCheckedData_encodesAmountLittleEndianAndDecimals() {
        byte[] data = Token2022InstructionBuilder.buildTransferCheckedData(42L, 6);

        assertThat(data).hasSize(10);
        assertThat(data[0] & 0xFF).isEqualTo(12); // TransferChecked
        assertThat(readU64(data, 1)).isEqualTo(42L);
        assertThat(data[9] & 0xFF).isEqualTo(6);
    }

    @Test
    void initializeMintInstruction_targetsToken2022Program() {
        byte[] mint = deterministicKey("mint");
        byte[] mintAuthority = deterministicKey("mint-authority");

        SolanaInstruction instruction = Token2022InstructionBuilder.initializeMint(
                mint, 6, mintAuthority, null);

        assertThat(Base58Codec.encode(instruction.programId()))
                .isEqualTo(Token2022Program.TOKEN_2022_PROGRAM_ID);
        assertThat(instruction.accounts()).hasSize(2);
        assertThat(instruction.data()).hasSize(35);
    }

    @Test
    void findProgramAddress_isOffCurveDeterministicAndSelfConsistent() {
        byte[] seed = Token2022Program.EXTRA_ACCOUNT_METAS_SEED;
        byte[] program = Base58Codec.decode(Token2022Program.TOKEN_2022_PROGRAM_ID);

        SolanaPdaUtil.Pda pda = SolanaPdaUtil.findProgramAddress(seed, program);

        assertThat(pda.address()).hasSize(32);
        assertThat(SolanaPdaUtil.isOffCurve(pda.address())).isTrue();
        assertThat(pda.bump()).isBetween(0, 255);

        // Deterministic across invocations.
        SolanaPdaUtil.Pda again = SolanaPdaUtil.findProgramAddress(seed, program);
        assertThat(again.address()).isEqualTo(pda.address());
        assertThat(again.bump()).isEqualTo(pda.bump());

        // Every higher bump must have been on-curve (otherwise the loop would
        // have stopped earlier). This proves the off-curve gate is consistent.
        for (int bump = pda.bump() + 1; bump <= 255; bump++) {
            byte[] candidate = candidateAddress(seed, program, bump);
            assertThat(SolanaPdaUtil.isOnCurve(candidate))
                    .as("bump %d should be on-curve", bump)
                    .isTrue();
        }
    }

    @Test
    void findProgramAddress_differsByProgramId() {
        byte[] seed = Token2022Program.EXTRA_ACCOUNT_METAS_SEED;
        byte[] programA = Base58Codec.decode(Token2022Program.TOKEN_2022_PROGRAM_ID);
        byte[] programB = deterministicKey("another-hook-program");

        SolanaPdaUtil.Pda pdaA = SolanaPdaUtil.findProgramAddress(seed, programA);
        SolanaPdaUtil.Pda pdaB = SolanaPdaUtil.findProgramAddress(seed, programB);

        assertThat(pdaA.address()).isNotEqualTo(pdaB.address());
    }

    private static byte[] candidateAddress(byte[] seed, byte[] program, int bump) {
        byte[] marker = "ProgramDerivedAddress".getBytes(StandardCharsets.US_ASCII);
        byte[] input = new byte[seed.length + 1 + program.length + marker.length];
        int offset = 0;
        System.arraycopy(seed, 0, input, offset, seed.length);
        offset += seed.length;
        input[offset++] = (byte) bump;
        System.arraycopy(program, 0, input, offset, program.length);
        offset += program.length;
        System.arraycopy(marker, 0, input, offset, marker.length);
        return SolanaPdaUtil.sha256(input);
    }

    private static byte[] deterministicKey(String material) {
        SolanaKeypairService service = new SolanaKeypairService("");
        return service.fromSeed(service.deriveSeed(material)).getPublicKeyBytes();
    }

    private static long readU64(byte[] data, int offset) {
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }
}
