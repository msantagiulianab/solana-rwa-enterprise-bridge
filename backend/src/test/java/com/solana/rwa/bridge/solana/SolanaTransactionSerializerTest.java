package com.solana.rwa.bridge.solana;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the strict Solana message account ordering, header byte counts, and
 * dynamic per-instruction account index mapping produced by
 * {@link SolanaTransactionSerializer}.
 *
 * <p>The test compiles a realistic two-instruction transaction: a System
 * program {@code CreateAccount} (creating the SPL mint account) followed by a
 * Token program {@code InitializeMint}.
 */
class SolanaTransactionSerializerTest {

    private static final String SYSTEM_PROGRAM_ID = "11111111111111111111111111111111";
    private static final String BLOCKHASH = "11111111111111111111111111111111";

    private SolanaKeypairService keypairService;
    private SolanaTransactionSerializer serializer;

    private byte[] payerPubkey;
    private byte[] mintPubkey;
    private byte[] tokenProgram;
    private byte[] rentSysvar;
    private byte[] systemProgram;

    @BeforeEach
    void setUp() {
        keypairService = new SolanaKeypairService("");
        serializer = new SolanaTransactionSerializer(keypairService);

        SolanaKeypair payer = keypairService.fromSeed(keypairService.deriveSeed("test-payer"));
        SolanaKeypair mint = keypairService.fromSeed(keypairService.deriveSeed("test-mint"));

        payerPubkey = payer.getPublicKeyBytes();
        mintPubkey = mint.getPublicKeyBytes();
        tokenProgram = Base58Codec.decode(SolanaMintService.TOKEN_PROGRAM_ID);
        rentSysvar = Base58Codec.decode(SolanaMintService.RENT_SYSVAR_ID);
        systemProgram = Base58Codec.decode(SYSTEM_PROGRAM_ID);
    }

    @Test
    void multiInstructionTx_producesStrictAccountOrderingAndHeaderBytes() {
        SolanaKeypair payer = keypairService.fromSeed(keypairService.deriveSeed("test-payer"));
        SolanaKeypair mint = keypairService.fromSeed(keypairService.deriveSeed("test-mint"));

        SolanaInstruction createAccount = new SolanaInstruction(
                Base58Codec.decode(SYSTEM_PROGRAM_ID),
                List.of(
                        new AccountMeta(payerPubkey, true, true),
                        new AccountMeta(mintPubkey, true, true)),
                buildCreateAccountData());

        SolanaInstruction initializeMint = new SolanaInstruction(
                tokenProgram,
                List.of(
                        new AccountMeta(mintPubkey, true, true),
                        new AccountMeta(rentSysvar, false, false)),
                buildInitializeMintData(payerPubkey, 6));

        String encoded = serializer.serializeAndSign(
                List.of(createAccount, initializeMint),
                BLOCKHASH,
                List.of(payer, mint));

        byte[] transaction = Base64.getDecoder().decode(encoded);

        // Skip the signature section: compact-u16 signature count + 64 bytes per
        // signature. There are exactly two signers (payer + mint).
        int[] offset = {0};
        int signatureCount = readCompactU16(transaction, offset);
        assertThat(signatureCount).isEqualTo(2);
        offset[0] += signatureCount * 64;

        // --- Message header (3 bytes) ---
        int numRequiredSignatures = transaction[offset[0]] & 0xFF;
        int numReadonlySigned = transaction[offset[0] + 1] & 0xFF;
        int numReadonlyUnsigned = transaction[offset[0] + 2] & 0xFF;
        offset[0] += 3;

        assertThat(numRequiredSignatures).isEqualTo(2); // payer + mint
        assertThat(numReadonlySigned).isZero();
        assertThat(numReadonlyUnsigned).isEqualTo(3); // system, rent, token program

        // --- Account address table (compiled in canonical order) ---
        int accountCount = readCompactU16(transaction, offset);
        assertThat(accountCount).isEqualTo(5);

        List<String> accountKeys = new ArrayList<>();
        for (int i = 0; i < accountCount; i++) {
            byte[] key = new byte[32];
            System.arraycopy(transaction, offset[0], key, 0, 32);
            offset[0] += 32;
            accountKeys.add(Base58Codec.encode(key));
        }

        // Canonical ordering: writable signers first, then readonly non-signers.
        assertThat(accountKeys.get(0)).isEqualTo(Base58Codec.encode(payerPubkey));
        assertThat(accountKeys.get(1)).isEqualTo(Base58Codec.encode(mintPubkey));
        assertThat(accountKeys.get(2)).isEqualTo(SYSTEM_PROGRAM_ID);
        assertThat(accountKeys.get(3)).isEqualTo(SolanaMintService.RENT_SYSVAR_ID);
        assertThat(accountKeys.get(4)).isEqualTo(SolanaMintService.TOKEN_PROGRAM_ID);

        // --- Recent blockhash (32 bytes) ---
        offset[0] += 32;

        // --- Instructions ---
        int instructionCount = readCompactU16(transaction, offset);
        assertThat(instructionCount).isEqualTo(2);

        // Instruction 0: System CreateAccount. Program index = system program (2),
        // account indices = [payer(0), mint(1)].
        int programIndex0 = transaction[offset[0]++] & 0xFF;
        assertThat(programIndex0).isEqualTo(2);

        int accountsLen0 = readCompactU16(transaction, offset);
        assertThat(accountsLen0).isEqualTo(2);
        assertThat(transaction[offset[0]++] & 0xFF).isZero();       // payer -> 0
        assertThat(transaction[offset[0]++] & 0xFF).isEqualTo(1);   // mint  -> 1

        int dataLen0 = readCompactU16(transaction, offset);
        assertThat(dataLen0).isEqualTo(52); // u32 (4) + u64 (8) + u64 (8) + [32] owner
        byte[] data0 = new byte[dataLen0];
        System.arraycopy(transaction, offset[0], data0, 0, dataLen0);
        offset[0] += dataLen0;

        // CreateAccount instruction data: discriminator (u32), lamports (u64),
        // space (u64), owner program ([32]byte).
        assertThat(readU32(data0, 0)).isZero();
        assertThat(readU64(data0, 4)).isEqualTo(1_461_600L);
        assertThat(readU64(data0, 12)).isEqualTo(82L);
        byte[] owner = new byte[32];
        System.arraycopy(data0, 20, owner, 0, 32);
        assertThat(Base58Codec.encode(owner)).isEqualTo(SolanaMintService.TOKEN_PROGRAM_ID);

        // Instruction 1: Token InitializeMint. Program index = token program (4),
        // account indices = [mint(1), rent sysvar(3)].
        int programIndex1 = transaction[offset[0]++] & 0xFF;
        assertThat(programIndex1).isEqualTo(4);

        int accountsLen1 = readCompactU16(transaction, offset);
        assertThat(accountsLen1).isEqualTo(2);
        assertThat(transaction[offset[0]++] & 0xFF).isEqualTo(1);   // mint       -> 1
        assertThat(transaction[offset[0]++] & 0xFF).isEqualTo(3);   // rent sysvar -> 3

        int dataLen1 = readCompactU16(transaction, offset);
        assertThat(dataLen1).isEqualTo(35);
    }

    private byte[] buildCreateAccountData() {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        writeU32(data, 0);              // CreateAccount discriminator (u32)
        writeU64(data, 1_461_600L);     // lamports (rent-exempt minimum)
        writeU64(data, 82L);            // space for SPL mint account
        data.writeBytes(tokenProgram);  // owner program id
        return data.toByteArray();
    }

    private void writeU32(ByteArrayOutputStream out, int value) {
        for (int i = 0; i < 4; i++) {
            out.write(value & 0xFF);
            value >>= 8;
        }
    }

    private static long readU64(byte[] data, int offset) {
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }

    private static int readU32(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private byte[] buildInitializeMintData(byte[] mintAuthority, int decimals) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(0); // InitializeMint discriminator
        data.write(decimals & 0xFF);
        data.writeBytes(mintAuthority);
        data.write(0); // freeze authority COption::None
        return data.toByteArray();
    }

    private void writeU64(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) (value & 0xFF));
            value >>= 8;
        }
    }

    private static int readCompactU16(byte[] data, int[] offset) {
        int first = data[offset[0]++] & 0xFF;
        if ((first & 0x80) == 0) {
            return first;
        }
        int second = data[offset[0]++] & 0xFF;
        if ((second & 0x80) == 0) {
            return (first & 0x7F) | (second << 7);
        }
        int third = data[offset[0]++] & 0xFF;
        return (first & 0x7F) | ((second & 0x7F) << 7) | (third << 14);
    }
}