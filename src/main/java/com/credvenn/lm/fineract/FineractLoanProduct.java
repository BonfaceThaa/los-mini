package com.credvenn.lm.fineract;

import java.math.BigDecimal;

public record FineractLoanProduct(
        String id,
        String name,
        String shortName,
        BigDecimal minPrincipal,
        BigDecimal maxPrincipal,
        Integer minNumberOfRepayments,
        Integer maxNumberOfRepayments,
        Integer loanType,
        Integer interestType,
        Integer interestCalculationPeriodType,
        BigDecimal interestRatePerPeriod,
        Integer amortizationType,
        Integer interestRateFrequencyType,
        Integer repaymentEvery,
        Integer repaymentFrequencyType,
        Integer numberOfRepayments,
        String currencyCode,
        boolean active) {
}
