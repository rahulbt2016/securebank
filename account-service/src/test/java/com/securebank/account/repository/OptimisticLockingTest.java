package com.securebank.account.repository;

import com.securebank.account.entity.Account;
import com.securebank.account.entity.AccountStatus;
import com.securebank.account.entity.AccountType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test proving that @Version (optimistic locking) prevents lost updates.
 *
 * Scenario: two users load the same account at the same time (both see version=0).
 * User 1 saves first — version increments to 1 and commits.
 * User 2 tries to save their stale copy (version=0) — must be rejected.
 *
 * Uses @DataJpaTest (H2 in-memory) with separate committed transactions via
 * TransactionTemplate to simulate two independent database sessions.
 */
@DataJpaTest
class OptimisticLockingTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    private UUID savedAccountId;

    @AfterEach
    void cleanup() {
        // NOT_SUPPORTED on the test means no automatic rollback, so we clean up manually.
        if (savedAccountId != null) {
            new TransactionTemplate(txManager).executeWithoutResult(
                    s -> accountRepository.deleteById(savedAccountId));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("@Version should reject a save when the account was modified by another session")
    void shouldRejectStaleUpdate() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // ── Step 1: Persist the account in a committed transaction ──────────────
        // After commit, version=0 in the database.
        savedAccountId = tx.execute(s ->
                accountRepository.save(buildAccount()).getId());

        // ── Step 2: Two sessions independently load the same account ────────────
        // Each findById runs in its own transaction, then commits and returns a
        // detached entity — both detached copies carry version=0.
        Account session1 = tx.execute(s ->
                accountRepository.findById(savedAccountId).orElseThrow());
        Account session2 = tx.execute(s ->
                accountRepository.findById(savedAccountId).orElseThrow());

        assertThat(session1.getVersion()).isZero();
        assertThat(session2.getVersion()).isZero();

        // ── Step 3: Session 1 saves first and commits ───────────────────────────
        // Hibernate issues: UPDATE accounts SET ... WHERE id=? AND version=0
        // 1 row updated → version becomes 1 in the database.
        tx.executeWithoutResult(s -> {
            session1.setAccountHolderName("Updated by Session 1");
            accountRepository.save(session1);
        });

        // Confirm version incremented
        Long versionAfterSession1 = tx.execute(s ->
                accountRepository.findById(savedAccountId).orElseThrow().getVersion());
        assertThat(versionAfterSession1).isEqualTo(1L);

        // ── Step 4: Session 2 tries to save its stale copy (version=0) ──────────
        // Hibernate issues: UPDATE accounts SET ... WHERE id=? AND version=0
        // 0 rows updated (DB has version=1) → OptimisticLockException → rejected.
        assertThatThrownBy(() ->
                tx.executeWithoutResult(s -> {
                    session2.setAccountHolderName("Updated by Session 2 — should fail");
                    accountRepository.save(session2);
                })
        ).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private Account buildAccount() {
        Account account = new Account();
        account.setAccountNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 13));
        account.setCustomerId(UUID.randomUUID());
        account.setAccountHolderName("Test User");
        account.setAccountType(AccountType.CHEQUING);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.valueOf(1000.00));
        account.setCurrency("CAD");
        return account;
    }
}
