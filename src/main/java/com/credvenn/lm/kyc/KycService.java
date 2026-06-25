package com.credvenn.lm.kyc;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.ApplicationDtos;
import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantKycMode;
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
    private final KycApprovalService kycApprovalService;

    public KycService(
            KycCheckRepository kycCheckRepository,
            KycProcessingService processingService,
            ApplicationService applicationService,
            TenantService tenantService,
            KycApprovalService kycApprovalService) {
        this.kycCheckRepository = kycCheckRepository;
        this.processingService = processingService;
        this.applicationService = applicationService;
        this.tenantService = tenantService;
        this.kycApprovalService = kycApprovalService;
    }

    @Transactional
    public KycDtos.KycCheckResponse run(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            Tenant tenant = tenantService.getRequiredTenant(tenantId);
            if (tenant.getKycMode() == TenantKycMode.DISABLED) {
                throw new BadRequestException("KYC is disabled for this tenant");
            }
            applicationService.getRequired(tenantId, applicationId);
            KycCheck check = kycCheckRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElseGet(KycCheck::new);
            check.setTenantId(tenantId);
            check.setApplicationId(applicationId);
            check.setProvider("QUEUED");
            check.setStatus(KycStatus.PENDING);
            check.setProviderReference(null);
            check.setSummary(null);
            clearActionDetails(check);
            check.setReviewedBy(null);
            check.setReviewReason(null);
            check.setReviewedAt(null);
            check = kycCheckRepository.save(check);
            log.info("Queued KYC processing for application");
            processingService.process(tenantId, applicationId, actor);
            return toResponse(check);
        }
    }

    @Transactional
    public void handleApplicationCreated(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            Tenant tenant = tenantService.getRequiredTenant(tenantId);
            if (tenant.getKycMode() == TenantKycMode.AUTO) {
                log.info("Tenant KYC mode is AUTO; starting configured KYC provider flow");
                run(tenantId, applicationId, actor);
                return;
            }
            if (tenant.getKycMode() == TenantKycMode.MANUAL) {
                log.info("Tenant KYC mode is MANUAL; application will remain pending tenant KYC action");
                return;
            }
            log.info("Tenant KYC mode is DISABLED; bypassing provider KYC and continuing application flow");
            bypassKyc(tenantId, applicationId, actor, "Tenant KYC mode disabled");
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
            Tenant tenant = tenantService.getRequiredTenant(tenantId);
            var application = applicationService.getRequired(tenantId, applicationId);
            var existingCheck = kycCheckRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
            boolean directManualAllowed = existingCheck.isEmpty() && tenant.getKycMode() == TenantKycMode.MANUAL;
            KycCheck check = existingCheck.orElseGet(KycCheck::new);
            if (directManualAllowed) {
                check.setTenantId(tenantId);
                check.setApplicationId(applicationId);
                check.setProvider("MANUAL");
                check.setStatus(KycStatus.MANUAL_REVIEW_REQUIRED);
                check.setProviderReference(null);
                check.setSummary("Tenant manual KYC review initiated without provider run");
                clearActionDetails(check);
            }
            if (!directManualAllowed && !isManualReviewAllowed(check.getStatus())) {
                throw new BadRequestException("Manual review is only allowed for pending, in-progress, failed, or review-required KYC");
            }
            check.setReviewedBy(actor);
            check.setReviewReason(request.reason().trim());
            check.setReviewedAt(Instant.now());
            log.info("Recording manual KYC review approved={} reason={}", request.approved(), request.reason().trim());
            if (request.approved()) {
                check.setStatus(KycStatus.MANUALLY_APPROVED);
                check = kycCheckRepository.save(check);
                kycApprovalService.approveAndRequestClientProvisioning(
                        tenantId,
                        applicationId,
                        actor,
                        check.getId(),
                        "KYC manually approved");
            } else {
                check.setStatus(KycStatus.MANUALLY_REJECTED);
                applicationService.handleKycFailed(tenantId, applicationId, actor, request.reason().trim());
                check = kycCheckRepository.save(check);
            }
            return toResponse(check);
        }
    }

    @Transactional
    public void bypassKyc(String tenantId, String applicationId, String actor, String reason) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            var application = applicationService.getRequired(tenantId, applicationId);
            KycCheck check = kycCheckRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElseGet(KycCheck::new);
            check.setTenantId(tenantId);
            check.setApplicationId(applicationId);
            check.setProvider("DISABLED");
            check.setStatus(KycStatus.MANUALLY_APPROVED);
            check.setProviderReference(null);
            check.setSummary(reason);
            clearActionDetails(check);
            check.setReviewedBy(actor);
            check.setReviewReason(reason);
            check.setReviewedAt(Instant.now());
            check = kycCheckRepository.save(check);
            kycApprovalService.approveAndRequestClientProvisioning(
                    tenantId,
                    applicationId,
                    actor,
                    check.getId(),
                    reason);
        }
    }

    @Transactional
    public KycDtos.KycCheckResponse retry(String tenantId, String applicationId, String actor) {
        return run(tenantId, applicationId, actor);
    }

    @Transactional
    public ApplicationDtos.LoanRequestApplicationResponse retryClientCreation(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            var application = applicationService.getRequired(tenantId, applicationId);
            if (application.getFineractClientId() != null && !application.getFineractClientId().isBlank()) {
                return applicationService.get(tenantId, applicationId);
            }
            if (application.getStatus() != ApplicationStatus.KYC_PASSED
                    && application.getStatus() != ApplicationStatus.CLIENT_CREATION_FAILED) {
                throw new BadRequestException("Client creation retry is only allowed after KYC pass or client creation failure");
            }
            KycCheck check = kycCheckRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                    .orElseThrow(() -> new NotFoundException("KYC check not found"));
            applicationService.markClientCreationInProgress(tenantId, applicationId, actor);
            kycApprovalService.requestClientProvisioning(tenantId, applicationId, actor, check.getId());
            return applicationService.get(tenantId, applicationId);
        }
    }

    static KycDtos.KycCheckResponse toResponse(KycCheck check) {
        return new KycDtos.KycCheckResponse(
                check.getId(),
                check.getApplicationId(),
                check.getProvider(),
                check.getStatus(),
                check.getProviderReference(),
                check.getSummary(),
                toActionResponse(check),
                check.getReviewedBy(),
                check.getReviewReason(),
                check.getReviewedAt(),
                check.getCreatedAt(),
                check.getUpdatedAt());
    }

    private static KycDtos.KycActionResponse toActionResponse(KycCheck check) {
        if (check.getActionNames() == null
                && check.getActionFirstName() == null
                && check.getActionLastName() == null
                && check.getActionOtherNames() == null
                && check.getActionDob() == null
                && check.getActionGender() == null
                && check.getActionPhoneNumber() == null
                && check.getActionVerifyIdNumber() == null
                && check.getActionIdVerification() == null) {
            return null;
        }
        return new KycDtos.KycActionResponse(
                check.getActionNames(),
                check.getActionFirstName(),
                check.getActionLastName(),
                check.getActionOtherNames(),
                check.getActionDob(),
                check.getActionGender(),
                check.getActionPhoneNumber(),
                check.getActionVerifyIdNumber(),
                check.getActionIdVerification());
    }

    private static void clearActionDetails(KycCheck check) {
        check.setActionNames(null);
        check.setActionFirstName(null);
        check.setActionLastName(null);
        check.setActionOtherNames(null);
        check.setActionDob(null);
        check.setActionGender(null);
        check.setActionPhoneNumber(null);
        check.setActionVerifyIdNumber(null);
        check.setActionIdVerification(null);
    }

    private boolean isManualReviewAllowed(KycStatus status) {
        return status == KycStatus.PENDING
                || status == KycStatus.IN_PROGRESS
                || status == KycStatus.FAILED
                || status == KycStatus.MANUAL_REVIEW_REQUIRED;
    }
}
