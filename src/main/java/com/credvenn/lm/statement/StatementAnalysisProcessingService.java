package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.lm.document.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementAnalysisProcessingService {

    private static final Logger log = LoggerFactory.getLogger(StatementAnalysisProcessingService.class);

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final StatementProviderRegistry statementProviderRegistry;
    private final ApplicationService applicationService;
    private final DocumentService documentService;

    public StatementAnalysisProcessingService(
            StatementAnalysisRepository statementAnalysisRepository,
            StatementProviderRegistry statementProviderRegistry,
            ApplicationService applicationService,
            DocumentService documentService) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.statementProviderRegistry = statementProviderRegistry;
        this.applicationService = applicationService;
        this.documentService = documentService;
    }

    @Async
    @Transactional
    public void process(String tenantId, String applicationId, String documentId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            log.info(
                    "Starting asynchronous statement analysis using provider={} documentId={}",
                    statementProviderRegistry.currentProvider().providerCode(),
                    documentId);
            applicationService.handleStatementInProgress(tenantId, applicationId, actor);
            ApplicationDocument document = documentService.getRequired(tenantId, documentId);
            StatementAnalysis analysis = statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElseGet(StatementAnalysis::new);
            analysis.setTenantId(tenantId);
            analysis.setApplicationId(applicationId);
            analysis.setSourceDocumentId(documentId);
            analysis.setProvider(statementProviderRegistry.currentProvider().providerCode());
            analysis.setStatus(StatementAnalysisStatus.IN_PROGRESS);
            statementAnalysisRepository.save(analysis);

            var application = applicationService.getRequired(tenantId, applicationId);
            StatementAnalysisProvider.StatementDecision decision = statementProviderRegistry.currentProvider().analyze(application, document);
            analysis.setStatus(decision.status());
            analysis.setAverageMonthlyInflow(decision.averageMonthlyInflow());
            analysis.setAverageMonthlyOutflow(decision.averageMonthlyOutflow());
            analysis.setAffordabilityScore(decision.affordabilityScore());
            analysis.setRecommendation(decision.recommendation());
            analysis.setSummary(decision.summary());
            log.info(
                    "Statement analysis completed with status={} affordabilityScore={} recommendation={}",
                    decision.status(),
                    decision.affordabilityScore(),
                    decision.recommendation());
            if (decision.status() == StatementAnalysisStatus.PASSED) {
                applicationService.handleStatementPassed(tenantId, applicationId, actor);
            } else if (decision.status() == StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED) {
                applicationService.handleStatementManualReview(tenantId, applicationId, actor, "Statement analysis requires manual review");
            } else {
                applicationService.handleStatementFailed(tenantId, applicationId, actor, "Statement analysis failed");
            }
        } catch (RuntimeException ex) {
            log.error("Asynchronous statement analysis failed", ex);
            throw ex;
        }
    }
}
