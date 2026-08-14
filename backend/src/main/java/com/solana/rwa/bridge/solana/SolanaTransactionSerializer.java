package com.solana.rwa.bridge.solana;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles a list of {@link SolanaInstruction}s into a Solana transaction
 * message, signs it with the supplied keypairs, and returns the base64-encoded
 * signed transaction suitable for the {@code sendTransaction} JSON-RPC method.
 *
 * <p>Implements the legacy (non-versioned) transaction wire format:
 * header, compacted account list, recent blockhash, and instructions. No live
 * node interaction occurs here; signing is performed in-process with the
 * {@link SolanaKeypairService}.
 */
@Service
public class SolanaTransactionSerializer {

    private final SolanaKeypairService keypairService;

    public SolanaTransactionSerializer(SolanaKeypairService keypairService) {
        this.keypairService = keypairService;
    }

    /**
     * Serializes and signs the given instructions.
     *
     * @param instructions   instructions to include (program ids are compiled as
     *                       readonly accounts automatically)
     * @param recentBlockhash base58 recent blockhash (decoded to 32 bytes)
     * @param signers        keypairs that must sign the transaction
     * @return base64-encoded signed transaction bytes
     */
    public String serializeAndSign(List<SolanaInstruction> instructions,
                                   String recentBlockhash,
                                   List<SolanaKeypair> signers) {
        byte[] blockhash = Base58Codec.decode(recentBlockhash);
        if (blockhash.length != 32) {
            throw new IllegalArgumentException("Recent blockhash must decode to 32 bytes");
        }

        CompiledAccounts compiled = compileAccounts(instructions);
        byte[] message = serializeMessage(instructions, compiled, blockhash);

        ByteArrayOutputStream transaction = new ByteArrayOutputStream();
        writeCompactU16(transaction, signers.size());
        for (SolanaKeypair signer : signers) {
            byte[] signature = keypairService.sign(message, signer);
            if (signature.length != 64) {
                throw new IllegalStateException("Ed25519 signature must be 64 bytes");
            }
            transaction.writeBytes(signature);
        }
        transaction.writeBytes(message);

        return Base64.getEncoder().encodeToString(transaction.toByteArray());
    }

    private CompiledAccounts compileAccounts(List<SolanaInstruction> instructions) {
        Map<String, AccountMeta> unique = new LinkedHashMap<>();

        for (SolanaInstruction instruction : instructions) {
            for (AccountMeta meta : instruction.accounts()) {
                merge(unique, meta);
            }
            // Program ids are referenced as readonly, non-signer accounts.
            merge(unique, new AccountMeta(instruction.programId(), false, false));
        }

        List<AccountMeta> ordered = new ArrayList<>(unique.values());
        ordered.sort(Comparator
                .comparingInt((AccountMeta meta) -> meta.signer() ? 0 : 1)
                .thenComparingInt(meta -> meta.writable() ? 0 : 1));

        Map<String, Integer> indexByKey = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            indexByKey.put(Base58Codec.encode(ordered.get(i).pubkey()), i);
        }

        int requiredSignatures = (int) ordered.stream().filter(AccountMeta::signer).count();
        int readonlySigned = (int) ordered.stream()
                .filter(meta -> meta.signer() && !meta.writable()).count();
        int readonlyUnsigned = (int) ordered.stream()
                .filter(meta -> !meta.signer() && !meta.writable()).count();

        return new CompiledAccounts(ordered, requiredSignatures, readonlySigned, readonlyUnsigned, indexByKey);
    }

    private void merge(Map<String, AccountMeta> unique, AccountMeta meta) {
        String key = Base58Codec.encode(meta.pubkey());
        AccountMeta existing = unique.get(key);
        if (existing == null) {
            unique.put(key, meta);
        } else {
            unique.put(key, new AccountMeta(
                    existing.pubkey(),
                    existing.signer() || meta.signer(),
                    existing.writable() || meta.writable()));
        }
    }

    private byte[] serializeMessage(List<SolanaInstruction> instructions,
                                    CompiledAccounts compiled,
                                    byte[] blockhash) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Message header.
        writeU8(out, compiled.requiredSignatures());
        writeU8(out, compiled.readonlySigned());
        writeU8(out, compiled.readonlyUnsigned());

        // Account addresses.
        writeCompactU16(out, compiled.accounts().size());
        for (AccountMeta meta : compiled.accounts()) {
            out.writeBytes(meta.pubkey());
        }

        // Recent blockhash.
        out.writeBytes(blockhash);

        // Instructions.
        writeCompactU16(out, instructions.size());
        for (SolanaInstruction instruction : instructions) {
            int programIndex = compiled.indexOf(instruction.programId());
            writeU8(out, programIndex);

            List<AccountMeta> accountMetas = instruction.accounts();
            writeCompactU16(out, accountMetas.size());
            for (AccountMeta meta : accountMetas) {
                writeU8(out, compiled.indexOf(meta.pubkey()));
            }

            byte[] data = instruction.data();
            writeCompactU16(out, data.length);
            out.writeBytes(data);
        }

        return out.toByteArray();
    }

    /**
     * Conventional instruction discriminators for the SPL Token program.
     */
    public static byte[] tokenInstructionData(int discriminator) {
        return new byte[]{(byte) discriminator};
    }

    private void writeU8(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
    }

    private void writeCompactU16(ByteArrayOutputStream out, int value) {
        // Solana compact-u16 (shortvec) length prefix.
        if (value < 0x80) {
            out.write(value);
        } else if (value < 0x4000) {
            out.write((value & 0x7F) | 0x80);
            out.write((value >> 7) & 0xFF);
        } else {
            out.write((value & 0x7F) | 0x80);
            out.write(((value >> 7) & 0x7F) | 0x80);
            out.write((value >> 14) & 0xFF);
        }
    }

    private record CompiledAccounts(
            List<AccountMeta> accounts,
            int requiredSignatures,
            int readonlySigned,
            int readonlyUnsigned,
            Map<String, Integer> indexByKey) {

        int indexOf(byte[] pubkey) {
            Integer index = indexByKey.get(Base58Codec.encode(pubkey));
            if (index == null) {
                throw new IllegalStateException("Account not found during instruction compilation");
            }
            return index;
        }
    }
}