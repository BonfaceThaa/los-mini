package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationService;
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
    public StatementDtos.StatementAnalysisResponse run(String tenantId, String applicationId, String documentId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            applicationService.getRequired(tenantId, applicationId);
            var document = documentService.getRequired(tenantId, documentId);
            if (!document.getApplicationId().equals(applicationId)) {
                throw new NotFoundException("Document does not belong to the loan request application");
            }
            StatementAnalysis analysis = statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElseGet(StatementAnalysis::new);
            analysis.setTenantId(tenantId);
            analysis.setApplicationId(applicationId);
            analysis.setSourceDocumentId(documentId);
            analysis.setProvider("QUEUED");
            analysis.setStatus(StatementAnalysisStatus.PENDING);
            analysis = statementAnalysisRepository.save(analysis);
            log.info("Queued statement analysis for documentId={}", documentId);
            processingService.process(tenantId, applicationId, documentId, actor);
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
                analysis.getRecommendation(),
                analysis.getSummary(),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt());
    }
}
