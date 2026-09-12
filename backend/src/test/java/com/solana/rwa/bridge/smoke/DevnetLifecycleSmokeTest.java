package com.solana.rwa.bridge.smoke;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.dto.TokenTransferRequest;
import com.solana.rwa.bridge.dto.TokenTransferResult;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.entity.TransferHookAuditLog;
import com.solana.rwa.bridge.entity.TransferHookAuditStatus;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.repository.AssetTokenRepository;
import com.solana.rwa.bridge.repository.InvestorRepository;
import com.solana.rwa.bridge.repository.TransferHookAuditLogRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.AccountInfo;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import com.solana.rwa.bridge.rpc.dto.SignatureStatusResult;
import com.solana.rwa.bridge.rpc.dto.TokenAccountBalance;
import com.solana.rwa.bridge.service.TokenService;
import com.solana.rwa.bridge.service.TokenTransferService;
import com.solana.rwa.bridge.solana.AccountMeta;
import com.solana.rwa.bridge.solana.Base58Codec;
import com.solana.rwa.bridge.solana.SolanaInstruction;
import com.solana.rwa.bridge.solana.SolanaKeypair;
import com.solana.rwa.bridge.solana.SolanaKeypairService;
import com.solana.rwa.bridge.solana.SolanaMintService;
import com.solana.rwa.bridge.solana.SolanaPdaUtil;
import com.solana.rwa.bridge.solana.SolanaTransactionSerializer;
import com.solana.rwa.bridge.solana.Token2022Program;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live lifecycle smoke test executed against the real Solana Devnet RPC.
 *
 * <p>This suite intentionally makes <em>live</em> JSON-RPC calls to
 * {@code api.devnet.solana.com} (or the configured {@code SOLANA_DEVNET_RPC_URL})
 * and is therefore guarded by
 * {@code @EnabledIfEnvironmentVariable(named = "RUN_DEVNET_SMOKE_TESTS", matches = "true")}.
 * During normal offline CI builds the environment variable is unset, so the entire
 * class — Spring context included — is skipped and zero network bytes are emitted.
 *
 * <p><strong>Operator pre-requisites:</strong> a funded Devnet keypair must be
 * supplied via {@code SOLANA_DEVNET_PRIVATE_KEY} (raw 32-byte base58 seed, Phantom
 * 64-byte base58 export, or a Solana CLI JSON byte array). Fund the derived fee-payer
 * at the Devnet faucet, then run:
 *
 * <pre>{@code
 * RUN_DEVNET_SMOKE_TESTS=true ./mvnw test -Dtest=DevnetLifecycleSmokeTest
 * }</pre>
 *
 * <p><strong>Phase 1 flow verified here:</strong>
 * <ol>
 *   <li>Onboard a KYC-{@code VERIFIED} investor whose wallet is the live fee payer.</li>
 *   <li>Register an off-chain asset and execute a live Token-2022 mint via
 *       {@link TokenService} (which delegates to {@link SolanaMintService}); the
 *       mint is asserted to exist on-chain and be owned by the Token-2022 program.</li>
 *   <li>Stage the transfer prerequisites on Devnet (associated token accounts +
 *       minted supply), then execute a compliant transfer via
 *       {@link TokenTransferService} and assert the broadcast signature is confirmed
 *       on-chain with no execution error and funds actually move.</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "RUN_DEVNET_SMOKE_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "solana.transfer-hook.program-id="
        + Token2022Program.TOKEN_2022_PROGRAM_ID)
class DevnetLifecycleSmokeTest {

    private static final int MINT_TO_DISCRIMINATOR = 7;
    private static final int ATA_CREATE_DISCRIMINATOR = 0;

    private static final long MINTED_SUPPLY = 1_000_000L; // 1.0 token (6 decimals)
    private static final long TRANSFER_AMOUNT = 250_000L; // 0.25 tokens

    private static final int FINALITY_POLL_SECONDS = 30;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private TokenTransferService tokenTransferService;

    @Autowired
    private SolanaKeypairService keypairService;

    @Autowired
    private SolanaTransactionSerializer transactionSerializer;

    @Autowired
    private SolanaRpcAdapter rpcAdapter;

    @Autowired
    private InvestorRepository investorRepository;

    @Autowired
    private AssetTokenRepository assetTokenRepository;

    @Autowired
    private TransferHookAuditLogRepository transferHookAuditLogRepository;

    @Test
    void fullDevnetLifecycle_registerMintAndClearedTransfer_verifiesLiveSignatures()
            throws Exception {
        requireFundedDevnetKey();

        // Isolate the off-chain ledger (H2) for a deterministic audit assertion.
        transferHookAuditLogRepository.deleteAll();
        assetTokenRepository.deleteAll();
        investorRepository.deleteAll();

        SolanaKeypair payer = keypairService.resolveKeypair();
        SolanaKeypair recipient = deriveRecipient();

        // Step 1: onboard the fee payer as a KYC-verified investor so the
        // TokenService fail-closed pre-flight gate passes.
        investorRepository.save(Investor.builder()
                .fullName("Devnet Smoke Issuer")
                .email("issuer@devnet.smoke.test")
                .walletAddress(payer.getPublicKeyBase58())
                .kycStatus(KycStatus.VERIFIED)
                .country("US")
                .build());

        // Step 2: register an off-chain asset and execute the live Token-2022
        // mint through the production TokenService/SolanaMintService path.
        AssetToken asset = tokenService.create(AssetTokenRegistrationRequest.builder()
                .assetName("Devnet Lifecycle Smoke Asset")
                .valuationUsd(new BigDecimal("1000000.00"))
                .issuerWalletAddress(payer.getPublicKeyBase58())
                .idempotencyKey("devnet-smoke-mint-" + System.currentTimeMillis())
                .build());

        assertThat(asset.getMintAddress()).isNotBlank();
        assertThat(asset.getSettlementStatus()).isEqualTo(SettlementStatus.CONFIRMED);

        String mintAddress = asset.getMintAddress();
        AccountInfo mintInfo = awaitAccount(mintAddress);
        assertThat(mintInfo.exists()).as("mint %s must exist on-chain", mintAddress).isTrue();
        assertThat(mintInfo.owner()).as("mint must be owned by the Token-2022 program")
                .isEqualTo(Token2022Program.TOKEN_2022_PROGRAM_ID);

        // Step 3: stage the transfer prerequisites on Devnet (associated token
        // accounts + minted supply) so the compliant TransferChecked can settle.
        byte[] mint = Base58Codec.decode(mintAddress);
        byte[] payerPubkey = payer.getPublicKeyBytes();
        byte[] recipientPubkey = recipient.getPublicKeyBytes();

        String sourceAta = associatedTokenAddress(payerPubkey, mint);
        String destinationAta = associatedTokenAddress(recipientPubkey, mint);

        submit(List.of(
                        createAssociatedTokenAccount(payerPubkey, Base58Codec.decode(sourceAta),
                                payerPubkey, mint),
                        createAssociatedTokenAccount(payerPubkey, Base58Codec.decode(destinationAta),
                                recipientPubkey, mint)),
                List.of(payer));

        submit(List.of(buildMintTo(mint, Base58Codec.decode(sourceAta), payerPubkey,
                        MINTED_SUPPLY)),
                List.of(payer));

        TokenAccountBalance sourceBalance = awaitTokenBalance(sourceAta);
        assertThat(Long.parseLong(sourceBalance.amount()))
                .as("source associated token account must hold the minted supply")
                .isEqualTo(MINTED_SUPPLY);

        // Step 4: execute the compliant transfer through the production
        // TokenTransferService path and verify the live Devnet signature.
        TokenTransferResult transfer = tokenTransferService.transfer(new TokenTransferRequest(
                payer.getPublicKeyBase58(),
                recipient.getPublicKeyBase58(),
                sourceAta,
                destinationAta,
                mintAddress,
                TRANSFER_AMOUNT));

        assertThat(transfer.signature()).isNotBlank();
        assertThat(transfer.complianceStatus()).isEqualTo("APPROVED");

        SignatureStatusResult status = awaitConfirmation(transfer.signature());
        assertThat(status.hasError())
                .as("transfer %s must settle without an on-chain error (err=%s)",
                        transfer.signature(), status.err())
                .isFalse();
        assertThat(status.isConfirmed() || status.isFinalized())
                .as("transfer %s must reach confirmed/finalized commitment", transfer.signature())
                .isTrue();

        TokenAccountBalance destinationBalance = awaitTokenBalance(destinationAta);
        assertThat(Long.parseLong(destinationBalance.amount()))
                .as("destination associated token account must receive the transferred amount")
                .isEqualTo(TRANSFER_AMOUNT);

        // The cleared transfer must leave a durable CLEARED audit row carrying
        // the real on-chain signature.
        List<TransferHookAuditLog> logs = transferHookAuditLogRepository.findByMintAddress(mintAddress);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getComplianceStatus()).isEqualTo(TransferHookAuditStatus.CLEARED);
        assertThat(logs.get(0).getTransactionSignature()).isEqualTo(transfer.signature());
    }

    // ---------------------------------------------------------------------
    // On-chain staging helpers (associated token account creation + mint-to)
    // ---------------------------------------------------------------------

    private String associatedTokenAddress(byte[] owner, byte[] mint) {
        // A Token-2022 associated token account is derived with the Token-2022
        // program id (the account owner) as the PDA seed — never the legacy
        // SPL Token program (Tokenkeg...). Both program ids are locked down as
        // literal base58 strings so no legacy constant can bleed into the PDA.
        return Base58Codec.encode(SolanaPdaUtil.findProgramAddress(
                        List.of(owner,
                                Base58Codec.decode("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"),
                                mint),
                        Base58Codec.decode("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"))
                .address());
    }

    private SolanaInstruction createAssociatedTokenAccount(byte[] funder, byte[] ata,
                                                           byte[] owner, byte[] mint) {
        // Program id is the ATA program and account 5 is the token program that
        // will OWN the created account. For a Token-2022 mint the token program
        // MUST be Token-2022 (Tokenz...), never legacy SPL Token (Tokenkeg...),
        // or the ATA program rejects the instruction with `IncorrectProgramId`.
        // Both are hardcoded as literal base58 strings to lock the program ids.
        return new SolanaInstruction(
                Base58Codec.decode("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"),
                List.of(
                        new AccountMeta(funder, true, true),
                        new AccountMeta(ata, false, true),
                        new AccountMeta(owner, false, false),
                        new AccountMeta(mint, false, false),
                        new AccountMeta(Base58Codec.decode(SolanaMintService.SYSTEM_PROGRAM_ID), false, false),
                        new AccountMeta(Base58Codec.decode("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"), false, false)),
                new byte[]{ATA_CREATE_DISCRIMINATOR});
    }

    private SolanaInstruction buildMintTo(byte[] mint, byte[] destination, byte[] authority,
                                          long amount) {
        // MintTo must target the Token-2022 program id (Tokenz...), not the legacy
        // SPL Token program (Tokenkeg...), because the mint is a Token-2022 mint.
        // The program id is hardcoded as a literal base58 string to lock it down.
        return new SolanaInstruction(
                Base58Codec.decode("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"),
                List.of(
                        new AccountMeta(mint, false, true),
                        new AccountMeta(destination, false, true),
                        new AccountMeta(authority, true, false)),
                buildMintToData(amount));
    }

    private byte[] buildMintToData(long amount) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(MINT_TO_DISCRIMINATOR);
        writeU64(data, amount);
        return data.toByteArray();
    }

    private void writeU64(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) (value & 0xFF));
            value >>= 8;
        }
    }

    private String submit(List<SolanaInstruction> instructions, List<SolanaKeypair> signers) {
        LatestBlockhash blockhash = rpcAdapter.getLatestBlockhash();
        String signedTransaction = transactionSerializer.serializeAndSign(
                instructions, blockhash.blockhash(), signers);
        return rpcAdapter.sendTransaction(signedTransaction);
    }

    // ---------------------------------------------------------------------
    // Preconditions & confirmation polling
    // ---------------------------------------------------------------------

    private void requireFundedDevnetKey() {
        String key = System.getenv("SOLANA_DEVNET_PRIVATE_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "SOLANA_DEVNET_PRIVATE_KEY must be set to a funded Devnet keypair "
                            + "(and RUN_DEVNET_SMOKE_TESTS=true) to run the live Devnet smoke test");
        }
    }

    private SolanaKeypair deriveRecipient() {
        return keypairService.fromSeed(keypairService.deriveSeed("devnet-smoke-recipient"));
    }

    private AccountInfo awaitAccount(String address) throws InterruptedException {
        for (int attempt = 0; attempt < FINALITY_POLL_SECONDS; attempt++) {
            AccountInfo info = rpcAdapter.getAccountInfo(address);
            if (info.exists()) {
                return info;
            }
            Thread.sleep(1000L);
        }
        throw new IllegalStateException("Account did not appear on Devnet within "
                + FINALITY_POLL_SECONDS + "s: " + address);
    }

    private TokenAccountBalance awaitTokenBalance(String tokenAccount) throws InterruptedException {
        for (int attempt = 0; attempt < FINALITY_POLL_SECONDS; attempt++) {
            try {
                TokenAccountBalance balance = rpcAdapter.getTokenAccountBalance(tokenAccount);
                if (balance != null && balance.amount() != null
                        && Long.parseLong(balance.amount()) > 0L) {
                    return balance;
                }
                // The account is visible but its balance is still 0: the mint/transfer
                // transaction has not yet propagated to the read endpoint. Fall through
                // and retry rather than returning a stale zero balance.
            } catch (SolanaRpcException ex) {
                if (!isMissingAccount(ex)) {
                    throw ex;
                }
                // The associated token account may not be visible to the node yet
                // (the creating/minting transaction is still confirming). Swallow
                // the transient "could not find account" error and retry.
            }
            Thread.sleep(2000L);
        }
        throw new IllegalStateException("Token account balance did not become available on Devnet "
                + "within " + FINALITY_POLL_SECONDS + "s: " + tokenAccount);
    }

    /**
     * A {@code getTokenAccountBalance} call for an account the node has not yet
     * indexed returns a transient JSON-RPC {@code -32602} "Invalid param: could
     * not find account" error rather than an empty balance.
     */
    private boolean isMissingAccount(SolanaRpcException ex) {
        String message = ex.getMessage();
        return message != null
                && (message.contains("could not find account") || message.contains("-32602"));
    }

    private SignatureStatusResult awaitConfirmation(String signature) throws InterruptedException {
        for (int attempt = 0; attempt < FINALITY_POLL_SECONDS; attempt++) {
            SignatureStatusResult status = rpcAdapter.getSignatureStatuses(List.of(signature)).get(0);
            if (status.hasError() || status.isConfirmed() || status.isFinalized()) {
                return status;
            }
            Thread.sleep(1000L);
        }
        return rpcAdapter.getSignatureStatuses(List.of(signature)).get(0);
    }
}

