package com.credvenn.lm.kyc;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KycProcessingService {

    private static final Logger log = LoggerFactory.getLogger(KycProcessingService.class);

    private final KycCheckRepository kycCheckRepository;
    private final KycProviderRegistry kycProviderRegistry;
    private final ApplicationService applicationService;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;
    private final SubscriptionBillingService subscriptionBillingService;

    public KycProcessingService(
            KycCheckRepository kycCheckRepository,
            KycProviderRegistry kycProviderRegistry,
            ApplicationService applicationService,
            TenantService tenantService,
            FineractGateway fineractGateway,
            SubscriptionBillingService subscriptionBillingService) {
        this.kycCheckRepository = kycCheckRepository;
        this.kycProviderRegistry = kycProviderRegistry;
        this.applicationService = applicationService;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @Async
    @Transactional
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
            log.info("KYC provider completed with status={} providerReference={}", decision.status(), decision.providerReference());

            if (decision.status() == KycStatus.PASSED) {
                Tenant tenant = tenantService.getRequiredTenant(tenantId);
                String fineractClientId = fineractGateway.createClient(tenant, application);
                subscriptionBillingService.chargeKycSuccess(tenantId, check.getId(), actor);
                applicationService.handleKycPassed(tenantId, applicationId, actor, fineractClientId);
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
}
