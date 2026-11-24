package com.credvenn.stmt.analyzer.core.extraction;

/** Provisional, constructor-configurable MVP limits, not a hard CPU/memory sandbox. */
public record ExtractionLimits(int maxBytes, int maxPages, int maxGlyphs, int maxCharacters) {
    public static final ExtractionLimits DEFAULT = new ExtractionLimits(10 * 1024 * 1024, 100, 250_000, 1_000_000);
    public ExtractionLimits {
        if (maxBytes < 1 || maxPages < 1 || maxGlyphs < 1 || maxCharacters < 1) {
            throw new IllegalArgumentException("Extraction limits must be positive");
        }
    }
}
