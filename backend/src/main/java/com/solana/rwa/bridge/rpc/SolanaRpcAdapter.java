package com.solana.rwa.bridge.rpc;

import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.rpc.dto.AccountInfo;
import com.solana.rwa.bridge.rpc.dto.AccountInfoResult;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhashResult;
import com.solana.rwa.bridge.rpc.dto.RpcEnvelope;
import com.solana.rwa.bridge.rpc.dto.TokenAccountBalance;
import com.solana.rwa.bridge.rpc.dto.TokenAccountBalanceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thin, resilient JSON-RPC client for the Solana Devnet node.
 *
 * <p>Reads the endpoint from {@code solana.rpc.url} ({@code SOLANA_DEVNET_RPC_URL}
 * env var). Every RPC interaction is wrapped in try-catch and translated into a
 * {@link SolanaRpcException} so callers can fail closed: a timeout, HTTP error,
 * malformed envelope, or JSON-RPC error payload NEVER silently yields a green
 * compliance decision.
 */
@Slf4j
@Service
public class SolanaRpcAdapter {

    private static final String JSONRPC_VERSION = "2.0";

    /**
     * Fallback rent-exempt minimum balance (lamports) for an 82-byte SPL Token
     * mint account, used when the live node cannot be queried.
     */
    public static final long DEFAULT_MINT_RENT_EXEMPTION = 1_461_600L;

    private final RestClient restClient;
    private final String rpcUrl;
    private final AtomicLong requestId = new AtomicLong(1);

    public SolanaRpcAdapter(RestClient.Builder restClientBuilder,
                            @Value("${solana.rpc.url}") String rpcUrl) {
        this.restClient = restClientBuilder.build();
        this.rpcUrl = rpcUrl;
    }

    /**
     * Queries live Devnet state for a wallet address.
     *
     * @param walletAddress base58 wallet public key
     * @return parsed account info; {@code exists()} is {@code false} when the
     *         account has not been created on-chain
     * @throws SolanaRpcException on network failure, HTTP error, or malformed/error response
     */
    public AccountInfo getAccountInfo(String walletAddress) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("jsonrpc", JSONRPC_VERSION);
        params.put("method", "getAccountInfo");
        params.put("id", requestId.getAndIncrement());
        params.put("params", List.of(walletAddress, Map.of("encoding", "base64")));

        RpcEnvelope<AccountInfoResult> envelope = call(
                "getAccountInfo", params, new ParameterizedTypeReference<>() {
                });
        if (envelope.hasError()) {
            throw new SolanaRpcException("Solana RPC call 'getAccountInfo' failed: JSON-RPC error "
                    + envelope.error().code() + " (" + envelope.error().message() + ")");
        }
        return envelope.result().valueOrAbsent();
    }

    /**
     * Queries the current Devnet recent blockhash and last valid block height.
     *
     * @return parsed recent blockhash to be embedded into a transaction message
     * @throws SolanaRpcException on network failure, HTTP error, or malformed/error response
     */
    public LatestBlockhash getLatestBlockhash() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("jsonrpc", JSONRPC_VERSION);
        params.put("method", "getLatestBlockhash");
        params.put("id", requestId.getAndIncrement());
        params.put("params", List.of(Map.of("commitment", "confirmed")));

        RpcEnvelope<LatestBlockhashResult> envelope = call(
                "getLatestBlockhash", params, new ParameterizedTypeReference<>() {
                });
        if (envelope.hasError()) {
            throw new SolanaRpcException("Solana RPC call 'getLatestBlockhash' failed: JSON-RPC error "
                    + envelope.error().code() + " (" + envelope.error().message() + ")");
        }
        if (envelope.result() == null || envelope.result().value() == null) {
            throw new SolanaRpcException("Solana RPC call 'getLatestBlockhash' failed: node returned a null result");
        }
        return envelope.result().value();
    }

    /**
     * Queries the rent-exempt minimum balance (lamports) for an account of a
     * given data length.
     *
     * <p>Unlike other adapters this call does not fail closed on an RPC error:
     * the rent schedule is a network-wide constant, so an unreachable node or a
     * malformed/error response falls back to the default for an 82-byte SPL
     * Token mint ({@link #DEFAULT_MINT_RENT_EXEMPTION}).
     *
     * @param dataLength account space in bytes
     * @return rent-exempt minimum balance in lamports
     */
    public long getMinimumBalanceForRentExemption(long dataLength) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("jsonrpc", JSONRPC_VERSION);
        params.put("method", "getMinimumBalanceForRentExemption");
        params.put("id", requestId.getAndIncrement());
        params.put("params", List.of(dataLength, Map.of("commitment", "confirmed")));

        try {
            RpcEnvelope<Long> envelope = call(
                    "getMinimumBalanceForRentExemption",
                    params,
                    new ParameterizedTypeReference<>() {
                    });
            if (!envelope.hasError() && envelope.result() != null) {
                return envelope.result();
            }
            log.warn("Solana RPC 'getMinimumBalanceForRentExemption' returned no usable value; "
                    + "falling back to {} lamports", DEFAULT_MINT_RENT_EXEMPTION);
        } catch (SolanaRpcException ex) {
            log.warn("Solana RPC 'getMinimumBalanceForRentExemption' unavailable ({}); "
                    + "falling back to {} lamports", ex.getMessage(), DEFAULT_MINT_RENT_EXEMPTION);
        }
        return DEFAULT_MINT_RENT_EXEMPTION;
    }

    /**
     * Submits a fully-signed base64 transaction to the Devnet node.
     *
     * @param base64Tx base64-encoded serialized signed transaction payload
     * @return base58 transaction signature confirmed accepted by the node
     * @throws SolanaRpcException on network failure, HTTP error, or malformed/error response
     */
    public String sendTransaction(String base64Tx) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("jsonrpc", JSONRPC_VERSION);
        params.put("method", "sendTransaction");
        params.put("id", requestId.getAndIncrement());
        params.put("params", List.of(
                base64Tx,
                Map.of("encoding", "base64", "preflightCommitment", "confirmed", "skipPreflight", false)));

        RpcEnvelope<String> envelope = call(
                "sendTransaction", params, new ParameterizedTypeReference<>() {
                });
        if (envelope.hasError()) {
            throw new SolanaRpcException("Solana RPC call 'sendTransaction' failed: JSON-RPC error "
                    + envelope.error().code() + " (" + envelope.error().message() + ")");
        }
        if (envelope.result() == null || envelope.result().isBlank()) {
            throw new SolanaRpcException("Solana RPC call 'sendTransaction' failed: node returned a null signature");
        }
        return envelope.result();
    }

    /**
     * Queries live Devnet state for an SPL token account balance.
     *
     * @param tokenAccountAddress base58 token account public key
     * @return parsed token balance (amount, decimals, UI string)
     * @throws SolanaRpcException when the token account does not exist, or on any
     *         network/HTTP/JSON-RPC failure
     */
    public TokenAccountBalance getTokenAccountBalance(String tokenAccountAddress) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("jsonrpc", JSONRPC_VERSION);
        params.put("method", "getTokenAccountBalance");
        params.put("id", requestId.getAndIncrement());
        params.put("params", List.of(tokenAccountAddress));

        RpcEnvelope<TokenAccountBalanceResult> envelope = call(
                "getTokenAccountBalance", params, new ParameterizedTypeReference<>() {
                });
        if (envelope.hasError()) {
            throw new SolanaRpcException("Solana RPC call 'getTokenAccountBalance' failed: JSON-RPC error "
                    + envelope.error().code() + " (" + envelope.error().message() + ")");
        }
        if (envelope.result() == null || envelope.result().value() == null) {
            throw new SolanaRpcException("Solana RPC call 'getTokenAccountBalance' failed: "
                    + "token account " + tokenAccountAddress + " does not exist");
        }
        return envelope.result().value();
    }

    private <T> RpcEnvelope<T> call(String method, Object payload,
                                    ParameterizedTypeReference<RpcEnvelope<T>> typeRef) {
        try {
            RpcEnvelope<T> envelope = restClient.post()
                    .uri(rpcUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(typeRef);

            if (envelope == null) {
                throw new SolanaRpcException("Solana RPC call '" + method
                        + "' failed: node returned a null or malformed response");
            }
            return envelope;
        } catch (ResourceAccessException ex) {
            throw new SolanaRpcException(method, "Solana node unreachable or timed out", ex);
        } catch (RestClientResponseException ex) {
            throw new SolanaRpcException(method,
                    "HTTP error from Solana RPC node (status " + ex.getStatusCode().value() + ")", ex);
        }
    }
}
