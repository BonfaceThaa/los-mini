package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.analysis.*;
import com.credvenn.stmt.analyzer.core.parsing.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ReconciliationToleranceTest {
    private ParsedStatement statement(String gap, String mode) {
        var rows = new ArrayList<ParsedStatement.TransactionDraft>();
        rows.add(FinancialAnalysisTest.row("a", "A", "2026-06-01", Direction.CREDIT,
                "1000.00", "1000.00", mode.equals("unknown") ? "Unrecognized receipt" : "Funds received from - SYN"));
        rows.add(FinancialAnalysisTest.row("b", mode.equals("duplicate") ? "A" : "B", "2026-06-02", Direction.DEBIT,
                "100.00", new BigDecimal("900.00").subtract(new BigDecimal(gap)).toPlainString(), "Airtime Purchase"));
        if (mode.equals("two")) rows.add(FinancialAnalysisTest.row("c", "C", "2026-06-03", Direction.DEBIT,
                "10.00", "880.00", "Airtime Purchase"));
        return FinancialAnalysisTest.statement(rows, new ParsedStatement.Summary(null, null,
                new BigDecimal(mode.equals("totals") ? "999.00" : "1000.00"),
                new BigDecimal(mode.equals("two") ? "110.00" : "100.00"), rows.size()),
                new StatementPeriod(LocalDate.of(2026,6,mode.equals("partial") ? 2 : 1),LocalDate.of(2026,6,30)));
    }
    @ParameterizedTest @CsvSource({"100.00", "-100.00", "0.01"})
    void smallMismatchReturnsSurplusAndVisibleWarning(String gap) {
        var r = new StatementAnalysisPipeline().analyzeParsed(statement(gap,"normal"));
        assertEquals(Status.COMPLETE_WITH_WARNINGS,r.status());
        assertTrue(r.reconciliationErrors().isEmpty());
        assertTrue(r.warnings().stream().anyMatch(w->w.code().equals("RUNNING_BALANCE_MISMATCH") && w.detail().contains("difference=")));
        var m=r.monthlyBreakdown().getFirst();
        assertEquals(Status.COMPLETE_WITH_WARNINGS,m.status());
        FinancialAnalysisTest.amount("900.00",m.incomeCalculation().monthlySurplus());
        FinancialAnalysisTest.amount("900.00",r.monthlyAverages().averageMonthlyBalance());
        assertNull(m.closingBalance());
        assertEquals(2,r.transactions().size());
    }
    @ParameterizedTest @CsvSource({"100.01,normal", "-100.01,normal", "5.00,two", "100.00,duplicate", "100.00,unknown", "100.00,totals"})
    void otherFailuresCannotBeTolerated(String gap,String mode) {
        var r=new StatementAnalysisPipeline().analyzeParsed(statement(gap,mode));
        assertEquals(Status.UNDETERMINED,r.status());
        assertFalse(r.reconciliationErrors().isEmpty());
        assertNull(r.monthlyBreakdown().getFirst().monthlyBalance());
    }
    @Test void configurableLimitsUseTotalAbsoluteDiscrepancy() {
        var p=statement("5.00","two");
        assertTrue(new StatementReconciler(2,new BigDecimal("10.00")).reconcile(p).reconciled());
        assertFalse(new StatementReconciler(2,new BigDecimal("9.99")).reconcile(p).reconciled());
        var noTotals=FinancialAnalysisTest.statement(p.transactions(),new ParsedStatement.Summary(null,null,null,null,3),p.period());
        assertFalse(new StatementReconciler(2,new BigDecimal("100.00")).reconcile(noTotals).reconciled());
    }
    @Test void knownOpeningDoesNotExposeAnExactWalletBalanceAfterTolerance() {
        var p=statement("100.00","normal");
        var withOpening=FinancialAnalysisTest.statement(p.transactions(),new ParsedStatement.Summary(BigDecimal.ZERO,null,
                new BigDecimal("1000.00"),new BigDecimal("100.00"),2),p.period());
        var r=new StatementAnalysisPipeline().analyzeParsed(withOpening);
        assertEquals(Status.COMPLETE_WITH_WARNINGS,r.status());
        assertNull(r.monthlyBreakdown().getFirst().openingBalance());
        assertNull(r.monthlyBreakdown().getFirst().closingBalance());
    }
    @Test void strictConfigurationAndPartialCoverageRemainBlocking() {
        assertFalse(new StatementReconciler(0,new BigDecimal("100.00")).reconcile(statement("100.00","normal")).reconciled());
        assertFalse(new StatementReconciler(1,BigDecimal.ZERO).reconcile(statement("100.00","normal")).reconciled());
        assertEquals(Status.UNDETERMINED,new StatementAnalysisPipeline().analyzeParsed(statement("100.00","partial")).status());
        assertThrows(IllegalArgumentException.class,()->new StatementReconciler(-1,BigDecimal.ONE));
    }
}
