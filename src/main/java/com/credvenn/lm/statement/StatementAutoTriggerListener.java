package com.credvenn.lm.statement;

import com.credvenn.lm.tenant.TenantService;
import com.credvenn.lm.tenant.TenantStatementAnalysisMode;
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
    private final TenantService tenantService;

    public StatementAutoTriggerListener(
            StatementProviderProperties properties,
            StatementAnalysisService statementAnalysisService,
            TenantService tenantService) {
        this.properties = properties;
        this.statementAnalysisService = statementAnalysisService;
        this.tenantService = tenantService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDocumentUploaded(StatementDocumentUploadedEvent event) {
        TenantStatementAnalysisMode mode = tenantService.getRequiredTenant(event.tenantId()).getStatementAnalysisMode();
        if (mode == TenantStatementAnalysisMode.DISABLED) {
            log.info(
                    "Skipping auto-trigger for applicationId={} documentId={} because tenant statementAnalysisMode=DISABLED",
                    event.applicationId(),
                    event.documentId());
            return;
        }
        if (mode == TenantStatementAnalysisMode.MANUAL) {
            log.info(
                    "Skipping auto-trigger for applicationId={} documentId={} because tenant statementAnalysisMode=MANUAL",
                    event.applicationId(),
                    event.documentId());
            return;
        }
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
