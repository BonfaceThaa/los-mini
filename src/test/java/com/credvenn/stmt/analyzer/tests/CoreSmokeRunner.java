package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Contract demonstration only: the synthetic pipeline does not parse PDFs. */
public final class CoreSmokeRunner {
    private CoreSmokeRunner() { }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "demo" : args[0];
        if (!List.of("demo", "empty", "unsupported", "unconfigured").contains(mode)) {
            throw new IllegalArgumentException("Use demo, empty, unsupported, or unconfigured");
        }
        byte[] content = mode.equals("empty") ? new byte[0]
                : "%PDF-synthetic-contract-input".getBytes(StandardCharsets.US_ASCII);
        var input = new AnalysisInput(content, null,
                mode.equals("unsupported") ? "OTHER" : "MPESA", "manual-002");
        StatementAnalyzer analyzer = mode.equals("unconfigured") ? new DefaultStatementAnalyzer(value -> {
                    throw new AnalysisProcessingException(ProcessingErrorCode.PIPELINE_NOT_CONFIGURED, value.correlationId());
                })
                : new DefaultStatementAnalyzer(CoreSmokeRunner::syntheticResult);
        try {
            var result = analyzer.analyze(input);
            System.out.println("Synthetic contract demo (no PDF parsing): correlationId="
                    + result.correlationId() + ", status=" + result.status()
                    + ", transactionCount=" + result.transactions().size());
        } catch (AnalysisProcessingException error) {
            System.out.println("Processing error: code=" + error.code()
                    + ", correlationId=" + error.correlationId());
            // These modes intentionally demonstrate a processing error, not a credit decision.
            if (mode.equals("demo")) throw error;
        }
    }

    static AnalysisResult syntheticResult(AnalysisInput input) {
        return new AnalysisResult(input.correlationId(), AnalysisResult.Status.UNDETERMINED,
                null, null, List.of(), null, null, List.of(),
                new AnalysisResult.MonthlyAverages(null, 0, null, null, null, null, null, null),
                List.of(new AnalysisResult.Diagnostic("SYNTHETIC_CONTRACT_DEMO",
                        "No extraction or financial calculation has been performed", null, null)),
                List.of(), null, new AnalysisResult.Versions(null, null, "balance-v2"));
    }
}
