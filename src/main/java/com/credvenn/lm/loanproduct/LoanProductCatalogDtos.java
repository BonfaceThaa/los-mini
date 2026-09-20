package com.credvenn.lm.loanproduct;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class LoanProductCatalogDtos {

    private LoanProductCatalogDtos() {
    }

    public static final String LOGBOOK_EXAMPLE = """
            {
              "productCode": "LOGBOOK_12_MONTHS",
              "displayName": "Logbook Loan - 12 Months",
              "shortName": "LB12",
              "description": "Cash loan secured by a motor vehicle",
              "currencyCode": "KES",
              "principal": {"min": 50000, "defaultAmount": 300000, "max": 2000000},
              "term": {"numberOfRepayments": 12, "repaymentEvery": 1, "repaymentFrequency": "MONTHS"},
              "interest": {"ratePerPeriod": 2.0, "interestType": "DECLINING_BALANCE",
                           "calculationPeriodType": "SAME_AS_REPAYMENT_PERIOD", "rateFrequency": "MONTHS"},
              "amortizationType": "EQUAL_INSTALLMENTS",
              "accountingTemplateCode": "STANDARD",
              "active": false,
              "originationProfileCode": "LOGBOOK"
            }
            """;

    @Schema(name = "CreateLoanProductRequest", description = "Tenant product configuration. Profile code omitted/null uses the active tenant default; an explicit code may target a draft profile. Example terms are illustrative, not a prescribed lending policy.", example = LOGBOOK_EXAMPLE)
    public record CreateLoanProductRequest(
            @NotBlank @Size(max = 100) String productCode,
            @NotBlank String displayName,
            @NotBlank @Size(max = 4) String shortName,
            String description,
            @NotBlank @Size(max = 10) String currencyCode,
            @NotNull @Valid PrincipalRequest principal,
            @NotNull @Valid TermRequest term,
            @NotNull @Valid InterestRequest interest,
            @NotBlank String amortizationType,
            String transactionProcessingStrategyCode,
            @NotBlank String accountingTemplateCode,
            @Schema(description = "Optional: omit to resolve the tenant GL accounts by business purpose; if supplied, all nine IDs are required.")
            @Valid AccountingAccountsRequest accountingAccounts,
            @NotNull Boolean active,
            @Schema(description = "Tenant-local profile code. Omitted/null uses the active tenant default. Draft profiles may be associated explicitly.", example = "LOGBOOK")
            @Size(max = 100) String originationProfileCode) {
    }

    @Schema(name = "UpdateLoanProductRequest")
    public record UpdateLoanProductRequest(
            @Size(max = 255) String displayName,
            @Size(max = 4) String shortName,
            String description,
            @Size(max = 10) String currencyCode,
            @Valid PrincipalPatchRequest principal,
            @Valid TermPatchRequest term,
            @Valid InterestPatchRequest interest,
            String amortizationType,
            String transactionProcessingStrategyCode,
            @Size(max = 100) String accountingTemplateCode,
            @Schema(description = "Optional: omit to preserve existing accounting accounts; if supplied, all nine IDs are required.")
            @Valid AccountingAccountsRequest accountingAccounts,
            Boolean active) {
    }

    public record PrincipalRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal min,
            @NotNull @DecimalMin(value = "0.01") BigDecimal defaultAmount,
            @NotNull @DecimalMin(value = "0.01") BigDecimal max) {
    }

    public record PrincipalPatchRequest(
            @DecimalMin(value = "0.01") BigDecimal min,
            @DecimalMin(value = "0.01") BigDecimal defaultAmount,
            @DecimalMin(value = "0.01") BigDecimal max) {
    }

    public record TermRequest(
            @NotNull Integer numberOfRepayments,
            @NotNull Integer repaymentEvery,
            @NotBlank String repaymentFrequency) {
    }

    public record TermPatchRequest(
            Integer numberOfRepayments,
            Integer repaymentEvery,
            String repaymentFrequency) {
    }

    public record InterestRequest(
            @NotNull @DecimalMin(value = "0.0") BigDecimal ratePerPeriod,
            @NotBlank String interestType,
            @NotBlank String calculationPeriodType,
            @NotBlank String rateFrequency) {
    }

    public record InterestPatchRequest(
            @DecimalMin(value = "0.0") BigDecimal ratePerPeriod,
            String interestType,
            String calculationPeriodType,
            String rateFrequency) {
    }

    public record AccountingAccountsRequest(
            Long loanPortfolioAccountId,
            Long fundSourceAccountId,
            Long interestOnLoanAccountId,
            Long incomeFromFeeAccountId,
            Long incomeFromPenaltyAccountId,
            Long incomeFromRecoveryAccountId,
            Long writeOffAccountId,
            Long transfersInSuspenseAccountId,
            Long overpaymentLiabilityAccountId) {
    }

    @Schema(name = "LoanProductCatalogResponse")
    public record LoanProductCatalogResponse(
            String id,
            String tenantId,
            String productCode,
            String displayName,
            String shortName,
            String description,
            String currencyCode,
            PrincipalRequest principal,
            TermRequest term,
            InterestRequest interest,
            String amortizationType,
            String transactionProcessingStrategyCode,
            String accountingTemplateCode,
            AccountingAccountsRequest accountingAccounts,
            @Schema(deprecated = true, description = "Legacy remote identifier. New clients select offers using productCode.") String fineractProductId,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            @Schema(description = "Resolved local origination profile ID") String originationProfileId) {

        public static LoanProductCatalogResponse from(LoanProductMapping mapping) {
            return new LoanProductCatalogResponse(
                    mapping.getId(),
                    mapping.getTenantId(),
                    mapping.getProductCode(),
                    mapping.getDisplayName(),
                    mapping.getShortName(),
                    mapping.getDescription(),
                    mapping.getCurrencyCode(),
                    new PrincipalRequest(
                            mapping.getPrincipalMin(),
                            mapping.getPrincipalDefaultAmount(),
                            mapping.getPrincipalMax()),
                    new TermRequest(
                            mapping.getNumberOfRepayments(),
                            mapping.getRepaymentEvery(),
                            mapping.getRepaymentFrequency()),
                    new InterestRequest(
                            mapping.getInterestRatePerPeriod(),
                            mapping.getInterestType(),
                            mapping.getInterestCalculationPeriodType(),
                            mapping.getInterestRateFrequency()),
                    mapping.getAmortizationType(),
                    mapping.getTransactionProcessingStrategyCode(),
                    mapping.getAccountingTemplateCode(),
                    null,
                    String.valueOf(mapping.getFineractProductId()),
                    mapping.isActive(),
                    mapping.getCreatedAt(),
                    mapping.getUpdatedAt(), mapping.getOriginationProfileId());
        }
    }
}
