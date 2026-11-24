package com.credvenn.lm.statement;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.stmt.analyzer.core.AnalysisInput;
import com.credvenn.stmt.analyzer.core.AnalysisResult;
import com.credvenn.stmt.analyzer.core.DefaultStatementAnalyzer;
import com.credvenn.stmt.analyzer.core.StatementAnalyzer;
import org.springframework.stereotype.Component;

@Component
public class InternalMpesaStatementAnalysisProvider implements StatementAnalysisProvider {
    private final StatementAnalyzer analyzer;

    public InternalMpesaStatementAnalysisProvider() {
        this(new DefaultStatementAnalyzer());
    }

    InternalMpesaStatementAnalysisProvider(StatementAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override public String providerCode() { return "INTERNAL_MPESA"; }

    @Override public StatementDecision analyze(LoanRequestApplication application, ApplicationDocument document) {
        throw new UnsupportedOperationException("Internal analysis requires document content and optional password");
    }

    AnalysisResult analyze(byte[] pdf, String password, String analysisId) {
        return analyzer.analyze(new AnalysisInput(pdf, password, "MPESA", analysisId));
    }
}
