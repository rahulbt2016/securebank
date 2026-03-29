package com.securebank.account.repository.projection;

import java.math.BigDecimal;

/**
 * Spring Data projection interface for the native balance summary query.
 * Spring automatically maps each column alias in the SQL result to the
 * matching getter here (e.g. "account_count" → getAccountCount()).
 */
public interface CustomerBalanceSummary {

    Long getAccountCount();

    BigDecimal getTotalBalance();

    BigDecimal getHighestBalance();

    BigDecimal getLowestBalance();
}
