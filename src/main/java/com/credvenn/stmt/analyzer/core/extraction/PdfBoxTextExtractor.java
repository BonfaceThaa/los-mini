package com.credvenn.stmt.analyzer.core.extraction;

import com.credvenn.stmt.analyzer.core.AnalysisInput;
import com.credvenn.stmt.analyzer.core.AnalysisProcessingException;
import com.credvenn.stmt.analyzer.core.AnalysisResult.Diagnostic;
import com.credvenn.stmt.analyzer.core.ProcessingErrorCode;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/** Selectable-text adapter. No OCR, rendering, embedded actions, or external resource fetching. */
public final class PdfBoxTextExtractor implements PdfTextExtractor {
    public static final String VERSION = "pdfbox-3.0.8/extractor-v1";
    private final ExtractionLimits limits;

    public PdfBoxTextExtractor() { this(ExtractionLimits.DEFAULT); }
    public PdfBoxTextExtractor(ExtractionLimits limits) { this.limits = Objects.requireNonNull(limits); }

    @Override
    public ExtractedDocument extract(AnalysisInput input) {
        String id = input == null ? null : input.correlationId();
        if (input == null || id == null || id.isBlank() || input.contentLength() == 0
                || input.documentType() == null || input.documentType().isBlank()) {
            throw error(ProcessingErrorCode.INVALID_INPUT, id);
        }
        if (!"MPESA".equals(input.documentType())) throw error(ProcessingErrorCode.UNSUPPORTED_DOCUMENT_TYPE, id);
        if (input.contentLength() > limits.maxBytes()) throw error(ProcessingErrorCode.RESOURCE_LIMIT_EXCEEDED, id);
        byte[] bytes = input.pdfContent();
        // Strict PDF signature: do not accept a renamed non-PDF or repair arbitrary leading data.
        if (bytes.length < 5 || !"%PDF-".equals(new String(bytes, 0, 5, StandardCharsets.US_ASCII))) {
            throw error(ProcessingErrorCode.MALFORMED_PDF, id);
        }
        try (PDDocument document = Loader.loadPDF(bytes, input.password() == null ? "" : input.password())) {
            int count = document.getNumberOfPages();
            if (count > limits.maxPages()) throw error(ProcessingErrorCode.RESOURCE_LIMIT_EXCEEDED, id);
            if (count == 0) throw error(ProcessingErrorCode.NO_EXTRACTABLE_TEXT, id);
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw error(ProcessingErrorCode.EXTRACTION_NOT_PERMITTED, id);
            }
            var stripper = new PositionedStripper(limits, id);
            stripper.writeText(document, Writer.nullWriter());
            // PDFTextStripper skips pages without content streams. Restore them explicitly
            // so empty pages never disappear from source provenance or coverage checks.
            var pagesByNumber = new java.util.HashMap<Integer, ExtractedDocument.Page>();
            for (var page : stripper.pages) pagesByNumber.put(page.number(), page);
            var pages = new ArrayList<ExtractedDocument.Page>();
            for (int i = 0; i < count; i++) {
                var page = document.getPage(i);
                pages.add(pagesByNumber.getOrDefault(i + 1, new ExtractedDocument.Page(i + 1,
                        page.getCropBox().getWidth(), page.getCropBox().getHeight(), page.getRotation(),
                        "", List.of())));
            }
            if (pages.stream().allMatch(page -> page.text().isBlank())) {
                // No selectable text does not establish whether a page is scanned or simply blank.
                throw error(ProcessingErrorCode.NO_EXTRACTABLE_TEXT, id);
            }
            var warnings = new ArrayList<Diagnostic>();
            for (var page : pages) {
                if (page.text().isBlank()) warnings.add(new Diagnostic("PAGE_WITHOUT_TEXT",
                        "Page has no selectable text; review coverage or use a separate OCR adapter",
                        null, page.number()));
            }
            return new ExtractedDocument(id, VERSION, pages, warnings);
        } catch (InvalidPasswordException e) {
            throw error(ProcessingErrorCode.INCORRECT_PASSWORD, id);
        } catch (IOException | IllegalArgumentException e) {
            // Do not propagate PDFBox messages/causes that may contain sensitive document fragments.
            throw error(ProcessingErrorCode.MALFORMED_PDF, id);
        }
    }

    private static AnalysisProcessingException error(ProcessingErrorCode code, String id) {
        return new AnalysisProcessingException(code, id);
    }

    private static final class PositionedStripper extends PDFTextStripper {
        private final ExtractionLimits limits;
        private final String correlationId;
        private final List<ExtractedDocument.Page> pages = new ArrayList<>();
        private StringBuilder text;
        private List<ExtractedDocument.Glyph> glyphs;
        private long seenGlyphs;
        private int characters;
        private long decodedCharacters;

        PositionedStripper(ExtractionLimits limits, String correlationId) throws IOException {
            this.limits = limits;
            this.correlationId = correlationId;
            setSortByPosition(true);
            setLineSeparator("\n");
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            text = new StringBuilder();
            glyphs = new ArrayList<>();
            super.startPage(page);
        }

        @Override
        protected void processTextPosition(TextPosition position) {
            decodedCharacters += position.getUnicode().length();
            if (++seenGlyphs > limits.maxGlyphs() || decodedCharacters > limits.maxCharacters()) {
                throw error(ProcessingErrorCode.RESOURCE_LIMIT_EXCEEDED, correlationId);
            }
            super.processTextPosition(position);
        }

        @Override
        protected void writeString(String value, List<TextPosition> positions) {
            append(value);
            for (var p : positions) glyphs.add(new ExtractedDocument.Glyph(p.getUnicode(),
                    p.getXDirAdj(), p.getYDirAdj(), p.getWidthDirAdj(), p.getHeightDir(), p.getDir()));
        }

        @Override
        protected void writeWordSeparator() { append(" "); }
        @Override
        protected void writeLineSeparator() { append("\n"); }

        private void append(String value) {
            if (value.length() > limits.maxCharacters() - characters) {
                throw error(ProcessingErrorCode.RESOURCE_LIMIT_EXCEEDED, correlationId);
            }
            characters += value.length();
            text.append(value);
        }

        @Override
        protected void writePageEnd() {
            if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') append("\n");
        }

        @Override
        protected void writeParagraphSeparator() throws IOException {
            if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') append("\n");
            super.writeParagraphSeparator();
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            pages.add(new ExtractedDocument.Page(getCurrentPageNo(), page.getCropBox().getWidth(),
                    page.getCropBox().getHeight(), page.getRotation(), text.toString(), glyphs));
            super.endPage(page);
        }
    }
}
