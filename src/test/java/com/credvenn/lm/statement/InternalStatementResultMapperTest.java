package com.credvenn.lm.statement;

import static org.junit.jupiter.api.Assertions.*;

import com.credvenn.stmt.analyzer.core.AnalysisInput;
import com.credvenn.stmt.analyzer.core.AnalysisResult;
import com.credvenn.stmt.analyzer.core.DefaultStatementAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InternalStatementResultMapperTest {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void readsPhaseThreeSnapshotAndMapsFinancialExample() throws Exception {
        AnalysisResult source;
        try (var pdf = getClass().getResourceAsStream("/fixtures/balance-example.pdf")) {
            source = new DefaultStatementAnalyzer().analyze(new AnalysisInput(pdf.readAllBytes(), null, "MPESA", "analysis-1"));
        }
        var analysis = new StatementAnalysis();
        ReflectionTestUtils.setField(analysis, "id", "analysis-1");
        analysis.setRawProviderResponse(json.writeValueAsString(source));
        AnalysisResult restored = InternalStatementResultMapper.read(json, analysis);
        var summary = InternalStatementResultMapper.statement(restored);
        assertEquals(new BigDecimal("32076.81"), summary.totalInflow());
        assertEquals(new BigDecimal("6600.00"), summary.totalOutflow());
        assertEquals(source.transactions().size(), summary.transactionCount());
        var month = InternalStatementResultMapper.monthlyMetrics(restored).getFirst();
        assertEquals(new BigDecimal("11900.00"), month.monthlyBalance());
        assertEquals(new BigDecimal("26476.81"), month.closingBalance());
        assertEquals(source.versions().parserVersion(), InternalStatementResultMapper.quality(restored).parserVersion());
        assertEquals(source.transactions().size(), InternalStatementResultMapper.categories("analysis-1", restored).totalTransactionCount());
    }

    @Test
    void categoriesIncludeAllTransactionsAndOnlyDebitRepaymentsInLenderTotals() {
        var txs = List.of(
                tx("1", "Loans", "Repayment", "DEBT_REPAYMENT", "MOGO", AnalysisResult.Direction.DEBIT, "100.00"),
                tx("2", "Loans", "Repayment", "DEBT_REPAYMENT", null, AnalysisResult.Direction.DEBIT, "50.00"),
                tx("3", "Loans", "Borrowing", "LOAN_DISBURSEMENT", "MOGO", AnalysisResult.Direction.CREDIT, "200.00"),
                tx("4", null, null, "OTHER", null, AnalysisResult.Direction.DEBIT, "10.00"));
        var result = new AnalysisResult("analysis-1", AnalysisResult.Status.UNDETERMINED, null, "KES", txs,
                null, null, List.of(), null, List.of(), List.of(), null, new AnalysisResult.Versions("p", "c", "m"));
        var categories = InternalStatementResultMapper.categories("analysis-1", result);
        assertEquals(4, categories.totalTransactionCount());
        var loans = categories.categories().getFirst();
        assertEquals("Loans", loans.mainCategory());
        assertEquals(new BigDecimal("350.00"), loans.totalAmount());
        assertEquals(new BigDecimal("200.00"), loans.totalCredits());
        assertEquals(new BigDecimal("150.00"), loans.totalDebits());
        assertEquals(2, loans.loanRepaymentProviders().size());
        assertEquals("MOGO", loans.loanRepaymentProviders().getFirst().loanProvider());
        assertEquals(new BigDecimal("100.00"), loans.loanRepaymentProviders().getFirst().totalRepaid());
        assertEquals("Unidentified lender", loans.loanRepaymentProviders().get(1).loanProvider());
        assertEquals("Uncategorized", categories.categories().get(1).mainCategory());
        assertEquals("Uncategorized", categories.categories().get(1).subCategories().getFirst().subCategory());
    }

    @Test
    void rejectsWrongOrUnreadableSnapshotAndDoesNotExposeItsContents() throws Exception {
        var analysis = new StatementAnalysis();
        ReflectionTestUtils.setField(analysis, "id", "analysis-1");
        analysis.setRawProviderResponse("sensitive-invalid-json");
        var exception = assertThrows(IllegalStateException.class, () -> InternalStatementResultMapper.read(json, analysis));
        assertFalse(exception.toString().contains("sensitive-invalid-json"));
        assertNull(exception.getCause());
    }

    private AnalysisResult.NormalizedTransaction tx(String id, String main, String sub, String economic,
            String lender, AnalysisResult.Direction direction, String amount) {
        var classification = new AnalysisResult.Classification("TYPE", economic, AnalysisResult.BalanceEligibility.ELIGIBLE,
                AnalysisResult.ReceiptVerification.VERIFIED, "rule", "explanation", main, sub, null, null, lender);
        return new AnalysisResult.NormalizedTransaction(id, id, LocalDate.of(2026, 1, 1), null, direction,
                new BigDecimal(amount), "KES", "private", "private", null,
                new AnalysisResult.SourceProvenance(1, 1, "private"), classification);
    }
}
