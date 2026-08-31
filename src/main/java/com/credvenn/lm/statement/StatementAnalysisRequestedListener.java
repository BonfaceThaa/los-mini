package com.credvenn.lm.statement;

import com.credvenn.lm.common.logging.LoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class StatementAnalysisRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(StatementAnalysisRequestedListener.class);

    private final StatementAnalysisProcessingService processingService;

    public StatementAnalysisRequestedListener(StatementAnalysisProcessingService processingService) {
        this.processingService = processingService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onStatementAnalysisRequested(StatementAnalysisRequestedEvent event) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(event.tenantId(), event.applicationId())) {
            log.info("Starting queued statement analysis after commit analysisId={} documentId={}", event.analysisId(), event.documentId());
            processingService.process(event.tenantId(), event.analysisId(), event.actor(), event.simulateOutcome());
        }
    }
}