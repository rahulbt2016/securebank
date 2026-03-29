package com.securebank.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerBalanceSummaryResponse {

    private UUID customerId;
    private long accountCount;
    private BigDecimal totalBalance;
    private BigDecimal highestBalance;
    private BigDecimal lowestBalance;
}
