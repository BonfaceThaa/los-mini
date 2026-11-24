package com.credvenn.stmt.analyzer.core.extraction;

import com.credvenn.stmt.analyzer.core.AnalysisInput;

/** Plain Java extraction port; no PDFBox types cross this boundary. */
public interface PdfTextExtractor {
    ExtractedDocument extract(AnalysisInput input);
}
