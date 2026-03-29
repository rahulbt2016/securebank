package com.securebank.account.repository;

import com.securebank.account.entity.Account;
import com.securebank.account.entity.AccountStatus;
import com.securebank.account.entity.AccountType;
import com.securebank.account.repository.projection.CustomerBalanceSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomerId(UUID customerId);

    Page<Account> findByStatus(AccountStatus status, Pageable pageable);

    Page<Account> findByAccountType(AccountType accountType, Pageable pageable);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * JPQL search with optional filters.
     * Each condition uses (:param IS NULL OR ...) so that passing null
     * for a parameter simply skips that filter — one query handles all combinations.
     */
    @Query("""
            SELECT a FROM Account a
            WHERE (:customerId IS NULL OR a.customerId = :customerId)
              AND (:status     IS NULL OR a.status      = :status)
              AND (:accountType IS NULL OR a.accountType = :accountType)
              AND (:minBalance  IS NULL OR a.balance    >= :minBalance)
            """)
    Page<Account> searchAccounts(
            @Param("customerId")   UUID customerId,
            @Param("status")       AccountStatus status,
            @Param("accountType")  AccountType accountType,
            @Param("minBalance")   BigDecimal minBalance,
            Pageable pageable
    );

    /**
     * Native SQL query — returns balance statistics for all non-closed accounts
     * belonging to a customer. Uses a projection interface to map column aliases
     * to typed getters without needing a full entity.
     */
    @Query(value = """
            SELECT
                COUNT(*)        AS accountCount,
                SUM(balance)    AS totalBalance,
                MAX(balance)    AS highestBalance,
                MIN(balance)    AS lowestBalance
            FROM accounts
            WHERE customer_id = :customerId
              AND status != 'CLOSED'
            """, nativeQuery = true)
    CustomerBalanceSummary getBalanceSummary(@Param("customerId") UUID customerId);
}
