package com.credvenn.stmt.analyzer.core;

/** Stable processing categories; no lending approval or rejection codes. */
public enum ProcessingErrorCode {
    INVALID_INPUT,
    UNSUPPORTED_DOCUMENT_TYPE,
    INCORRECT_PASSWORD,
    MALFORMED_PDF,
    UNSUPPORTED_LAYOUT,
    IMAGE_ONLY_DOCUMENT,
    NO_EXTRACTABLE_TEXT,
    EXTRACTION_NOT_PERMITTED,
    RESOURCE_LIMIT_EXCEEDED,
    PIPELINE_NOT_CONFIGURED
}
