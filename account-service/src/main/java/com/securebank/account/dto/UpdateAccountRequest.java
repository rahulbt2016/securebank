package com.securebank.account.dto;

import com.securebank.account.entity.AccountStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccountRequest {

    @Size(min = 2, max = 100, message = "Account holder name must be between 2 and 100 characters")
    private String accountHolderName;

    private AccountStatus status;

    @Size(max = 50, message = "Branch code must be at most 50 characters")
    private String branchCode;
}
