package com.credvenn.stmt.analyzer.core.extraction;

import com.credvenn.stmt.analyzer.core.AnalysisResult.Diagnostic;
import java.util.List;

/** Sensitive extraction data: callers must not log text/glyph accessors. */
public record ExtractedDocument(String correlationId, String extractorVersion,
        List<Page> pages, List<Diagnostic> warnings) {
    public ExtractedDocument {
        pages = List.copyOf(pages);
        warnings = List.copyOf(warnings);
    }

    @Override
    public String toString() { return "ExtractedDocument[pageCount=" + pages.size() + "]"; }

    /** Coordinates are PDFBox direction-adjusted points, from top-left; direction is in degrees.
     * Page dimensions describe the crop box before rotation. Pages are one-based.
     */
    public record Page(int number, float width, float height, int rotation,
            String text, List<Glyph> glyphs) {
        public Page { glyphs = List.copyOf(glyphs); }
        @Override
        public String toString() { return "Page[number=" + number + ", glyphCount=" + glyphs.size() + "]"; }
    }

    public record Glyph(String text, float x, float y, float width, float height, float direction) {
        @Override
        public String toString() { return "Glyph[text=REDACTED]"; }
    }
}
