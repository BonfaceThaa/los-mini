package com.credvenn.lm.kyc;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import com.credvenn.lm.tenant.TenantService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final KycCheckRepository kycCheckRepository;
    private final KycProcessingService processingService;
    private final ApplicationService applicationService;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;
    private final SubscriptionBillingService subscriptionBillingService;

    public KycService(
            KycCheckRepository kycCheckRepository,
            KycProcessingService processingService,
            ApplicationService applicationService,
            TenantService tenantService,
            FineractGateway fineractGateway,
            SubscriptionBillingService subscriptionBillingService) {
        this.kycCheckRepository = kycCheckRepository;
        this.processingService = processingService;
        this.applicationService = applicationService;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @Transactional
    public KycDtos.KycCheckResponse run(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            applicationService.getRequired(tenantId, applicationId);
            KycCheck check = kycCheckRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElseGet(KycCheck::new);
            check.setTenantId(tenantId);
            check.setApplicationId(applicationId);
            check.setProvider("QUEUED");
            check.setStatus(KycStatus.PENDING);
            check.setReviewedBy(null);
            check.setReviewReason(null);
            check.setReviewedAt(null);
            check = kycCheckRepository.save(check);
            log.info("Queued KYC processing for application");
            processingService.process(tenantId, applicationId, actor);
            return toResponse(check);
        }
    }

    @Transactional(readOnly = true)
    public KycDtos.KycCheckResponse get(String tenantId, String applicationId) {
        applicationService.getRequired(tenantId, applicationId);
        KycCheck check = kycCheckRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new NotFoundException("KYC check not found"));
        return toResponse(check);
    }

    @Transactional
    public KycDtos.KycCheckResponse manualReview(
            String tenantId,
            String applicationId,
            String actor,
            KycDtos.ManualKycReviewRequest request) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            var application = applicationService.getRequired(tenantId, applicationId);
            KycCheck check = kycCheckRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                    .orElseThrow(() -> new NotFoundException("KYC check not found"));
            if (check.getStatus() != KycStatus.FAILED && check.getStatus() != KycStatus.MANUAL_REVIEW_REQUIRED) {
                throw new BadRequestException("Manual review is only allowed after failed or review-required KYC");
            }
            check.setReviewedBy(actor);
            check.setReviewReason(request.reason().trim());
            check.setReviewedAt(Instant.now());
            log.info("Recording manual KYC review approved={} reason={}", request.approved(), request.reason().trim());
            if (request.approved()) {
                check.setStatus(KycStatus.MANUALLY_APPROVED);
                String fineractClientId = application.getFineractClientId();
                if (fineractClientId == null || fineractClientId.isBlank()) {
                    fineractClientId = fineractGateway.createClient(tenantService.getRequiredTenant(tenantId), application);
                }
                subscriptionBillingService.chargeKycSuccess(tenantId, check.getId(), actor);
                applicationService.handleKycPassed(tenantId, applicationId, actor, fineractClientId);
            } else {
                check.setStatus(KycStatus.MANUALLY_REJECTED);
                applicationService.handleKycFailed(tenantId, applicationId, actor, request.reason().trim());
            }
            return toResponse(check);
        }
    }

    @Transactional
    public KycDtos.KycCheckResponse retry(String tenantId, String applicationId, String actor) {
        return run(tenantId, applicationId, actor);
    }

    static KycDtos.KycCheckResponse toResponse(KycCheck check) {
        return new KycDtos.KycCheckResponse(
                check.getId(),
                check.getApplicationId(),
                check.getProvider(),
                check.getStatus(),
                check.getProviderReference(),
                check.getSummary(),
                check.getReviewedBy(),
                check.getReviewReason(),
                check.getReviewedAt(),
                check.getCreatedAt(),
                check.getUpdatedAt());
    }
}
