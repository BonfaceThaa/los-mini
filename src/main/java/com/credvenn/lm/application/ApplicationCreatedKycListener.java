package com.credvenn.lm.application;

import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.kyc.KycService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ApplicationCreatedKycListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationCreatedKycListener.class);

    private final KycService kycService;

    public ApplicationCreatedKycListener(KycService kycService) {
        this.kycService = kycService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationCreated(ApplicationCreatedEvent event) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(event.tenantId(), event.applicationId())) {
            log.info("Received application-created event and evaluating tenant KYC mode");
            kycService.handleApplicationCreated(event.tenantId(), event.applicationId(), event.actor());
        }
    }
}
