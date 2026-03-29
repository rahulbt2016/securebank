package com.securebank.account.controller;

import com.securebank.account.dto.AccountResponse;
import com.securebank.account.dto.CreateAccountRequest;
import com.securebank.account.dto.CustomerBalanceSummaryResponse;
import com.securebank.account.dto.UpdateAccountRequest;
import com.securebank.account.entity.AccountStatus;
import com.securebank.account.entity.AccountType;
import com.securebank.account.service.AccountService;
import com.securebank.common.dto.ApiResponse;
import com.securebank.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Account Management", description = "CRUD operations for bank accounts")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @Operation(summary = "Create a new bank account")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse account = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(@PathVariable UUID id) {
        AccountResponse account = accountService.getAccountById(id);
        return ResponseEntity.ok(ApiResponse.ok(account));
    }

    @GetMapping("/number/{accountNumber}")
    @Operation(summary = "Get account by account number")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountByNumber(
            @PathVariable String accountNumber) {
        AccountResponse account = accountService.getAccountByNumber(accountNumber);
        return ResponseEntity.ok(ApiResponse.ok(account));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get all accounts for a customer")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccountsByCustomer(
            @PathVariable UUID customerId) {
        List<AccountResponse> accounts = accountService.getAccountsByCustomerId(customerId);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping
    @Operation(summary = "Get all accounts (paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<AccountResponse>>> getAllAccounts(
            @Parameter(hidden = true) @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        PagedResponse<AccountResponse> accounts = accountService.getAllAccounts(pageable);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping("/search")
    @Operation(summary = "Search accounts with optional filters",
               description = "All parameters are optional — omit any to skip that filter")
    public ResponseEntity<ApiResponse<PagedResponse<AccountResponse>>> searchAccounts(
            @Parameter(description = "Filter by customer ID")       @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Filter by account status")    @RequestParam(required = false) AccountStatus status,
            @Parameter(description = "Filter by account type")      @RequestParam(required = false) AccountType accountType,
            @Parameter(description = "Minimum balance (inclusive)") @RequestParam(required = false) java.math.BigDecimal minBalance,
            @Parameter(hidden = true) @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        PagedResponse<AccountResponse> accounts = accountService.searchAccounts(customerId, status, accountType, minBalance, pageable);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping("/customer/{customerId}/summary")
    @Operation(summary = "Get balance summary for a customer",
               description = "Returns total, highest and lowest balance across all non-closed accounts")
    public ResponseEntity<ApiResponse<CustomerBalanceSummaryResponse>> getBalanceSummary(
            @PathVariable UUID customerId) {
        CustomerBalanceSummaryResponse summary = accountService.getBalanceSummary(customerId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountRequest request) {
        AccountResponse account = accountService.updateAccount(id, request);
        return ResponseEntity.ok(ApiResponse.ok(account));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Close an account (balance must be zero)")
    public ResponseEntity<ApiResponse<Void>> closeAccount(@PathVariable UUID id) {
        accountService.closeAccount(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
