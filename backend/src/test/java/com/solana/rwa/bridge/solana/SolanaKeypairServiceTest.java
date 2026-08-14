package com.solana.rwa.bridge.solana;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the flexible SOLANA_DEVNET_PRIVATE_KEY parsing in
 * {@link SolanaKeypairService}: base58 32-byte seeds, base58 64-byte Phantom
 * exports, JSON byte arrays, quote/whitespace trimming, and invalid lengths.
 */
class SolanaKeypairServiceTest {

    private SolanaKeypairService keypairService;

    @BeforeEach
    void setUp() {
        keypairService = new SolanaKeypairService("");
    }

    @Test
    void parseSecretKeyToSeed_parses32ByteBase58Seed() {
        byte[] seed = deterministicBytes(32, 1);
        String base58 = Base58Codec.encode(seed);

        byte[] parsed = keypairService.parseSecretKeyToSeed(base58);

        assertThat(parsed).isEqualTo(seed);
    }

    @Test
    void parseSecretKeyToSeed_trimsWhitespaceAndQuotes() {
        byte[] seed = deterministicBytes(32, 2);
        String base58 = Base58Codec.encode(seed);
        String wrapped = "  \"" + base58 + "\"  ";

        byte[] parsed = keypairService.parseSecretKeyToSeed(wrapped);

        assertThat(parsed).isEqualTo(seed);
    }

    @Test
    void parseSecretKeyToSeed_parses64BytePhantomBase58ByTruncatingToFirst32Bytes() {
        byte[] secretKey = deterministicBytes(64, 3);
        String base58 = Base58Codec.encode(secretKey);

        byte[] parsed = keypairService.parseSecretKeyToSeed(base58);

        assertThat(parsed).isEqualTo(Arrays.copyOf(secretKey, 32));
    }

    @Test
    void parseSecretKeyToSeed_parsesJsonByteArray() {
        byte[] seed = deterministicBytes(32, 4);
        String json = toJsonArray(seed);

        byte[] parsed = keypairService.parseSecretKeyToSeed(json);

        assertThat(parsed).isEqualTo(seed);
    }

    @Test
    void parseSecretKeyToSeed_parsesJson64ByteArrayToSeed() {
        byte[] secretKey = deterministicBytes(64, 5);
        String json = toJsonArray(secretKey);

        byte[] parsed = keypairService.parseSecretKeyToSeed(json);

        assertThat(parsed).isEqualTo(Arrays.copyOf(secretKey, 32));
    }

    @Test
    void parseSecretKeyToSeed_throwsForUnsupportedLength() {
        String base58 = Base58Codec.encode(deterministicBytes(31, 6));

        assertThatThrownBy(() -> keypairService.parseSecretKeyToSeed(base58))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("found 31 bytes");
    }

    private byte[] deterministicBytes(int length, int start) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) ((start + i) % 256);
        }
        return bytes;
    }

    private String toJsonArray(byte[] bytes) {
        return "[" + IntStream.range(0, bytes.length)
                .mapToObj(i -> String.valueOf(bytes[i] & 0xFF))
                .reduce((a, b) -> a + "," + b)
                .orElse("") + "]";
    }
}