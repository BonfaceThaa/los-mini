package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.analysis.*;
import com.credvenn.stmt.analyzer.core.parsing.ParsedStatement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PartialMonthStatusTest {
    private com.credvenn.stmt.analyzer.core.AnalysisResult analyze(String start,String end,String problem) {
        var rows=new ArrayList<ParsedStatement.TransactionDraft>();
        rows.add(FinancialAnalysisTest.row("one","A","2026-06-10",Direction.CREDIT,"1000.00",null,
                problem.equals("unknown-complete")?"Unrecognized receipt":"Funds received from - SYN"));
        if(problem.equals("unknown-partial"))rows.add(FinancialAnalysisTest.row("two","B","2026-07-02",Direction.CREDIT,"100.00",null,"Unrecognized receipt"));
        var total=problem.equals("unknown-partial")?"1100.00":problem.equals("totals")?"999.00":"1000.00";
        var parsed=FinancialAnalysisTest.statement(rows,new ParsedStatement.Summary(null,null,new BigDecimal(total),BigDecimal.ZERO,rows.size()),
                new StatementPeriod(LocalDate.parse(start),LocalDate.parse(end)));
        return new StatementAnalysisPipeline().analyzeParsed(parsed);
    }
    @ParameterizedTest @ValueSource(strings={"both","start","end"})
    void completeMonthsRemainUsableWithPartialBoundaries(String boundaries) {
        var r=analyze(boundaries.equals("end")?"2026-06-01":"2026-05-14",
                boundaries.equals("start")?"2026-06-30":"2026-07-14","normal");
        assertEquals(Status.COMPLETE_WITH_WARNINGS,r.status());
        assertTrue(r.reconciliationErrors().isEmpty());
        assertTrue(r.warnings().stream().anyMatch(w->w.code().equals("PARTIAL_MONTHS_EXCLUDED")));
        var june=r.monthlyBreakdown().stream().filter(m->m.month().toString().equals("2026-06")).findFirst().orElseThrow();
        assertEquals(Status.COMPLETE,june.status());FinancialAnalysisTest.amount("1000.00",june.monthlyBalance());
        for(var m:r.monthlyBreakdown())if(m.coverageStatus()==CoverageStatus.PARTIAL) {
            assertEquals(Status.UNDETERMINED,m.status());assertNull(m.monthlyBalance());assertNull(m.incomeCalculation().monthlySurplus());
        }
        assertEquals(1,r.monthlyAverages().completeMonthCount());
        FinancialAnalysisTest.amount("1000.00",r.monthlyAverages().averageMonthlyBalance());
    }
    @ParameterizedTest @ValueSource(strings={"totals","unknown-complete","unknown-partial"})
    void independentProblemsRemainBlocking(String problem) {
        assertEquals(Status.UNDETERMINED,analyze("2026-05-14","2026-07-14",problem).status());
    }
    @Test void noCompleteMonthRemainsUndetermined() {
        var r=analyze("2026-06-02","2026-06-29","normal");
        assertEquals(Status.UNDETERMINED,r.status());assertEquals(0,r.monthlyAverages().completeMonthCount());
    }
}
