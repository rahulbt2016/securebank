package com.securebank.account.service;

import com.securebank.account.dto.AccountResponse;
import com.securebank.account.dto.CreateAccountRequest;
import com.securebank.account.dto.CustomerBalanceSummaryResponse;
import com.securebank.account.dto.UpdateAccountRequest;
import com.securebank.account.entity.Account;
import com.securebank.account.entity.AccountStatus;
import com.securebank.account.entity.AccountType;
import com.securebank.account.mapper.AccountMapper;
import com.securebank.account.repository.AccountRepository;
import com.securebank.account.repository.projection.CustomerBalanceSummary;
import com.securebank.common.dto.PagedResponse;
import com.securebank.common.exception.BusinessRuleException;
import com.securebank.common.exception.ForbiddenException;
import com.securebank.common.exception.ResourceNotFoundException;
import com.securebank.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        Account account = accountMapper.toEntity(request);
        account.setAccountNumber(generateAccountNumber());
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);

        Account saved = accountRepository.save(account);
        log.info("Account created: {} for customer {}", saved.getAccountNumber(), saved.getCustomerId());
        return accountMapper.toResponse(saved);
    }

    public AccountResponse getAccountById(UUID id, AuthenticatedUser caller) {
        Account account = findAccountOrThrow(id);
        requireOwnershipOrStaff(account.getCustomerId(), caller);
        return accountMapper.toResponse(account);
    }

    public AccountResponse getAccountByNumber(String accountNumber, AuthenticatedUser caller) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "accountNumber", accountNumber));
        requireOwnershipOrStaff(account.getCustomerId(), caller);
        return accountMapper.toResponse(account);
    }

    public List<AccountResponse> getAccountsByCustomerId(UUID customerId, AuthenticatedUser caller) {
        requireOwnershipOrStaff(customerId, caller);
        return accountRepository.findByCustomerId(customerId).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    public PagedResponse<AccountResponse> getAllAccounts(Pageable pageable, AuthenticatedUser caller) {
        if (!caller.isStaff()) {
            throw new ForbiddenException("Access to all accounts requires staff privileges");
        }
        Page<Account> page = accountRepository.findAll(pageable);
        return toPagedResponse(page);
    }

    public PagedResponse<AccountResponse> searchAccounts(
            UUID customerId, AccountStatus status, AccountType accountType,
            BigDecimal minBalance, Pageable pageable, AuthenticatedUser caller) {

        // Customers can only search within their own accounts
        if (!caller.isStaff()) {
            if (customerId != null && !customerId.equals(caller.userId())) {
                throw new ForbiddenException("You can only search your own accounts");
            }
            customerId = caller.userId(); // force filter to their own accounts
        }

        Page<Account> page = accountRepository.searchAccounts(customerId, status, accountType, minBalance, pageable);
        return toPagedResponse(page);
    }

    public CustomerBalanceSummaryResponse getBalanceSummary(UUID customerId, AuthenticatedUser caller) {
        requireOwnershipOrStaff(customerId, caller);
        CustomerBalanceSummary summary = accountRepository.getBalanceSummary(customerId);
        return CustomerBalanceSummaryResponse.builder()
                .customerId(customerId)
                .accountCount(summary.getAccountCount() != null ? summary.getAccountCount() : 0)
                .totalBalance(summary.getTotalBalance() != null ? summary.getTotalBalance() : BigDecimal.ZERO)
                .highestBalance(summary.getHighestBalance() != null ? summary.getHighestBalance() : BigDecimal.ZERO)
                .lowestBalance(summary.getLowestBalance() != null ? summary.getLowestBalance() : BigDecimal.ZERO)
                .build();
    }

    @Transactional
    public AccountResponse updateAccount(UUID id, UpdateAccountRequest request) {
        Account account = findAccountOrThrow(id);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("ACCOUNT_CLOSED", "Cannot update a closed account");
        }

        if (request.getAccountHolderName() != null) {
            account.setAccountHolderName(request.getAccountHolderName());
        }
        if (request.getStatus() != null) {
            account.setStatus(request.getStatus());
        }
        if (request.getBranchCode() != null) {
            account.setBranchCode(request.getBranchCode());
        }

        Account updated = accountRepository.save(account);
        log.info("Account updated: {}", updated.getAccountNumber());
        return accountMapper.toResponse(updated);
    }

    @Transactional
    public void closeAccount(UUID id) {
        Account account = findAccountOrThrow(id);

        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleException("BALANCE_NOT_ZERO",
                    "Account balance must be zero before closing. Current balance: " + account.getBalance());
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);
        log.info("Account closed: {}", account.getAccountNumber());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Throws ForbiddenException if the caller is a CUSTOMER and the resource
     * does not belong to them. Staff (TELLER, MANAGER, ADMIN) pass through.
     */
    private void requireOwnershipOrStaff(UUID resourceCustomerId, AuthenticatedUser caller) {
        if (!caller.isStaff() && !resourceCustomerId.equals(caller.userId())) {
            throw new ForbiddenException("You do not have access to this account");
        }
    }

    private Account findAccountOrThrow(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
    }

    /**
     * Generates a bank-style account number.
     * Format: 3-digit institution code + 5-digit transit + 7-digit account
     * In production, this would use a sequence or external service.
     */
    private String generateAccountNumber() {
        long number = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_999_999_999_999L);
        String accountNumber = String.valueOf(number);

        while (accountRepository.existsByAccountNumber(accountNumber)) {
            number = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_999_999_999_999L);
            accountNumber = String.valueOf(number);
        }

        return accountNumber;
    }

    private PagedResponse<AccountResponse> toPagedResponse(Page<Account> page) {
        List<AccountResponse> content = page.getContent().stream()
                .map(accountMapper::toResponse)
                .toList();

        return PagedResponse.<AccountResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
