package com.securebank.account.repository.projection;

import java.math.BigDecimal;

/**
 * Spring Data projection interface for the native balance summary query.
 * Spring maps each SQL column alias to the matching getter (case-insensitive).
 * e.g. SQL alias "accountCount" → getAccountCount()
 */
public interface CustomerBalanceSummary {

    Long getAccountCount();

    BigDecimal getTotalBalance();

    BigDecimal getHighestBalance();

    BigDecimal getLowestBalance();
}
