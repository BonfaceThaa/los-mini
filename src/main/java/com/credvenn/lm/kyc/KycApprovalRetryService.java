package com.credvenn.lm.kyc;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class KycApprovalRetryService {

    private final KycApprovalService approvalService;

    public KycApprovalRetryService(KycApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Retryable(
            includes = {
                    PessimisticLockingFailureException.class,
                    OptimisticLockingFailureException.class
            },
            maxRetries = 3,
            delay = 100,
            jitter = 100,
            multiplier = 2)
    public void finalizeApprovedKyc(KycApprovedEvent event) {
        approvalService.approveAndRequestClientProvisioning(
                event.tenantId(),
                event.applicationId(),
                event.actor(),
                event.kycCheckId(),
                event.reason());
    }
}
