package com.credvenn.lm.statement;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.document.ApplicationDocument;
import java.math.BigDecimal;

public interface StatementAnalysisProvider {

    String providerCode();

    StatementDecision analyze(LoanRequestApplication application, ApplicationDocument document);

    record StatementDecision(
            StatementAnalysisStatus status,
            BigDecimal averageMonthlyInflow,
            BigDecimal averageMonthlyOutflow,
            BigDecimal affordabilityScore,
            String recommendation,
            String summary) {
    }
}
