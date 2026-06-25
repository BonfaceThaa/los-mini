package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CladfyAnalysisCompletionService {

    private static final Logger log = LoggerFactory.getLogger(CladfyAnalysisCompletionService.class);

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final CladfyStatementTransactionRepository transactionRepository;
    private final CladfyGateway cladfyGateway;
    private final CladfyStatementAnalysisProvider provider;
    private final ApplicationService applicationService;
    private final SubscriptionBillingService subscriptionBillingService;

    public CladfyAnalysisCompletionService(
            StatementAnalysisRepository statementAnalysisRepository,
            CladfyStatementTransactionRepository transactionRepository,
            CladfyGateway cladfyGateway,
            CladfyStatementAnalysisProvider provider,
            ApplicationService applicationService,
            SubscriptionBillingService subscriptionBillingService) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.transactionRepository = transactionRepository;
        this.cladfyGateway = cladfyGateway;
        this.provider = provider;
        this.applicationService = applicationService;
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @Transactional
    public boolean completeAnalyzed(StatementAnalysis analysis, String actor, String completionSource, String externalBusinessId) {
        if (analysis.getStatus() != StatementAnalysisStatus.IN_PROGRESS) {
            log.info(
                    "Skipping Cladfy completion for applicationId={} analysisId={} because status is already {}",
                    analysis.getApplicationId(),
                    analysis.getId(),
                    analysis.getStatus());
            return false;
        }
        if (externalBusinessId != null && !externalBusinessId.isBlank()) {
            analysis.setExternalBusinessId(externalBusinessId);
        }
        CladfyDtos.AnalysisResultsResponse results = cladfyGateway.fetchAnalysisResults(analysis.getExternalClientId());
        CladfyDtos.CreditScoreResponse score = cladfyGateway.fetchCreditScore(analysis.getExternalClientId());
        StatementAnalysisProvider.StatementDecision decision = provider.toDecision(results, score);

        analysis.setProviderStatus(results != null && results.document() != null ? results.document().status() : "analyzed");
        analysis.setStatus(decision.status());
        analysis.setAverageMonthlyInflow(decision.averageMonthlyInflow());
        analysis.setAverageMonthlyOutflow(decision.averageMonthlyOutflow());
        analysis.setAffordabilityScore(decision.affordabilityScore());
        analysis.setRecommendation(decision.recommendation());
        analysis.setSummary(decision.summary());
        analysis.setCreditScore(score == null ? null : score.score());
        analysis.setRiskTier(score == null || score.risk_tier() == null ? null : score.risk_tier().tier());
        analysis.setRawProviderResponse("analysisResults=%s score=%s fetchedAt=%s".formatted(results, score, Instant.now()));
        markCompleted(analysis, completionSource);

        transactionRepository.deleteAllByStatementAnalysisId(analysis.getId());
        if (results != null && results.transactions() != null) {
            for (CladfyDtos.Transaction transaction : results.transactions()) {
                CladfyStatementTransaction row = new CladfyStatementTransaction();
                row.setStatementAnalysisId(analysis.getId());
                row.setExternalTransactionId(transaction.id() == null ? null : String.valueOf(transaction.id()));
                row.setTransactionType(transaction.type());
                row.setTransactionAmount(transaction.amount());
                row.setTransactionDate(transaction.date());
                row.setNarration(transaction.narration());
                row.setBalance(transaction.balance());
                row.setCurrency(transaction.currency());
                transactionRepository.save(row);
            }
        }
        statementAnalysisRepository.save(analysis);

        applyApplicationOutcome(analysis, actor, decision.status(), "Cladfy score requires manual review", "Cladfy score failed statement analysis");
        log.info(
                "Completed Cladfy statement analysis applicationId={} clientId={} documentId={} score={} riskTier={} status={} source={}",
                analysis.getApplicationId(),
                analysis.getExternalClientId(),
                analysis.getExternalDocumentId(),
                analysis.getCreditScore(),
                analysis.getRiskTier(),
                analysis.getStatus(),
                completionSource);
        return true;
    }

    @Transactional
    public boolean completeFailedStatus(
            StatementAnalysis analysis,
            CladfyDtos.DocumentStatusResponse statusResponse,
            String actor,
            String completionSource) {
        if (analysis.getStatus() != StatementAnalysisStatus.IN_PROGRESS) {
            log.info(
                    "Skipping Cladfy failed-status completion for applicationId={} analysisId={} because status is already {}",
                    analysis.getApplicationId(),
                    analysis.getId(),
                    analysis.getStatus());
            return false;
        }
        analysis.setProviderStatus(statusResponse == null ? "failed" : statusResponse.status());
        analysis.setStatus(StatementAnalysisStatus.FAILED);
        analysis.setRecommendation("DECLINE");
        analysis.setSummary(statusResponse == null || statusResponse.fail_reason() == null || statusResponse.fail_reason().isBlank()
                ? "Cladfy document analysis failed"
                : "Cladfy document analysis failed: " + statusResponse.fail_reason());
        analysis.setRawProviderResponse("documentStatus=%s fetchedAt=%s".formatted(statusResponse, Instant.now()));
        markCompleted(analysis, completionSource);
        statementAnalysisRepository.save(analysis);

        applicationService.handleStatementFailed(
                analysis.getTenantId(),
                analysis.getApplicationId(),
                actor,
                statusResponse == null || statusResponse.fail_reason() == null || statusResponse.fail_reason().isBlank()
                        ? "Cladfy document analysis failed"
                        : statusResponse.fail_reason());
        log.warn(
                "Marked Cladfy statement analysis as failed applicationId={} documentId={} failReason={} source={}",
                analysis.getApplicationId(),
                analysis.getExternalDocumentId(),
                statusResponse == null ? null : statusResponse.fail_reason(),
                completionSource);
        return true;
    }

    private void markCompleted(StatementAnalysis analysis, String completionSource) {
        analysis.setNextStatusCheckAt(null);
        analysis.setLastStatusCheckAt(Instant.now());
        analysis.setCompletionSource(completionSource);
        analysis.setCompletedAt(Instant.now());
    }

    private void applyApplicationOutcome(
            StatementAnalysis analysis,
            String actor,
            StatementAnalysisStatus status,
            String manualReviewReason,
            String failedReason) {
        if (status == StatementAnalysisStatus.PASSED) {
            subscriptionBillingService.chargeStatementSuccess(analysis.getTenantId(), analysis.getId(), actor);
            applicationService.handleStatementPassed(analysis.getTenantId(), analysis.getApplicationId(), actor);
        } else if (status == StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED) {
            applicationService.handleStatementManualReview(analysis.getTenantId(), analysis.getApplicationId(), actor, manualReviewReason);
        } else {
            applicationService.handleStatementFailed(analysis.getTenantId(), analysis.getApplicationId(), actor, failedReason);
        }
    }
}
