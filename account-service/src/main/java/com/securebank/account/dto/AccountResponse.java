package com.securebank.account.dto;

import com.securebank.account.entity.AccountStatus;
import com.securebank.account.entity.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private UUID id;
    private String accountNumber;
    private UUID customerId;
    private String accountHolderName;
    private AccountType accountType;
    private AccountStatus status;
    private BigDecimal balance;
    private String currency;
    private String branchCode;
    private Instant createdAt;
    private Instant updatedAt;
}
