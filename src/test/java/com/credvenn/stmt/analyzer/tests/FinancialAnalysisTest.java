package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.*;
import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.analysis.*;
import com.credvenn.stmt.analyzer.core.parsing.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class FinancialAnalysisTest {
    static AnalysisResult analyze(String scenario) throws Exception {
        return new DefaultStatementAnalyzer().analyze(new AnalysisInput(
                StatementPdfFixtures.financial(scenario), null, "MPESA", "finance-test"));
    }
    static void amount(String expected, BigDecimal actual) {
        assertNotNull(actual); assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
    @Test
    void committedPdfMatchesIndependentMetricOracle() throws Exception {
        try (var pdf = getClass().getResourceAsStream("/fixtures/balance-example.pdf");
                var expected = getClass().getResourceAsStream("/fixtures/balance-example.expected.json")) {
            assertNotNull(pdf); assertNotNull(expected);
            var result = new DefaultStatementAnalyzer().analyze(new AnalysisInput(pdf.readAllBytes(), null, "MPESA", "oracle"));
            assertEquals(new String(expected.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip(),
                    AnalysisSmokeRunner.metricsProjection(result));
        }
    }
    @Test
    void completePdfRunsThroughProductionEntryPointAndMatchesWorkedExample() throws Exception {
        var result = analyze("complete");
        assertEquals(Status.COMPLETE, result.status()); assertEquals("finance-test", result.correlationId());
        assertEquals(9, result.transactions().size()); assertTrue(result.reconciliationErrors().isEmpty());
        amount("32076.81", result.totalInflow()); amount("6600.00", result.totalOutflow());
        var month = result.monthlyBreakdown().getFirst();
        amount("18500.00", month.eligibleInflows()); amount("18500.00", month.verifiedInflows());
        amount("3000.00", month.eligibleDebtPayments()); amount("3600.00", month.eligibleSpending());
        amount("13576.81", month.excludedCredits()); amount("11900.00", month.monthlyBalance());
        amount("25476.81", month.netCashMovement()); amount("1000.00", month.openingBalance());
        amount("26476.81", month.closingBalance());
        assertEquals(9, month.evidence().size());
        assertEquals(1, month.evidence().stream().filter(e -> e.role() == CalculationRole.DEBT_PAYMENT).count());
        assertEquals(4, month.evidence().stream().filter(e -> e.role() == CalculationRole.SPENDING).count());
        assertEquals(1, result.monthlyAverages().completeMonthCount());
        amount("11900.00", result.monthlyAverages().averageMonthlyBalance());
        assertEquals("balance-v12", result.versions().metricPolicyVersion());
        assertFalse(result.transactions().getFirst().source().sourceLines().isEmpty());
    }
    @ParameterizedTest
    @CsvSource({"duplicate,DUPLICATE_REFERENCE", "missing-row,TRANSACTION_COUNT_MISMATCH",
        "totals-mismatch,CREDIT_TOTAL_MISMATCH", "balance-mismatch,RUNNING_BALANCE_MISMATCH"})
    void inconsistentStatementsNeverProduceFinalBalance(String scenario, String code) throws Exception {
        var result = analyze(scenario);
        assertEquals(Status.UNDETERMINED, result.status());
        assertTrue(result.reconciliationErrors().stream().anyMatch(e -> e.code().equals(code)));
        assertNull(result.monthlyBreakdown().getFirst().monthlyBalance());
        assertNull(result.monthlyBreakdown().getFirst().closingBalance());
        assertEquals(0, result.monthlyAverages().completeMonthCount());
        if (scenario.equals("duplicate")) assertEquals(9, result.transactions().size());
    }
    @Test
    void partialCoverageAndUnknownTypesAreNotZero() throws Exception {
        var partial = analyze("partial");
        assertEquals(CoverageStatus.PARTIAL, partial.monthlyBreakdown().getFirst().coverageStatus());
        assertNull(partial.monthlyBreakdown().getFirst().monthlyBalance());
        assertEquals(0, partial.monthlyAverages().completeMonthCount());
        var unknown = analyze("unknown");
        assertEquals(Status.UNDETERMINED, unknown.status());
        assertNull(unknown.monthlyBreakdown().getFirst().monthlyBalance());
        amount("13500.00", unknown.monthlyBreakdown().getFirst().unresolvedCredits());
        assertEquals(CalculationRole.UNRESOLVED, unknown.monthlyBreakdown().getFirst().evidence().getFirst().role());
        // Reconciled all-entry averages remain valid; eligible-income averages do not.
        amount("32076.81", unknown.monthlyAverages().averageInflow());
        assertNull(unknown.monthlyAverages().averageMonthlyBalance());
    }
    @Test
    void missingOpeningAndMissingRowBalancesHaveDifferentMeanings() throws Exception {
        var noOpening = analyze("no-opening");
        assertEquals(Status.COMPLETE, noOpening.status());
        assertNull(noOpening.monthlyBreakdown().getFirst().closingBalance());
        assertNull(noOpening.monthlyBreakdown().getFirst().openingBalance());
        amount("11900.00", noOpening.monthlyBreakdown().getFirst().monthlyBalance());
        assertEquals(Status.COMPLETE, analyze("missing-balances").status());
    }
    @ParameterizedTest
    @CsvSource({"negative,-7600.00", "zero,0.00"})
    void negativeAndZeroPolicyBalancesRemainExact(String scenario, String expected) throws Exception {
        amount(expected, analyze(scenario).monthlyBreakdown().getFirst().monthlyBalance());
    }
    @Test
    void calendarMonthsAndZeroActivityAreNeverNettedTogether() throws Exception {
        var result = analyze("cross-month");
        assertEquals(7, result.monthlyBreakdown().size());
        amount("-6600.00", result.monthlyBreakdown().getFirst().monthlyBalance());
        var october = result.monthlyBreakdown().stream().filter(m -> m.month().equals(YearMonth.of(2026, 10))).findFirst().orElseThrow();
        amount("18500.00", october.monthlyBalance()); amount("0.00", october.eligibleDebtPayments());
        amount("0.00", result.monthlyBreakdown().get(1).monthlyBalance());
        assertEquals(7, result.monthlyAverages().completeMonthCount());
        amount("1700.00", result.monthlyAverages().averageMonthlyBalance());
        var twoMonths = analyze("zero-month");
        assertEquals(2, twoMonths.monthlyAverages().completeMonthCount());
        amount("5950.00", twoMonths.monthlyAverages().averageMonthlyBalance());
    }

    static ParsedStatement statement(List<ParsedStatement.TransactionDraft> rows, ParsedStatement.Summary summary,
            StatementPeriod period) {
        return new ParsedStatement("unit-finance", MpesaTableParser.VERSION, ParsedStatement.Status.PARSED,
                period, "KES", rows, List.of(), List.of(), summary);
    }
    static ParsedStatement.TransactionDraft row(String key, String ref, String date, Direction direction,
            String amount, String balance, String narration) {
        return new ParsedStatement.TransactionDraft(key, ref, LocalDate.parse(date), null, direction,
                new BigDecimal(amount), "KES", narration, narration.toUpperCase(Locale.ROOT), "Completed",
                balance == null ? null : new BigDecimal(balance),
                List.of(new ParsedStatement.SourceLine(1, 1, "synthetic", 20, 20, 100, 8)));
    }
    @Test
    void noEvidenceBlocksFinalizationButEqualRealPaymentsSurvive() {
        var rows = List.of(row("one", "A", "2026-10-01", Direction.CREDIT, "100.00", null, "Funds received from - SYN"),
                row("two", "B", "2026-10-02", Direction.CREDIT, "100.00", null, "Funds received from - SYN"));
        var period = new StatementPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        var parsed = statement(rows, new ParsedStatement.Summary(null, null, null, null, null), period);
        var result = new StatementAnalysisPipeline().analyzeParsed(parsed);
        assertEquals(2, result.transactions().size()); assertNull(result.monthlyBreakdown().getFirst().monthlyBalance());
        assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("INSUFFICIENT_RECONCILIATION_EVIDENCE")));
        parsed = statement(rows, new ParsedStatement.Summary(null, null, new BigDecimal("200.00"), BigDecimal.ZERO, 2), period);
        result = new StatementAnalysisPipeline().analyzeParsed(parsed);
        assertEquals(Status.COMPLETE, result.status()); amount("200.00", result.monthlyBreakdown().getFirst().monthlyBalance());
    }
    @Test
    void zeroActivityAndMonthResourceLimitAreExplicit() {
        var period = new StatementPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        var parsed = statement(List.of(), new ParsedStatement.Summary(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 0), period);
        var result = new StatementAnalysisPipeline().analyzeParsed(parsed);
        assertEquals(Status.COMPLETE, result.status()); amount("0.00", result.monthlyBreakdown().getFirst().monthlyBalance());
        amount("10.00", result.monthlyBreakdown().getFirst().closingBalance());
        var longPeriod = statement(List.of(), parsed.summary(),
                new StatementPeriod(LocalDate.of(2000, 1, 1), LocalDate.of(2026, 10, 31)));
        assertEquals(ProcessingErrorCode.RESOURCE_LIMIT_EXCEEDED,
                assertThrows(AnalysisProcessingException.class, () -> new StatementAnalysisPipeline().analyzeParsed(longPeriod)).code());
    }
    @Test
    void balanceChainCanReconcileWithoutSummaryAndAveragesRoundHalfUp() {
        var rows = List.of(row("one", null, "2026-10-05", Direction.CREDIT, "100.01", "100.01", "Funds received from - SYN"));
        var parsed = statement(rows, new ParsedStatement.Summary(BigDecimal.ZERO, null, null, null, null),
                new StatementPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)));
        var result = new StatementAnalysisPipeline().analyzeParsed(parsed);
        assertEquals(Status.COMPLETE, result.status());
        assertEquals(3, result.monthlyAverages().completeMonthCount());
        amount("33.34", result.monthlyAverages().averageMonthlyBalance());
        amount("100.01", result.monthlyBreakdown().getLast().closingBalance());
    }
    @Test
    void missingReferencesDoNotCollapseRowsButDuplicateSourceKeysFail() {
        var rows = List.of(row("one", null, "2026-10-01", Direction.CREDIT, "100.00", "100.00", "Funds received from - SYN"),
                row("two", null, "2026-10-02", Direction.CREDIT, "100.00", "200.00", "Funds received from - SYN"));
        var period = new StatementPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        var summary = new ParsedStatement.Summary(BigDecimal.ZERO, new BigDecimal("200.00"), null, null, 2);
        var result = new StatementAnalysisPipeline().analyzeParsed(statement(rows, summary, period));
        assertEquals(Status.COMPLETE, result.status()); assertEquals(2, result.transactions().size());
        var duplicate = statement(List.of(rows.getFirst(), rows.getFirst()), summary, period);
        assertTrue(new StatementReconciler().reconcile(duplicate).errors().stream().anyMatch(e -> e.code().equals("DUPLICATE_SOURCE_ROW")));
    }
    @Test
    void rejectedSourceRowsAndConfidenceRemainVisibleInFinalResult() throws Exception {
        var result = new DefaultStatementAnalyzer().analyze(new AnalysisInput(
                StatementPdfFixtures.create("bad-amount"), null, "MPESA", "rejected-test"));
        assertEquals(Status.UNDETERMINED, result.status());
        assertEquals(1, result.rejectedRows().size());
        assertEquals("INVALID_AMOUNT", result.rejectedRows().getFirst().reasonCode());
        assertEquals(2, result.rejectedRows().getFirst().sourceLines().size());
        assertEquals(2, result.rejectedRows().getFirst().sourceLines().getLast().page());
        amount("0.6667", result.parsingConfidence());
        assertNull(result.monthlyBreakdown().getFirst().monthlyBalance());
        assertThrows(UnsupportedOperationException.class, () -> result.rejectedRows().clear());
    }
    @Test
    void availableBalanceAfterMissingBalanceIsStillChecked() {
        var rows = List.of(row("one", "A", "2026-10-01", Direction.CREDIT, "100.00", null, "Funds received from - SYN"),
                row("two", "B", "2026-10-02", Direction.CREDIT, "100.00", "9999.00", "Funds received from - SYN"));
        var parsed = statement(rows, new ParsedStatement.Summary(BigDecimal.ZERO, null, new BigDecimal("200.00"), BigDecimal.ZERO, 2),
                new StatementPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertTrue(new StatementReconciler().reconcile(parsed).errors().stream()
                .anyMatch(e -> e.code().equals("RUNNING_BALANCE_MISMATCH")));
    }
}
