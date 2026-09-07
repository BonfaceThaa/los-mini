package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementSubmissionCompletionService {

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final CladfyStatusPollingService cladfyStatusPollingService;
    private final StatementReviewService statementReviewService;
    private final SubscriptionBillingService subscriptionBillingService;
    private final ApplicationService applicationService;

    public StatementSubmissionCompletionService(
            StatementAnalysisRepository statementAnalysisRepository,
            CladfyStatusPollingService cladfyStatusPollingService,
            StatementReviewService statementReviewService,
            SubscriptionBillingService subscriptionBillingService,
            ApplicationService applicationService) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.cladfyStatusPollingService = cladfyStatusPollingService;
        this.statementReviewService = statementReviewService;
        this.subscriptionBillingService = subscriptionBillingService;
        this.applicationService = applicationService;
    }

    @Transactional
    public void completeSubmission(String tenantId, String analysisId, StatementAnalysisSubmission submission) {
        StatementAnalysis analysis = getForUpdate(tenantId, analysisId);
        if (analysis.getExternalDocumentId() != null && !analysis.getExternalDocumentId().isBlank()) {
            return;
        }
        analysis.setProvider(submission.provider());
        analysis.setProviderStatus(submission.providerStatus());
        analysis.setExternalClientId(submission.externalClientId());
        analysis.setExternalDocumentId(submission.externalDocumentId());
        analysis.setExternalBusinessId(submission.externalBusinessId());
        analysis.setSummary(submission.summary());
        analysis.setRawProviderResponse(submission.rawProviderResponse());
        cladfyStatusPollingService.scheduleInitialStatusCheck(analysis);
        statementAnalysisRepository.save(analysis);
    }

    @Transactional
    public void completeDecision(
            String tenantId,
            String analysisId,
            String actor,
            StatementAnalysisProvider.StatementDecision decision,
            boolean chargeProviderCompletion) {
        StatementAnalysis analysis = getForUpdate(tenantId, analysisId);
        analysis.setStatus(decision.status());
        analysis.setAverageMonthlyInflow(decision.averageMonthlyInflow());
        analysis.setAverageMonthlyOutflow(decision.averageMonthlyOutflow());
        analysis.setAffordabilityScore(decision.affordabilityScore());
        analysis.setRecommendation(decision.recommendation());
        analysis.setSummary(decision.summary());
        analysis.setCreditScore(null);
        analysis.setRiskTier(null);
        analysis.setNextStatusCheckAt(null);
        analysis.setLastStatusCheckAt(Instant.now());
        analysis.setCompletionSource("DIRECT");
        analysis.setCompletedAt(Instant.now());
        statementAnalysisRepository.save(analysis);
        statementReviewService.recordDecision(
                tenantId,
                analysis.getApplicationId(),
                analysis.getId(),
                toReviewDecision(decision.status()),
                StatementReviewSource.SYSTEM,
                actor,
                decision.summary());
        if (chargeProviderCompletion) {
            subscriptionBillingService.chargeStatementCompletion(tenantId, analysis.getId(), actor);
        }
        if (decision.status() == StatementAnalysisStatus.PASSED) {
            applicationService.handleStatementPassed(tenantId, analysis.getApplicationId(), actor);
        } else if (decision.status() == StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED) {
            applicationService.handleStatementManualReview(
                    tenantId,
                    analysis.getApplicationId(),
                    actor,
                    "Statement analysis requires manual review");
        } else {
            applicationService.handleStatementFailed(
                    tenantId,
                    analysis.getApplicationId(),
                    actor,
                    "Statement analysis failed");
        }
    }

    private StatementAnalysis getForUpdate(String tenantId, String analysisId) {
        return statementAnalysisRepository.findForSubmissionUpdate(analysisId, tenantId)
                .orElseThrow(() -> new NotFoundException("Statement analysis not found"));
    }

    private StatementReviewDecision toReviewDecision(StatementAnalysisStatus status) {
        return switch (status) {
            case PASSED -> StatementReviewDecision.APPROVED;
            case FAILED -> StatementReviewDecision.REJECTED;
            case MANUAL_REVIEW_REQUIRED -> StatementReviewDecision.MANUAL_REVIEW_REQUIRED;
            case PENDING, IN_PROGRESS -> throw new IllegalArgumentException(
                    "Cannot record review decision for non-final status " + status);
        };
    }
}
