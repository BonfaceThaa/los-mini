package com.credvenn.lm.kyc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.LoanRequestApplication;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KycProcessingServiceTest {

    @Test
    void smileIdAssessmentRunsOnceAndSuccessfulDecisionUsesAfterCommitFlow() {
        KycCheckRepository repository = mock(KycCheckRepository.class);
        KycProviderRegistry registry = mock(KycProviderRegistry.class);
        ApplicationService applicationService = mock(ApplicationService.class);
        KycDecisionService decisionService = mock(KycDecisionService.class);
        KycProvider provider = mock(KycProvider.class);
        LoanRequestApplication application = new LoanRequestApplication();
        KycCheck check = new KycCheck();
        setId(check, "kyc-1");
        check.setTenantId("tenant-1");
        check.setApplicationId("app-1");
        KycProvider.KycDecision decision = new KycProvider.KycDecision(
                KycStatus.PASSED, "smile-1", "Exact match", null);

        when(registry.currentProvider()).thenReturn(provider);
        when(provider.providerCode()).thenReturn("SMILE_ID");
        when(applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        when(repository.findFirstByApplicationIdOrderByCreatedAtDesc("app-1")).thenReturn(Optional.of(check));
        when(repository.save(check)).thenReturn(check);
        when(provider.assess(application)).thenReturn(decision);
        when(decisionService.recordApprovedDecision(check, "officer", "KYC passed")).thenReturn(check);
        KycProcessingService service = new KycProcessingService(
                repository, registry, applicationService, decisionService);

        service.process("tenant-1", "app-1", "officer");

        verify(provider, times(1)).assess(application);
        verify(decisionService).recordApprovedDecision(check, "officer", "KYC passed");
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