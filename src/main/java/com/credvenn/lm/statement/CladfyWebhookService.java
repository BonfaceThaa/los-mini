package com.credvenn.lm.statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CladfyWebhookService {

    private static final Logger log = LoggerFactory.getLogger(CladfyWebhookService.class);

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final CladfyAnalysisCompletionService completionService;

    public CladfyWebhookService(
            StatementAnalysisRepository statementAnalysisRepository,
            CladfyAnalysisCompletionService completionService) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.completionService = completionService;
    }

    @Transactional
    public void handleWebhook(CladfyDtos.WebhookRequest request) {
        String clientId = String.valueOf(request.client_id());
        String documentId = String.valueOf(request.document_id());
        StatementAnalysis analysis = statementAnalysisRepository.findByExternalClientIdAndExternalDocumentId(clientId, documentId)
                .orElseThrow(() -> new com.credvenn.lm.common.exception.NotFoundException("Statement analysis not found for Cladfy webhook"));
        analysis.setExternalBusinessId(request.business_id() == null ? null : String.valueOf(request.business_id()));
        analysis.setProviderStatus("WEBHOOK_RECEIVED");
        boolean completed = completionService.completeAnalyzed(
                analysis,
                "cladfy-webhook",
                "WEBHOOK",
                request.business_id() == null ? null : String.valueOf(request.business_id()));
        if (!completed) {
            log.info(
                    "Ignoring duplicate or late Cladfy webhook applicationId={} clientId={} documentId={} currentStatus={}",
                    analysis.getApplicationId(),
                    clientId,
                    documentId,
                    analysis.getStatus());
        }
    }
}
