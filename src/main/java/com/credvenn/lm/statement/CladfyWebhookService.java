package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CladfyWebhookService {

    private static final Logger log = LoggerFactory.getLogger(CladfyWebhookService.class);

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final CladfyStatementTransactionRepository transactionRepository;
    private final CladfyGateway cladfyGateway;
    private final CladfyStatementAnalysisProvider provider;
    private final ApplicationService applicationService;
    private final SubscriptionBillingService subscriptionBillingService;

    public CladfyWebhookService(
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
    public void handleWebhook(CladfyDtos.WebhookRequest request) {
        String clientId = String.valueOf(request.client_id());
        String documentId = String.valueOf(request.document_id());
        StatementAnalysis analysis = statementAnalysisRepository.findByExternalClientIdAndExternalDocumentId(clientId, documentId)
                .orElseThrow(() -> new com.credvenn.lm.common.exception.NotFoundException("Statement analysis not found for Cladfy webhook"));
        analysis.setExternalBusinessId(request.business_id() == null ? null : String.valueOf(request.business_id()));
        analysis.setProviderStatus("WEBHOOK_RECEIVED");

        CladfyDtos.AnalysisResultsResponse results = cladfyGateway.fetchAnalysisResults(clientId);
        CladfyDtos.CreditScoreResponse score = cladfyGateway.fetchCreditScore(clientId);
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

        String actor = "cladfy-webhook";
        if (decision.status() == StatementAnalysisStatus.PASSED) {
            subscriptionBillingService.chargeStatementSuccess(analysis.getTenantId(), analysis.getId(), actor);
            applicationService.handleStatementPassed(analysis.getTenantId(), analysis.getApplicationId(), actor);
        } else if (decision.status() == StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED) {
            applicationService.handleStatementManualReview(analysis.getTenantId(), analysis.getApplicationId(), actor, "Cladfy score requires manual review");
        } else {
            applicationService.handleStatementFailed(analysis.getTenantId(), analysis.getApplicationId(), actor, "Cladfy score failed statement analysis");
        }
        log.info(
                "Completed Cladfy statement analysis applicationId={} clientId={} documentId={} score={} riskTier={} status={}",
                analysis.getApplicationId(),
                clientId,
                documentId,
                analysis.getCreditScore(),
                analysis.getRiskTier(),
                analysis.getStatus());
    }
}
