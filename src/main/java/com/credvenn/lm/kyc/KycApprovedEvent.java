package com.credvenn.lm.kyc;

public record KycApprovedEvent(
        String tenantId,
        String applicationId,
        String actor,
        String kycCheckId,
        String reason) {
}
