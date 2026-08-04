package com.credvenn.lm.statement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
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
            Instant updatedAt) {
    }

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
