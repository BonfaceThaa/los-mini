package com.credvenn.lm.kyc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import org.junit.jupiter.api.Test;

class ClientProvisioningServiceTest {

    @Test
    void processAdvancesWorkflowWithoutCreatingClientWhenApplicationAlreadyHasFineractClientId() {
        ApplicationService applicationService = mock(ApplicationService.class);
        TenantService tenantService = mock(TenantService.class);
        FineractGateway fineractGateway = mock(FineractGateway.class);
        SubscriptionBillingService subscriptionBillingService = mock(SubscriptionBillingService.class);
        ClientProvisioningService service = new ClientProvisioningService(
                applicationService,
                tenantService,
                fineractGateway,
                subscriptionBillingService);

        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, "app-1");
        application.setTenantId("tenant-1");
        application.setStatus(ApplicationStatus.KYC_PASSED);
        application.setFineractClientId("fineract-client-1");
        when(applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        doNothing().when(subscriptionBillingService).chargeKycSuccess("tenant-1", "kyc-1", "system");
        doNothing().when(applicationService).handleClientCreated("tenant-1", "app-1", "system", "fineract-client-1");

        service.process("tenant-1", "app-1", "system", "kyc-1");

        verify(subscriptionBillingService).chargeKycSuccess("tenant-1", "kyc-1", "system");
        verify(applicationService).handleClientCreated("tenant-1", "app-1", "system", "fineract-client-1");
        verify(fineractGateway, never()).createClient(org.mockito.ArgumentMatchers.any(Tenant.class), org.mockito.ArgumentMatchers.any(LoanRequestApplication.class));
        verify(applicationService, never()).markClientCreationInProgress("tenant-1", "app-1", "system");
        verify(tenantService, never()).getRequiredTenant("tenant-1");
    }

    private static void setId(LoanRequestApplication application, String id) {
        try {
            var field = LoanRequestApplication.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(application, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
