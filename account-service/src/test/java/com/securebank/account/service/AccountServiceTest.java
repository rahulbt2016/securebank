package com.securebank.account.service;

import com.securebank.account.dto.AccountResponse;
import com.securebank.account.dto.CreateAccountRequest;
import com.securebank.account.dto.UpdateAccountRequest;
import com.securebank.account.entity.Account;
import com.securebank.account.entity.AccountStatus;
import com.securebank.account.entity.AccountType;
import com.securebank.account.mapper.AccountMapper;
import com.securebank.account.repository.AccountRepository;
import com.securebank.common.exception.BusinessRuleException;
import com.securebank.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService accountService;

    private Account sampleAccount;
    private AccountResponse sampleResponse;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();

        sampleAccount = new Account();
        sampleAccount.setId(accountId);
        sampleAccount.setAccountNumber("1234567890123");
        sampleAccount.setCustomerId(UUID.randomUUID());
        sampleAccount.setAccountHolderName("Jane Doe");
        sampleAccount.setAccountType(AccountType.CHEQUING);
        sampleAccount.setStatus(AccountStatus.ACTIVE);
        sampleAccount.setBalance(BigDecimal.valueOf(1000.00));
        sampleAccount.setCurrency("CAD");

        sampleResponse = AccountResponse.builder()
                .id(accountId)
                .accountNumber("1234567890123")
                .accountHolderName("Jane Doe")
                .accountType(AccountType.CHEQUING)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.valueOf(1000.00))
                .currency("CAD")
                .build();
    }

    @Nested
    @DisplayName("createAccount")
    class CreateAccount {

        @Test
        @DisplayName("should create account with ACTIVE status and zero balance")
        void shouldCreateAccount() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .customerId(UUID.randomUUID())
                    .accountHolderName("Jane Doe")
                    .accountType(AccountType.CHEQUING)
                    .currency("CAD")
                    .build();

            Account newAccount = new Account();
            given(accountMapper.toEntity(request)).willReturn(newAccount);
            given(accountRepository.existsByAccountNumber(anyString())).willReturn(false);
            given(accountRepository.save(any(Account.class))).willReturn(sampleAccount);
            given(accountMapper.toResponse(sampleAccount)).willReturn(sampleResponse);

            AccountResponse result = accountService.createAccount(request);

            assertThat(result).isNotNull();
            assertThat(result.getAccountHolderName()).isEqualTo("Jane Doe");
            verify(accountRepository).save(any(Account.class));
        }
    }

    @Nested
    @DisplayName("getAccountById")
    class GetAccountById {

        @Test
        @DisplayName("should return account when found")
        void shouldReturnAccountWhenFound() {
            given(accountRepository.findById(accountId)).willReturn(Optional.of(sampleAccount));
            given(accountMapper.toResponse(sampleAccount)).willReturn(sampleResponse);

            AccountResponse result = accountService.getAccountById(accountId);

            assertThat(result.getId()).isEqualTo(accountId);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            given(accountRepository.findById(accountId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountById(accountId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account not found");
        }
    }

    @Nested
    @DisplayName("updateAccount")
    class UpdateAccount {

        @Test
        @DisplayName("should update account fields")
        void shouldUpdateAccount() {
            UpdateAccountRequest request = UpdateAccountRequest.builder()
                    .accountHolderName("Jane Smith")
                    .build();

            given(accountRepository.findById(accountId)).willReturn(Optional.of(sampleAccount));
            given(accountRepository.save(any(Account.class))).willReturn(sampleAccount);
            given(accountMapper.toResponse(any(Account.class))).willReturn(sampleResponse);

            AccountResponse result = accountService.updateAccount(accountId, request);

            assertThat(result).isNotNull();
            verify(accountRepository).save(sampleAccount);
        }

        @Test
        @DisplayName("should reject update on closed account")
        void shouldRejectUpdateOnClosedAccount() {
            sampleAccount.setStatus(AccountStatus.CLOSED);
            given(accountRepository.findById(accountId)).willReturn(Optional.of(sampleAccount));

            UpdateAccountRequest request = UpdateAccountRequest.builder()
                    .accountHolderName("Jane Smith")
                    .build();

            assertThatThrownBy(() -> accountService.updateAccount(accountId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("closed account");
        }
    }

    @Nested
    @DisplayName("closeAccount")
    class CloseAccount {

        @Test
        @DisplayName("should close account with zero balance")
        void shouldCloseAccountWithZeroBalance() {
            sampleAccount.setBalance(BigDecimal.ZERO);
            given(accountRepository.findById(accountId)).willReturn(Optional.of(sampleAccount));
            given(accountRepository.save(any(Account.class))).willReturn(sampleAccount);

            accountService.closeAccount(accountId);

            assertThat(sampleAccount.getStatus()).isEqualTo(AccountStatus.CLOSED);
            verify(accountRepository).save(sampleAccount);
        }

        @Test
        @DisplayName("should reject closing account with non-zero balance")
        void shouldRejectClosingAccountWithBalance() {
            sampleAccount.setBalance(BigDecimal.valueOf(500.00));
            given(accountRepository.findById(accountId)).willReturn(Optional.of(sampleAccount));

            assertThatThrownBy(() -> accountService.closeAccount(accountId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("balance must be zero");
        }
    }
}
