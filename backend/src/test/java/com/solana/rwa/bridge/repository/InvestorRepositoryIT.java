package com.solana.rwa.bridge.repository;

import com.solana.rwa.bridge.entity.Investor;
import com.solana.rwa.bridge.entity.KycStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA repository integration tests for {@link InvestorRepository} (H2, PostgreSQL mode).
 */
@DataJpaTest
@ActiveProfiles("test")
class InvestorRepositoryIT {

    @Autowired
    private InvestorRepository investorRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private Investor investor(String walletAddress, KycStatus kycStatus, String country) {
        return Investor.builder()
                .walletAddress(walletAddress)
                .kycStatus(kycStatus)
                .country(country)
                .build();
    }

    @Test
    void save_persistsInvestorWithGeneratedUuidAndTimestamps() {
        Investor saved = investorRepository.save(investor("7XeXLabcDEF", KycStatus.PENDING, "US"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getKycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    void findByWalletAddress_returnsInvestorWhenPresent() {
        Investor persisted = testEntityManager.persistAndFlush(
                investor("7XeXLabcDEF", KycStatus.VERIFIED, "US"));

        Optional<Investor> found = investorRepository.findByWalletAddress("7XeXLabcDEF");

        assertThat(found).isPresent();
        assertThat(found.get().getKycStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(found.get().getCountry()).isEqualTo("US");
    }

    @Test
    void findByWalletAddress_returnsEmptyWhenAbsent() {
        Optional<Investor> found = investorRepository.findByWalletAddress("unknown-wallet");

        assertThat(found).isEmpty();
    }

    @Test
    void findByKycStatus_returnsOnlyInvestorsWithStatus() {
        testEntityManager.persistAndFlush(investor("WALLET_VERIFIED_1", KycStatus.VERIFIED, "US"));
        testEntityManager.persistAndFlush(investor("WALLET_PENDING_1", KycStatus.PENDING, "CA"));
        testEntityManager.persistAndFlush(investor("WALLET_REJECTED_1", KycStatus.REJECTED, "UK"));

        List<Investor> verified = investorRepository.findByKycStatus(KycStatus.VERIFIED);

        assertThat(verified).hasSize(1);
        assertThat(verified.get(0).getWalletAddress()).isEqualTo("WALLET_VERIFIED_1");
    }

    @Test
    void existsByWalletAddress_returnsTrueForExisting() {
        testEntityManager.persistAndFlush(investor("WALLET_EXISTS", KycStatus.PENDING, "US"));

        assertThat(investorRepository.existsByWalletAddress("WALLET_EXISTS")).isTrue();
        assertThat(investorRepository.existsByWalletAddress("WALLET_MISSING")).isFalse();
    }

    @Test
    void uniqueWalletAddress_throwsConstraintViolationOnDuplicate() {
        String wallet = "7XeXLabcDEFUNIQUE";
        testEntityManager.persistAndFlush(investor(wallet, KycStatus.PENDING, "US"));

        Investor duplicate = Investor.builder()
                .walletAddress(wallet)
                .kycStatus(KycStatus.VERIFIED)
                .country("CA")
                .build();

        // saveAndFlush goes through the Spring Data repository proxy, so the
        // underlying H2 unique-index violation is translated by Spring.
        assertThatThrownBy(() -> investorRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_kycStatusPersistsLatestState() {
        Investor investor = testEntityManager.persistAndFlush(
                investor("WALLET_UPDATE", KycStatus.PENDING, "US"));

        investor.setKycStatus(KycStatus.FLAGGED_SANCTION);
        testEntityManager.persistAndFlush(investor);

        Optional<Investor> reloaded = investorRepository.findByWalletAddress("WALLET_UPDATE");
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getKycStatus()).isEqualTo(KycStatus.FLAGGED_SANCTION);
    }

    @Test
    void countByKycStatus_countsInvestorsByStatus() {
        testEntityManager.persistAndFlush(investor("W1", KycStatus.VERIFIED, "US"));
        testEntityManager.persistAndFlush(investor("W2", KycStatus.VERIFIED, "CA"));
        testEntityManager.persistAndFlush(investor("W3", KycStatus.PENDING, "UK"));

        long verifiedCount = investorRepository.countByKycStatus(KycStatus.VERIFIED);
        long pendingCount = investorRepository.countByKycStatus(KycStatus.PENDING);

        assertThat(verifiedCount).isEqualTo(2);
        assertThat(pendingCount).isEqualTo(1);
    }
}
