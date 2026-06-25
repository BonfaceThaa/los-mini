package com.credvenn.lm.statementinbox;

import com.credvenn.lm.application.ApplicationCreatedEvent;
import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.common.logging.LoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ApplicationCreatedInboundStatementRetryListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationCreatedInboundStatementRetryListener.class);

    private final InboundStatementProcessor inboundStatementProcessor;
    private final ApplicationService applicationService;

    public ApplicationCreatedInboundStatementRetryListener(
            InboundStatementProcessor inboundStatementProcessor,
            ApplicationService applicationService) {
        this.inboundStatementProcessor = inboundStatementProcessor;
        this.applicationService = applicationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationCreated(ApplicationCreatedEvent event) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(event.tenantId(), event.applicationId())) {
            log.info("Retrying waiting inbound statements after application creation");
            inboundStatementProcessor.retryWaitingReceiptsForApplication(
                    event.tenantId(),
                    applicationService.getRequired(event.tenantId(), event.applicationId()),
                    event.actor());
        }
    }
}
