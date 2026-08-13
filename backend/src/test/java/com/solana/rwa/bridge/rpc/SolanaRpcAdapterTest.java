package com.solana.rwa.bridge.rpc;

import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.rpc.dto.AccountInfo;
import com.solana.rwa.bridge.rpc.dto.AccountInfoResult;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhashResult;
import com.solana.rwa.bridge.rpc.dto.RpcContext;
import com.solana.rwa.bridge.rpc.dto.RpcEnvelope;
import com.solana.rwa.bridge.rpc.dto.RpcError;
import com.solana.rwa.bridge.rpc.dto.TokenAccountBalance;
import com.solana.rwa.bridge.rpc.dto.TokenAccountBalanceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;


import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Pure Mockito unit tests for {@link SolanaRpcAdapter}.
 *
 * <p>The {@link RestClient} fluent HTTP chain is fully mocked, so no live
 * Devnet RPC traffic is ever attempted during the build. Covers successful
 * JSON-RPC response parsing, absent accounts, JSON-RPC error payloads,
 * HTTP error statuses, and network timeouts.
 */
@ExtendWith(MockitoExtension.class)
class SolanaRpcAdapterTest {

    private static final String RPC_URL = "https://api.devnet.solana.com";
    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";
    private static final String TOKEN_ACCOUNT = "2mN7kqwQ1dPt1qFYGm2Y7yGxVcX9n8zL4v5Wm6pQq7rS8tU";
    private static final String SYSTEM_OWNER = "11111111111111111111111111111111";

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec uriSpec;

    @Mock
    private RestClient.RequestBodySpec bodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private SolanaRpcAdapter adapter;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        adapter = new SolanaRpcAdapter(restClientBuilder, RPC_URL);
    }

    /**
     * Stubs the RestClient fluent chain up to (but excluding) {@code retrieve()}
     * so tests can independently stub the response or an error on retrieve.
     */
    private void stubChain() {
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        doReturn(bodySpec).when(bodySpec).contentType(MediaType.APPLICATION_JSON);
        // NOTE: explicitly bind to the body(Object) overload. Using a bare
        // any() would bind to body(MultiValueMap), which the adapter never calls.
        doReturn(bodySpec).when(bodySpec).body(any(Object.class));
    }



    // ------------------------------------------------------------------
    // getLatestBlockhash
    // ------------------------------------------------------------------

    @Test
    void getLatestBlockhash_returnsParsedBlockhash() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        RpcEnvelope<LatestBlockhashResult> envelope = new RpcEnvelope<>(
                "2.0",
                new LatestBlockhashResult(new RpcContext(42L),
                        new LatestBlockhash("6xPfXFhpREB8cWcGVFr9Eevf8K6sL1yV3z7pMggNfcUx", 999L)),
                null, 1L);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(envelope);

        LatestBlockhash blockhash = adapter.getLatestBlockhash();

        assertThat(blockhash.blockhash()).isEqualTo("6xPfXFhpREB8cWcGVFr9Eevf8K6sL1yV3z7pMggNfcUx");
        assertThat(blockhash.lastValidBlockHeight()).isEqualTo(999L);

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec).body(bodyCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) bodyCaptor.getValue();
        assertThat(payload).containsEntry("method", "getLatestBlockhash");
    }

    @Test
    void getLatestBlockhash_throwsSolanaRpcExceptionOnNullResult() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        RpcEnvelope<LatestBlockhashResult> envelope = new RpcEnvelope<>(
                "2.0", new LatestBlockhashResult(new RpcContext(1L), null), null, 1L);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(envelope);

        assertThatThrownBy(() -> adapter.getLatestBlockhash())
                .isInstanceOf(SolanaRpcException.class)
                .hasMessageContaining("getLatestBlockhash");
    }

    // ------------------------------------------------------------------
    // sendTransaction
    // ------------------------------------------------------------------

    @Test
    void sendTransaction_returnsTransactionSignature() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        RpcEnvelope<String> envelope = new RpcEnvelope<>(
                "2.0", "4xSgnSignatureabcdefghijkmnopqrstuvwxyz", null, 1L);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(envelope);

        String signature = adapter.sendTransaction("base58tx");

        assertThat(signature).isEqualTo("4xSgnSignatureabcdefghijkmnopqrstuvwxyz");

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec).body(bodyCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) bodyCaptor.getValue();
        assertThat(payload).containsEntry("method", "sendTransaction");
    }

    @Test
    void sendTransaction_throwsSolanaRpcExceptionOnNullSignature() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        RpcEnvelope<String> envelope = new RpcEnvelope<>("2.0", null, null, 1L);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(envelope);

        assertThatThrownBy(() -> adapter.sendTransaction("base58tx"))
                .isInstanceOf(SolanaRpcException.class)
                .hasMessageContaining("sendTransaction");
    }

    // ------------------------------------------------------------------
    // getAccountInfo
    // ------------------------------------------------------------------

    @Test
    void getAccountInfo_returnsAccountInfoWhenAccountExists() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        RpcEnvelope<AccountInfoResult> envelope = new RpcEnvelope<>(
                "2.0",
                new AccountInfoResult(new RpcContext(12345L),
                        new AccountInfo(SYSTEM_OWNER, 1_000_000_000L, false, 80L)),
                null, 1L);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(envelope);

        AccountInfo account = adapter.getAccountInfo(WALLET);

        assertThat(account.exists()).isTrue();
        assertThat(account.owner()).isEqualTo(SYSTEM_OWNER);
        assertThat(account.lamports()).isEqualTo(1_000_000_000L);
        assertThat(account.executable()).isFalse();
        assertThat(account.space()).isEqualTo(80L);

        // Verify the JSON-RPC payload is well-formed.
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec).body(bodyCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) bodyCaptor.getValue();
        assertThat(payload).containsEntry("jsonrpc", "2.0")
                .containsEntry("method", "getAccountInfo")
                .containsEntry("id", 1L);
        assertThat(payload.get("params")).asList()
                .hasSize(2)
                .contains(WALLET);
    }

    @Test
    void getAccountInfo_returnsAbsentAccountWhenWalletDoesNotExist() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        RpcEnvelope<AccountInfoResult> envelope = new RpcEnvelope<>(
                "2.0",
                new AccountInfoResult(new RpcContext(10L), null),
                null, 1L);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(envelope);

        AccountInfo account = adapter.getAccountInfo(WALLET);

        assertThat(account.exists()).isFalse();
        assertThat(account.owner()).isNull();
        assertThat(account.lamports()).isZero();
    }

    @Test
    void getAccountInfo_throwsSolanaRpcExceptionOnRpcErrorPayload() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        RpcEnvelope<AccountInfoResult> envelope = new RpcEnvelope<>(
                "2.0", null, new RpcError(-32602, "Invalid params: invalid account"), 1L);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(envelope);

        assertThatThrownBy(() -> adapter.getAccountInfo(WALLET))
                .isInstanceOf(SolanaRpcException.class)
                .hasMessageContaining("getAccountInfo")
                .hasMessageContaining("-32602");
    }

    @Test
    void getAccountInfo_throwsSolanaRpcExceptionOnNullResponse() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(null);

        assertThatThrownBy(() -> adapter.getAccountInfo(WALLET))
                .isInstanceOf(SolanaRpcException.class)
                .hasMessageContaining("getAccountInfo");
    }

    @Test
    void getAccountInfo_throwsSolanaRpcExceptionOnNetworkTimeout() {
        stubChain();
        when(bodySpec.retrieve()).thenThrow(new ResourceAccessException("Read timed out"));

        assertThatThrownBy(() -> adapter.getAccountInfo(WALLET))
                .isInstanceOf(SolanaRpcException.class)
                .hasMessageContaining("unreachable or timed out");
    }

    @Test
    void getAccountInfo_throwsSolanaRpcExceptionOnHttpErrorStatus() {
        stubChain();
        when(bodySpec.retrieve())
                .thenThrow(new HttpServerErrorException(HttpStatusCode.valueOf(502), "Bad Gateway"));

        assertThatThrownBy(() -> adapter.getAccountInfo(WALLET))
                .isInstanceOf(SolanaRpcException.class)
                .hasMessageContaining("HTTP error");
    }

    // ------------------------------------------------------------------
    // getTokenAccountBalance
    // ------------------------------------------------------------------

    @Test
    void getTokenAccountBalance_returnsParsedBalanceWhenTokenAccountExists() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        RpcEnvelope<TokenAccountBalanceResult> envelope = new RpcEnvelope<>(
                "2.0",
                new TokenAccountBalanceResult(new RpcContext(999L),
                        new TokenAccountBalance("125000000", 6, "125.000000")),
                null, 1L);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(envelope);

        TokenAccountBalance balance = adapter.getTokenAccountBalance(TOKEN_ACCOUNT);

        assertThat(balance.amount()).isEqualTo("125000000");
        assertThat(balance.decimals()).isEqualTo(6);
        assertThat(balance.uiAmountString()).isEqualTo("125.000000");

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec).body(bodyCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) bodyCaptor.getValue();
        assertThat(payload).containsEntry("jsonrpc", "2.0")
                .containsEntry("method", "getTokenAccountBalance");
        assertThat(payload.get("params")).asList().contains(TOKEN_ACCOUNT);
    }

    @Test
    void getTokenAccountBalance_throwsSolanaRpcExceptionWhenTokenAccountMissing() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        RpcEnvelope<TokenAccountBalanceResult> envelope = new RpcEnvelope<>(
                "2.0",
                new TokenAccountBalanceResult(new RpcContext(1L), null),
                null, 1L);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(envelope);

        assertThatThrownBy(() -> adapter.getTokenAccountBalance(TOKEN_ACCOUNT))
                .isInstanceOf(SolanaRpcException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void getTokenAccountBalance_throwsSolanaRpcExceptionOnRpcErrorPayload() {
        stubChain();
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        RpcEnvelope<TokenAccountBalanceResult> envelope = new RpcEnvelope<>(
                "2.0", null, new RpcError(-32600, "Invalid request"), 1L);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(envelope);

        assertThatThrownBy(() -> adapter.getTokenAccountBalance(TOKEN_ACCOUNT))
                .isInstanceOf(SolanaRpcException.class)
                .hasMessageContaining("getTokenAccountBalance");
    }
}
