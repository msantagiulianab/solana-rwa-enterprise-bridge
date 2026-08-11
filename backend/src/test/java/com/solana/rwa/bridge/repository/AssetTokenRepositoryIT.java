package com.solana.rwa.bridge.repository;

import com.solana.rwa.bridge.entity.AssetToken;
import com.solana.rwa.bridge.entity.AssetTokenComplianceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA repository integration tests for {@link AssetTokenRepository} (H2, PostgreSQL mode).
 */
@DataJpaTest
@ActiveProfiles("test")
class AssetTokenRepositoryIT {

    @Autowired
    private AssetTokenRepository assetTokenRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void save_persistsAssetTokenWithGeneratedUuidAndTimestamps() {
        AssetToken token = AssetToken.builder()
                .assetName("Prime Manhattan Office Fund")
                .valuationUsd(new BigDecimal("125000000.00"))
                .complianceStatus(AssetTokenComplianceStatus.COMPLIANT)
                .build();

        AssetToken saved = assetTokenRepository.save(token);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getMintAddress()).isNull();
        assertThat(saved.getComplianceStatus()).isEqualTo(AssetTokenComplianceStatus.COMPLIANT);
    }

    @Test
    void findByMintAddress_returnsTokenWhenPresent() {
        AssetToken token = AssetToken.builder()
                .assetName("Golden Gate Industrial REIT")
                .valuationUsd(new BigDecimal("75000000.00"))
                .mintAddress("MNTabcdefghijklmnopqrstuvwxyz1234567890ABC")
                .complianceStatus(AssetTokenComplianceStatus.COMPLIANT)
                .build();
        AssetToken saved = testEntityManager.persistAndFlush(token);

        Optional<AssetToken> found = assetTokenRepository.findByMintAddress(saved.getMintAddress());

        assertThat(found).isPresent();
        assertThat(found.get().getAssetName()).isEqualTo("Golden Gate Industrial REIT");
    }

    @Test
    void findByMintAddress_returnsEmptyWhenAbsent() {
        Optional<AssetToken> found = assetTokenRepository.findByMintAddress("MNTnonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void findByComplianceStatus_returnsOnlyMatchingTokens() {
        testEntityManager.persistAndFlush(AssetToken.builder()
                .assetName("Compliant Solar Asset")
                .valuationUsd(new BigDecimal("1000000.00"))
                .complianceStatus(AssetTokenComplianceStatus.COMPLIANT)
                .build());
        testEntityManager.persistAndFlush(AssetToken.builder()
                .assetName("Non Compliant Asset")
                .valuationUsd(new BigDecimal("2000000.00"))
                .complianceStatus(AssetTokenComplianceStatus.NON_COMPLIANT)
                .build());

        List<AssetToken> compliant = assetTokenRepository.findByComplianceStatus(AssetTokenComplianceStatus.COMPLIANT);

        assertThat(compliant).hasSize(1);
        assertThat(compliant.get(0).getAssetName()).isEqualTo("Compliant Solar Asset");
    }

    @Test
    void uniqueMintAddress_throwsConstraintViolationOnDuplicate() {
        String mintAddress = "MNTunique00000000000000000000000000000000";
        testEntityManager.persistAndFlush(AssetToken.builder()
                .assetName("First Token")
                .valuationUsd(new BigDecimal("100.00"))
                .mintAddress(mintAddress)
                .complianceStatus(AssetTokenComplianceStatus.COMPLIANT)
                .build());

        AssetToken duplicate = AssetToken.builder()
                .assetName("Duplicate Token")
                .valuationUsd(new BigDecimal("200.00"))
                .mintAddress(mintAddress)
                .complianceStatus(AssetTokenComplianceStatus.COMPLIANT)
                .build();

        // saveAndFlush goes through the Spring Data repository proxy, so the
        // underlying H2 unique-index violation is translated by Spring.
        assertThatThrownBy(() -> assetTokenRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAssetTokensWithMintAddress_returnsOnlyTokensWithMint() {
        testEntityManager.persistAndFlush(AssetToken.builder()
                .assetName("Minted Token")
                .valuationUsd(new BigDecimal("500.00"))
                .mintAddress("MNTminted00000000000000000000000000000000")
                .complianceStatus(AssetTokenComplianceStatus.COMPLIANT)
                .build());
        testEntityManager.persistAndFlush(AssetToken.builder()
                .assetName("Unminted Token")
                .valuationUsd(new BigDecimal("600.00"))
                .complianceStatus(AssetTokenComplianceStatus.NON_COMPLIANT)
                .build());

        List<AssetToken> minted = assetTokenRepository.findAssetTokensWithMintAddress();

        assertThat(minted).hasSize(1);
        assertThat(minted.get(0).getAssetName()).isEqualTo("Minted Token");
    }
}
