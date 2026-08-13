package com.solana.rwa.bridge.solana;

import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
}