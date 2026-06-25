package com.credvenn.lm.kyc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantKycMode;
import com.credvenn.lm.tenant.TenantService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KycManualReviewFlowTest {

    @Test
    void manualReviewAllowsApprovalWhenLatestCheckIsInProgress() {
        KycCheckRepository kycCheckRepository = mock(KycCheckRepository.class);
        KycProcessingService processingService = mock(KycProcessingService.class);
        ApplicationService applicationService = mock(ApplicationService.class);
        TenantService tenantService = mock(TenantService.class);
        KycApprovalService kycApprovalService = mock(KycApprovalService.class);
        KycService service = new KycService(
                kycCheckRepository,
                processingService,
                applicationService,
                tenantService,
                kycApprovalService);

        Tenant tenant = new Tenant();
        tenant.setKycMode(TenantKycMode.AUTO);
        when(tenantService.getRequiredTenant("tenant-1")).thenReturn(tenant);
        when(applicationService.getRequired("tenant-1", "app-1")).thenReturn(new LoanRequestApplication());

        KycCheck check = new KycCheck();
        setId(check, "kyc-1");
        check.setTenantId("tenant-1");
        check.setApplicationId("app-1");
        check.setProvider("SMILE_ID");
        check.setStatus(KycStatus.IN_PROGRESS);
        when(kycCheckRepository.findFirstByApplicationIdOrderByCreatedAtDesc("app-1")).thenReturn(Optional.of(check));
        when(kycCheckRepository.save(check)).thenReturn(check);
        doNothing().when(kycApprovalService).approveAndRequestClientProvisioning(
                "tenant-1",
                "app-1",
                "officer",
                "kyc-1",
                "KYC manually approved");

        service.manualReview(
                "tenant-1",
                "app-1",
                "officer",
                new KycDtos.ManualKycReviewRequest(true, "approved manually"));

        verify(kycCheckRepository).save(check);
        verify(kycApprovalService).approveAndRequestClientProvisioning(
                "tenant-1",
                "app-1",
                "officer",
                "kyc-1",
                "KYC manually approved");
    }

    private static void setId(KycCheck check, String id) {
        try {
            var field = KycCheck.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(check, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
