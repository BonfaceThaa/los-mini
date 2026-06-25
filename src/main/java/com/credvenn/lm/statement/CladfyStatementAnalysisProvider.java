package com.credvenn.lm.statement;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.document.ApplicationDocument;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CladfyStatementAnalysisProvider implements StatementAnalysisProvider {

    private final CladfyGateway cladfyGateway;

    public CladfyStatementAnalysisProvider(CladfyGateway cladfyGateway) {
        this.cladfyGateway = cladfyGateway;
    }

    @Override
    public String providerCode() {
        return "CLADFY";
    }

    @Override
    public StatementDecision analyze(LoanRequestApplication application, ApplicationDocument document) {
        throw new UnsupportedOperationException("Cladfy uses asynchronous submission and webhook completion");
    }

    @Override
    public boolean supportsAsyncWebhookCompletion() {
        return true;
    }

    @Override
    public StatementAnalysisSubmission submit(LoanRequestApplication application, ApplicationDocument document) {
        return cladfyGateway.submit(application, document);
    }

    public StatementDecision toDecision(CladfyDtos.AnalysisResultsResponse analysis, CladfyDtos.CreditScoreResponse score) {
        String tier = score == null || score.risk_tier() == null ? null : score.risk_tier().tier();
        StatementAnalysisStatus status = mapTierToStatus(tier);
        String recommendation = switch (status) {
            case PASSED -> "APPROVE";
            case MANUAL_REVIEW_REQUIRED -> "REVIEW";
            case FAILED -> "DECLINE";
            default -> "REVIEW";
        };
        String summary = "Cladfy score=%s riskTier=%s totalIn=%s totalOut=%s".formatted(
                score == null ? null : score.score(),
                tier,
                analysis == null || analysis.summary() == null ? null : analysis.summary().total_in(),
                analysis == null || analysis.summary() == null ? null : analysis.summary().total_out());
        return new StatementDecision(
                status,
                analysis == null || analysis.summary() == null ? null : analysis.summary().total_in(),
                analysis == null || analysis.summary() == null ? null : analysis.summary().total_out(),
                score == null || score.score() == null ? null : BigDecimal.valueOf(score.score()),
                recommendation,
                summary);
    }

    public StatementDecision toDecisionFromUploadScoring(Integer score, String riskTier) {
        StatementAnalysisStatus status = mapTierToStatus(riskTier);
        String recommendation = switch (status) {
            case PASSED -> "APPROVE";
            case MANUAL_REVIEW_REQUIRED -> "REVIEW";
            case FAILED -> "DECLINE";
            default -> "REVIEW";
        };
        String summary = "Cladfy reused existing scoring score=%s riskTier=%s".formatted(score, riskTier);
        return new StatementDecision(
                status,
                null,
                null,
                score == null ? null : BigDecimal.valueOf(score),
                recommendation,
                summary);
    }

    private StatementAnalysisStatus mapTierToStatus(String tier) {
        if (tier == null) {
            return StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED;
        }
        return switch (tier.trim().toUpperCase()) {
            case "EXCELLENT", "GOOD" -> StatementAnalysisStatus.PASSED;
            case "FAIR" -> StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED;
            case "POOR", "VERY POOR" -> StatementAnalysisStatus.FAILED;
            default -> StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED;
        };
    }
}
