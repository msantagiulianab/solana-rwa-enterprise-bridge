package com.solana.rwa.bridge.solana;

import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Issues the on-chain SPL Token "InitializeMint" instruction on Solana Devnet.
 *
 * <p>Generates a fresh mint account keypair, compiles and signs the transaction,
 * then submits it through the {@link SolanaRpcAdapter}. The resulting base58
 * mint address is returned for persistence against the asset.
 */
@Slf4j
@Service
public class SolanaMintService {

    /**
     * SPL Token program id (the standard token program on all clusters).
     */
    public static final String TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";

    /**
     * Rent sysvar account, required by InitializeMint.
     */
    public static final String RENT_SYSVAR_ID = "SysvarRent111111111111111111111111111111111";

    /**
     * Decimals for minted real-world asset tokens.
     */
    public static final int RWA_TOKEN_DECIMALS = 6;

    private static final int INITIALIZE_MINT_DISCRIMINATOR = 0;

    private final SolanaRpcAdapter rpcAdapter;
    private final SolanaKeypairService keypairService;
    private final SolanaTransactionSerializer transactionSerializer;

    public SolanaMintService(SolanaRpcAdapter rpcAdapter,
                             SolanaKeypairService keypairService,
                             SolanaTransactionSerializer transactionSerializer) {
        this.rpcAdapter = rpcAdapter;
        this.keypairService = keypairService;
        this.transactionSerializer = transactionSerializer;
    }

    /**
     * Creates an SPL Token mint account on Solana Devnet.
     *
     * @return base58 mint address of the newly created token mint
     * @throws SolanaRpcException when the Devnet RPC layer fails
     */
    public String createMint() {
        try {
            SolanaKeypair payer = keypairService.resolveKeypair();
            SolanaKeypair mint = keypairService.generateKeypair();

            byte[] mintPubkey = mint.getPublicKeyBytes();
            byte[] payerPubkey = payer.getPublicKeyBytes();

            SolanaInstruction initializeMint = new SolanaInstruction(
                    Base58Codec.decode(TOKEN_PROGRAM_ID),
                    List.of(
                            new AccountMeta(mintPubkey, true, true),
                            new AccountMeta(Base58Codec.decode(RENT_SYSVAR_ID), false, false)),
                    buildInitializeMintData(RWA_TOKEN_DECIMALS, payerPubkey, false));

            return submitWithBlockhashRetry(initializeMint, List.of(payer, mint), mint.getPublicKeyBase58());
        } catch (Exception ex) {
            log.error("Failed to create SPL Token mint on Devnet", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solana Devnet Mint Error: " + ex.getMessage(), ex);
        }
    }

    /**
     * Serializes, signs, and submits the InitializeMint transaction, retrying up
     * to three attempts when the node reports that the recent blockhash has
     * expired ("Blockhash not found"). Each attempt fetches a fresh blockhash
     * immediately before signing so the transaction is never bound to a stale
     * blockhash.
     */
    private String submitWithBlockhashRetry(SolanaInstruction initializeMint,
                                            List<SolanaKeypair> signers,
                                            String mintAddress) {
        SolanaRpcException lastBlockhashFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                LatestBlockhash latest = rpcAdapter.getLatestBlockhash();
                String signedTransaction = transactionSerializer.serializeAndSign(
                        List.of(initializeMint),
                        latest.blockhash(),
                        signers);

                rpcAdapter.sendTransaction(signedTransaction);
                return mintAddress;
            } catch (SolanaRpcException ex) {
                if (isBlockhashNotFound(ex)) {
                    lastBlockhashFailure = ex;
                    log.warn("Stale blockhash on attempt {}/3 for SPL mint creation; "
                            + "fetching a fresh blockhash and retrying", attempt);
                    continue;
                }
                throw ex;
            }
        }
        log.error("Exhausted blockhash retries while creating SPL Token mint", lastBlockhashFailure);
        throw lastBlockhashFailure;
    }

    private boolean isBlockhashNotFound(SolanaRpcException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("blockhash not found");
    }

    private byte[] buildInitializeMintData(int decimals, byte[] mintAuthority, boolean freezeAuthoritySet) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(INITIALIZE_MINT_DISCRIMINATOR);
        data.write(decimals & 0xFF);
        data.writeBytes(mintAuthority);
        // COption<Pubkey>: 0 = None, 1 = Some followed by 32-byte authority.
        data.write(freezeAuthoritySet ? 1 : 0);
        if (freezeAuthoritySet) {
            data.writeBytes(mintAuthority);
        }
        return data.toByteArray();
    }
}