package com.credvenn.lm.statement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationStatementOtpService;
import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.lm.document.DocumentService;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StatementAnalysisProcessingServiceTest {

    @Test
    void processSchedulesPollingWhenUploadScoringIsFresh() {
        TestContext context = new TestContext();
        ApplicationDocument document = document("doc-1");
        var application = application("tenant-1", "app-1");
        StatementAnalysis analysis = analysis("analysis-1");

        when(context.statementProviderRegistry.currentProvider()).thenReturn(context.provider);
        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.statementAnalysisRepository.findByIdAndTenantId("analysis-1", "tenant-1"))
                .thenReturn(Optional.of(analysis));
        when(context.statementAnalysisRepository.save(any(StatementAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        when(context.applicationStatementOtpService.reserveFirstPendingOtpThatOpensDocument("tenant-1", "app-1", "doc-1"))
                .thenReturn(Optional.of(new ApplicationStatementOtpService.ResolvedOtp("otp-1", "123456", "****56")));
        when(context.cladfyGateway.submit(application, document, "123456")).thenReturn(new StatementAnalysisSubmission(
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

        context.service.process("tenant-1", "analysis-1", "system", null);

        verify(context.cladfyStatusPollingService).scheduleInitialStatusCheck(any(StatementAnalysis.class));
        verify(context.subscriptionBillingService, never()).chargeStatementCompletion(any(), any(), any());
        verify(context.statementReviewService, never()).recordDecision(any(), any(), any(), any(), any(), any(), any());
        verify(context.applicationService, never()).handleStatementPassed(any(), any(), any());
        verify(context.applicationService, never()).handleStatementManualReview(any(), any(), any(), any());
        verify(context.applicationService, never()).handleStatementFailed(any(), any(), any(), any());
        verify(context.statementAnalysisRepository, org.mockito.Mockito.times(2)).save(any(StatementAnalysis.class));
    }

    @Test
    void processSchedulesPollingWhenUploadScoringIsStale() {
        TestContext context = new TestContext();
        ApplicationDocument document = document("doc-1");
        var application = application("tenant-1", "app-1");
        StatementAnalysis analysis = analysis("analysis-1");

        when(context.statementProviderRegistry.currentProvider()).thenReturn(context.provider);
        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.statementAnalysisRepository.findByIdAndTenantId("analysis-1", "tenant-1"))
                .thenReturn(Optional.of(analysis));
        when(context.statementAnalysisRepository.save(any(StatementAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        when(context.applicationStatementOtpService.reserveFirstPendingOtpThatOpensDocument("tenant-1", "app-1", "doc-1"))
                .thenReturn(Optional.of(new ApplicationStatementOtpService.ResolvedOtp("otp-1", "123456", "****56")));
        when(context.cladfyGateway.submit(application, document, "123456")).thenReturn(new StatementAnalysisSubmission(
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

        context.service.process("tenant-1", "analysis-1", "system", null);

        verify(context.cladfyStatusPollingService).scheduleInitialStatusCheck(any(StatementAnalysis.class));
        verify(context.subscriptionBillingService, never()).chargeStatementCompletion(any(), any(), any());
        verify(context.statementReviewService, never()).recordDecision(any(), any(), any(), any(), any(), any(), any());
        verify(context.applicationService, never()).handleStatementPassed(any(), any(), any());
        verify(context.applicationService, never()).handleStatementManualReview(any(), any(), any(), any());
        verify(context.applicationService, never()).handleStatementFailed(any(), any(), any(), any());
    }

    @Test
    void processFailsBeforeStatusTransitionWhenNoPendingOtpOpensDocument() {
        TestContext context = new TestContext();
        ApplicationDocument document = document("doc-1");
        var application = application("tenant-1", "app-1");
        StatementAnalysis analysis = analysis("analysis-1");

        when(context.statementProviderRegistry.currentProvider()).thenReturn(context.provider);
        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.statementAnalysisRepository.findByIdAndTenantId("analysis-1", "tenant-1"))
                .thenReturn(Optional.of(analysis));
        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        when(context.applicationStatementOtpService.reserveFirstPendingOtpThatOpensDocument("tenant-1", "app-1", "doc-1"))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> context.service.process("tenant-1", "analysis-1", "system", null));

        verify(context.applicationService, never()).handleStatementInProgress(any(), any(), any());
        verify(context.statementAnalysisRepository, never()).save(any(StatementAnalysis.class));
        verify(context.cladfyGateway, never()).submit(any(), any(), any());
    }

    @Test
    void processChargesCompletedProviderAnalysisEvenWhenFailed() {
        TestContext context = new TestContext();
        ApplicationDocument document = document("doc-1");
        var application = application("tenant-1", "app-1");
        StatementAnalysis analysis = analysis("analysis-1");
        StatementAnalysisProvider directProvider = mock(StatementAnalysisProvider.class);

        when(context.statementProviderRegistry.currentProvider()).thenReturn(directProvider);
        when(directProvider.providerCode()).thenReturn("DIRECT");
        when(directProvider.supportsAsyncWebhookCompletion()).thenReturn(false);
        when(directProvider.analyze(application, document)).thenReturn(new StatementAnalysisProvider.StatementDecision(
                StatementAnalysisStatus.FAILED,
                null,
                null,
                null,
                "DECLINE",
                "Provider rejected the statement"));
        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.statementAnalysisRepository.findByIdAndTenantId("analysis-1", "tenant-1"))
                .thenReturn(Optional.of(analysis));
        when(context.statementAnalysisRepository.save(any(StatementAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);

        context.service.process("tenant-1", "analysis-1", "system", null);

        verify(context.subscriptionBillingService).chargeStatementCompletion("tenant-1", "analysis-1", "system");
        verify(context.applicationService).handleStatementFailed("tenant-1", "app-1", "system", "Statement analysis failed");
    }

    @Test
    void processDoesNotChargeSimulatedFailure() {
        TestContext context = new TestContext();
        ApplicationDocument document = document("doc-1");
        var application = application("tenant-1", "app-1");
        StatementAnalysis analysis = analysis("analysis-1");
        StatementAnalysisProvider directProvider = mock(StatementAnalysisProvider.class);

        when(context.statementProviderRegistry.currentProvider()).thenReturn(directProvider);
        when(directProvider.providerCode()).thenReturn("DIRECT");
        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.statementAnalysisRepository.findByIdAndTenantId("analysis-1", "tenant-1"))
                .thenReturn(Optional.of(analysis));
        when(context.statementAnalysisRepository.save(any(StatementAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);

        context.service.process("tenant-1", "analysis-1", "system", "FAILED");

        verify(context.subscriptionBillingService, never()).chargeStatementCompletion(any(), any(), any());
        verify(context.applicationService).handleStatementFailed("tenant-1", "app-1", "system", "Statement analysis failed");
        verify(directProvider, never()).analyze(any(), any());
    }

    private static com.credvenn.lm.application.LoanRequestApplication application(String tenantId, String applicationId) {
        var application = new com.credvenn.lm.application.LoanRequestApplication();
        setId(application, applicationId);
        application.setTenantId(tenantId);
        application.setNationalId("28862588");
        application.setPhoneNumber("254717529722");
        application.setApplicantFirstName("Bonface");
        application.setApplicantLastName("Thaa");
        return application;
    }

    private static StatementAnalysis analysis(String id) {
        StatementAnalysis analysis = new StatementAnalysis();
        setId(analysis, id);
        analysis.setTenantId("tenant-1");
        analysis.setApplicationId("app-1");
        analysis.setSourceDocumentId("doc-1");
        analysis.setProvider("QUEUED");
        analysis.setStatus(StatementAnalysisStatus.PENDING);
        return analysis;
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

    private static void setId(com.credvenn.lm.application.LoanRequestApplication application, String id) {
        try {
            var field = com.credvenn.lm.application.LoanRequestApplication.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(application, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void setId(StatementAnalysis analysis, String id) {
        try {
            var field = StatementAnalysis.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(analysis, id);
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
        private final StatementReviewService statementReviewService = mock(StatementReviewService.class);
        private final ApplicationStatementOtpService applicationStatementOtpService = mock(ApplicationStatementOtpService.class);
        private final CladfyGateway cladfyGateway = mock(CladfyGateway.class);
        private final CladfyStatementAnalysisProvider provider = new CladfyStatementAnalysisProvider(cladfyGateway);
        private final StatementAnalysisProcessingService service = new StatementAnalysisProcessingService(
                statementAnalysisRepository,
                statementProviderRegistry,
                applicationService,
                documentService,
                subscriptionBillingService,
                cladfyStatusPollingService,
                statementReviewService,
                applicationStatementOtpService);
    }
}
