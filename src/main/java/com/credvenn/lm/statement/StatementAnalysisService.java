package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.document.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StatementAnalysisService.class);

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final StatementAnalysisProcessingService processingService;
    private final ApplicationService applicationService;
    private final DocumentService documentService;

    public StatementAnalysisService(
            StatementAnalysisRepository statementAnalysisRepository,
            StatementAnalysisProcessingService processingService,
            ApplicationService applicationService,
            DocumentService documentService) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.processingService = processingService;
        this.applicationService = applicationService;
        this.documentService = documentService;
    }

    @Transactional
    public StatementDtos.StatementAnalysisResponse run(
            String tenantId,
            String applicationId,
            String documentId,
            String actor,
            String simulateOutcome) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            applicationService.getRequired(tenantId, applicationId);
            var document = documentService.getRequired(tenantId, documentId);
            if (!document.getApplicationId().equals(applicationId)) {
                throw new NotFoundException("Document does not belong to the loan request application");
            }
            if (statementAnalysisRepository.existsByApplicationIdAndStatusIn(applicationId, java.util.Set.of(
                    StatementAnalysisStatus.PENDING,
                    StatementAnalysisStatus.IN_PROGRESS))) {
                log.info("Skipping statement analysis run because an analysis is already pending/in progress");
                return get(tenantId, applicationId);
            }
            StatementAnalysis analysis = statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElseGet(StatementAnalysis::new);
            analysis.setTenantId(tenantId);
            analysis.setApplicationId(applicationId);
            analysis.setSourceDocumentId(documentId);
            analysis.setProvider(normalizeSimulateOutcome(simulateOutcome) == null ? "QUEUED" : "SIMULATED");
            analysis.setStatus(StatementAnalysisStatus.PENDING);
            analysis = statementAnalysisRepository.save(analysis);
            log.info("Queued statement analysis for documentId={}", documentId);
            processingService.process(tenantId, applicationId, documentId, actor, simulateOutcome);
            return toResponse(analysis);
        }
    }

    @Transactional
    public StatementDtos.StatementAnalysisResponse manualPass(
            String tenantId,
            String applicationId,
            String actor,
            StatementDtos.ManualStatementPassRequest request) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            applicationService.getRequired(tenantId, applicationId);
            if (statementAnalysisRepository.existsByApplicationIdAndStatusIn(applicationId, java.util.Set.of(
                    StatementAnalysisStatus.PENDING,
                    StatementAnalysisStatus.IN_PROGRESS))) {
                throw new BadRequestException("A statement analysis is already pending or in progress");
            }
            StatementAnalysis latest = statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElse(null);
            if (latest != null && latest.getStatus() == StatementAnalysisStatus.PASSED) {
                log.info("Skipping manual statement pass because latest statement analysis is already passed");
                return toResponse(latest);
            }
            StatementAnalysis analysis = new StatementAnalysis();
            analysis.setTenantId(tenantId);
            analysis.setApplicationId(applicationId);
            analysis.setProvider("MANUAL_OVERRIDE");
            analysis.setProviderStatus("MANUALLY_PASSED");
            analysis.setStatus(StatementAnalysisStatus.PASSED);
            analysis.setSourceDocumentId(null);
            analysis.setRecommendation("MANUAL_APPROVE");
            analysis.setSummary(trimToDefault(request.summary(), "Manually passed without uploaded statement"));
            analysis = statementAnalysisRepository.save(analysis);
            log.info("Recorded manual statement pass without source document");
            applicationService.handleStatementPassed(tenantId, applicationId, actor);
            return toResponse(analysis);
        }
    }

    @Transactional(readOnly = true)
    public StatementDtos.StatementAnalysisResponse get(String tenantId, String applicationId) {
        applicationService.getRequired(tenantId, applicationId);
        StatementAnalysis analysis = statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new NotFoundException("Statement analysis not found"));
        return toResponse(analysis);
    }

    static StatementDtos.StatementAnalysisResponse toResponse(StatementAnalysis analysis) {
        return new StatementDtos.StatementAnalysisResponse(
                analysis.getId(),
                analysis.getApplicationId(),
                analysis.getProvider(),
                analysis.getStatus(),
                analysis.getSourceDocumentId(),
                analysis.getAverageMonthlyInflow(),
                analysis.getAverageMonthlyOutflow(),
                analysis.getAffordabilityScore(),
                analysis.getCreditScore(),
                analysis.getRiskTier(),
                analysis.getRecommendation(),
                analysis.getSummary(),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt());
    }

    private String normalizeSimulateOutcome(String simulateOutcome) {
        if (simulateOutcome == null) {
            return null;
        }
        String normalized = simulateOutcome.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String trimToDefault(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }
}
