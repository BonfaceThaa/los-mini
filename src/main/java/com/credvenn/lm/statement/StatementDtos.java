package com.credvenn.lm.statement;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.credvenn.stmt.analyzer.core.AnalysisResult;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class StatementDtos {

    private StatementDtos() {
    }

    @Schema(name = "StatementTransactionResponse")
    public record StatementTransactionResponse(
            String id,
            String externalTransactionId,
            String transactionType,
            BigDecimal transactionAmount,
            String transactionDate,
            String narration,
            BigDecimal balance,
            String currency) {
    }

    @Schema(name = "StatementLoanSummaryResponse")
    public record StatementLoanSummaryResponse(
            String lender,
            BigDecimal amountBorrowed,
            BigDecimal amountRepaid,
            Integer timesTaken,
            Integer timesRepaid,
            String status,
            String lastActivityDate) {
    }

    @Schema(name = "StatementProviderAnalysisResponse")
    public record StatementProviderAnalysisResponse(
            String id,
            String applicationId,
            String provider,
            StatementAnalysisStatus status,
            String sourceDocumentId,
            BigDecimal averageMonthlyInflow,
            BigDecimal averageMonthlyOutflow,
            BigDecimal affordabilityScore,
            Integer creditScore,
            String riskTier,
            String recommendation,
            String summary,
            String lastAnalyzedOn,
            BigDecimal totalIn,
            BigDecimal totalOut,
            List<StatementLoanSummaryResponse> loans,
            List<StatementTransactionResponse> transactions,
            Instant createdAt,
            Instant updatedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) StatementSummaryResponse statement,
            @JsonInclude(JsonInclude.Include.NON_NULL) MonthlySummaryResponse monthlySummary,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<MonthlyMetricResponse> monthlyMetrics,
            @JsonInclude(JsonInclude.Include.NON_NULL) CategorySummaryResponse categories,
            @JsonInclude(JsonInclude.Include.NON_NULL) StatementQualityResponse quality) {
    }

    public record StatementSummaryResponse(String currency, LocalDate periodStart, LocalDate periodEnd,
            int transactionCount, int rejectedRowCount, BigDecimal totalInflow, BigDecimal totalOutflow) { }

    public record MonthlySummaryResponse(LocalDate periodStart, LocalDate periodEnd, int completeMonthCount,
            BigDecimal averageInflow, BigDecimal averageOutflow, BigDecimal averageEligibleInflow,
            BigDecimal averageDebtPayments, BigDecimal averageSpending, BigDecimal averageMonthlyBalance) { }

    public record MonthlyMetricResponse(YearMonth month, AnalysisResult.CoverageStatus coverageStatus,
            AnalysisResult.Status status, BigDecimal totalCredits, BigDecimal totalDebits,
            BigDecimal verifiedInflows, BigDecimal eligibleInflows, BigDecimal eligibleDebtPayments,
            BigDecimal eligibleSpending, BigDecimal excludedCredits, BigDecimal excludedDebits,
            BigDecimal unresolvedCredits, BigDecimal unresolvedDebits, BigDecimal monthlyBalance,
            BigDecimal netCashMovement, BigDecimal openingBalance, BigDecimal closingBalance,
            List<AnalysisResult.Diagnostic> warnings, AnalysisResult.IncomeCalculation incomeCalculation) { }

    public record CategorySummaryResponse(String statementAnalysisId, String currency, long totalTransactionCount,
            List<CategoryResponse> categories) { }
    public record CategoryResponse(String mainCategory, long transactionCount, BigDecimal totalAmount,
            BigDecimal totalCredits, BigDecimal totalDebits, List<SubCategoryResponse> subCategories,
            List<LoanRepaymentProviderResponse> loanRepaymentProviders) { }
    public record SubCategoryResponse(String subCategory, long transactionCount, BigDecimal totalAmount,
            BigDecimal totalCredits, BigDecimal totalDebits) { }
    public record LoanRepaymentProviderResponse(String loanProvider, long transactionCount, BigDecimal totalRepaid) { }
    public record StatementQualityResponse(AnalysisResult.Status status, BigDecimal parsingConfidence,
            List<AnalysisResult.Diagnostic> warnings, List<AnalysisResult.Diagnostic> reconciliationErrors,
            String parserVersion, String classificationVersion, String metricPolicyVersion) { }

    @Schema(name = "StatementReviewResponse")
    public record StatementReviewResponse(
            String id,
            String applicationId,
            String statementAnalysisId,
            StatementReviewDecision decision,
            StatementReviewSource decisionSource,
            String reason,
            String reviewedBy,
            Instant reviewedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "StatementAssessmentResponse")
    public record StatementAssessmentResponse(
            StatementProviderAnalysisResponse analysis,
            StatementReviewResponse review,
            StatementEffectiveOutcome effectiveOutcome) {
    }

    @Schema(name = "ManualStatementPassRequest")
    public record ManualStatementPassRequest(@Size(max = 1000) String summary) {
    }
}
