package com.solana.rwa.bridge.solana;

import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline unit test for {@link SolanaMintService}. The {@link SolanaRpcAdapter}
 * is mocked so no live Devnet traffic occurs, while the real keypair service
 * and transaction serializer verify the full sign-and-submit pipeline locally.
 */
@ExtendWith(MockitoExtension.class)
class SolanaMintServiceTest {

    private static final String MINT_BLOCKHASH = "11111111111111111111111111111111";

    @Mock
    private SolanaRpcAdapter rpcAdapter;

    private SolanaKeypairService keypairService;
    private SolanaTransactionSerializer transactionSerializer;
    private SolanaMintService mintService;

    @BeforeEach
    void setUp() {
        keypairService = new SolanaKeypairService("");
        transactionSerializer = new SolanaTransactionSerializer(keypairService);
        mintService = new SolanaMintService(rpcAdapter, keypairService, transactionSerializer);
    }

    @Test
    void createMint_returnsBase58MintAddressAndSubmitsSignedTransaction() {
        when(rpcAdapter.getLatestBlockhash())
                .thenReturn(new LatestBlockhash(MINT_BLOCKHASH, 1234L));
        when(rpcAdapter.sendTransaction(ArgumentMatchers.anyString()))
                .thenReturn("4xSignature");

        String mintAddress = mintService.createMint();

        assertThat(mintAddress)
                .isNotBlank()
                .matches("^[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{32,44}$");

        verify(rpcAdapter).getLatestBlockhash();
        verify(rpcAdapter).sendTransaction(ArgumentMatchers.anyString());
    }

    @Test
    void createMint_submitsBase64EncodedCompactU16WireFormat() {
        when(rpcAdapter.getLatestBlockhash())
                .thenReturn(new LatestBlockhash(MINT_BLOCKHASH, 1234L));
        when(rpcAdapter.sendTransaction(ArgumentMatchers.anyString()))
                .thenReturn("4xSignature");

        mintService.createMint();

        ArgumentCaptor<String> txCaptor = ArgumentCaptor.forClass(String.class);
        verify(rpcAdapter).sendTransaction(txCaptor.capture());

        String encodedTx = txCaptor.getValue();
        // Must be valid standard Base64 (the RPC adapter encodes with base64 now).
        byte[] transaction = Base64.getDecoder().decode(encodedTx);

        // [0] compact-u16 signature count = 2 (payer + mint)
        int[] offset = {0};
        int signatureCount = readCompactU16(transaction, offset);
        assertThat(signatureCount).isEqualTo(2);

        // Skip the Ed25519 signatures (64 bytes each).
        offset[0] += signatureCount * 64;

        // Message header: exactly 3 bytes.
        int numRequiredSignatures = transaction[offset[0]];
        int numReadonlySignedAccounts = transaction[offset[0] + 1];
        int numReadonlyUnsignedAccounts = transaction[offset[0] + 2];
        offset[0] += 3;

        // InitializeMint references the mint (signer + writable), the rent
        // sysvar (readonly), and the token program id (readonly). After sorting,
        // exactly one account is a required signer and two are readonly unsigned.
        assertThat(numRequiredSignatures).isEqualTo(1);
        assertThat(numReadonlySignedAccounts).isZero();
        assertThat(numReadonlyUnsignedAccounts).isEqualTo(2);

        // Account list: compact-u16 length prefix + 32-byte keys.
        int accountCount = readCompactU16(transaction, offset);
        assertThat(accountCount).isEqualTo(3);
        offset[0] += accountCount * 32;

        // Recent blockhash (32 bytes).
        offset[0] += 32;

        // Instruction list: compact-u16 length prefix.
        int instructionCount = readCompactU16(transaction, offset);
        assertThat(instructionCount).isEqualTo(1);

        // One InitializeMint instruction: 1 byte program index,
        // compact-u16 account index list (mint + rent sysvar), compact-u16 data.
        int programIndex = transaction[offset[0]] & 0xFF;
        offset[0] += 1;
        assertThat(programIndex).isEqualTo(2);

        int accountListLength = readCompactU16(transaction, offset);
        assertThat(accountListLength).isEqualTo(2);
        offset[0] += accountListLength;

        int dataLength = readCompactU16(transaction, offset);
        // InitializeMint data: discriminator (1) + decimals (1) + mint authority
        // (32) + freeze-authority COption (1) = 35 bytes.
        assertThat(dataLength).isEqualTo(35);
    }

    @Test
    void createMint_wrapsRpcFailureAsBadRequest() {
        when(rpcAdapter.getLatestBlockhash())
                .thenThrow(new SolanaRpcException("getLatestBlockhash", new RuntimeException("Read timed out")));

        assertThatThrownBy(() -> mintService.createMint())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solana Devnet Mint Error: ")
                .hasMessageContaining("Read timed out")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /**
     * Decodes a Solana compact-u16 (shortvec) length prefix and advances
     * {@code offset[0]} past the consumed bytes.
     */
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