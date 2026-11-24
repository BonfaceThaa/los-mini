package com.credvenn.stmt.analyzer.core.analysis;

import com.credvenn.stmt.analyzer.core.*;
import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.extraction.*;
import com.credvenn.stmt.analyzer.core.parsing.*;
import java.util.*;

/** Stateless plain Java pipeline; no database, HTTP, or host workflow dependencies. */
public final class StatementAnalysisPipeline implements AnalysisPipeline {
    private final PdfTextExtractor extractor;
    private final StatementParser parser;
    private final StatementReconciler reconciler;
    private final NarrationClassifier classifier;
    private final MonthlyMetricsCalculator calculator;
    public StatementAnalysisPipeline() {
        this(new StatementReconciler());
    }
    public StatementAnalysisPipeline(StatementReconciler reconciler) {
        this(new PdfBoxTextExtractor(), new MpesaTableParser(), reconciler,
                new NarrationClassifier(), new MonthlyMetricsCalculator());
    }
    public StatementAnalysisPipeline(PdfTextExtractor extractor, StatementParser parser, StatementReconciler reconciler,
            NarrationClassifier classifier, MonthlyMetricsCalculator calculator) {
        this.extractor = Objects.requireNonNull(extractor); this.parser = Objects.requireNonNull(parser);
        this.reconciler = Objects.requireNonNull(reconciler); this.classifier = Objects.requireNonNull(classifier);
        this.calculator = Objects.requireNonNull(calculator);
    }
    @Override public AnalysisResult process(AnalysisInput input) {
        return analyzeParsed(parser.parse(extractor.extract(input)));
    }
    public AnalysisResult analyzeParsed(ParsedStatement parsed) {
        var reconciliation = reconciler.reconcile(parsed);
        var transactions = parsed.transactions().stream().map(row -> {
            var source = row.sourceLines().getFirst();
            return new NormalizedTransaction(row.sourceRowKey(), row.sourceTransactionId(), row.transactionDate(),
                    row.transactionAt(), row.direction(), row.amount(), row.currency(), row.rawNarration(), row.normalizedNarration(),
                    row.runningBalance(), new SourceProvenance(source.page(), source.row(),
                            String.join("\n", row.sourceLines().stream().map(ParsedStatement.SourceLine::rawText).toList()),
                            locations(row.sourceLines())), classifier.classify(row.direction(), row.rawNarration(), row.transactionStatus()));
        }).toList();
        var months = calculator.calculate(parsed, transactions, reconciliation);
        var warnings = new ArrayList<>(parsed.warnings());
        warnings.addAll(reconciliation.warnings());
        var rejected = parsed.rejectedRows().stream()
                .map(row -> new RejectedTransaction(row.reasonCode(), locations(row.sourceLines()))).toList();
        boolean hasCompleteMonth = months.stream().anyMatch(m -> m.coverageStatus() == CoverageStatus.COMPLETE);
        boolean hasPartialMonths = months.stream().anyMatch(m -> m.coverageStatus() == CoverageStatus.PARTIAL);
        boolean eligibilityResolved = transactions.stream().allMatch(t ->
                t.classification().balanceEligibility() != BalanceEligibility.UNDETERMINED);
        boolean usable = reconciliation.reconciled() && hasCompleteMonth && eligibilityResolved
                && months.stream().allMatch(m -> m.coverageStatus() == CoverageStatus.PARTIAL
                        || (m.coverageStatus() == CoverageStatus.COMPLETE && m.status() != Status.UNDETERMINED));
        if (hasPartialMonths) warnings.add(new Diagnostic("PARTIAL_MONTHS_EXCLUDED",
                "Partial months remain UNDETERMINED and are excluded from monthly averages.", null, null));
        Status status = !usable ? Status.UNDETERMINED
                : hasPartialMonths || reconciliation.tolerated() ? Status.COMPLETE_WITH_WARNINGS : Status.COMPLETE;
        return new AnalysisResult(parsed.correlationId(), status,
                parsed.period(), parsed.currency(), transactions, reconciliation.totalCredits(), reconciliation.totalDebits(),
                months, calculator.averages(months, reconciliation.reconciled()), warnings, reconciliation.errors(),
                reconciliation.parsingConfidence(), new Versions(parsed.parserVersion(), NarrationClassifier.VERSION,
                        MonthlyMetricsCalculator.VERSION), rejected);
    }
    private static List<SourceLocation> locations(List<ParsedStatement.SourceLine> lines) {
        return lines.stream().map(s -> new SourceLocation(s.page(), s.row(), s.rawText(), s.x(), s.y(), s.width(), s.height())).toList();
    }
}
