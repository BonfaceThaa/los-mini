package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.lm.document.DocumentService;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementAnalysisProcessingService {

    private static final Logger log = LoggerFactory.getLogger(StatementAnalysisProcessingService.class);
    private static final Duration REUSED_UPLOAD_SCORING_MAX_AGE = Duration.ofDays(30);

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final StatementProviderRegistry statementProviderRegistry;
    private final ApplicationService applicationService;
    private final DocumentService documentService;
    private final SubscriptionBillingService subscriptionBillingService;
    private final CladfyStatusPollingService cladfyStatusPollingService;

    public StatementAnalysisProcessingService(
            StatementAnalysisRepository statementAnalysisRepository,
            StatementProviderRegistry statementProviderRegistry,
            ApplicationService applicationService,
            DocumentService documentService,
            SubscriptionBillingService subscriptionBillingService,
            CladfyStatusPollingService cladfyStatusPollingService) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.statementProviderRegistry = statementProviderRegistry;
        this.applicationService = applicationService;
        this.documentService = documentService;
        this.subscriptionBillingService = subscriptionBillingService;
        this.cladfyStatusPollingService = cladfyStatusPollingService;
    }

    @Async
    @Transactional
    public void process(String tenantId, String applicationId, String documentId, String actor, String simulateOutcome) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            log.info(
                    "Starting asynchronous statement analysis using provider={} documentId={}",
                    statementProviderRegistry.currentProvider().providerCode(),
                    documentId);
            applicationService.handleStatementInProgress(tenantId, applicationId, actor);
            ApplicationDocument document = documentService.getRequired(tenantId, documentId);
            StatementAnalysis analysis = statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElseGet(StatementAnalysis::new);
            StatementAnalysisProvider provider = statementProviderRegistry.currentProvider();
            analysis.setTenantId(tenantId);
            analysis.setApplicationId(applicationId);
            analysis.setSourceDocumentId(documentId);
            analysis.setProvider(normalizeSimulateOutcome(simulateOutcome) == null ? provider.providerCode() : "SIMULATED");
            analysis.setStatus(StatementAnalysisStatus.IN_PROGRESS);
            statementAnalysisRepository.save(analysis);

            var application = applicationService.getRequired(tenantId, applicationId);
            StatementAnalysisProvider.StatementDecision simulatedDecision = simulatedDecision(simulateOutcome);
            if (simulatedDecision != null) {
                applyDecision(tenantId, applicationId, actor, analysis, simulatedDecision);
                return;
            }
            if (provider.supportsAsyncWebhookCompletion()) {
                StatementAnalysisSubmission submission = provider.submit(application, document);
                analysis.setProvider(submission.provider());
                analysis.setProviderStatus(submission.providerStatus());
                analysis.setExternalClientId(submission.externalClientId());
                analysis.setExternalDocumentId(submission.externalDocumentId());
                analysis.setExternalBusinessId(submission.externalBusinessId());
                analysis.setSummary(submission.summary());
                analysis.setRawProviderResponse(submission.rawProviderResponse());
                if (provider instanceof CladfyStatementAnalysisProvider cladfyProvider
                        && isFreshUploadScoring(submission.uploadScoredAt())
                        && submission.uploadCreditScore() != null
                        && submission.uploadRiskTier() != null
                        && !submission.uploadRiskTier().isBlank()) {
                    var reusedDecision = cladfyProvider.toDecisionFromUploadScoring(
                            submission.uploadCreditScore(),
                            submission.uploadRiskTier());
                    applyDecision(
                            tenantId,
                            applicationId,
                            actor,
                            analysis,
                            reusedDecision,
                            submission.uploadCreditScore(),
                            submission.uploadRiskTier(),
                            "UPLOAD_RESPONSE_REUSE");
                    log.info(
                            "Completed statement analysis immediately from fresh upload scoring provider={} externalClientId={} externalDocumentId={} scoredAt={}",
                            submission.provider(),
                            submission.externalClientId(),
                            submission.externalDocumentId(),
                            submission.uploadScoredAt());
                    return;
                }
                cladfyStatusPollingService.scheduleInitialStatusCheck(analysis);
                log.info(
                        "Submitted statement analysis to provider={} externalClientId={} externalDocumentId={}",
                        submission.provider(),
                        submission.externalClientId(),
                        submission.externalDocumentId());
                return;
            }
            applyDecision(tenantId, applicationId, actor, analysis, provider.analyze(application, document));
        } catch (RuntimeException ex) {
            log.error("Asynchronous statement analysis failed", ex);
            throw ex;
        }
    }

    private void applyDecision(
            String tenantId,
            String applicationId,
            String actor,
            StatementAnalysis analysis,
            StatementAnalysisProvider.StatementDecision decision) {
        applyDecision(tenantId, applicationId, actor, analysis, decision, null, null, "DIRECT");
    }

    private void applyDecision(
            String tenantId,
            String applicationId,
            String actor,
            StatementAnalysis analysis,
            StatementAnalysisProvider.StatementDecision decision,
            Integer creditScore,
            String riskTier,
            String completionSource) {
        analysis.setStatus(decision.status());
        analysis.setAverageMonthlyInflow(decision.averageMonthlyInflow());
        analysis.setAverageMonthlyOutflow(decision.averageMonthlyOutflow());
        analysis.setAffordabilityScore(decision.affordabilityScore());
        analysis.setRecommendation(decision.recommendation());
        analysis.setSummary(decision.summary());
        analysis.setCreditScore(creditScore);
        analysis.setRiskTier(riskTier);
        analysis.setNextStatusCheckAt(null);
        analysis.setLastStatusCheckAt(Instant.now());
        analysis.setCompletionSource(completionSource);
        analysis.setCompletedAt(Instant.now());
        log.info(
                "Statement analysis completed with status={} affordabilityScore={} recommendation={}",
                decision.status(),
                decision.affordabilityScore(),
                decision.recommendation());
        if (decision.status() == StatementAnalysisStatus.PASSED) {
            subscriptionBillingService.chargeStatementSuccess(tenantId, analysis.getId(), actor);
            applicationService.handleStatementPassed(tenantId, applicationId, actor);
        } else if (decision.status() == StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED) {
            applicationService.handleStatementManualReview(tenantId, applicationId, actor, "Statement analysis requires manual review");
        } else {
            applicationService.handleStatementFailed(tenantId, applicationId, actor, "Statement analysis failed");
        }
    }

    private StatementAnalysisProvider.StatementDecision simulatedDecision(String simulateOutcome) {
        String normalized = normalizeSimulateOutcome(simulateOutcome);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "PASSED", "PASS" -> new StatementAnalysisProvider.StatementDecision(
                    StatementAnalysisStatus.PASSED,
                    BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(2400),
                    BigDecimal.valueOf(82),
                    "APPROVE",
                    "Forced simulated statement pass");
            case "FAILED", "FAIL" -> new StatementAnalysisProvider.StatementDecision(
                    StatementAnalysisStatus.FAILED,
                    BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(950),
                    BigDecimal.valueOf(20),
                    "DECLINE",
                    "Forced simulated statement failure");
            case "MANUAL_REVIEW_REQUIRED", "MANUAL_REVIEW", "REVIEW" -> new StatementAnalysisProvider.StatementDecision(
                    StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED,
                    BigDecimal.valueOf(2000),
                    BigDecimal.valueOf(1600),
                    BigDecimal.valueOf(45),
                    "REVIEW",
                    "Forced simulated statement manual review");
            default -> throw new BadRequestException("Unsupported simulateOutcome. Use PASSED, FAILED, or MANUAL_REVIEW_REQUIRED");
        };
    }

    private String normalizeSimulateOutcome(String simulateOutcome) {
        if (simulateOutcome == null) {
            return null;
        }
        String normalized = simulateOutcome.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isFreshUploadScoring(Instant scoredAt) {
        return scoredAt != null && !scoredAt.isBefore(Instant.now().minus(REUSED_UPLOAD_SCORING_MAX_AGE));
    }
}
