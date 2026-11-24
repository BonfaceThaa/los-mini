package com.credvenn.stmt.analyzer.core.analysis;

import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.parsing.ParsedStatement;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

/** Descriptive balance-v12 metrics, with exact amounts and no lending approval rules. */
public final class MonthlyMetricsCalculator {
    public static final String VERSION = "balance-v12";
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final int maxMonths;
    public MonthlyMetricsCalculator() { this(120); }
    /** Provisional resource guard, not a minimum-income-history rule. */
    public MonthlyMetricsCalculator(int maxMonths) {
        if (maxMonths < 1) throw new IllegalArgumentException("maxMonths must be positive");
        this.maxMonths = maxMonths;
    }

    public List<MonthlyMetrics> calculate(ParsedStatement parsed, List<NormalizedTransaction> rows,
            ReconciliationResult reconciliation) {
        var period = parsed.period();
        var months = new TreeSet<YearMonth>();
        if (period != null) {
            var first = YearMonth.from(period.start());
            var last = YearMonth.from(period.end());
            if (ChronoUnit.MONTHS.between(first, last) >= maxMonths) {
                throw new com.credvenn.stmt.analyzer.core.AnalysisProcessingException(
                        com.credvenn.stmt.analyzer.core.ProcessingErrorCode.RESOURCE_LIMIT_EXCEEDED, parsed.correlationId());
            }
            for (var month = first; !month.isAfter(last); month = month.plusMonths(1)) months.add(month);
        }
        for (var row : rows) months.add(YearMonth.from(row.transactionDate()));
        if (months.size() > maxMonths) {
            throw new com.credvenn.stmt.analyzer.core.AnalysisProcessingException(
                    com.credvenn.stmt.analyzer.core.ProcessingErrorCode.RESOURCE_LIMIT_EXCEEDED, parsed.correlationId());
        }
        var output = new ArrayList<MonthlyMetrics>();
        BigDecimal carry = reconciliation.reconciled() && !reconciliation.tolerated() ? parsed.summary().openingBalance() : null;
        for (var month : months) {
            var subset = rows.stream().filter(row -> YearMonth.from(row.transactionDate()).equals(month)).toList();
            CoverageStatus coverage = period == null ? CoverageStatus.UNKNOWN
                    : !period.start().isAfter(month.atDay(1)) && !period.end().isBefore(month.atEndOfMonth())
                            ? CoverageStatus.COMPLETE : CoverageStatus.PARTIAL;
            var warnings = new ArrayList<Diagnostic>();
            if (coverage != CoverageStatus.COMPLETE) warnings.add(StatementReconciler.issue("INCOMPLETE_MONTH_COVERAGE", null));
            if (reconciliation.tolerated()) warnings.addAll(reconciliation.warnings());
            if (!reconciliation.reconciled()) warnings.add(StatementReconciler.issue("STATEMENT_NOT_RECONCILED", null));
            BigDecimal credits = ZERO, debits = ZERO, verified = ZERO, eligible = ZERO, debt = ZERO,
                    spending = ZERO, excludedCredits = ZERO, excludedDebits = ZERO, unresolvedCredits = ZERO, unresolvedDebits = ZERO;
            boolean unresolved = false;
            var evidence = new ArrayList<TransactionDecision>();
            var categories = new LinkedHashMap<CategoryKey, CategoryAmount>();
            for (var row : subset) {
                boolean credit = row.direction() == Direction.CREDIT;
                if (credit) credits = credits.add(row.amount()); else debits = debits.add(row.amount());
                var classification = row.classification();
                if (credit && classification.receiptVerification() == ReceiptVerification.VERIFIED) verified = verified.add(row.amount());
                CalculationRole role;
                if (classification.balanceEligibility() == BalanceEligibility.UNDETERMINED) {
                    role = CalculationRole.UNRESOLVED; unresolved = true;
                    if (credit) unresolvedCredits = unresolvedCredits.add(row.amount());
                    else unresolvedDebits = unresolvedDebits.add(row.amount());
                    warnings.add(StatementReconciler.issue("UNRESOLVED_TRANSACTION_ELIGIBILITY", row.id()));
                } else if (classification.balanceEligibility() == BalanceEligibility.INELIGIBLE) {
                    role = CalculationRole.EXCLUDED;
                    if (credit) excludedCredits = excludedCredits.add(row.amount()); else excludedDebits = excludedDebits.add(row.amount());
                } else if (credit) {
                    role = CalculationRole.INFLOW; eligible = eligible.add(row.amount());
                } else if (classification.economicCategory().equals("DEBT_REPAYMENT")) {
                    role = CalculationRole.DEBT_PAYMENT; debt = debt.add(row.amount());
                } else {
                    role = CalculationRole.SPENDING; spending = spending.add(row.amount());
                }
                var key = new CategoryKey(classification.mainCategory() == null ? "Legacy" : classification.mainCategory(),
                        classification.subCategory() == null ? classification.economicCategory() : classification.subCategory(), role);
                var prior = categories.get(key);
                categories.put(key, new CategoryAmount(key.main(), key.sub(), role,
                        (prior == null ? ZERO : prior.amount()).add(row.amount()), prior == null ? 1 : prior.transactionCount() + 1));
                evidence.add(new TransactionDecision(row.id(), role,
                        role == CalculationRole.EXCLUDED || role == CalculationRole.UNRESOLVED ? ZERO : row.amount(),
                        classification.ruleId()));
            }
            boolean determined = reconciliation.reconciled() && coverage == CoverageStatus.COMPLETE && !unresolved;
            BigDecimal movement = credits.subtract(debits);
            BigDecimal opening = coverage == CoverageStatus.COMPLETE ? carry : null;
            if (carry != null) carry = carry.add(movement);
            BigDecimal closing = coverage == CoverageStatus.COMPLETE ? carry : null;
            if (opening == null) warnings.add(StatementReconciler.issue("OPENING_BALANCE_UNAVAILABLE", null));
            output.add(new MonthlyMetrics(month, coverage, determined ? (reconciliation.tolerated() ? Status.COMPLETE_WITH_WARNINGS : Status.COMPLETE) : Status.UNDETERMINED,
                    credits, debits, verified, eligible, debt, spending, excludedCredits, excludedDebits,
                    determined ? eligible.subtract(debt).subtract(spending) : null, movement, opening, closing, warnings, evidence,
                    unresolvedCredits, unresolvedDebits, new IncomeCalculation(
                            "Monthly surplus = Included receipts - Loan repayments - Spending", eligible, debt, spending,
                            determined ? eligible.subtract(debt).subtract(spending) : null, List.copyOf(categories.values()))));
        }
        return List.copyOf(output);
    }

    public MonthlyAverages averages(List<MonthlyMetrics> months, boolean reconciled) {
        var complete = months.stream().filter(m -> reconciled && m.coverageStatus() == CoverageStatus.COMPLETE).toList();
        int count = complete.size();
        if (count == 0) return new MonthlyAverages(null, 0, null, null, null, null, null, null);
        boolean policyComplete = complete.stream().allMatch(m -> m.status() != Status.UNDETERMINED);
        return new MonthlyAverages(new StatementPeriod(complete.getFirst().month().atDay(1), complete.getLast().month().atEndOfMonth()),
                count, mean(complete, MonthlyMetrics::totalCredits), mean(complete, MonthlyMetrics::totalDebits),
                policyComplete ? mean(complete, MonthlyMetrics::eligibleInflows) : null,
                policyComplete ? mean(complete, MonthlyMetrics::eligibleDebtPayments) : null,
                policyComplete ? mean(complete, MonthlyMetrics::eligibleSpending) : null,
                policyComplete ? mean(complete, MonthlyMetrics::monthlyBalance) : null);
    }
    private record CategoryKey(String main, String sub, CalculationRole role) { }
    private static BigDecimal mean(List<MonthlyMetrics> months, Function<MonthlyMetrics, BigDecimal> value) {
        return months.stream().map(value).reduce(ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_UP);
    }
}
