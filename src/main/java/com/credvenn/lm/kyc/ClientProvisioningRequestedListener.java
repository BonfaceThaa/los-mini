package com.credvenn.lm.kyc;

import com.credvenn.lm.common.logging.LoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ClientProvisioningRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(ClientProvisioningRequestedListener.class);

    private final ClientProvisioningService clientProvisioningService;

    public ClientProvisioningRequestedListener(ClientProvisioningService clientProvisioningService) {
        this.clientProvisioningService = clientProvisioningService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClientProvisioningRequested(ClientProvisioningRequestedEvent event) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(event.tenantId(), event.applicationId())) {
            log.info("Received client provisioning request after KYC approval");
            clientProvisioningService.process(event.tenantId(), event.applicationId(), event.actor(), event.kycCheckId());
        }
    }
}
