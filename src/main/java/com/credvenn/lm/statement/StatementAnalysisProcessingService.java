package com.credvenn.lm.statement;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.logging.LoggingContext;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class StatementAnalysisProcessingService {

    private static final Logger log = LoggerFactory.getLogger(StatementAnalysisProcessingService.class);
    private static final int MAX_SUBMISSION_ATTEMPTS = 3;

    private final StatementProviderRegistry statementProviderRegistry;
    private final StatementSubmissionPreparationService preparationService;
    private final StatementSubmissionCompletionService completionService;

    public StatementAnalysisProcessingService(
            StatementProviderRegistry statementProviderRegistry,
            StatementSubmissionPreparationService preparationService,
            StatementSubmissionCompletionService completionService) {
        this.statementProviderRegistry = statementProviderRegistry;
        this.preparationService = preparationService;
        this.completionService = completionService;
    }

    @Async
    public void process(String tenantId, String analysisId, String actor, String simulateOutcome) {
        StatementAnalysisProvider provider = statementProviderRegistry.currentProvider();
        StatementAnalysisProvider.StatementDecision simulatedDecision = simulatedDecision(simulateOutcome);
        boolean asynchronousSubmission = simulatedDecision == null && provider.supportsAsyncWebhookCompletion();

        StatementSubmissionPreparationService.PreparedSubmission work = prepareWithRetry(
                tenantId,
                analysisId,
                actor,
                normalizeSimulateOutcome(simulateOutcome) == null ? provider.providerCode() : "SIMULATED",
                asynchronousSubmission);

        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, work.applicationId())) {
            log.info(
                    "Starting asynchronous statement analysis using provider={} documentId={}",
                    provider.providerCode(),
                    work.documentId());
            if (work.alreadySubmitted()) {
                log.info("Statement submission already has external identifiers; skipping provider call");
                return;
            }
            if (simulatedDecision != null) {
                completionService.completeDecision(tenantId, analysisId, actor, simulatedDecision, false);
                return;
            }
            if (!asynchronousSubmission) {
                StatementAnalysisProvider.StatementDecision decision = provider.analyze(work.application(), work.document());
                completionService.completeDecision(tenantId, analysisId, actor, decision, true);
                return;
            }
            submitWithRecovery(provider, work, actor);
        } catch (RuntimeException ex) {
            log.error("Asynchronous statement analysis failed", ex);
            throw ex;
        }
    }

    private void submitWithRecovery(
            StatementAnalysisProvider provider,
            StatementSubmissionPreparationService.PreparedSubmission initialWork,
            String actor) {
        StatementSubmissionPreparationService.PreparedSubmission work = initialWork;
        RuntimeException lastFailure = null;
        StatementAnalysisSubmission submission = null;

        for (int attempt = 1; attempt <= MAX_SUBMISSION_ATTEMPTS; attempt++) {
            try {
                if (submission == null && (work.recoveryAttempt() || attempt > 1)) {
                    Optional<StatementAnalysisSubmission> recovered = provider.recoverSubmission(
                            work.application(), work.document(), work.recoveryNotBefore());
                    if (recovered.isPresent()) {
                        submission = recovered.get();
                        log.info(
                                "Recovered existing provider statement submission externalClientId={} externalDocumentId={}",
                                submission.externalClientId(),
                                submission.externalDocumentId());
                    }
                }
                if (submission == null) {
                    submission = provider.submit(
                            work.application(),
                            work.document(),
                            work.resolvedOtp().otpValue());
                }
                completionService.completeSubmission(work.tenantId(), work.analysisId(), submission);
                log.info(
                        "Submitted statement analysis to provider={} externalClientId={} externalDocumentId={} otpId={}",
                        submission.provider(),
                        submission.externalClientId(),
                        submission.externalDocumentId(),
                        work.resolvedOtp().otpId());
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt == MAX_SUBMISSION_ATTEMPTS || !isRetryable(ex)) {
                    throw ex;
                }
                backoff(attempt);
                work = prepareWithRetry(
                        work.tenantId(),
                        work.analysisId(),
                        actor,
                        provider.providerCode(),
                        true);
            }
        }
        throw lastFailure == null ? new IllegalStateException("Statement submission failed") : lastFailure;
    }

    private StatementSubmissionPreparationService.PreparedSubmission prepareWithRetry(
            String tenantId,
            String analysisId,
            String actor,
            String providerCode,
            boolean requiresOtp) {
        for (int attempt = 1; ; attempt++) {
            try {
                return preparationService.prepare(tenantId, analysisId, actor, providerCode, requiresOtp);
            } catch (TransientDataAccessException ex) {
                if (attempt == MAX_SUBMISSION_ATTEMPTS) {
                    throw ex;
                }
                backoff(attempt);
            }
        }
    }

    private boolean isRetryable(RuntimeException exception) {
        return exception instanceof TransientDataAccessException
                || exception instanceof RestClientException;
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(100L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Statement submission retry interrupted", ex);
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
            default -> throw new BadRequestException(
                    "Unsupported simulateOutcome. Use PASSED, FAILED, or MANUAL_REVIEW_REQUIRED");
        };
    }

    private String normalizeSimulateOutcome(String simulateOutcome) {
        if (simulateOutcome == null) {
            return null;
        }
        String normalized = simulateOutcome.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
