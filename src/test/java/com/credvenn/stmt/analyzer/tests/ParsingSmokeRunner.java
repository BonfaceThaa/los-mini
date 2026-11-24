package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.*;
import com.credvenn.stmt.analyzer.core.extraction.*;
import com.credvenn.stmt.analyzer.core.parsing.*;
import java.nio.file.*;
import java.util.List;

/** Safe console summary. Only synthetic fixture results are automatically exported. */
public final class ParsingSmokeRunner {
    private ParsingSmokeRunner() { }
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "wrapped" : args[0];
        var scenarios = List.of("wrapped", "year-boundary", "ambiguous-year", "missing-period",
                "bad-amount", "negative-amount", "both-directions", "invalid-date", "date-only",
                "negative-balance", "missing-reference", "missing-currency", "orphan", "unsupported");
        boolean file = mode.equals("--file");
        if (!file && !scenarios.contains(mode)) throw new IllegalArgumentException("Unknown parser scenario");
        byte[] bytes;
        if (file) {
            if (args.length != 2) throw new IllegalArgumentException("Use --file <path>");
            try (var stream = Files.newInputStream(Path.of(args[1]))) {
                bytes = stream.readNBytes(ExtractionLimits.DEFAULT.maxBytes() + 1);
            }
        } else {
            bytes = StatementPdfFixtures.create(mode);
        }
        try {
            var extracted = new PdfBoxTextExtractor().extract(new AnalysisInput(bytes,
                    file ? System.getenv("ANALYZER_PDF_PASSWORD") : null, "MPESA", "manual-004"));
            var result = new MpesaTableParser().parse(extracted);
            System.out.println("Parsing: status=" + result.status() + ", transactions=" + result.transactions().size()
                    + ", rejected=" + result.rejectedRows().size() + ", warnings=" + result.warnings().size());
            for (var warning : result.warnings()) System.out.println("Review code=" + warning.code());
            if (!file) {
                var output = Path.of("analyzer-tests/target/parser-" + mode + ".json");
                Files.createDirectories(output.getParent());
                Files.writeString(output, StatementJson.write(result) + "\n");
                System.out.println("Synthetic transaction JSON: " + output);
            }
        } catch (AnalysisProcessingException e) {
            System.out.println("Processing error: code=" + e.code() + ", correlationId=" + e.correlationId());
            if (!mode.equals("unsupported")) throw e;
        }
    }
}
