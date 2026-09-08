package com.credvenn.lm.kyc;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class KycDecisionServiceTest {

    @Test
    void approvedDecisionIsSavedAndPublishedAsOneTransactionalOperation() {
        KycCheckRepository repository = mock(KycCheckRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        KycDecisionService service = new KycDecisionService(repository, publisher);
        KycCheck check = approvedCheck();
        when(repository.save(check)).thenReturn(check);

        KycCheck saved = service.recordApprovedDecision(check, "officer", "KYC passed");

        assertSame(check, saved);
        verify(repository).save(check);
        verify(publisher).publishEvent(new KycApprovedEvent(
                "tenant-1", "app-1", "officer", "kyc-1", "KYC passed"));
    }

    @Test
    void nonApprovedDecisionCannotRequestFinalization() {
        KycCheckRepository repository = mock(KycCheckRepository.class);
        KycDecisionService service = new KycDecisionService(repository, mock(ApplicationEventPublisher.class));
        KycCheck check = approvedCheck();
        check.setStatus(KycStatus.FAILED);

        assertThrows(IllegalArgumentException.class,
                () -> service.recordApprovedDecision(check, "officer", "failed"));
    }

    private static KycCheck approvedCheck() {
        KycCheck check = new KycCheck();
        setId(check, "kyc-1");
        check.setTenantId("tenant-1");
        check.setApplicationId("app-1");
        check.setProvider("SMILE_ID");
        check.setStatus(KycStatus.PASSED);
        return check;
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