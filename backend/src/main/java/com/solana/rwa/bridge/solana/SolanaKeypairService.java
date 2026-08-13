package com.solana.rwa.bridge.solana;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Generates and signs Solana Ed25519 keypairs.
 *
 * <p>The signing key may be supplied through the {@code solana.rpc.private-key}
 * env var (base58) for deterministic enterprise custody, or generated fresh
 * per mint when left blank. Signing is performed in-process with a pure-Java
 * Ed25519 provider, so no live Devnet node or wallet provider is involved at
 * key-generation time.
 */
@Service
public class SolanaKeypairService {

    private static final int SEED_LENGTH = 32;

    private final EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName("Ed25519");

    private final String configuredPrivateKey;
    private final byte[] ephemeralSeed;
    private final SecureRandom secureRandom = new SecureRandom();

    public SolanaKeypairService(@Value("${solana.rpc.private-key:}") String configuredPrivateKey) {
        this.configuredPrivateKey = configuredPrivateKey;
        byte[] seed = new byte[SEED_LENGTH];
        this.secureRandom.nextBytes(seed);
        this.ephemeralSeed = seed;
    }

    /**
     * Resolves the keypair used for mint creation.
     *
     * <p>When {@code SOLANA_DEVNET_PRIVATE_KEY} is configured, decodes the base58
     * secret seed. Otherwise generates a fresh ephemeral keypair. No private key
     * is logged or persisted.
     */
    public SolanaKeypair resolveKeypair() {
        if (configuredPrivateKey != null && !configuredPrivateKey.isBlank()) {
            byte[] seed = Base58Codec.decode(configuredPrivateKey);
            if (seed.length != SEED_LENGTH) {
                throw new IllegalStateException(
                        "SOLANA_DEVNET_PRIVATE_KEY must be a 32-byte base58-encoded seed");
            }
            return fromSeed(seed);
        }
        return fromSeed(ephemeralSeed.clone());
    }

    /**
     * Generates a fresh random keypair. Used for on-chain accounts (e.g. the
     * SPL mint account address) that must be unique per tokenized asset.
     */
    public SolanaKeypair generateKeypair() {
        byte[] seed = new byte[SEED_LENGTH];
        secureRandom.nextBytes(seed);
        return fromSeed(seed);
    }

    /**
     * Builds a keypair from a raw 32-byte Ed25519 seed.
     */
    public SolanaKeypair fromSeed(byte[] seed) {
        if (seed.length != SEED_LENGTH) {
            throw new IllegalArgumentException("Solana seed must be 32 bytes");
        }
        EdDSAPrivateKeySpec privateKeySpec = new EdDSAPrivateKeySpec(seed, spec);
        EdDSAPrivateKey privateKey = new EdDSAPrivateKey(privateKeySpec);
        EdDSAPublicKeySpec publicKeySpec = new EdDSAPublicKeySpec(privateKey.getA(), spec);
        EdDSAPublicKey publicKey = new EdDSAPublicKey(publicKeySpec);

        byte[] publicKeyBytes = publicKey.getAbyte().clone();
        return new SolanaKeypair(publicKeyBytes, privateKey);
    }

    /**
     * Signs an arbitrary-length message (the serialized Solana transaction
     * message) with the keypair.
     *
     * @return 64-byte Ed25519 signature
     */
    public byte[] sign(byte[] message, SolanaKeypair keypair) {
        if (message.length == 0) {
            throw new IllegalArgumentException("Solana signature message must not be empty");
        }
        try {
            EdDSAPrivateKey privateKey = keypair.getPrivateKey();
            EdDSAEngine signer = new EdDSAEngine();
            signer.initSign(privateKey);
            signer.update(message);
            return signer.sign();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign Solana transaction message", ex);
        }
    }

    /**
     * Creates a deterministic 32-byte seed from a UTF-8 string. Used to derive
     * test keypairs inline without committing real secret keys.
     */
    public byte[] deriveSeed(String material) {
        byte[] bytes = material.getBytes(StandardCharsets.UTF_8);
        byte[] seed = new byte[SEED_LENGTH];
        Arrays.fill(seed, (byte) 0);
        for (int i = 0; i < bytes.length; i++) {
            seed[i % SEED_LENGTH] ^= bytes[i];
        }
        return seed;
    }
}