package com.credvenn.lm.kyc;

import com.credvenn.lm.application.ApplicationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KycApprovalService {

    private final ApplicationService applicationService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public KycApprovalService(ApplicationService applicationService, ApplicationEventPublisher applicationEventPublisher) {
        this.applicationService = applicationService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public void approveAndRequestClientProvisioning(
            String tenantId,
            String applicationId,
            String actor,
            String kycCheckId,
            String reason) {
        applicationService.markKycPassed(tenantId, applicationId, actor, reason);
        requestClientProvisioning(tenantId, applicationId, actor, kycCheckId);
    }

    @Transactional
    public void requestClientProvisioning(String tenantId, String applicationId, String actor, String kycCheckId) {
        applicationEventPublisher.publishEvent(new ClientProvisioningRequestedEvent(tenantId, applicationId, actor, kycCheckId));
    }
}
