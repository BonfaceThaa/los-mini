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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

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
        verify(fineractGateway, never()).createClient(ArgumentMatchers.any(Tenant.class), ArgumentMatchers.any(LoanRequestApplication.class));
        verify(applicationService, never()).markClientCreationInProgress("tenant-1", "app-1", "system");
        verify(tenantService, never()).getRequiredTenant("tenant-1");
    }

    @Test
    void processReusesExistingFineractClientWhenExternalIdAlreadyExistsInFineract() {
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
        when(applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        Tenant tenant = new Tenant();
        tenant.setId("tenant-1");
        when(tenantService.getRequiredTenant("tenant-1")).thenReturn(tenant);
        when(fineractGateway.fetchClients(tenant)).thenReturn(List.of(new FineractGateway.FineractClient(
                "fineract-client-1",
                "account-1",
                "app-1",
                "Active",
                true,
                "Jane",
                null,
                "Doe",
                "Jane Doe",
                "+254700000001",
                "HQ")));

        service.process("tenant-1", "app-1", "system", "kyc-1");

        verify(subscriptionBillingService).chargeKycSuccess("tenant-1", "kyc-1", "system");
        verify(applicationService).handleClientCreated("tenant-1", "app-1", "system", "fineract-client-1");
        verify(fineractGateway, never()).createClient(ArgumentMatchers.any(Tenant.class), ArgumentMatchers.any(LoanRequestApplication.class));
        verify(applicationService, never()).markClientCreationInProgress("tenant-1", "app-1", "system");
    }

    @Test
    void processRecoversFromDuplicateExternalIdByReusingExistingFineractClient() {
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
        when(applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        Tenant tenant = new Tenant();
        tenant.setId("tenant-1");
        when(tenantService.getRequiredTenant("tenant-1")).thenReturn(tenant);
        when(fineractGateway.fetchClients(tenant))
                .thenReturn(List.of())
                .thenReturn(List.of(new FineractGateway.FineractClient(
                        "fineract-client-1",
                        "account-1",
                        "app-1",
                        "Active",
                        true,
                        "Jane",
                        null,
                        "Doe",
                        "Jane Doe",
                        "+254700000001",
                        "HQ")));
        when(fineractGateway.createClient(tenant, application))
                .thenThrow(new RuntimeException("error.msg.client.duplicate.externalId"));

        service.process("tenant-1", "app-1", "system", "kyc-1");

        verify(applicationService).markClientCreationInProgress("tenant-1", "app-1", "system");
        verify(subscriptionBillingService).chargeKycSuccess("tenant-1", "kyc-1", "system");
        verify(applicationService).handleClientCreated("tenant-1", "app-1", "system", "fineract-client-1");
        verify(applicationService, never()).handleClientCreationFailed(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
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
