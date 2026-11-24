package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.*;
import com.credvenn.stmt.analyzer.core.extraction.*;
import com.credvenn.stmt.analyzer.core.parsing.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class StatementParsingTest {
    private static ParsedStatement parse(byte[] bytes) {
        return new MpesaTableParser().parse(new PdfBoxTextExtractor().extract(
                new AnalysisInput(bytes, null, "MPESA", "parser-test")));
    }
    @Test
    void pdfFixtureMatchesHandWrittenTransactionJson() throws Exception {
        try (var pdf = getClass().getResourceAsStream("/fixtures/mpesa-table-v1.pdf");
                var expected = getClass().getResourceAsStream("/fixtures/mpesa-table-v1.expected.json")) {
            assertNotNull(pdf); assertNotNull(expected);
            var result = parse(pdf.readAllBytes());
            assertEquals(new String(expected.readAllBytes(), StandardCharsets.UTF_8).strip(), StatementJson.write(result));
            var first = result.transactions().getFirst();
            assertEquals(2, first.sourceLines().size());
            assertEquals(20, first.sourceLines().getFirst().x(), 0.01);
            assertEquals(122, first.sourceLines().getFirst().y(), 0.01);
            assertEquals(2, first.sourceLines().getLast().page());
            assertTrue(first.sourceLines().getFirst().rawText().contains("SYN00001"));
            assertFalse(first.toString().contains("Funds received"));
            assertThrows(UnsupportedOperationException.class, () -> first.sourceLines().clear());
            assertThrows(UnsupportedOperationException.class, () -> result.transactions().clear());
            // Equal-value legitimate receipts must survive independently.
            assertEquals(result.transactions().get(0).amount(), result.transactions().get(1).amount());
            assertNotEquals(result.transactions().get(0).sourceRowKey(), result.transactions().get(1).sourceRowKey());
        }
    }
    @Test
    void yearBoundaryUsesUniqueYearFromPeriod() throws Exception {
        var result = parse(StatementPdfFixtures.create("year-boundary"));
        assertEquals(ParsedStatement.Status.PARSED, result.status());
        assertEquals(LocalDate.of(2026, 12, 31), result.transactions().get(0).transactionDate());
        assertEquals(LocalDate.of(2027, 1, 1), result.transactions().get(1).transactionDate());
        assertEquals("+03:00", result.transactions().getFirst().transactionAt().getOffset().toString());
    }
    @ParameterizedTest
    @CsvSource({"ambiguous-year,AMBIGUOUS_YEAR", "missing-period,AMBIGUOUS_YEAR",
            "bad-amount,INVALID_AMOUNT", "negative-amount,AMBIGUOUS_DIRECTION",
            "both-directions,AMBIGUOUS_DIRECTION", "invalid-date,INVALID_DATE",
            "missing-currency,CURRENCY_UNRESOLVED", "orphan,UNSUPPORTED_TRANSACTION_STATUS"})
    void ambiguousRowsRetainProvenanceForReview(String scenario, String code) throws Exception {
        var result = parse(StatementPdfFixtures.create(scenario));
        assertEquals(ParsedStatement.Status.REQUIRES_REVIEW, result.status());
        assertTrue(result.rejectedRows().stream().anyMatch(row -> row.reasonCode().equals(code)));
        assertTrue(result.rejectedRows().stream().allMatch(row -> !row.sourceLines().isEmpty()));
    }
    @Test
    void dateOnlyDoesNotInventTimeAndMissingReferencesRemainNull() throws Exception {
        var result = parse(StatementPdfFixtures.create("date-only"));
        assertNull(result.transactions().getFirst().transactionAt());
        assertEquals(LocalDate.of(2026, 10, 5), result.transactions().getFirst().transactionDate());
        result = parse(StatementPdfFixtures.create("missing-reference"));
        assertNull(result.transactions().getFirst().sourceTransactionId());
        assertEquals("p1:r5", result.transactions().getFirst().sourceRowKey());
        result = parse(StatementPdfFixtures.create("negative-balance"));
        assertEquals("-50.00", result.transactions().getLast().runningBalance().toPlainString());
    }
    @Test
    void unsupportedLayoutAndMissingInputAreExplicit() throws Exception {
        byte[] bytes = StatementPdfFixtures.create("unsupported");
        assertEquals(ProcessingErrorCode.UNSUPPORTED_LAYOUT,
                assertThrows(AnalysisProcessingException.class, () -> parse(bytes)).code());
        assertEquals(ProcessingErrorCode.INVALID_INPUT,
                assertThrows(AnalysisProcessingException.class, () -> new MpesaTableParser().parse(null)).code());
    }
}
