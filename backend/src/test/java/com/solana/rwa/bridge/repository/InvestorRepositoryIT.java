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
    private TestEntityManager testEntityManager;

    @Autowired
    private InvestorRepository investorRepository;

    private Investor investor(String walletAddress, KycStatus kycStatus) {
        return Investor.builder()
                .fullName("Test User")
                .email("test@example.com")
                .walletAddress(walletAddress)
                .kycStatus(kycStatus)
                .build();
    }

    @Test
    void save_persistsInvestorWithGeneratedUuidAndTimestamps() {
        Investor saved = investorRepository.save(investor("7XeXLabcDEF", KycStatus.PENDING));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getKycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(saved.getFullName()).isEqualTo("Test User");
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void findByWalletAddress_returnsInvestorWhenPresent() {
        Investor persisted = testEntityManager.persistAndFlush(
                investor("7XeXLabcDEF", KycStatus.VERIFIED));

        Optional<Investor> found = investorRepository.findByWalletAddress("7XeXLabcDEF");

        assertThat(found).isPresent();
        assertThat(found.get().getKycStatus()).isEqualTo(KycStatus.VERIFIED);
    }

    @Test
    void findByWalletAddress_returnsEmptyWhenAbsent() {
        Optional<Investor> found = investorRepository.findByWalletAddress("unknown-wallet");

        assertThat(found).isEmpty();
    }

    @Test
    void findByKycStatus_returnsOnlyInvestorsWithStatus() {
        testEntityManager.persistAndFlush(investor("WALLET_VERIFIED_1", KycStatus.VERIFIED));
        testEntityManager.persistAndFlush(investor("WALLET_VERIFIED_2", KycStatus.VERIFIED));
        testEntityManager.persistAndFlush(investor("WALLET_PENDING_1", KycStatus.PENDING));

        List<Investor> verified = investorRepository.findByKycStatus(KycStatus.VERIFIED);
        assertThat(verified).hasSize(2);

        List<Investor> pending = investorRepository.findByKycStatus(KycStatus.PENDING);
        assertThat(pending).hasSize(1);
    }

    @Test
    void existsByWalletAddress_returnsTrueWhenPresent() {
        testEntityManager.persistAndFlush(investor("WALLET_EXISTS", KycStatus.PENDING));

        assertThat(investorRepository.existsByWalletAddress("WALLET_EXISTS")).isTrue();
    }

    @Test
    void existsByWalletAddress_returnsFalseWhenAbsent() {
        assertThat(investorRepository.existsByWalletAddress("NO_SUCH_WALLET")).isFalse();
    }

    @Test
    void countByKycStatus_returnsCorrectCount() {
        testEntityManager.persistAndFlush(investor("W_COUNT_P1", KycStatus.PENDING));
        testEntityManager.persistAndFlush(investor("W_COUNT_P2", KycStatus.PENDING));

        assertThat(investorRepository.countByKycStatus(KycStatus.PENDING)).isEqualTo(2);
    }

    @Test
    void uniqueWalletAddressConstraint_preventsDuplicates() {
        testEntityManager.persistAndFlush(investor("WALLET_DUP", KycStatus.PENDING));

        Investor duplicate = Investor.builder()
                .fullName("Duplicate User")
                .email("dup@example.com")
                .walletAddress("WALLET_DUP")
                .kycStatus(KycStatus.VERIFIED)
                .build();

        assertThatThrownBy(() -> {
            investorRepository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}