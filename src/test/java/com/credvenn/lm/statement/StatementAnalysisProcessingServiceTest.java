package com.credvenn.lm.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.lm.document.DocumentService;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StatementAnalysisProcessingServiceTest {

    @Test
    void processCompletesImmediatelyWhenUploadScoringIsFresh() {
        TestContext context = new TestContext();
        ApplicationDocument document = document("doc-1");
        LoanRequestApplication application = application("tenant-1", "app-1");
        StatementAnalysis analysis = new StatementAnalysis();

        when(context.statementProviderRegistry.currentProvider()).thenReturn(context.provider);
        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc("app-1"))
                .thenReturn(Optional.empty());
        when(context.statementAnalysisRepository.save(any(StatementAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        when(context.cladfyGateway.submit(application, document)).thenReturn(new StatementAnalysisSubmission(
                "CLADFY",
                "1",
                "55981",
                "57435",
                null,
                "Statement submitted to Cladfy for analysis",
                "{...}",
                743,
                "Good",
                Instant.now().minusSeconds(60)));
        doNothing().when(context.applicationService).handleStatementPassed("tenant-1", "app-1", "system");

        context.service.process("tenant-1", "app-1", "doc-1", "system", null);

        verify(context.cladfyStatusPollingService, never()).scheduleInitialStatusCheck(any());
        verify(context.applicationService).handleStatementPassed("tenant-1", "app-1", "system");
        verify(context.statementAnalysisRepository).save(any(StatementAnalysis.class));
    }

    @Test
    void processSchedulesPollingWhenUploadScoringIsStale() {
        TestContext context = new TestContext();
        ApplicationDocument document = document("doc-1");
        LoanRequestApplication application = application("tenant-1", "app-1");

        when(context.statementProviderRegistry.currentProvider()).thenReturn(context.provider);
        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc("app-1"))
                .thenReturn(Optional.empty());
        when(context.statementAnalysisRepository.save(any(StatementAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        when(context.cladfyGateway.submit(application, document)).thenReturn(new StatementAnalysisSubmission(
                "CLADFY",
                "1",
                "55981",
                "57435",
                null,
                "Statement submitted to Cladfy for analysis",
                "{...}",
                743,
                "Good",
                Instant.now().minusSeconds(31L * 24 * 60 * 60)));

        context.service.process("tenant-1", "app-1", "doc-1", "system", null);

        verify(context.cladfyStatusPollingService).scheduleInitialStatusCheck(any(StatementAnalysis.class));
        verify(context.applicationService, never()).handleStatementPassed(any(), any(), any());
        verify(context.applicationService, never()).handleStatementManualReview(any(), any(), any(), any());
        verify(context.applicationService, never()).handleStatementFailed(any(), any(), any(), any());
    }

    private static LoanRequestApplication application(String tenantId, String applicationId) {
        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, applicationId);
        application.setTenantId(tenantId);
        application.setNationalId("28862588");
        application.setPhoneNumber("254717529722");
        application.setApplicantFirstName("Bonface");
        application.setApplicantLastName("Thaa");
        return application;
    }

    private static ApplicationDocument document(String id) {
        ApplicationDocument document = new ApplicationDocument();
        setId(document, id);
        document.setTenantId("tenant-1");
        document.setApplicationId("app-1");
        document.setDocumentType("MPESA_STATEMENT");
        document.setOriginalFilename("statement.pdf");
        document.setStoredFilename("statement.pdf");
        document.setRelativePath("statements/statement.pdf");
        document.setContentType("application/pdf");
        document.setFileSize(100L);
        document.setPublicUrl("https://example.com/statement.pdf");
        document.setCreatedBy("tester");
        return document;
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

    private static void setId(ApplicationDocument document, String id) {
        try {
            var field = ApplicationDocument.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(document, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class TestContext {
        private final StatementAnalysisRepository statementAnalysisRepository = mock(StatementAnalysisRepository.class);
        private final StatementProviderRegistry statementProviderRegistry = mock(StatementProviderRegistry.class);
        private final ApplicationService applicationService = mock(ApplicationService.class);
        private final DocumentService documentService = mock(DocumentService.class);
        private final SubscriptionBillingService subscriptionBillingService = mock(SubscriptionBillingService.class);
        private final CladfyStatusPollingService cladfyStatusPollingService = mock(CladfyStatusPollingService.class);
        private final CladfyGateway cladfyGateway = mock(CladfyGateway.class);
        private final CladfyStatementAnalysisProvider provider = new CladfyStatementAnalysisProvider(cladfyGateway);
        private final StatementAnalysisProcessingService service = new StatementAnalysisProcessingService(
                statementAnalysisRepository,
                statementProviderRegistry,
                applicationService,
                documentService,
                subscriptionBillingService,
                cladfyStatusPollingService);
    }
}
