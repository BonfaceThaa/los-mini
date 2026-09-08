package com.credvenn.lm.kyc;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KycDecisionService {

    private final KycCheckRepository kycCheckRepository;
    private final ApplicationEventPublisher eventPublisher;

    public KycDecisionService(
            KycCheckRepository kycCheckRepository,
            ApplicationEventPublisher eventPublisher) {
        this.kycCheckRepository = kycCheckRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public KycCheck recordApprovedDecision(KycCheck check, String actor, String reason) {
        if (check.getStatus() != KycStatus.PASSED && check.getStatus() != KycStatus.MANUALLY_APPROVED) {
            throw new IllegalArgumentException("Only an approved KYC decision can request application finalization");
        }
        KycCheck saved = kycCheckRepository.save(check);
        eventPublisher.publishEvent(new KycApprovedEvent(
                saved.getTenantId(),
                saved.getApplicationId(),
                actor,
                saved.getId(),
                reason));
        return saved;
    }
}
