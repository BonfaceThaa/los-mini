package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationStatementOtpService;
import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    private final StatementReviewService statementReviewService;
    private final ApplicationStatementOtpService applicationStatementOtpService;
    private final ObjectMapper objectMapper;

    public CladfyAnalysisCompletionService(
            StatementAnalysisRepository statementAnalysisRepository,
            CladfyStatementTransactionRepository transactionRepository,
            CladfyGateway cladfyGateway,
            CladfyStatementAnalysisProvider provider,
            ApplicationService applicationService,
            SubscriptionBillingService subscriptionBillingService,
            StatementReviewService statementReviewService,
            ApplicationStatementOtpService applicationStatementOtpService,
            ObjectMapper objectMapper) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.transactionRepository = transactionRepository;
        this.cladfyGateway = cladfyGateway;
        this.provider = provider;
        this.applicationService = applicationService;
        this.subscriptionBillingService = subscriptionBillingService;
        this.statementReviewService = statementReviewService;
        this.applicationStatementOtpService = applicationStatementOtpService;
        this.objectMapper = objectMapper;
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
        analysis.setRiskTier(score == null || score.risk_tier() == null ? null : score.risk_tier().risk());
        analysis.setRawProviderResponse(buildStoredAnalysisResponse(results));
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
        if (analysis.getStatementOtpId() != null && !analysis.getStatementOtpId().isBlank()) {
            applicationStatementOtpService.markSuccessful(analysis.getTenantId(), analysis.getStatementOtpId(), analysis.getSourceDocumentId());
        }
        recordSystemDecision(analysis, actor, decision.status(), decision.summary());

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
        if (analysis.getStatementOtpId() != null && !analysis.getStatementOtpId().isBlank()) {
            applicationStatementOtpService.markFailed(
                    analysis.getTenantId(),
                    analysis.getStatementOtpId(),
                    statusResponse == null ? null : statusResponse.fail_reason());
        }
        recordSystemDecision(analysis, actor, StatementAnalysisStatus.FAILED, analysis.getSummary());

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

    private void recordSystemDecision(
            StatementAnalysis analysis,
            String actor,
            StatementAnalysisStatus status,
            String reason) {
        statementReviewService.recordDecision(
                analysis.getTenantId(),
                analysis.getApplicationId(),
                analysis.getId(),
                toReviewDecision(status),
                StatementReviewSource.SYSTEM,
                actor,
                reason);
    }

    private StatementReviewDecision toReviewDecision(StatementAnalysisStatus status) {
        return switch (status) {
            case PASSED -> StatementReviewDecision.APPROVED;
            case FAILED -> StatementReviewDecision.REJECTED;
            case MANUAL_REVIEW_REQUIRED -> StatementReviewDecision.MANUAL_REVIEW_REQUIRED;
            case PENDING, IN_PROGRESS -> throw new IllegalArgumentException("Cannot record review decision for non-final status " + status);
        };
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
        subscriptionBillingService.chargeStatementCompletion(analysis.getTenantId(), analysis.getId(), actor);
        if (status == StatementAnalysisStatus.PASSED) {
            applicationService.handleStatementPassed(analysis.getTenantId(), analysis.getApplicationId(), actor);
        } else if (status == StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED) {
            applicationService.handleStatementManualReview(analysis.getTenantId(), analysis.getApplicationId(), actor, manualReviewReason);
        } else {
            applicationService.handleStatementFailed(analysis.getTenantId(), analysis.getApplicationId(), actor, failedReason);
        }
    }

    private String buildStoredAnalysisResponse(CladfyDtos.AnalysisResultsResponse results) {
        try {
            return objectMapper.writeValueAsString(new StoredAnalysisResponse(
                    results == null || results.document() == null ? null : results.document().last_analyzed_on(),
                    results == null || results.summary() == null ? null : results.summary().total_in(),
                    results == null || results.summary() == null ? null : results.summary().total_out(),
                    summarizeLoans(results == null ? null : results.loans())));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize filtered Cladfy analysis response", ex);
        }
    }

    private List<StoredLoanSummary> summarizeLoans(CladfyDtos.Loans loans) {
        if (loans == null || loans.summary() == null) {
            return List.of();
        }
        return loans.summary().stream()
                .map(loan -> new StoredLoanSummary(
                        loan.lender(),
                        loan.amount_borrowed(),
                        loan.amount_repaid(),
                        loan.times_taken(),
                        loan.times_repaid(),
                        loan.status(),
                        loan.last_activity_date()))
                .toList();
    }

    private record StoredAnalysisResponse(
            String last_analyzed_on,
            BigDecimal total_in,
            BigDecimal total_out,
            List<StoredLoanSummary> loans) {
    }

    private record StoredLoanSummary(
            String lender,
            BigDecimal amount_borrowed,
            BigDecimal amount_repaid,
            Integer times_taken,
            Integer times_repaid,
            String status,
            String last_activity_date) {
    }
}


