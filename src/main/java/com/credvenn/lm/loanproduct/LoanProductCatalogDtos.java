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

    @Schema(name = "CreateLoanProductRequest")
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
            @NotNull Boolean active) {
    }

    public record PrincipalRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal min,
            @NotNull @DecimalMin(value = "0.01") BigDecimal defaultAmount,
            @NotNull @DecimalMin(value = "0.01") BigDecimal max) {
    }

    public record TermRequest(
            @NotNull Integer numberOfRepayments,
            @NotNull Integer repaymentEvery,
            @NotBlank String repaymentFrequency) {
    }

    public record InterestRequest(
            @NotNull @DecimalMin(value = "0.0") BigDecimal ratePerPeriod,
            @NotBlank String interestType,
            @NotBlank String calculationPeriodType,
            @NotBlank String rateFrequency) {
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
            String fineractProductId,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {

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
                    String.valueOf(mapping.getFineractProductId()),
                    mapping.isActive(),
                    mapping.getCreatedAt(),
                    mapping.getUpdatedAt());
        }
    }
}
