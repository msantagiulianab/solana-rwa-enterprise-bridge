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
import static org.mockito.Mockito.times;
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
    private static final long MINT_RENT_EXEMPTION = 1_461_600L;

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
        when(rpcAdapter.getMinimumBalanceForRentExemption(82L)).thenReturn(MINT_RENT_EXEMPTION);
        when(rpcAdapter.getLatestBlockhash())
                .thenReturn(new LatestBlockhash(MINT_BLOCKHASH, 1234L));
        when(rpcAdapter.sendTransaction(ArgumentMatchers.anyString()))
                .thenReturn("4xSignature");

        String mintAddress = mintService.createMint();

        assertThat(mintAddress)
                .isNotBlank()
                .matches("^[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{32,44}$");

        verify(rpcAdapter).getMinimumBalanceForRentExemption(82L);
        verify(rpcAdapter).getLatestBlockhash();
        verify(rpcAdapter).sendTransaction(ArgumentMatchers.anyString());
    }

    @Test
    void createMint_submitsAtomicCreateAccountAndInitializeMintWireFormat() {
        when(rpcAdapter.getMinimumBalanceForRentExemption(82L)).thenReturn(MINT_RENT_EXEMPTION);
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
        int numRequiredSignatures = transaction[offset[0]] & 0xFF;
        int numReadonlySignedAccounts = transaction[offset[0] + 1] & 0xFF;
        int numReadonlyUnsignedAccounts = transaction[offset[0] + 2] & 0xFF;
        offset[0] += 3;

        // The fee payer and mint are both writable signers, while the system
        // program, rent sysvar, and token program are readonly unsigned accounts.
        assertThat(numRequiredSignatures).isEqualTo(2);
        assertThat(numReadonlySignedAccounts).isZero();
        assertThat(numReadonlyUnsignedAccounts).isEqualTo(3);

        // Account list: compact-u16 length prefix + 32-byte keys
        // (payer, mint, system program, rent sysvar, token program).
        int accountCount = readCompactU16(transaction, offset);
        assertThat(accountCount).isEqualTo(5);

        byte[][] accountKeys = new byte[accountCount][];
        for (int i = 0; i < accountCount; i++) {
            accountKeys[i] = new byte[32];
            System.arraycopy(transaction, offset[0], accountKeys[i], 0, 32);
            offset[0] += 32;
        }
        assertThat(Base58Codec.encode(accountKeys[2]))
                .isEqualTo(SolanaMintService.SYSTEM_PROGRAM_ID);
        assertThat(Base58Codec.encode(accountKeys[3]))
                .isEqualTo(SolanaMintService.RENT_SYSVAR_ID);
        assertThat(Base58Codec.encode(accountKeys[4]))
                .isEqualTo(SolanaMintService.TOKEN_PROGRAM_ID);

        // Recent blockhash (32 bytes).
        offset[0] += 32;

        // Instruction list: compact-u16 length prefix.
        int instructionCount = readCompactU16(transaction, offset);
        assertThat(instructionCount).isEqualTo(2);

        // Instruction 0: SystemProgram.createAccount.
        int programIndex0 = transaction[offset[0]++] & 0xFF;
        assertThat(programIndex0).isEqualTo(2); // system program

        int accountsLen0 = readCompactU16(transaction, offset);
        assertThat(accountsLen0).isEqualTo(2);
        assertThat(transaction[offset[0]++] & 0xFF).isZero();      // payer -> 0
        assertThat(transaction[offset[0]++] & 0xFF).isEqualTo(1);  // mint  -> 1

        int dataLen0 = readCompactU16(transaction, offset);
        assertThat(dataLen0).isEqualTo(52); // u32 (4) + u64 (8) + u64 (8) + [32] owner
        byte[] data0 = new byte[dataLen0];
        System.arraycopy(transaction, offset[0], data0, 0, dataLen0);
        offset[0] += dataLen0;

        // u32 discriminator = 0 (little-endian).
        assertThat(readU32(data0, 0)).isZero();
        // u64 lamports = rent-exempt minimum (little-endian).
        assertThat(readU64(data0, 4)).isEqualTo(MINT_RENT_EXEMPTION);
        // u64 space = 82 bytes (little-endian).
        assertThat(readU64(data0, 12)).isEqualTo(82L);
        // Owner program id = SPL Token program.
        byte[] owner = new byte[32];
        System.arraycopy(data0, 20, owner, 0, 32);
        assertThat(Base58Codec.encode(owner)).isEqualTo(SolanaMintService.TOKEN_PROGRAM_ID);

        // Instruction 1: TokenProgram.initializeMint.
        int programIndex1 = transaction[offset[0]++] & 0xFF;
        assertThat(programIndex1).isEqualTo(4); // token program

        int accountsLen1 = readCompactU16(transaction, offset);
        assertThat(accountsLen1).isEqualTo(2);
        assertThat(transaction[offset[0]++] & 0xFF).isEqualTo(1);      // mint       -> 1
        assertThat(transaction[offset[0]++] & 0xFF).isEqualTo(3);      // rent sysvar -> 3

        int dataLen1 = readCompactU16(transaction, offset);
        assertThat(dataLen1).isEqualTo(35); // discriminator (1) + decimals (1) + authority (32) + COption (1)
        byte[] data1 = new byte[dataLen1];
        System.arraycopy(transaction, offset[0], data1, 0, dataLen1);
        offset[0] += dataLen1;

        assertThat(data1[0] & 0xFF).isZero();          // InitializeMint discriminator
        assertThat(data1[1] & 0xFF).isEqualTo(6);      // decimals
        assertThat(data1[data1.length - 1] & 0xFF).isZero(); // freeze authority COption::None
    }

    @Test
    void createMint_usesRentExemptionFallbackWhenRpcReturnsDefaultValue() {
        // When the RPC layer is mocked absent, getMinimumBalanceForRentExemption
        // returns its built-in fallback for an 82-byte mint account.
        when(rpcAdapter.getMinimumBalanceForRentExemption(82L))
                .thenReturn(SolanaRpcAdapter.DEFAULT_MINT_RENT_EXEMPTION);
        when(rpcAdapter.getLatestBlockhash())
                .thenReturn(new LatestBlockhash(MINT_BLOCKHASH, 1234L));
        when(rpcAdapter.sendTransaction(ArgumentMatchers.anyString()))
                .thenReturn("4xSignature");

        mintService.createMint();

        ArgumentCaptor<String> txCaptor = ArgumentCaptor.forClass(String.class);
        verify(rpcAdapter).sendTransaction(txCaptor.capture());

        byte[] transaction = Base64.getDecoder().decode(txCaptor.getValue());
        byte[] data0 = readInstructionData(transaction, 0);
        assertThat(readU64(data0, 4))
                .isEqualTo(SolanaRpcAdapter.DEFAULT_MINT_RENT_EXEMPTION);
    }

    @Test
    void createMint_wrapsRpcFailureAsBadRequest() {
        when(rpcAdapter.getMinimumBalanceForRentExemption(82L)).thenReturn(MINT_RENT_EXEMPTION);
        when(rpcAdapter.getLatestBlockhash())
                .thenThrow(new SolanaRpcException("getLatestBlockhash", new RuntimeException("Read timed out")));

        assertThatThrownBy(() -> mintService.createMint())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solana Devnet Mint Error: ")
                .hasMessageContaining("Read timed out")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createMint_retriesOnBlockhashNotFoundThenSucceeds() {
        when(rpcAdapter.getMinimumBalanceForRentExemption(82L)).thenReturn(MINT_RENT_EXEMPTION);
        when(rpcAdapter.getLatestBlockhash())
                .thenReturn(new LatestBlockhash(MINT_BLOCKHASH, 1234L));
        when(rpcAdapter.sendTransaction(ArgumentMatchers.anyString()))
                .thenThrow(new SolanaRpcException(
                        "Solana RPC call 'sendTransaction' failed: JSON-RPC error -32002 "
                                + "(Blockhash not found: maybe the blockhash has expired)"))
                .thenReturn("4xSignature");

        String mintAddress = mintService.createMint();

        assertThat(mintAddress)
                .isNotBlank()
                .matches("^[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{32,44}$");

        verify(rpcAdapter, times(2)).getLatestBlockhash();
        verify(rpcAdapter, times(2)).sendTransaction(ArgumentMatchers.anyString());
    }

    @Test
    void createMint_exhaustsRetriesAfterThreeBlockhashNotFoundFailures() {
        when(rpcAdapter.getMinimumBalanceForRentExemption(82L)).thenReturn(MINT_RENT_EXEMPTION);
        when(rpcAdapter.getLatestBlockhash())
                .thenReturn(new LatestBlockhash(MINT_BLOCKHASH, 1234L));
        when(rpcAdapter.sendTransaction(ArgumentMatchers.anyString()))
                .thenThrow(new SolanaRpcException(
                        "Solana RPC call 'sendTransaction' failed: JSON-RPC error -32002 "
                                + "(Blockhash not found: maybe the blockhash has expired)"));

        assertThatThrownBy(() -> mintService.createMint())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solana Devnet Mint Error: ")
                .hasMessageContaining("Blockhash not found")
                .hasMessageContaining("sendTransaction")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(rpcAdapter, times(3)).getLatestBlockhash();
        verify(rpcAdapter, times(3)).sendTransaction(ArgumentMatchers.anyString());
    }

    /**
     * Navigates to the instruction at {@code index} and returns its raw data
     * bytes. Handles the signature section, message header, account table,
     * blockhash, and preceding instructions.
     */
    private static byte[] readInstructionData(byte[] transaction, int index) {
        int[] offset = {0};
        int signatureCount = readCompactU16(transaction, offset);
        offset[0] += signatureCount * 64;
        offset[0] += 3; // header

        int accountCount = readCompactU16(transaction, offset);
        offset[0] += accountCount * 32;
        offset[0] += 32; // recent blockhash

        int instructionCount = readCompactU16(transaction, offset);
        for (int i = 0; i < instructionCount; i++) {
            offset[0] += 1; // program index
            int accountsLen = readCompactU16(transaction, offset);
            offset[0] += accountsLen;
            int dataLen = readCompactU16(transaction, offset);
            if (i == index) {
                byte[] data = new byte[dataLen];
                System.arraycopy(transaction, offset[0], data, 0, dataLen);
                return data;
            }
            offset[0] += dataLen;
        }
        throw new IllegalArgumentException("Instruction index out of bounds: " + index);
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