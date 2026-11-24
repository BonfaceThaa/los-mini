package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.*;
import com.credvenn.stmt.analyzer.core.extraction.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Manual extraction check. Prints only safe counts/codes, never PDF text or passwords. */
public final class ExtractionSmokeRunner {
    private ExtractionSmokeRunner() { }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "plain" : args[0];
        var limits = ExtractionLimits.DEFAULT;
        String password = null;
        byte[] bytes;
        if (mode.equals("--file")) {
            if (args.length != 2) throw new IllegalArgumentException("Use --file <pdf-path>");
            password = System.getenv("ANALYZER_PDF_PASSWORD");
            try (var stream = Files.newInputStream(Path.of(args[1]))) {
                bytes = stream.readNBytes(limits.maxBytes() + 1);
            }
        } else {
            switch (mode) {
                case "plain" -> bytes = SyntheticPdfs.text(2, null, true);
                case "encrypted", "wrong-password", "restricted" -> {
                    String correctPassword = UUID.randomUUID().toString();
                    bytes = SyntheticPdfs.text(1, correctPassword, !mode.equals("restricted"));
                    password = mode.equals("wrong-password") ? UUID.randomUUID().toString() : correctPassword;
                }
                case "malformed" -> bytes = "%PDF-1.7\ninvalid synthetic body".getBytes(StandardCharsets.US_ASCII);
                case "non-pdf" -> bytes = "synthetic text, not PDF".getBytes(StandardCharsets.US_ASCII);
                case "image-only" -> bytes = SyntheticPdfs.withoutText(true);
                case "blank" -> bytes = SyntheticPdfs.withoutText(false);
                case "mixed" -> bytes = SyntheticPdfs.mixed();
                case "page-limit" -> {
                    bytes = SyntheticPdfs.text(2, null, true);
                    limits = new ExtractionLimits(limits.maxBytes(), 1, limits.maxGlyphs(), limits.maxCharacters());
                }
                case "byte-limit" -> {
                    bytes = SyntheticPdfs.text(1, null, true);
                    limits = new ExtractionLimits(bytes.length - 1, 100, 250_000, 1_000_000);
                }
                case "glyph-limit", "text-limit" -> {
                    bytes = SyntheticPdfs.text(1, null, true);
                    limits = new ExtractionLimits(limits.maxBytes(), 100,
                            mode.equals("glyph-limit") ? 5 : 250_000,
                            mode.equals("text-limit") ? 5 : 1_000_000);
                }
                default -> throw new IllegalArgumentException("Unknown extraction scenario");
            }
        }
        try {
            var result = new PdfBoxTextExtractor(limits).extract(
                    new AnalysisInput(bytes, password, "MPESA", "manual-003"));
            System.out.println("Extraction OK: correlationId=" + result.correlationId()
                    + ", pages=" + result.pages().size() + ", warnings=" + result.warnings().size());
            for (var page : result.pages()) {
                System.out.println("Page " + page.number() + ": characters=" + page.text().length()
                        + ", glyphs=" + page.glyphs().size());
            }
            for (var warning : result.warnings()) {
                System.out.println("Warning: code=" + warning.code() + ", page=" + warning.sourcePage());
            }
        } catch (AnalysisProcessingException error) {
            System.out.println("Processing error: code=" + error.code() + ", correlationId=" + error.correlationId());
            // Fixture error scenarios intentionally demonstrate an error and exit normally.
            if (List.of("plain", "encrypted", "mixed", "--file").contains(mode)) throw error;
        }
    }
}
