package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.*;
import com.credvenn.stmt.analyzer.core.analysis.StatementReconciler;
import com.credvenn.stmt.analyzer.core.extraction.PdfBoxTextExtractor;
import com.credvenn.stmt.analyzer.core.parsing.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DetailedStatementParsingTest {
    private ParsedStatement parse(byte[] pdf) {
        return new MpesaTableParser().parse(new PdfBoxTextExtractor().extract(
                new AnalysisInput(pdf,null,"MPESA","detailed-fixture")));
    }
    @Test
    void centeredHeadersSignedDebitsAndReverseOrderMatchExpectedTransactions() throws Exception {
        try(var pdf=getClass().getResourceAsStream("/fixtures/mpesa-detailed.pdf");
                var expected=getClass().getResourceAsStream("/fixtures/mpesa-detailed.expected.json")) {
            assertNotNull(pdf); assertNotNull(expected);
            var parsed=parse(pdf.readAllBytes());
            assertEquals(new String(expected.readAllBytes(),StandardCharsets.UTF_8).strip(),StatementJson.write(parsed));
            assertEquals(new BigDecimal("1000.00"),parsed.summary().totalCredits());
            assertEquals(new BigDecimal("30.00"),parsed.summary().totalDebits());
            var reconciliation=new StatementReconciler().reconcile(parsed);
            assertTrue(reconciliation.reconciled());
            assertTrue(reconciliation.errors().isEmpty());
            assertEquals(java.util.List.of(1,2),parsed.transactions().get(1).sourceLines().stream().map(ParsedStatement.SourceLine::page).toList());
        }
    }
    @Test
    void completedDebitMissingNarrationIsRetainedForFulizaRepaymentClassification() throws Exception {
        var result=parse(DetailedStatementFixture.create(true));
        assertEquals(3,result.transactions().size());
        assertTrue(result.rejectedRows().isEmpty());
        assertEquals(ParsedStatement.Status.PARSED,result.status());
        assertTrue(result.warnings().isEmpty());
        var analysis=new com.credvenn.stmt.analyzer.core.analysis.StatementAnalysisPipeline().analyzeParsed(result);
        assertEquals(AnalysisResult.Status.COMPLETE,analysis.status());
        var missing=analysis.transactions().stream().filter(t->t.rawNarration().isBlank()).findFirst().orElseThrow();
        assertEquals("OVERDRAFT_REPAYMENT",missing.classification().transactionType());
        assertEquals("FULIZA",missing.classification().fundingSource());
        assertTrue(missing.classification().explanation().contains("Policy assumption"));
        assertEquals(new BigDecimal("10.00"),analysis.monthlyBreakdown().getFirst().eligibleDebtPayments());
        assertEquals(new BigDecimal("30.00"),new StatementReconciler().reconcile(result).totalDebits());
    }
    @Test void committedEmptyDetailsPdfMatchesIndependentTransactionOracle() throws Exception {
        try (var pdf=getClass().getResourceAsStream("/fixtures/mpesa-detailed-empty-debit.pdf");
                var expected=getClass().getResourceAsStream("/fixtures/mpesa-detailed-empty-debit.expected.json")) {
            assertNotNull(pdf);assertNotNull(expected);
            assertEquals(new String(expected.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).strip(),StatementJson.write(parse(pdf.readAllBytes())));
        }
    }
    @Test void missingCreditDetailsStillRequireReview() throws Exception {
        var parsed=parse(DetailedStatementFixture.create(false,true));
        assertEquals(ParsedStatement.Status.REQUIRES_REVIEW,parsed.status());
        assertTrue(parsed.warnings().stream().anyMatch(w->w.code().equals("MISSING_NARRATION")));
        var result=new com.credvenn.stmt.analyzer.core.analysis.StatementAnalysisPipeline().analyzeParsed(parsed);
        assertEquals(AnalysisResult.Status.UNDETERMINED,result.status());
        assertEquals("UNKNOWN",result.transactions().stream().filter(t->t.direction()==AnalysisResult.Direction.CREDIT).findFirst().orElseThrow().classification().transactionType());
    }
}
