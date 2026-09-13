package com.solana.rwa.bridge.smoke;

import com.solana.rwa.bridge.compliance.adapter.out.simulation.SimulatedTransferComplianceAdapter;
import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.dto.ClawbackRequest;
import com.solana.rwa.bridge.dto.ClawbackResult;
import com.solana.rwa.bridge.dto.TokenTransferRequest;
import com.solana.rwa.bridge.dto.TokenTransferResult;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.entity.TransferHookAuditLog;
import com.solana.rwa.bridge.entity.TransferHookAuditStatus;
import com.solana.rwa.bridge.exception.ComplianceViolationException;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.repository.AssetTokenRepository;
import com.solana.rwa.bridge.repository.AuditLogRepository;
import com.solana.rwa.bridge.repository.InvestorRepository;
import com.solana.rwa.bridge.repository.TransferHookAuditLogRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.AccountInfo;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import com.solana.rwa.bridge.rpc.dto.SignatureStatusResult;
import com.solana.rwa.bridge.rpc.dto.TokenAccountBalance;
import com.solana.rwa.bridge.service.TokenClawbackService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /** SPL Associated Token Account program id (shared by legacy and Token-2022). */
    private static final String ASSOCIATED_TOKEN_PROGRAM_ID =
            "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL";

    private static final long MINTED_SUPPLY = 1_000_000L; // 1.0 token (6 decimals)
    private static final long TRANSFER_AMOUNT = 250_000L; // 0.25 tokens

    private static final int FINALITY_POLL_SECONDS = 30;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private TokenTransferService tokenTransferService;

    @Autowired
    private TokenClawbackService tokenClawbackService;

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

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void fullDevnetLifecycle_registerMintAndClearedTransfer_verifiesLiveSignatures()
            throws Exception {
        requireFundedDevnetKey();
        resetLedger();

        SolanaKeypair payer = keypairService.resolveKeypair();
        SolanaKeypair recipient = deriveRecipient();

        // Step 1: onboard a KYC-verified issuer and execute the live Token-2022
        // mint through the production TokenService/SolanaMintService path. The
        // mint carries the Permanent Delegate extension set to the fee payer.
        String mintAddress = onboardAndMint(payer, "devnet-smoke-mint-");

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

        // Wait for both associated token accounts to become visible/initialized on
        // Devnet before minting — otherwise the MintTo can race the ATA creation
        // and target an account the node has not indexed yet.
        awaitAccount(sourceAta);
        awaitAccount(destinationAta);

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

    @Test
    void blockedTransfer_recordsNullSignatureAuditAndLeavesDevnetUnchanged()
            throws Exception {
        requireFundedDevnetKey();
        resetLedger();

        SolanaKeypair payer = keypairService.resolveKeypair();
        SolanaKeypair recipient = deriveRecipient();

        String mintAddress = onboardAndMint(payer, "devnet-smoke-blocked-");
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

        // Wait for both associated token accounts to become visible/initialized on
        // Devnet before minting — otherwise the MintTo can race the ATA creation
        // and target an account the node has not indexed yet.
        awaitAccount(sourceAta);
        awaitAccount(destinationAta);

        submit(List.of(buildMintTo(mint, Base58Codec.decode(sourceAta), payerPubkey,
                        MINTED_SUPPLY)),
                List.of(payer));

        awaitTokenBalance(sourceAta);

        // Snapshot the on-chain balances before attempting the blocked transfer.
        long sourceBefore = Long.parseLong(rpcAdapter.getTokenAccountBalance(sourceAta).amount());
        long destinationBefore = Long.parseLong(rpcAdapter.getTokenAccountBalance(destinationAta).amount());
        assertThat(destinationBefore).as("destination must start unfunded").isZero();

        // A sanctioned destination wallet forces the compliance SPI to BLOCK.
        TokenTransferRequest blocked = new TokenTransferRequest(
                payer.getPublicKeyBase58(),
                SimulatedTransferComplianceAdapter.SANCTIONED_DESTINATION_WALLET,
                sourceAta,
                destinationAta,
                mintAddress,
                TRANSFER_AMOUNT);

        assertThatThrownBy(() -> tokenTransferService.transfer(blocked))
                .isInstanceOf(ComplianceViolationException.class)
                .hasMessageContaining("SANCTIONED_DESTINATION");

        // Fail-closed audit: exactly one BLOCKED row carrying a null transaction
        // signature (no broadcast ever occurred).
        List<TransferHookAuditLog> logs = transferHookAuditLogRepository.findByMintAddress(mintAddress);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getComplianceStatus()).isEqualTo(TransferHookAuditStatus.BLOCKED);
        assertThat(logs.get(0).getTransactionSignature()).isNull();
        assertThat(logs.get(0).getDestinationWallet())
                .isEqualTo(SimulatedTransferComplianceAdapter.SANCTIONED_DESTINATION_WALLET);

        // Devnet state is unchanged: the source still holds the full minted
        // supply and the destination was never funded.
        long sourceAfter = Long.parseLong(rpcAdapter.getTokenAccountBalance(sourceAta).amount());
        assertThat(sourceAfter).as("blocked transfer must not move source funds")
                .isEqualTo(sourceBefore);
        assertThat(sourceAfter).isEqualTo(MINTED_SUPPLY);
        long destinationAfter = Long.parseLong(rpcAdapter.getTokenAccountBalance(destinationAta).amount());
        assertThat(destinationAfter).as("blocked transfer must not fund the destination")
                .isZero();
    }

    @Test
    void liveClawback_permanentDelegateRecoversFundsWithoutOwnerSignature()
            throws Exception {
        requireFundedDevnetKey();
        resetLedger();

        // The fee payer is both the mint's Permanent Delegate and the recovery
        // destination; the recipient is the token holder whose funds are clawed
        // back without their signature.
        SolanaKeypair payer = keypairService.resolveKeypair();
        SolanaKeypair recipient = deriveRecipient();

        String mintAddress = onboardAndMint(payer, "devnet-smoke-clawback-");
        byte[] mint = Base58Codec.decode(mintAddress);
        byte[] payerPubkey = payer.getPublicKeyBytes();
        byte[] recipientPubkey = recipient.getPublicKeyBytes();

        String holderAta = associatedTokenAddress(recipientPubkey, mint);
        String recoveryAta = associatedTokenAddress(payerPubkey, mint);

        submit(List.of(
                        createAssociatedTokenAccount(payerPubkey, Base58Codec.decode(holderAta),
                                recipientPubkey, mint),
                        createAssociatedTokenAccount(payerPubkey, Base58Codec.decode(recoveryAta),
                                payerPubkey, mint)),
                List.of(payer));

        // Wait for both associated token accounts to become visible/initialized on
        // Devnet before minting — otherwise the MintTo can race the ATA creation
        // and target an account the node has not indexed yet.
        awaitAccount(holderAta);
        awaitAccount(recoveryAta);

        // Mint the supply to the HOLDER's account so the clawback has funds to recover.
        submit(List.of(buildMintTo(mint, Base58Codec.decode(holderAta), payerPubkey,
                        MINTED_SUPPLY)),
                List.of(payer));

        awaitTokenBalance(holderAta);
        long holderBefore = Long.parseLong(rpcAdapter.getTokenAccountBalance(holderAta).amount());
        assertThat(holderBefore).isEqualTo(MINTED_SUPPLY);

        ClawbackResult clawback = tokenClawbackService.clawback(new ClawbackRequest(
                        mintAddress,
                        holderAta,
                        recoveryAta,
                        TRANSFER_AMOUNT,
                        "Regulatory breach - permanent delegate recovery"),
                "devnet-smoke-clawback-" + System.currentTimeMillis());

        assertThat(clawback.signature()).isNotBlank();
        assertThat(clawback.action()).isEqualTo(TokenClawbackService.ACTION_CLAWBACK);

        SignatureStatusResult status = awaitConfirmation(clawback.signature());
        assertThat(status.hasError())
                .as("clawback %s must settle without an on-chain error (err=%s)",
                        clawback.signature(), status.err())
                .isFalse();
        assertThat(status.isConfirmed() || status.isFinalized())
                .as("clawback %s must reach confirmed/finalized commitment", clawback.signature())
                .isTrue();

        // The permanent delegate moved funds without the holder's signature. Wait
        // for the read RPC to propagate the on-chain balance change before
        // asserting, rather than reading immediately after the confirmed broadcast.
        TokenAccountBalance holderBalance = awaitTokenBalance(holderAta, holderBefore - TRANSFER_AMOUNT);
        assertThat(Long.parseLong(holderBalance.amount()))
                .as("clawback must debit the holder")
                .isEqualTo(holderBefore - TRANSFER_AMOUNT);

        TokenAccountBalance recoveryBalance = awaitTokenBalance(recoveryAta, TRANSFER_AMOUNT);
        assertThat(Long.parseLong(recoveryBalance.amount()))
                .as("recovery account must receive the clawed-back amount")
                .isEqualTo(TRANSFER_AMOUNT);

        // The clawback leaves an immutable APPROVED CLAWBACK audit row carrying
        // the real on-chain signature.
        List<AuditLog> logs = auditLogRepository.findByWalletAddressAndAction(
                holderAta, TokenClawbackService.ACTION_CLAWBACK);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo(AuditLogStatus.APPROVED);
        assertThat(logs.get(0).getSolanaTransactionSignature()).isEqualTo(clawback.signature());
    }

    // ---------------------------------------------------------------------
    // Lifecycle setup helpers
    // ---------------------------------------------------------------------

    private void resetLedger() {
        transferHookAuditLogRepository.deleteAll();
        assetTokenRepository.deleteAll();
        investorRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    /**
     * Onboards a KYC-{@code VERIFIED} issuer and executes a live Token-2022 mint
     * (carrying the Permanent Delegate extension) through the production
     * {@link TokenService}/{@link SolanaMintService} path, then waits for the
     * mint account to appear on Devnet.
     */
    private String onboardAndMint(SolanaKeypair payer, String idempotencyPrefix)
            throws InterruptedException {
        investorRepository.save(Investor.builder()
                .fullName("Devnet Smoke Issuer")
                .email("issuer@devnet.smoke.test")
                .walletAddress(payer.getPublicKeyBase58())
                .kycStatus(KycStatus.VERIFIED)
                .country("US")
                .build());

        AssetToken asset = tokenService.create(AssetTokenRegistrationRequest.builder()
                .assetName("Devnet Lifecycle Smoke Asset")
                .valuationUsd(new BigDecimal("1000000.00"))
                .issuerWalletAddress(payer.getPublicKeyBase58())
                .idempotencyKey(idempotencyPrefix + System.currentTimeMillis())
                .build());

        assertThat(asset.getMintAddress()).isNotBlank();
        assertThat(asset.getSettlementStatus()).isEqualTo(SettlementStatus.CONFIRMED);

        String mintAddress = asset.getMintAddress();
        awaitAccount(mintAddress);
        return mintAddress;
    }

    // ---------------------------------------------------------------------
    // On-chain staging helpers (associated token account creation + mint-to)
    // ---------------------------------------------------------------------

    private String associatedTokenAddress(byte[] owner, byte[] mint) {
        // A Token-2022 associated token account is derived with the Token-2022
        // program id (the account owner) as the PDA seed — never the legacy
        // SPL Token program (Tokenkeg...). Both program ids are locked to the
        // canonical Token-2022 / ATA constants so no legacy id can bleed in.
        return Base58Codec.encode(SolanaPdaUtil.findProgramAddress(
                        List.of(owner,
                                Base58Codec.decode(Token2022Program.TOKEN_2022_PROGRAM_ID),
                                mint),
                        Base58Codec.decode(ASSOCIATED_TOKEN_PROGRAM_ID))
                .address());
    }

    private SolanaInstruction createAssociatedTokenAccount(byte[] funder, byte[] ata,
                                                           byte[] owner, byte[] mint) {
        // Program id is the ATA program and account 5 (0-indexed) is the token
        // program that will OWN the created account. For a Token-2022 mint the
        // token program MUST be Token-2022 (Tokenz...), never legacy SPL Token
        // (Tokenkeg...), or the ATA program rejects the instruction with
        // `IncorrectProgramId`. Both program ids are locked to canonical constants.
        return new SolanaInstruction(
                Base58Codec.decode(ASSOCIATED_TOKEN_PROGRAM_ID),
                List.of(
                        new AccountMeta(funder, true, true),
                        new AccountMeta(ata, false, true),
                        new AccountMeta(owner, false, false),
                        new AccountMeta(mint, false, false),
                        new AccountMeta(Base58Codec.decode(SolanaMintService.SYSTEM_PROGRAM_ID), false, false),
                        new AccountMeta(Base58Codec.decode(Token2022Program.TOKEN_2022_PROGRAM_ID), false, false)),
                new byte[]{ATA_CREATE_DISCRIMINATOR});
    }

    private SolanaInstruction buildMintTo(byte[] mint, byte[] destination, byte[] authority,
                                          long amount) {
        // MintTo must target the Token-2022 program id (Tokenz...), not the legacy
        // SPL Token program (Tokenkeg...), because the mint is a Token-2022 mint.
        // The program id is locked to the canonical Token-2022 constant.
        return new SolanaInstruction(
                Base58Codec.decode(Token2022Program.TOKEN_2022_PROGRAM_ID),
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
     * Polls until the account's balance reaches {@code expectedAmount} (raw token
     * units), tolerating the read-side propagation delay that can lag a confirmed
     * broadcast as well as the transient "could not find account" error.
     */
    private TokenAccountBalance awaitTokenBalance(String tokenAccount, long expectedAmount)
            throws InterruptedException {
        for (int attempt = 0; attempt < FINALITY_POLL_SECONDS; attempt++) {
            try {
                TokenAccountBalance balance = rpcAdapter.getTokenAccountBalance(tokenAccount);
                if (balance != null && balance.amount() != null
                        && Long.parseLong(balance.amount()) == expectedAmount) {
                    return balance;
                }
                // The account is visible but the read endpoint still reports a stale
                // balance (e.g. the pre-clawback amount). Fall through and retry
                // until the post-transaction balance propagates.
            } catch (SolanaRpcException ex) {
                if (!isMissingAccount(ex)) {
                    throw ex;
                }
                // Transient "could not find account": swallow and retry.
            }
            Thread.sleep(2000L);
        }
        throw new IllegalStateException("Token account balance did not reach " + expectedAmount
                + " on Devnet within " + FINALITY_POLL_SECONDS + "s: " + tokenAccount);
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

