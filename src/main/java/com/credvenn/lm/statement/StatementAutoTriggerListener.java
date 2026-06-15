package com.credvenn.lm.statement;

import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class StatementAutoTriggerListener {

    private static final Logger log = LoggerFactory.getLogger(StatementAutoTriggerListener.class);

    private final StatementProviderProperties properties;
    private final StatementAnalysisService statementAnalysisService;

    public StatementAutoTriggerListener(StatementProviderProperties properties, StatementAnalysisService statementAnalysisService) {
        this.properties = properties;
        this.statementAnalysisService = statementAnalysisService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDocumentUploaded(StatementDocumentUploadedEvent event) {
        Set<String> autoTypes = properties.autoTriggerDocumentTypes().stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        String normalizedDocumentType = event.documentType() == null ? "" : event.documentType().toUpperCase(Locale.ROOT);
        if (!autoTypes.contains(normalizedDocumentType)) {
            log.info(
                    "Skipping auto-trigger for applicationId={} documentId={} documentType={} because it is not in autoTriggerDocumentTypes={}",
                    event.applicationId(),
                    event.documentId(),
                    event.documentType(),
                    autoTypes);
            return;
        }
        log.info(
                "Auto-triggering statement analysis for applicationId={} documentId={} documentType={}",
                event.applicationId(),
                event.documentId(),
                event.documentType());
        statementAnalysisService.run(event.tenantId(), event.applicationId(), event.documentId(), event.actor(), null);
    }
}
