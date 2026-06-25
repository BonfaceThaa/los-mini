package com.credvenn.lm.kyc;

public record ClientProvisioningRequestedEvent(
        String tenantId,
        String applicationId,
        String actor,
        String kycCheckId) {
}
