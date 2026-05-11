package com.credvenn.lm.fineract;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class FineractDtos {

    private FineractDtos() {
    }

    @Schema(name = "LoanProductResponse")
    public record LoanProductResponse(
            String id,
            String name,
            String shortName,
            BigDecimal minPrincipal,
            BigDecimal maxPrincipal,
            Integer minTermMonths,
            Integer maxTermMonths,
            Integer loanType,
            Integer interestType,
            Integer interestCalculationPeriodType,
            BigDecimal interestRatePerPeriod,
            Integer amortizationType,
            Integer interestRateFrequencyType,
            Integer repaymentEvery,
            Integer repaymentFrequencyType,
            Integer numberOfRepayments,
            String currencyCode) {

        public static LoanProductResponse from(FineractLoanProduct product) {
            return new LoanProductResponse(
                    product.id(),
                    product.name(),
                    product.shortName(),
                    product.minPrincipal(),
                    product.maxPrincipal(),
                    product.minTermMonths(),
                    product.maxTermMonths(),
                    product.loanType(),
                    product.interestType(),
                    product.interestCalculationPeriodType(),
                    product.interestRatePerPeriod(),
                    product.amortizationType(),
                    product.interestRateFrequencyType(),
                    product.repaymentEvery(),
                    product.repaymentFrequencyType(),
                    product.numberOfRepayments(),
                    product.currencyCode());
        }
    }

    @Schema(name = "LoanRepaymentResponse")
    public record LoanRepaymentResponse(
            String id,
            Instant transactionDate,
            BigDecimal amount,
            BigDecimal principalPortion,
            BigDecimal interestPortion,
            BigDecimal feeChargesPortion,
            BigDecimal penaltyChargesPortion,
            BigDecimal overpaymentPortion,
            BigDecimal outstandingLoanBalance,
            boolean reversed,
            String typeCode,
            String typeValue) {

        public static LoanRepaymentResponse from(FineractGateway.LoanRepayment repayment) {
            return new LoanRepaymentResponse(
                    repayment.id(),
                    repayment.transactionDate(),
                    repayment.amount(),
                    repayment.principalPortion(),
                    repayment.interestPortion(),
                    repayment.feeChargesPortion(),
                    repayment.penaltyChargesPortion(),
                    repayment.overpaymentPortion(),
                    repayment.outstandingLoanBalance(),
                    repayment.reversed(),
                    repayment.typeCode(),
                    repayment.typeValue());
        }
    }

    @Schema(name = "LoanRepaymentListResponse")
    public record LoanRepaymentListResponse(List<LoanRepaymentResponse> repayments) {
    }
}
