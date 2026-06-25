package com.credvenn.lm.kyc;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.logging.LoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class KycProcessingService {

    private static final Logger log = LoggerFactory.getLogger(KycProcessingService.class);

    private final KycCheckRepository kycCheckRepository;
    private final KycProviderRegistry kycProviderRegistry;
    private final ApplicationService applicationService;
    private final KycApprovalService kycApprovalService;

    public KycProcessingService(
            KycCheckRepository kycCheckRepository,
            KycProviderRegistry kycProviderRegistry,
            ApplicationService applicationService,
            KycApprovalService kycApprovalService) {
        this.kycCheckRepository = kycCheckRepository;
        this.kycProviderRegistry = kycProviderRegistry;
        this.applicationService = applicationService;
        this.kycApprovalService = kycApprovalService;
    }

    @Async
    public void process(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            log.info("Starting asynchronous KYC processing using provider={}", kycProviderRegistry.currentProvider().providerCode());
            applicationService.markKycInProgress(tenantId, applicationId, actor);
            LoanRequestApplication application = applicationService.getRequired(tenantId, applicationId);
            KycCheck check = kycCheckRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElseGet(KycCheck::new);
            check.setTenantId(tenantId);
            check.setApplicationId(applicationId);
            check.setProvider(kycProviderRegistry.currentProvider().providerCode());
            check.setStatus(KycStatus.IN_PROGRESS);
            kycCheckRepository.save(check);

            KycProvider.KycDecision decision = kycProviderRegistry.currentProvider().assess(application);
            check.setStatus(decision.status());
            check.setProviderReference(decision.providerReference());
            check.setSummary(decision.summary());
            applyActionDetails(check, decision.actionDetails());
            log.info("KYC provider completed with status={} providerReference={}", decision.status(), decision.providerReference());
            check = kycCheckRepository.save(check);

            if (decision.status() == KycStatus.PASSED) {
                kycApprovalService.approveAndRequestClientProvisioning(
                        tenantId,
                        applicationId,
                        actor,
                        check.getId(),
                        "KYC passed");
            } else if (decision.status() == KycStatus.MANUAL_REVIEW_REQUIRED) {
                applicationService.handleKycManualReview(tenantId, applicationId, actor, "KYC requires manual review");
            } else {
                applicationService.handleKycFailed(tenantId, applicationId, actor, "KYC failed and may be manually overridden");
            }
        } catch (RuntimeException ex) {
            log.error("Asynchronous KYC processing failed", ex);
            throw ex;
        }
    }

    private void applyActionDetails(KycCheck check, KycProvider.KycActionDetails details) {
        check.setActionNames(details == null ? null : details.names());
        check.setActionFirstName(details == null ? null : details.firstName());
        check.setActionLastName(details == null ? null : details.lastName());
        check.setActionOtherNames(details == null ? null : details.otherNames());
        check.setActionDob(details == null ? null : details.dob());
        check.setActionGender(details == null ? null : details.gender());
        check.setActionPhoneNumber(details == null ? null : details.phoneNumber());
        check.setActionVerifyIdNumber(details == null ? null : details.verifyIdNumber());
        check.setActionIdVerification(details == null ? null : details.idVerification());
    }
}
