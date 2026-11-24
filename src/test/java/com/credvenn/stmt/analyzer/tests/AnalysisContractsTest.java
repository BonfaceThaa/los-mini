package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisContractsTest {
    static AnalysisInput input() {
        return new AnalysisInput(new byte[]{1, 2, 3}, "synthetic-secret", "MPESA", "test-002");
    }

    @Test
    void inputCopiesBytesAndRedactsSensitiveFields() {
        byte[] bytes = {1, 2, 3};
        var input = new AnalysisInput(bytes, "synthetic-secret", "MPESA", "test-002");
        bytes[0] = 9;
        input.pdfContent()[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, input.pdfContent());
        assertFalse(input.toString().contains("synthetic-secret"));
        assertFalse(input.toString().contains("test-002"));
    }

    static Stream<AnalysisInput> invalidInputs() {
        return Stream.of(null,
                new AnalysisInput(null, null, "MPESA", "test-002"),
                new AnalysisInput(new byte[0], null, "MPESA", "test-002"),
                new AnalysisInput(new byte[]{1}, null, null, "test-002"),
                new AnalysisInput(new byte[]{1}, null, " ", "test-002"),
                new AnalysisInput(new byte[]{1}, null, "MPESA", null),
                new AnalysisInput(new byte[]{1}, null, "MPESA", " "));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void invalidInputNeverReachesPipeline(AnalysisInput input) {
        var called = new AtomicBoolean();
        var analyzer = new DefaultStatementAnalyzer(value -> {
            called.set(true);
            return CoreSmokeRunner.syntheticResult(value);
        });
        var error = assertThrows(AnalysisProcessingException.class, () -> analyzer.analyze(input));
        assertEquals(ProcessingErrorCode.INVALID_INPUT, error.code());
        assertEquals(input == null ? null : input.correlationId(), error.correlationId());
        assertFalse(called.get());
    }

    @Test
    void unsupportedTypeAndMalformedPdfAreExplicitProcessingErrors() {
        var analyzer = new DefaultStatementAnalyzer();
        var unsupported = new AnalysisInput(new byte[]{1}, null, "OTHER", "type-test");
        var error = assertThrows(AnalysisProcessingException.class, () -> analyzer.analyze(unsupported));
        assertEquals(ProcessingErrorCode.UNSUPPORTED_DOCUMENT_TYPE, error.code());
        assertEquals("type-test", error.correlationId());
        error = assertThrows(AnalysisProcessingException.class, () -> analyzer.analyze(input()));
        assertEquals(ProcessingErrorCode.MALFORMED_PDF, error.code());
        assertEquals("test-002", error.correlationId());
        assertFalse(error.toString().contains("synthetic-secret"));
    }

    @Test
    void plainJavaPipelineReturnsResultAndPropagatesTypedErrors() {
        var expected = CoreSmokeRunner.syntheticResult(input());
        StatementAnalyzer analyzer = new DefaultStatementAnalyzer(value -> expected);
        assertSame(expected, analyzer.analyze(input()));
        var failure = new AnalysisProcessingException(ProcessingErrorCode.INCORRECT_PASSWORD, "test-002");
        analyzer = new DefaultStatementAnalyzer(value -> { throw failure; });
        var failingAnalyzer = analyzer;
        assertSame(failure, assertThrows(AnalysisProcessingException.class,
                () -> failingAnalyzer.analyze(input())));
        var mismatched = new DefaultStatementAnalyzer(value -> CoreSmokeRunner.syntheticResult(
                new AnalysisInput(new byte[]{1}, null, "MPESA", "wrong-correlation")));
        assertThrows(IllegalStateException.class, () -> mismatched.analyze(input()));
    }

    @Test
    void resultCollectionsAreSnapshotsAndMissingMetricsStayUnknown() {
        var warnings = new ArrayList<AnalysisResult.Diagnostic>();
        warnings.add(new AnalysisResult.Diagnostic("TEST", "Safe detail", null, null));
        var result = new AnalysisResult("test-002", AnalysisResult.Status.UNDETERMINED,
                null, null, List.of(), null, null, List.of(), null, warnings, List.of(),
                null, new AnalysisResult.Versions(null, null, "balance-v2"));
        warnings.clear();
        assertEquals(1, result.warnings().size());
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().clear());
        assertNull(result.totalInflow());
        assertNull(result.parsingConfidence());
    }

    @Test
    void contractRejectsInventedAveragesAndUndeterminedBalances() {
        assertThrows(IllegalArgumentException.class, () -> new AnalysisResult.MonthlyAverages(
                null, 0, BigDecimal.ZERO, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisResult.MonthlyMetrics(
                YearMonth.of(2026, 10), AnalysisResult.CoverageStatus.PARTIAL,
                AnalysisResult.Status.UNDETERMINED,
                null, null, null, null, null, null, null, null, BigDecimal.ZERO,
                null, null, null, List.of(), List.of()));
    }
}
