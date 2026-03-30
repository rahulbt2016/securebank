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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
    private UUID ownerId;
    private AuthenticatedUser staffCaller;
    private AuthenticatedUser ownerCaller;
    private AuthenticatedUser otherCustomerCaller;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        sampleAccount = new Account();
        sampleAccount.setId(accountId);
        sampleAccount.setAccountNumber("1234567890123");
        sampleAccount.setCustomerId(ownerId);
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

        staffCaller         = new AuthenticatedUser(UUID.randomUUID(), "admin@securebank.ca", "ADMIN");
        ownerCaller         = new AuthenticatedUser(ownerId, "jane@example.com", "CUSTOMER");
        otherCustomerCaller = new AuthenticatedUser(UUID.randomUUID(), "other@example.com", "CUSTOMER");
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
        @DisplayName("should return account when staff calls")
        void shouldReturnAccountForStaff() {
            given(accountRepository.findById(accountId)).willReturn(Optional.of(sampleAccount));
            given(accountMapper.toResponse(sampleAccount)).willReturn(sampleResponse);

            AccountResponse result = accountService.getAccountById(accountId, staffCaller);

            assertThat(result.getId()).isEqualTo(accountId);
        }

        @Test
        @DisplayName("should return account when owner calls")
        void shouldReturnAccountForOwner() {
            given(accountRepository.findById(accountId)).willReturn(Optional.of(sampleAccount));
            given(accountMapper.toResponse(sampleAccount)).willReturn(sampleResponse);

            AccountResponse result = accountService.getAccountById(accountId, ownerCaller);

            assertThat(result.getId()).isEqualTo(accountId);
        }

        @Test
        @DisplayName("should throw ForbiddenException when customer accesses another's account")
        void shouldThrowForbiddenForNonOwner() {
            given(accountRepository.findById(accountId)).willReturn(Optional.of(sampleAccount));

            assertThatThrownBy(() -> accountService.getAccountById(accountId, otherCustomerCaller))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            given(accountRepository.findById(accountId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountById(accountId, staffCaller))
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
    @DisplayName("searchAccounts")
    class SearchAccounts {

        @Test
        @DisplayName("should return paginated results for staff with given filters")
        void shouldReturnPagedResultsForStaff() {
            PageRequest pageable = PageRequest.of(0, 20);
            given(accountRepository.searchAccounts(isNull(), eq(AccountStatus.ACTIVE), isNull(), isNull(), eq(pageable)))
                    .willReturn(new PageImpl<>(List.of(sampleAccount)));
            given(accountMapper.toResponse(sampleAccount)).willReturn(sampleResponse);

            PagedResponse<AccountResponse> result = accountService.searchAccounts(
                    null, AccountStatus.ACTIVE, null, null, pageable, staffCaller);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("should force customerId to caller's own ID when customer searches")
        void shouldForceCustomerIdForCustomerCaller() {
            PageRequest pageable = PageRequest.of(0, 20);
            given(accountRepository.searchAccounts(eq(ownerId), isNull(), isNull(), isNull(), eq(pageable)))
                    .willReturn(new PageImpl<>(List.of(sampleAccount)));
            given(accountMapper.toResponse(sampleAccount)).willReturn(sampleResponse);

            // Customer passes null — service must fill in their own ID
            PagedResponse<AccountResponse> result = accountService.searchAccounts(
                    null, null, null, null, pageable, ownerCaller);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should throw ForbiddenException when customer tries to search another's accounts")
        void shouldThrowWhenCustomerSearchesOthersAccounts() {
            PageRequest pageable = PageRequest.of(0, 20);
            UUID someOtherCustomer = UUID.randomUUID();

            assertThatThrownBy(() -> accountService.searchAccounts(
                    someOtherCustomer, null, null, null, pageable, otherCustomerCaller))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("getBalanceSummary")
    class GetBalanceSummary {

        @Test
        @DisplayName("should return summary with mapped values from projection")
        void shouldReturnBalanceSummary() {
            CustomerBalanceSummary projection = mock(CustomerBalanceSummary.class);
            given(projection.getAccountCount()).willReturn(2L);
            given(projection.getTotalBalance()).willReturn(BigDecimal.valueOf(5000));
            given(projection.getHighestBalance()).willReturn(BigDecimal.valueOf(3000));
            given(projection.getLowestBalance()).willReturn(BigDecimal.valueOf(2000));
            given(accountRepository.getBalanceSummary(ownerId)).willReturn(projection);

            CustomerBalanceSummaryResponse result = accountService.getBalanceSummary(ownerId, staffCaller);

            assertThat(result.getCustomerId()).isEqualTo(ownerId);
            assertThat(result.getAccountCount()).isEqualTo(2L);
            assertThat(result.getTotalBalance()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        @DisplayName("should return zero values when customer has no active accounts")
        void shouldHandleCustomerWithNoAccounts() {
            CustomerBalanceSummary projection = mock(CustomerBalanceSummary.class);
            given(projection.getAccountCount()).willReturn(0L);
            given(projection.getTotalBalance()).willReturn(null);
            given(projection.getHighestBalance()).willReturn(null);
            given(projection.getLowestBalance()).willReturn(null);
            given(accountRepository.getBalanceSummary(ownerId)).willReturn(projection);

            CustomerBalanceSummaryResponse result = accountService.getBalanceSummary(ownerId, ownerCaller);

            assertThat(result.getAccountCount()).isZero();
            assertThat(result.getTotalBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getHighestBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getLowestBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should throw ForbiddenException when customer requests another's summary")
        void shouldThrowForbiddenForNonOwner() {
            assertThatThrownBy(() -> accountService.getBalanceSummary(ownerId, otherCustomerCaller))
                    .isInstanceOf(ForbiddenException.class);
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
