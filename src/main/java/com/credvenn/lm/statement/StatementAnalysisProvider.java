package com.credvenn.lm.statement;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.document.ApplicationDocument;
import java.math.BigDecimal;

public interface StatementAnalysisProvider {

    String providerCode();

    StatementDecision analyze(LoanRequestApplication application, ApplicationDocument document);

    default boolean supportsAsyncWebhookCompletion() {
        return false;
    }

    default StatementAnalysisSubmission submit(LoanRequestApplication application, ApplicationDocument document) {
        throw new UnsupportedOperationException("Provider does not support asynchronous submission");
    }

    record StatementDecision(
            StatementAnalysisStatus status,
            BigDecimal averageMonthlyInflow,
            BigDecimal averageMonthlyOutflow,
            BigDecimal affordabilityScore,
            String recommendation,
            String summary) {
    }
}
