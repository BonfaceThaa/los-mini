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

@Service
public class ClientProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ClientProvisioningService.class);

    private final ApplicationService applicationService;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;
    private final SubscriptionBillingService subscriptionBillingService;

    public ClientProvisioningService(
            ApplicationService applicationService,
            TenantService tenantService,
            FineractGateway fineractGateway,
            SubscriptionBillingService subscriptionBillingService) {
        this.applicationService = applicationService;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @Async
    public void process(String tenantId, String applicationId, String actor, String kycCheckId) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = applicationService.getRequired(tenantId, applicationId);
            if (application.getFineractClientId() != null && !application.getFineractClientId().isBlank()) {
                log.info("Skipping Fineract client provisioning because fineractClientId already exists");
                subscriptionBillingService.chargeKycSuccess(tenantId, kycCheckId, actor);
                applicationService.handleClientCreated(tenantId, applicationId, actor, application.getFineractClientId());
                return;
            }
            applicationService.markClientCreationInProgress(tenantId, applicationId, actor);
            Tenant tenant = tenantService.getRequiredTenant(tenantId);
            String fineractClientId = fineractGateway.createClient(tenant, application);
            subscriptionBillingService.chargeKycSuccess(tenantId, kycCheckId, actor);
            applicationService.handleClientCreated(tenantId, applicationId, actor, fineractClientId);
        } catch (RuntimeException ex) {
            log.error("Asynchronous Fineract client provisioning failed", ex);
            applicationService.handleClientCreationFailed(tenantId, applicationId, actor, summarize(ex));
        }
    }

    private String summarize(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Fineract client creation failed";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
