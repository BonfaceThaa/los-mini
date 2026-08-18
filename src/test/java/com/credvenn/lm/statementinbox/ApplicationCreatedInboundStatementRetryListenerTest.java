package com.credvenn.lm.statementinbox;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationCreatedEvent;
import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.LoanRequestApplication;
import org.junit.jupiter.api.Test;

class ApplicationCreatedInboundStatementRetryListenerTest {

    @Test
    void onApplicationCreatedRetriesWaitingInboundStatementsForApplication() {
        InboundStatementProcessor inboundStatementProcessor = mock(InboundStatementProcessor.class);
        ApplicationService applicationService = mock(ApplicationService.class);
        ApplicationCreatedInboundStatementRetryListener listener =
                new ApplicationCreatedInboundStatementRetryListener(inboundStatementProcessor, applicationService);
        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, "app-1");
        application.setTenantId("tenant-1");
        when(applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);

        listener.onApplicationCreated(new ApplicationCreatedEvent("tenant-1", "app-1", "tester"));

        verify(applicationService).getRequired("tenant-1", "app-1");
        verify(inboundStatementProcessor).retryWaitingReceiptsForApplication("tenant-1", application, "tester");
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
