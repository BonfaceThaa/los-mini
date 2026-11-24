package com.credvenn.lm.statement;

import com.credvenn.stmt.analyzer.core.AnalysisResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Maps the tenant-owned internal snapshot without exposing raw PDF rows or narrations. */
final class InternalStatementResultMapper {
    private InternalStatementResultMapper() { }

    static AnalysisResult read(ObjectMapper mapper, StatementAnalysis analysis) {
        String snapshot = analysis.getRawProviderResponse();
        if (snapshot == null || snapshot.isBlank()) return null;
        try {
            AnalysisResult result = mapper.readValue(snapshot, AnalysisResult.class);
            if (!analysis.getId().equals(result.correlationId())) {
                throw new IllegalStateException("Internal statement result does not match analysis");
            }
            return result;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            // The exception can contain statement content; do not include it in logs or the response.
            throw new IllegalStateException("Unable to read stored internal statement result");
        }
    }

    static StatementDtos.StatementSummaryResponse statement(AnalysisResult result) {
        if (result == null) return null;
        var period = result.statementPeriod();
        return new StatementDtos.StatementSummaryResponse(result.currency(), period == null ? null : period.start(),
                period == null ? null : period.end(), result.transactions().size(), result.rejectedRows().size(),
                result.totalInflow(), result.totalOutflow());
    }

    static StatementDtos.MonthlySummaryResponse monthlySummary(AnalysisResult result) {
        if (result == null || result.monthlyAverages() == null) return null;
        var averages = result.monthlyAverages();
        var period = averages.periodUsed();
        return new StatementDtos.MonthlySummaryResponse(period == null ? null : period.start(),
                period == null ? null : period.end(), averages.completeMonthCount(), averages.averageInflow(),
                averages.averageOutflow(), averages.averageEligibleInflows(), averages.averageDebtPayments(),
                averages.averageSpending(), averages.averageMonthlyBalance());
    }

    static List<StatementDtos.MonthlyMetricResponse> monthlyMetrics(AnalysisResult result) {
        if (result == null) return null;
        return result.monthlyBreakdown().stream().map(month -> new StatementDtos.MonthlyMetricResponse(
                month.month(), month.coverageStatus(), month.status(), month.totalCredits(), month.totalDebits(),
                month.verifiedInflows(), month.eligibleInflows(), month.eligibleDebtPayments(), month.eligibleSpending(),
                month.excludedCredits(), month.excludedDebits(), month.unresolvedCredits(), month.unresolvedDebits(),
                month.monthlyBalance(), month.netCashMovement(), month.openingBalance(), month.closingBalance(),
                month.warnings(), month.incomeCalculation())).toList();
    }

    static StatementDtos.StatementQualityResponse quality(AnalysisResult result) {
        if (result == null) return null;
        var versions = result.versions();
        return new StatementDtos.StatementQualityResponse(result.status(), result.parsingConfidence(), result.warnings(),
                result.reconciliationErrors(), versions.parserVersion(), versions.classificationVersion(), versions.metricPolicyVersion());
    }

    static StatementDtos.CategorySummaryResponse categories(String analysisId, AnalysisResult result) {
        if (result == null) return null;
        Map<String, CategoryAccumulator> groups = new TreeMap<>();
        for (var tx : result.transactions()) {
            var classification = tx.classification();
            String main = fallback(classification.mainCategory(), "Uncategorized");
            String sub = fallback(classification.subCategory(), "Uncategorized");
            CategoryAccumulator category = groups.computeIfAbsent(main, key -> new CategoryAccumulator());
            category.totals.add(tx);
            category.subcategories.computeIfAbsent(sub, key -> new Totals()).add(tx);
            if ("Loans".equals(main) && "DEBT_REPAYMENT".equals(classification.economicCategory())
                    && tx.direction() == AnalysisResult.Direction.DEBIT) {
                category.lenders.computeIfAbsent(fallback(classification.loanProvider(), "Unidentified lender"), key -> new Totals()).add(tx);
            }
        }
        List<StatementDtos.CategoryResponse> categories = new ArrayList<>();
        groups.forEach((name, category) -> {
            var subcategories = category.subcategories.entrySet().stream().map(entry -> {
                Totals total = entry.getValue();
                return new StatementDtos.SubCategoryResponse(entry.getKey(), total.count, total.amount, total.credits, total.debits);
            }).toList();
            var lenders = category.lenders.entrySet().stream().map(entry ->
                    new StatementDtos.LoanRepaymentProviderResponse(entry.getKey(), entry.getValue().count, entry.getValue().amount)).toList();
            Totals total = category.totals;
            categories.add(new StatementDtos.CategoryResponse(name, total.count, total.amount, total.credits, total.debits,
                    subcategories, lenders));
        });
        return new StatementDtos.CategorySummaryResponse(analysisId, result.currency(), result.transactions().size(), List.copyOf(categories));
    }

    private static String fallback(String value, String fallback) { return value == null ? fallback : value; }

    private static final class CategoryAccumulator {
        final Totals totals = new Totals();
        final Map<String, Totals> subcategories = new TreeMap<>();
        final Map<String, Totals> lenders = new TreeMap<>();
    }

    private static final class Totals {
        long count;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;

        void add(AnalysisResult.NormalizedTransaction tx) {
            count++;
            amount = amount.add(tx.amount());
            if (tx.direction() == AnalysisResult.Direction.CREDIT) credits = credits.add(tx.amount());
            else debits = debits.add(tx.amount());
        }
    }
}
