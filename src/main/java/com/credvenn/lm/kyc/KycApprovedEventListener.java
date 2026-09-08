package com.credvenn.lm.kyc;

import com.credvenn.lm.common.logging.LoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KycApprovedEventListener {

    private static final Logger log = LoggerFactory.getLogger(KycApprovedEventListener.class);

    private final KycApprovalRetryService retryService;

    public KycApprovedEventListener(KycApprovalRetryService retryService) {
        this.retryService = retryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onKycApproved(KycApprovedEvent event) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(
                event.tenantId(), event.applicationId())) {
            try {
                retryService.finalizeApprovedKyc(event);
            } catch (RuntimeException ex) {
                log.error(
                        "KYC application finalization failed after retries kycCheckId={}",
                        event.kycCheckId(),
                        ex);
            }
        }
    }
}
