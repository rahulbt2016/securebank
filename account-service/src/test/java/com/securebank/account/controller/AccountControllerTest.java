package com.securebank.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.account.dto.AccountResponse;
import com.securebank.account.dto.CreateAccountRequest;
import com.securebank.account.dto.CustomerBalanceSummaryResponse;
import com.securebank.account.entity.AccountStatus;
import com.securebank.account.entity.AccountType;
import com.securebank.account.service.AccountService;
import com.securebank.common.dto.PagedResponse;
import com.securebank.common.exception.GlobalExceptionHandler;
import com.securebank.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    @Test
    @DisplayName("POST /api/v1/accounts - should create account and return 201")
    void shouldCreateAccount() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        CreateAccountRequest request = CreateAccountRequest.builder()
                .customerId(customerId)
                .accountHolderName("Jane Doe")
                .accountType(AccountType.CHEQUING)
                .currency("CAD")
                .build();

        AccountResponse response = AccountResponse.builder()
                .id(accountId)
                .accountNumber("1234567890123")
                .customerId(customerId)
                .accountHolderName("Jane Doe")
                .accountType(AccountType.CHEQUING)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .currency("CAD")
                .build();

        given(accountService.createAccount(any(CreateAccountRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountHolderName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/accounts - should return 400 for invalid request")
    void shouldReturn400ForInvalidRequest() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .customerId(null)          // required
                .accountHolderName("")     // required, min 2 chars
                .accountType(null)         // required
                .currency(null)            // required
                .build();

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors").exists());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - should return 404 for non-existent account")
    void shouldReturn404ForNonExistentAccount() throws Exception {
        UUID id = UUID.randomUUID();
        given(accountService.getAccountById(id))
                .willThrow(new ResourceNotFoundException("Account", "id", id));

        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/search - should return paginated results")
    void shouldSearchAccounts() throws Exception {
        UUID accountId = UUID.randomUUID();
        AccountResponse response = AccountResponse.builder()
                .id(accountId)
                .accountNumber("1234567890123")
                .accountHolderName("Jane Doe")
                .accountType(AccountType.CHEQUING)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.valueOf(1500.00))
                .currency("CAD")
                .build();

        PagedResponse<AccountResponse> pagedResponse = PagedResponse.<AccountResponse>builder()
                .content(List.of(response))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();

        given(accountService.searchAccounts(any(), any(), any(), any(), any())).willReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/accounts/search")
                        .param("status", "ACTIVE")
                        .param("accountType", "CHEQUING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/customer/{customerId}/summary - should return balance summary")
    void shouldReturnBalanceSummary() throws Exception {
        UUID customerId = UUID.randomUUID();

        CustomerBalanceSummaryResponse summary = CustomerBalanceSummaryResponse.builder()
                .customerId(customerId)
                .accountCount(2)
                .totalBalance(BigDecimal.valueOf(5000))
                .highestBalance(BigDecimal.valueOf(3000))
                .lowestBalance(BigDecimal.valueOf(2000))
                .build();

        given(accountService.getBalanceSummary(customerId)).willReturn(summary);

        mockMvc.perform(get("/api/v1/accounts/customer/{customerId}/summary", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountCount").value(2))
                .andExpect(jsonPath("$.data.totalBalance").value(5000));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - should return account")
    void shouldReturnAccount() throws Exception {
        UUID accountId = UUID.randomUUID();

        AccountResponse response = AccountResponse.builder()
                .id(accountId)
                .accountNumber("1234567890123")
                .accountHolderName("Jane Doe")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.valueOf(5000.00))
                .currency("CAD")
                .build();

        given(accountService.getAccountById(accountId)).willReturn(response);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(accountId.toString()))
                .andExpect(jsonPath("$.data.accountType").value("SAVINGS"));
    }
}
