package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.*;
import com.credvenn.stmt.analyzer.core.extraction.ExtractionLimits;
import java.nio.file.*;
import java.util.List;

/** Full core pipeline, with safe console summaries and synthetic-only JSON exports. */
public final class AnalysisSmokeRunner {
    public record ExampleMetrics(String status, java.math.BigDecimal totalInflow, java.math.BigDecimal totalOutflow,
            java.math.BigDecimal eligibleInflows, java.math.BigDecimal eligibleDebtPayments,
            java.math.BigDecimal eligibleSpending, java.math.BigDecimal monthlyBalance,
            java.math.BigDecimal excludedCredits, java.math.BigDecimal netCashMovement,
            java.math.BigDecimal openingBalance, java.math.BigDecimal closingBalance) { }
    public static String metricsProjection(AnalysisResult result) {
        var month = result.monthlyBreakdown().getFirst();
        return AnalysisJson.write(new ExampleMetrics(result.status().name(), result.totalInflow(), result.totalOutflow(),
                month.eligibleInflows(), month.eligibleDebtPayments(), month.eligibleSpending(), month.monthlyBalance(),
                month.excludedCredits(), month.netCashMovement(), month.openingBalance(), month.closingBalance()));
    }
    private AnalysisSmokeRunner() { }
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "complete" : args[0];
        boolean file = mode.equals("--file");
        if (!file && !List.of("complete", "partial", "duplicate", "missing-row", "totals-mismatch",
                "balance-mismatch", "unknown", "no-opening", "missing-balances", "negative", "zero",
                "cross-month", "zero-month").contains(mode)) throw new IllegalArgumentException("Unknown scenario");
        byte[] bytes;
        if (file) {
            if (args.length != 2) throw new IllegalArgumentException("Use --file <path>");
            try (var stream = Files.newInputStream(Path.of(args[1]))) {
                bytes = stream.readNBytes(ExtractionLimits.DEFAULT.maxBytes() + 1);
            }
        } else bytes = StatementPdfFixtures.financial(mode);
        var result = new DefaultStatementAnalyzer().analyze(new AnalysisInput(bytes,
                file ? System.getenv("ANALYZER_PDF_PASSWORD") : null, "MPESA", "manual-006"));
        System.out.println("Analysis: status=" + result.status() + ", transactions=" + result.transactions().size()
                + ", reconciliationErrors=" + result.reconciliationErrors().size());
        for (var error : result.reconciliationErrors()) System.out.println("Reconciliation code=" + error.code());
        for (var month : result.monthlyBreakdown()) {
            if (!file) System.out.println("Synthetic month=" + month.month() + ", status=" + month.status()
                    + ", monthlyBalance=" + month.monthlyBalance() + ", closingBalance=" + month.closingBalance());
        }
        if (!file) {
            var path = Path.of("analyzer-tests/target/analysis-" + mode + ".json");
            Files.createDirectories(path.getParent()); Files.writeString(path, AnalysisJson.write(result) + "\n");
            System.out.println("Synthetic analysis JSON: " + path);
        }
    }
}
