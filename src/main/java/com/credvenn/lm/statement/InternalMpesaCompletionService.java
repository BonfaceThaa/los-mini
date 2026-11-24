package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.ApplicationStatementOtpService;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.stmt.analyzer.core.AnalysisResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalMpesaCompletionService {
    private final StatementAnalysisRepository repository;
    private final StatementReviewService reviews;
    private final ApplicationService applications;
    private final ApplicationStatementOtpService otps;
    private final ObjectMapper json;

    public InternalMpesaCompletionService(StatementAnalysisRepository repository, StatementReviewService reviews,
            ApplicationService applications, ApplicationStatementOtpService otps, ObjectMapper json) {
        this.repository = repository;
        this.reviews = reviews;
        this.applications = applications;
        this.otps = otps;
        this.json = json;
    }

    @Transactional
    public void complete(String tenantId, String analysisId, String actor, AnalysisResult result, String otpId) {
        StatementAnalysis analysis = getForUpdate(tenantId, analysisId);
        if (analysis.getStatus() != StatementAnalysisStatus.IN_PROGRESS) return;
        try {
            analysis.setRawProviderResponse(json.writeValueAsString(result));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize internal statement result", ex);
        }
        analysis.setProviderStatus(result.status().name());
        analysis.setStatementOtpId(otpId);
        if (otpId != null) otps.markSuccessful(tenantId, otpId, analysis.getSourceDocumentId());
        if (result.transactions().isEmpty()) {
            markFailed(analysis, actor, "NO_TRANSACTIONS_EXTRACTED");
            return;
        }
        analysis.setStatus(StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED);
        analysis.setRecommendation("REVIEW");
        analysis.setSummary("Statement extracted; manual review required");
        if (result.monthlyAverages() != null) {
            analysis.setAverageMonthlyInflow(result.monthlyAverages().averageInflow());
            analysis.setAverageMonthlyOutflow(result.monthlyAverages().averageOutflow());
        }
        finish(analysis);
        repository.save(analysis);
        reviews.recordDecision(tenantId, analysis.getApplicationId(), analysisId,
                StatementReviewDecision.MANUAL_REVIEW_REQUIRED, StatementReviewSource.SYSTEM, actor, analysis.getSummary());
        applications.handleStatementManualReview(tenantId, analysis.getApplicationId(), actor, analysis.getSummary());
    }

    @Transactional
    public void fail(String tenantId, String analysisId, String actor, String code, String otpId) {
        StatementAnalysis analysis = getForUpdate(tenantId, analysisId);
        if (analysis.getStatus() != StatementAnalysisStatus.IN_PROGRESS) return;
        analysis.setStatementOtpId(otpId);
        if (otpId != null) otps.markFailed(tenantId, otpId, code);
        markFailed(analysis, actor, code);
    }

    private void markFailed(StatementAnalysis analysis, String actor, String code) {
        analysis.setStatus(StatementAnalysisStatus.PROCESSING_FAILED);
        analysis.setProviderStatus(code);
        analysis.setRecommendation(null);
        analysis.setSummary("Statement processing failed: " + code);
        finish(analysis);
        repository.save(analysis);
        applications.handleStatementFailed(analysis.getTenantId(), analysis.getApplicationId(), actor, analysis.getSummary());
    }

    private void finish(StatementAnalysis analysis) {
        analysis.setCompletedAt(Instant.now());
        analysis.setLastStatusCheckAt(analysis.getCompletedAt());
        analysis.setNextStatusCheckAt(null);
        analysis.setCompletionSource("INTERNAL");
    }

    private StatementAnalysis getForUpdate(String tenantId, String analysisId) {
        return repository.findForSubmissionUpdate(analysisId, tenantId)
                .orElseThrow(() -> new NotFoundException("Statement analysis not found"));
    }
}
