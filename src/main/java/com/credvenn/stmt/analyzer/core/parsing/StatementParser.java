package com.credvenn.stmt.analyzer.core.parsing;

import com.credvenn.stmt.analyzer.core.extraction.ExtractedDocument;

public interface StatementParser {
    ParsedStatement parse(ExtractedDocument document);
}
