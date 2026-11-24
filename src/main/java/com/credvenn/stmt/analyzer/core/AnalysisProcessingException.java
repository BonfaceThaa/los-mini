package com.credvenn.stmt.analyzer.core;

import java.util.Objects;

/** Contains only a stable code and correlation ID, never raw parser messages or secrets. */
public final class AnalysisProcessingException extends RuntimeException {
    private final ProcessingErrorCode code;
    private final String correlationId;

    public AnalysisProcessingException(ProcessingErrorCode code, String correlationId) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
        this.correlationId = correlationId;
    }

    public ProcessingErrorCode code() { return code; }
    public String correlationId() { return correlationId; }
}
