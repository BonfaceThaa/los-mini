package com.credvenn.lm.statement;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.document.ApplicationDocument;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class SimulatedStatementAnalysisProvider implements StatementAnalysisProvider {

    @Override
    public String providerCode() {
        return "SIMULATED";
    }

    @Override
    public StatementDecision analyze(LoanRequestApplication application, ApplicationDocument document) {
        String filename = document.getOriginalFilename().toLowerCase();
        if (filename.contains("fail")) {
            return new StatementDecision(
                    StatementAnalysisStatus.FAILED,
                    BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(950),
                    BigDecimal.valueOf(20),
                    "DECLINE",
                    "Deterministic simulated statement failure");
        }
        if (filename.contains("review")) {
            return new StatementDecision(
                    StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED,
                    BigDecimal.valueOf(2000),
                    BigDecimal.valueOf(1600),
                    BigDecimal.valueOf(45),
                    "REVIEW",
                    "Deterministic simulated statement manual review");
        }
        return new StatementDecision(
                StatementAnalysisStatus.PASSED,
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(2400),
                BigDecimal.valueOf(82),
                "APPROVE",
                "Deterministic simulated statement pass");
    }
}
