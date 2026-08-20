package com.solana.rwa.bridge.service;

import com.solana.rwa.bridge.dto.AssetTokenRegistrationRequest;
import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import com.solana.rwa.bridge.exception.AssetTokenNotFoundException;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.repository.AssetTokenRepository;
import com.solana.rwa.bridge.repository.InvestorRepository;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.solana.SolanaMintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TokenService {

    private final AssetTokenRepository assetTokenRepository;
    private final SolanaMintService solanaMintService;
    private final InvestorRepository investorRepository;
    private final SolanaRpcAdapter solanaRpcAdapter;

    public List<AssetToken> findAll() {
        return assetTokenRepository.findAll();
    }

    public AssetToken findById(UUID id) {
        return assetTokenRepository.findById(id)
                .orElseThrow(() -> new AssetTokenNotFoundException(id.toString(), true));
    }

    /**
     * Tokenizes a real-world asset after passing the pre-flight compliance gate.
     *
     * <p>The issuer wallet is verified <em>before</em> any on-chain mint occurs:
     * the issuer must be registered, KYC {@link KycStatus#VERIFIED}, and present
     * on-chain. Any failure aborts immediately with {@code 422} so that
     * {@link SolanaMintService#createMint()} is never invoked (fail-closed).
     * Only a cleared issuer results in a minted token, which is persisted as
     * {@link AssetTokenComplianceStatus#COMPLIANT}.
     */
    @Transactional
    public AssetToken create(AssetTokenRegistrationRequest request) {
        String issuerWalletAddress = request.getIssuerWalletAddress();
        assertIssuerCompliant(issuerWalletAddress);

        // Issue the real on-chain SPL mint BEFORE persisting the off-chain
        // registry record. A failed RPC call aborts the tokenization rather
        // than leaving an asset without a verifiable mint address.
        String mintAddress = solanaMintService.createMint();

        AssetToken token = AssetToken.builder()
                .assetName(request.getAssetName())
                .valuationUsd(request.getValuationUsd())
                .mintAddress(mintAddress)
                .complianceStatus(AssetTokenComplianceStatus.COMPLIANT)
                .build();
        return assetTokenRepository.save(token);
    }

    /**
     * Fail-closed pre-flight KYC/AML + on-chain existence check for the issuer.
     *
     * @throws ResponseStatusException (422) when the issuer is not registered,
     *         KYC-blocked, not verified, absent on-chain, or when the RPC layer
     *         is unavailable.
     */
    private void assertIssuerCompliant(String walletAddress) {
        Investor investor = investorRepository.findByWalletAddress(walletAddress).orElse(null);

        if (investor == null) {
            throw blocked("Investor not registered");
        }
        KycStatus kycStatus = investor.getKycStatus();
        if (kycStatus == KycStatus.REJECTED) {
            throw blocked("Investor KYC status is REJECTED");
        }
        if (kycStatus == KycStatus.FLAGGED_SANCTION) {
            throw blocked("Investor is flagged for sanctions screening");
        }
        if (kycStatus != KycStatus.VERIFIED) {
            throw blocked("Investor KYC verification is not complete (status: " + kycStatus + ")");
        }

        try {
            if (!solanaRpcAdapter.getAccountInfo(walletAddress).exists()) {
                throw blocked("Wallet does not exist on Solana chain");
            }
        } catch (SolanaRpcException ex) {
            throw blocked("Solana RPC unavailable - on-chain verification failed");
        }
    }

    private ResponseStatusException blocked(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Tokenization blocked by compliance: " + reason);
    }
}