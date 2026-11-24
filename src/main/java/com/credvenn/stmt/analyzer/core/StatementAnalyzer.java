package com.credvenn.stmt.analyzer.core;

/** Framework-independent entry point. Processing errors are never credit decisions. */
public interface StatementAnalyzer {
    AnalysisResult analyze(AnalysisInput input);
}
