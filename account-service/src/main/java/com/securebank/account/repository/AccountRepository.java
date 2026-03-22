package com.securebank.account.repository;

import com.securebank.account.entity.Account;
import com.securebank.account.entity.AccountStatus;
import com.securebank.account.entity.AccountType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
