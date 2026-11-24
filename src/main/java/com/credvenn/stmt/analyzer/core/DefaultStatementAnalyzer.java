package com.credvenn.stmt.analyzer.core;

import java.util.Objects;

/** Validates the public boundary and delegates to a constructor-injected plain Java pipeline. */
public final class DefaultStatementAnalyzer implements StatementAnalyzer {
    private final AnalysisPipeline pipeline;

    /** Production PDF extraction, parsing, reconciliation, classification and metric pipeline. */
    public DefaultStatementAnalyzer() {
        this(new com.credvenn.stmt.analyzer.core.analysis.StatementAnalysisPipeline());
    }

    public DefaultStatementAnalyzer(AnalysisPipeline pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    @Override
    public AnalysisResult analyze(AnalysisInput input) {
        String correlationId = input == null ? null : input.correlationId();
        if (input == null || correlationId == null || correlationId.isBlank()
                || input.contentLength() == 0 || input.documentType() == null
                || input.documentType().isBlank()) {
            throw new AnalysisProcessingException(ProcessingErrorCode.INVALID_INPUT, correlationId);
        }
        if (!"MPESA".equals(input.documentType())) {
            throw new AnalysisProcessingException(ProcessingErrorCode.UNSUPPORTED_DOCUMENT_TYPE, correlationId);
        }
        var result = Objects.requireNonNull(pipeline.process(input), "pipeline result");
        if (!correlationId.equals(result.correlationId())) {
            throw new IllegalStateException("Pipeline result correlation ID does not match input");
        }
        return result;
    }
}
