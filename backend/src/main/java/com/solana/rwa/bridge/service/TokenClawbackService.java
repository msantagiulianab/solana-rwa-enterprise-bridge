package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.ClawbackRequest;
import com.solana.rwa.bridge.dto.ClawbackResult;
import com.solana.rwa.bridge.entity.AuditLog;
import com.solana.rwa.bridge.entity.AuditLogStatus;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.repository.AuditLogRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.rpc.dto.LatestBlockhash;
import com.solana.rwa.bridge.solana.AccountMeta;
import com.solana.rwa.bridge.solana.Base58Codec;
import com.solana.rwa.bridge.solana.SolanaInstruction;
import com.solana.rwa.bridge.solana.SolanaKeypair;
import com.solana.rwa.bridge.solana.SolanaKeypairService;
import com.solana.rwa.bridge.solana.SolanaPdaUtil;
import com.solana.rwa.bridge.solana.SolanaTransactionSerializer;
import com.solana.rwa.bridge.solana.Token2022InstructionBuilder;
import com.solana.rwa.bridge.solana.Token2022Program;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Executes Token-2022 permanent-delegate clawbacks.
 *
 * <p>The Permanent Delegate extension lets a designated authority transfer or burn
 * tokens from any account without the wallet owner's signature. This service uses
 * that authority — the enterprise fee-payer derived from {@link SolanaKeypairService}
 * — to sign a {@code TransferChecked} instruction that bypasses the source account
 * owner, appends the transfer-hook {@code extra-account-metas} validation PDA, and
 * broadcasts the result. Every successful broadcast is recorded immutably via
 * {@link AuditLogRepository} with {@code action = CLAWBACK}.
 */
@Slf4j
@Service
public class TokenClawbackService {

    public static final String ACTION_CLAWBACK = "CLAWBACK";
    public static final int DEFAULT_DECIMALS = 6;

    private final SolanaRpcAdapter rpcAdapter;
    private final SolanaKeypairService keypairService;
    private final SolanaTransactionSerializer transactionSerializer;
    private final AuditLogRepository auditLogRepository;
    private final String transferHookProgramId;

    public TokenClawbackService(SolanaRpcAdapter rpcAdapter,
                                SolanaKeypairService keypairService,
                                SolanaTransactionSerializer transactionSerializer,
                                AuditLogRepository auditLogRepository,
                                @Value("${solana.transfer-hook.program-id:}") String transferHookProgramId) {
        this.rpcAdapter = rpcAdapter;
        this.keypairService = keypairService;
        this.transactionSerializer = transactionSerializer;
        this.auditLogRepository = auditLogRepository;
        this.transferHookProgramId = transferHookProgramId;
    }

    /**
     * Builds, signs, and broadcasts the clawback transfer, then records an
     * immutable {@code CLAWBACK} audit entry.
     *
     * @param request clawback target (mint, source/destination token accounts, amount, reason)
     * @return the broadcast signature and clawback metadata
     * @throws SolanaRpcException when the RPC layer fails after exhausting blockhash retries
     */
    public ClawbackResult clawback(ClawbackRequest request) {
        SolanaInstruction transfer = buildTransferChecked(request);
        SolanaKeypair delegate = keypairService.resolveKeypair();
        String signature = submitWithBlockhashRetry(List.of(transfer), List.of(delegate));

        auditLogRepository.save(toAuditLog(request, signature));

        return new ClawbackResult(signature, ACTION_CLAWBACK,
                request.mintAddress(), request.sourceTokenAccount(),
                request.destinationTokenAccount(), request.amount(), Instant.now());
    }

    /**
     * Builds the {@code TransferChecked} instruction signed by the permanent
     * delegate and augmented with the transfer-hook {@code extra-account-metas}
     * validation account.
     */
    public SolanaInstruction buildTransferChecked(ClawbackRequest request) {
        byte[] hookProgramId = Base58Codec.decode(requireTransferHookProgramId());
        byte[] mint = Base58Codec.decode(request.mintAddress());
        byte[] source = Base58Codec.decode(request.sourceTokenAccount());
        byte[] destination = Base58Codec.decode(request.destinationTokenAccount());
        byte[] authority = keypairService.resolveKeypair().getPublicKeyBytes();

        byte[] validationAddress = SolanaPdaUtil.findProgramAddress(
                        List.of(Token2022Program.EXTRA_ACCOUNT_METAS_SEED, mint), hookProgramId)
                .address();

        return Token2022InstructionBuilder.transferChecked(
                source, mint, destination, authority, request.amount(), DEFAULT_DECIMALS,
                List.of(new AccountMeta(validationAddress, false, false)));
    }

    private AuditLog toAuditLog(ClawbackRequest request, String transactionSignature) {
        return AuditLog.builder()
                .walletAddress(request.sourceTokenAccount())
                .action(ACTION_CLAWBACK)
                .status(AuditLogStatus.APPROVED)
                .reason(request.reason())
                .idempotencyKey(UUID.randomUUID().toString())
                .assetId(request.mintAddress())
                .solanaTransactionSignature(transactionSignature)
                .timestamp(Instant.now())
                .build();
    }


    private String requireTransferHookProgramId() {
        if (transferHookProgramId == null || transferHookProgramId.isBlank()) {
            throw new IllegalStateException(
                    "solana.transfer-hook.program-id must be configured before clawing back "
                            + "Token-2022 assets with a transfer hook");
        }
        return transferHookProgramId;
    }

    private String submitWithBlockhashRetry(List<SolanaInstruction> instructions,
                                            List<SolanaKeypair> signers) {
        SolanaRpcException lastBlockhashFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                LatestBlockhash latest = rpcAdapter.getLatestBlockhash();
                String signedTransaction = transactionSerializer.serializeAndSign(
                        instructions, latest.blockhash(), signers);
                return rpcAdapter.sendTransaction(signedTransaction);
            } catch (SolanaRpcException ex) {
                if (isBlockhashNotFound(ex)) {
                    lastBlockhashFailure = ex;
                    log.warn("Stale blockhash on attempt {}/3 for Token-2022 clawback; retrying", attempt);
                    continue;
                }
                throw ex;
            }
        }
        throw lastBlockhashFailure;
    }

    private boolean isBlockhashNotFound(SolanaRpcException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("blockhash not found");
    }
}
