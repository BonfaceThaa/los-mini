package com.credvenn.stmt.analyzer.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/** Immutable result contracts. Nullable values mean unknown/unavailable, never assumed zero.
 * Optional scoring fields are omitted from this MVP contract.
 */
public record AnalysisResult(
        String correlationId, Status status, StatementPeriod statementPeriod, String currency,
        List<NormalizedTransaction> transactions, BigDecimal totalInflow, BigDecimal totalOutflow,
        List<MonthlyMetrics> monthlyBreakdown, MonthlyAverages monthlyAverages,
        List<Diagnostic> warnings, List<Diagnostic> reconciliationErrors,
        BigDecimal parsingConfidence, Versions versions, List<RejectedTransaction> rejectedRows) {
    public AnalysisResult(String correlationId, Status status, StatementPeriod statementPeriod, String currency,
            List<NormalizedTransaction> transactions, BigDecimal totalInflow, BigDecimal totalOutflow,
            List<MonthlyMetrics> monthlyBreakdown, MonthlyAverages monthlyAverages,
            List<Diagnostic> warnings, List<Diagnostic> reconciliationErrors, BigDecimal parsingConfidence, Versions versions) {
        this(correlationId, status, statementPeriod, currency, transactions, totalInflow, totalOutflow,
                monthlyBreakdown, monthlyAverages, warnings, reconciliationErrors, parsingConfidence, versions, List.of());
    }
    public AnalysisResult {
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(status, "status");
        transactions = List.copyOf(transactions);
        monthlyBreakdown = List.copyOf(monthlyBreakdown);
        warnings = List.copyOf(warnings);
        reconciliationErrors = List.copyOf(reconciliationErrors);
        rejectedRows = List.copyOf(rejectedRows);
        Objects.requireNonNull(versions, "versions");
        if (parsingConfidence != null && (parsingConfidence.signum() < 0
                || parsingConfidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("Parsing confidence must be between 0 and 1");
        }
    }

    // Safe summary: do not accidentally print raw transactions or diagnostics.
    @Override
    public String toString() {
        return "AnalysisResult[status=" + status + ", transactionCount=" + transactions.size() + "]";
    }

    public enum Status { COMPLETE, COMPLETE_WITH_WARNINGS, UNDETERMINED }
    public enum Direction { CREDIT, DEBIT }
    public enum BalanceEligibility { ELIGIBLE, INELIGIBLE, UNDETERMINED }
    public enum ReceiptVerification { VERIFIED, UNVERIFIED, NOT_APPLICABLE }
    public enum CoverageStatus { COMPLETE, PARTIAL, UNKNOWN }

    public record StatementPeriod(LocalDate start, LocalDate end) {
        public StatementPeriod {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (end.isBefore(start)) throw new IllegalArgumentException("Invalid statement period");
        }
    }

    public record Versions(String parserVersion, String classificationVersion, String metricPolicyVersion) { }

    /** Safe code/detail only; adapters must never include raw PDF/passwords in diagnostics. */
    public record Diagnostic(String code, String detail, String transactionId, Integer sourcePage) { }

    public record Classification(String transactionType, String economicCategory,
            BalanceEligibility balanceEligibility, ReceiptVerification receiptVerification,
            String ruleId, String explanation, String mainCategory, String subCategory,
            String purposeClassification, String fundingSource, String loanProvider) {
        public Classification(String type, String category, BalanceEligibility eligibility, ReceiptVerification verification,
                String ruleId, String explanation, String main, String sub, String purpose, String funding) {
            this(type, category, eligibility, verification, ruleId, explanation, main, sub, purpose, funding, null);
        }
        /** Compatibility with old callers and historical snapshots. */
        public Classification(String transactionType, String economicCategory, BalanceEligibility eligibility,
                ReceiptVerification verification, String ruleId, String explanation) {
            this(transactionType, economicCategory, eligibility, verification, ruleId, explanation, null, null, null, null);
        }
    }

    /** Source page/row are one-based and refer to the beginning of a reconstructed logical row. */
    public record SourceLocation(int page, int row, String rawRow, float x, float y, float width, float height) {
        @Override public String toString() { return "SourceLocation[page=" + page + ", row=" + row + "]"; }
    }
    public record RejectedTransaction(String reasonCode, List<SourceLocation> sourceLines) {
        public RejectedTransaction { sourceLines = List.copyOf(sourceLines); }
        @Override public String toString() { return "RejectedTransaction[reasonCode=" + reasonCode + "]"; }
    }
    public record SourceProvenance(int page, int row, String rawRow, List<SourceLocation> sourceLines) {
        public SourceProvenance(int page, int row, String rawRow) { this(page, row, rawRow, List.of()); }
        public SourceProvenance {
            sourceLines = List.copyOf(sourceLines);
            if (page < 1 || row < 1) throw new IllegalArgumentException("Source page/row must be positive");
        }
        @Override public String toString() { return "SourceProvenance[page=" + page + ", row=" + row + "]"; }
    }

    public record NormalizedTransaction(String id, String sourceTransactionId,
            LocalDate transactionDate, OffsetDateTime transactionAt, Direction direction,
            BigDecimal amount, String currency, String rawNarration, String normalizedNarration,
            BigDecimal runningBalance, SourceProvenance source, Classification classification) {
        public NormalizedTransaction {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(transactionDate, "transactionDate");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(classification, "classification");
            if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public record MonthlyMetrics(YearMonth month, CoverageStatus coverageStatus, Status status,
            BigDecimal totalCredits, BigDecimal totalDebits, BigDecimal verifiedInflows,
            BigDecimal eligibleInflows, BigDecimal eligibleDebtPayments, BigDecimal eligibleSpending,
            BigDecimal excludedCredits, BigDecimal excludedDebits, BigDecimal monthlyBalance,
            BigDecimal netCashMovement, BigDecimal openingBalance, BigDecimal closingBalance,
            List<Diagnostic> warnings, List<TransactionDecision> evidence,
            BigDecimal unresolvedCredits, BigDecimal unresolvedDebits, IncomeCalculation incomeCalculation) {
        public MonthlyMetrics(YearMonth month, CoverageStatus coverageStatus, Status status,
                BigDecimal totalCredits, BigDecimal totalDebits, BigDecimal verifiedInflows,
                BigDecimal eligibleInflows, BigDecimal eligibleDebtPayments, BigDecimal eligibleSpending,
                BigDecimal excludedCredits, BigDecimal excludedDebits, BigDecimal monthlyBalance,
                BigDecimal netCashMovement, BigDecimal openingBalance, BigDecimal closingBalance,
                List<Diagnostic> warnings, List<TransactionDecision> evidence,
                BigDecimal unresolvedCredits, BigDecimal unresolvedDebits) {
            this(month, coverageStatus, status, totalCredits, totalDebits, verifiedInflows, eligibleInflows,
                    eligibleDebtPayments, eligibleSpending, excludedCredits, excludedDebits, monthlyBalance,
                    netCashMovement, openingBalance, closingBalance, warnings, evidence, unresolvedCredits, unresolvedDebits, null);
        }
        public MonthlyMetrics(YearMonth month, CoverageStatus coverageStatus, Status status,
                BigDecimal totalCredits, BigDecimal totalDebits, BigDecimal verifiedInflows,
                BigDecimal eligibleInflows, BigDecimal eligibleDebtPayments, BigDecimal eligibleSpending,
                BigDecimal excludedCredits, BigDecimal excludedDebits, BigDecimal monthlyBalance,
                BigDecimal netCashMovement, BigDecimal openingBalance, BigDecimal closingBalance,
                List<Diagnostic> warnings, List<TransactionDecision> evidence) {
            this(month, coverageStatus, status, totalCredits, totalDebits, verifiedInflows, eligibleInflows,
                    eligibleDebtPayments, eligibleSpending, excludedCredits, excludedDebits, monthlyBalance,
                    netCashMovement, openingBalance, closingBalance, warnings, evidence, null, null);
        }
        public MonthlyMetrics {
            Objects.requireNonNull(month, "month");
            Objects.requireNonNull(coverageStatus, "coverageStatus");
            Objects.requireNonNull(status, "status");
            warnings = List.copyOf(warnings);
            evidence = List.copyOf(evidence);
            if (status == Status.UNDETERMINED && monthlyBalance != null) {
                throw new IllegalArgumentException("Undetermined monthly balance must be null");
            }
        }
    }

    public record CategoryAmount(String mainCategory, String subCategory, CalculationRole role,
            BigDecimal amount, int transactionCount) { }
    public record IncomeCalculation(String formula, BigDecimal includedReceipts, BigDecimal loanRepayments,
            BigDecimal spending, BigDecimal monthlySurplus, List<CategoryAmount> categoryBreakdown) {
        public IncomeCalculation { categoryBreakdown = List.copyOf(categoryBreakdown); }
    }

    public enum CalculationRole { INFLOW, DEBT_PAYMENT, SPENDING, EXCLUDED, UNRESOLVED }
    public record TransactionDecision(String transactionId, CalculationRole role,
            BigDecimal includedAmount, String reasonCode) { }

    /** Sum / complete reconciled calendar months, including covered zero-activity months.
     * Partial months are excluded; zero complete months means null average amounts.
     * Final calculations round to two decimals with HALF_UP.
     */
    public record MonthlyAverages(StatementPeriod periodUsed, int completeMonthCount,
            BigDecimal averageInflow, BigDecimal averageOutflow, BigDecimal averageEligibleInflows,
            BigDecimal averageDebtPayments, BigDecimal averageSpending, BigDecimal averageMonthlyBalance) {
        public MonthlyAverages {
            if (completeMonthCount < 0) throw new IllegalArgumentException("Month count cannot be negative");
            if (completeMonthCount == 0 && (averageInflow != null || averageOutflow != null
                    || averageEligibleInflows != null || averageDebtPayments != null
                    || averageSpending != null || averageMonthlyBalance != null)) {
                throw new IllegalArgumentException("No complete months means null averages");
            }
        }
    }
}
