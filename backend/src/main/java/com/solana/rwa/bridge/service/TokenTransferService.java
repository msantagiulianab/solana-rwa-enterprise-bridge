package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.compliance.port.TransferCompliancePort;
import com.solana.rwa.bridge.compliance.port.TransferComplianceRequest;
import com.solana.rwa.bridge.compliance.port.TransferComplianceResult;
import com.solana.rwa.bridge.compliance.port.TransferComplianceStatus;
import com.solana.rwa.bridge.dto.TokenTransferRequest;
import com.solana.rwa.bridge.dto.TokenTransferResult;
import com.solana.rwa.bridge.exception.ComplianceViolationException;
import com.solana.rwa.bridge.exception.SolanaRpcException;
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

import java.util.List;

/**
 * Executes compliance-gated Token-2022 secondary-market transfers.
 *
 * <p>The transfer hook execution path is fail-closed end-to-end:
 * <ol>
 *   <li>Evaluate the transfer against the {@link TransferCompliancePort} SPI.
 *       A {@link TransferComplianceStatus#BLOCKED} decision aborts with
 *       {@link ComplianceViolationException} before any RPC bytes are emitted.</li>
 *   <li>Resolve the transfer hook {@code extra-account-metas} PDA and append it
 *       to the {@code TransferChecked} instruction so the validator runtime can
 *       CPI into the hook program during settlement.</li>
 *   <li>Serialize, sign and broadcast (with stale-blockhash retry).</li>
 * </ol>
 */
@Slf4j
@Service
public class TokenTransferService {

    public static final int DEFAULT_DECIMALS = 6;

    private final TransferCompliancePort transferCompliancePort;
    private final SolanaRpcAdapter rpcAdapter;
    private final SolanaKeypairService keypairService;
    private final SolanaTransactionSerializer transactionSerializer;
    private final String transferHookProgramId;

    public TokenTransferService(TransferCompliancePort transferCompliancePort,
                                SolanaRpcAdapter rpcAdapter,
                                SolanaKeypairService keypairService,
                                SolanaTransactionSerializer transactionSerializer,
                                @Value("${solana.transfer-hook.program-id:}") String transferHookProgramId) {
        this.transferCompliancePort = transferCompliancePort;
        this.rpcAdapter = rpcAdapter;
        this.keypairService = keypairService;
        this.transactionSerializer = transactionSerializer;
        this.transferHookProgramId = transferHookProgramId;
    }

    /**
     * Evaluates compliance and, when approved, broadcasts the transfer.
     *
     * @return the broadcast signature and compliance metadata
     * @throws ComplianceViolationException when the compliance SPI blocks the transfer
     */
    public TokenTransferResult transfer(TokenTransferRequest request) {
        TransferComplianceResult compliance = transferCompliancePort.evaluateTransfer(
                new TransferComplianceRequest(
                        request.sourceWallet(),
                        request.destinationWallet(),
                        request.assetMintAddress(),
                        request.amount()));

        if (compliance.status() == TransferComplianceStatus.BLOCKED) {
            log.warn("Transfer blocked by compliance SPI: {} ({})",
                    compliance.reason() == null ? "UNKNOWN" : compliance.reason().code(),
                    compliance.referenceId());
            throw new ComplianceViolationException(compliance);
        }

        SolanaInstruction transfer = buildTransferChecked(request);
        SolanaKeypair authority = keypairService.resolveKeypair();
        String signature = submitWithBlockhashRetry(List.of(transfer), List.of(authority));

        return new TokenTransferResult(signature, compliance.status().name(),
                compliance.referenceId(), compliance.evaluatedAt());
    }

    /**
     * Builds the {@code TransferChecked} instruction augmented with the transfer
     * hook {@code extra-account-metas} validation account.
     */
    public SolanaInstruction buildTransferChecked(TokenTransferRequest request) {
        byte[] hookProgramId = Base58Codec.decode(requireTransferHookProgramId());
        byte[] mint = Base58Codec.decode(request.assetMintAddress());
        byte[] source = Base58Codec.decode(request.sourceTokenAccount());
        byte[] destination = Base58Codec.decode(request.destinationTokenAccount());
        byte[] authority = keypairService.resolveKeypair().getPublicKeyBytes();

        byte[] validationAddress = SolanaPdaUtil.findProgramAddress(
                        List.of(Token2022Program.EXTRA_ACCOUNT_METAS_SEED, mint), hookProgramId)
                .address();

        int decimals = request.decimals() <= 0 ? DEFAULT_DECIMALS : request.decimals();
        return Token2022InstructionBuilder.transferChecked(
                source, mint, destination, authority, request.amount(), decimals,
                List.of(new AccountMeta(validationAddress, false, false)));
    }

    private String requireTransferHookProgramId() {
        if (transferHookProgramId == null || transferHookProgramId.isBlank()) {
            throw new IllegalStateException(
                    "solana.transfer-hook.program-id must be configured before transferring "
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
                    log.warn("Stale blockhash on attempt {}/3 for Token-2022 transfer; retrying", attempt);
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
