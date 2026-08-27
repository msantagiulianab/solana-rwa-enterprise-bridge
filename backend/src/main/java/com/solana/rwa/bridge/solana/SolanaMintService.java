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
     * System program id, required by the CreateAccount instruction.
     */
    public static final String SYSTEM_PROGRAM_ID = "11111111111111111111111111111111";

    /**
     * Rent sysvar account, required by InitializeMint.
     */
    public static final String RENT_SYSVAR_ID = "SysvarRent111111111111111111111111111111111";

    /**
     * Decimals for minted real-world asset tokens.
     */
    public static final int RWA_TOKEN_DECIMALS = 6;

    private static final int INITIALIZE_MINT_DISCRIMINATOR = 0;
    private static final int CREATE_ACCOUNT_DISCRIMINATOR = 0;

    /**
     * Space (bytes) required by a standard SPL Token Mint account.
     */
    public static final int SPL_MINT_SPACE = 82;

    /**
     * Explicit compute-unit cap for the mint transaction.
     */
    public static final int DEFAULT_COMPUTE_UNIT_LIMIT = 10_000;

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
            byte[] tokenProgram = Base58Codec.decode(TOKEN_PROGRAM_ID);

            long rentExemption = rpcAdapter.getMinimumBalanceForRentExemption(SPL_MINT_SPACE);

            // Price the transaction dynamically from the node's recent fee samples.
            // Both the fee payer and the freshly-generated mint account are passed
            // as writable-lock filters; the adapter falls back to the configured
            // baseline when the fee oracle is unavailable.
            long priorityFee = rpcAdapter.getRecentPrioritizationFees(
                    List.of(payer.getPublicKeyBase58(), mint.getPublicKeyBase58()));

            // Instruction 0: ComputeBudget.setComputeUnitPrice — set the dynamic
            // priority fee (micro-lamports per compute unit).
            SolanaInstruction setComputeUnitPrice =
                    ComputeBudgetInstruction.setComputeUnitPrice(priorityFee);

            // Instruction 1: ComputeBudget.setComputeUnitLimit — cap compute units
            // so the transaction never consumes more than the mint workflow needs.
            SolanaInstruction setComputeUnitLimit =
                    ComputeBudgetInstruction.setComputeUnitLimit(DEFAULT_COMPUTE_UNIT_LIMIT);

            // Instruction 2: SystemProgram.createAccount — allocate+assign the
            // rent-exempt mint account owned by the SPL Token program.
            SolanaInstruction createAccount = new SolanaInstruction(
                    Base58Codec.decode(SYSTEM_PROGRAM_ID),
                    List.of(
                            new AccountMeta(payerPubkey, true, true),
                            new AccountMeta(mintPubkey, true, true)),
                    buildCreateAccountData(rentExemption, tokenProgram));

            // Instruction 3: TokenProgram.initializeMint — initialize the freshly
            // created account as an SPL Token mint.
            SolanaInstruction initializeMint = new SolanaInstruction(
                    tokenProgram,
                    List.of(
                            new AccountMeta(mintPubkey, true, true),
                            new AccountMeta(Base58Codec.decode(RENT_SYSVAR_ID), false, false)),
                    buildInitializeMintData(RWA_TOKEN_DECIMALS, payerPubkey, false));

            return submitWithBlockhashRetry(
                    List.of(setComputeUnitPrice, setComputeUnitLimit, createAccount, initializeMint),
                    List.of(payer, mint), mint.getPublicKeyBase58());
        } catch (Exception ex) {
            log.error("Failed to create SPL Token mint on Devnet", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solana Devnet Mint Error: " + ex.getMessage(), ex);
        }
    }

    /**
     * Serializes, signs, and submits the mint creation transaction, retrying up
     * to three attempts when the node reports that the recent blockhash has
     * expired ("Blockhash not found"). Each attempt fetches a fresh blockhash
     * immediately before signing so the transaction is never bound to a stale
     * blockhash.
     */
    private String submitWithBlockhashRetry(List<SolanaInstruction> instructions,
                                            List<SolanaKeypair> signers,
                                            String mintAddress) {
        SolanaRpcException lastBlockhashFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                LatestBlockhash latest = rpcAdapter.getLatestBlockhash();
                String signedTransaction = transactionSerializer.serializeAndSign(
                        instructions,
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

    private byte[] buildCreateAccountData(long lamports, byte[] ownerProgramId) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(CREATE_ACCOUNT_DISCRIMINATOR); // u32 (4 bytes) little-endian
        data.write(0);
        data.write(0);
        data.write(0);
        writeU64(data, lamports);   // u64 lamports
        writeU64(data, SPL_MINT_SPACE); // u64 space
        data.writeBytes(ownerProgramId); // [32]byte owner
        return data.toByteArray();
    }

    private void writeU64(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) (value & 0xFF));
            value >>= 8;
        }
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