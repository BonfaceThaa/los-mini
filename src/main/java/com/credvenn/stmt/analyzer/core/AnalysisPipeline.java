package com.credvenn.stmt.analyzer.core;

/** Port for extraction, parsing, classification and metrics, implemented in later increments. */
@FunctionalInterface
public interface AnalysisPipeline {
    AnalysisResult process(AnalysisInput input);
}
