package com.credvenn.lm.statement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationStatementOtpService;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.document.ApplicationDocument;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

class StatementAnalysisProcessingServiceTest {

    @Test
    void processSubmitsOutsidePreparationAndDelegatesPersistenceToCompletion() {
        TestContext context = new TestContext();
        var work = work(false);
        var submission = submission();
        when(context.registry.currentProvider()).thenReturn(context.provider);
        when(context.provider.providerCode()).thenReturn("CLADFY");
        when(context.provider.supportsAsyncWebhookCompletion()).thenReturn(true);
        when(context.preparation.prepare("tenant-1", "analysis-1", "system", "CLADFY", true)).thenReturn(work);
        when(context.provider.submit(work.application(), work.document(), "123456")).thenReturn(submission);

        context.service.process("tenant-1", "analysis-1", "system", null);

        verify(context.provider).submit(work.application(), work.document(), "123456");
        verify(context.completion).completeSubmission("tenant-1", "analysis-1", submission);
        verify(context.provider, never()).recoverSubmission(any(), any(), any());
    }

    @Test
    void processRetriesByRecoveringExistingCladfyDocumentBeforeReuploading() {
        TestContext context = new TestContext();
        var firstWork = work(false);
        var retryWork = work(true);
        var recovered = submission();
        when(context.registry.currentProvider()).thenReturn(context.provider);
        when(context.provider.providerCode()).thenReturn("CLADFY");
        when(context.provider.supportsAsyncWebhookCompletion()).thenReturn(true);
        when(context.preparation.prepare("tenant-1", "analysis-1", "system", "CLADFY", true))
                .thenReturn(firstWork, retryWork);
        when(context.provider.submit(firstWork.application(), firstWork.document(), "123456"))
                .thenThrow(new RestClientException("response lost after upload"));
        when(context.provider.recoverSubmission(
                retryWork.application(), retryWork.document(), retryWork.recoveryNotBefore()))
                .thenReturn(Optional.of(recovered));

        context.service.process("tenant-1", "analysis-1", "system", null);

        verify(context.provider).recoverSubmission(
                retryWork.application(), retryWork.document(), retryWork.recoveryNotBefore());
        verify(context.completion).completeSubmission("tenant-1", "analysis-1", recovered);
    }

    @Test
    void processRunsDirectProviderOutsideTransactionThenCompletesInTxService() {
        TestContext context = new TestContext();
        var work = work(false);
        var decision = new StatementAnalysisProvider.StatementDecision(
                StatementAnalysisStatus.FAILED, null, null, null, "DECLINE", "Rejected");
        when(context.registry.currentProvider()).thenReturn(context.provider);
        when(context.provider.providerCode()).thenReturn("DIRECT");
        when(context.provider.supportsAsyncWebhookCompletion()).thenReturn(false);
        when(context.preparation.prepare("tenant-1", "analysis-1", "system", "DIRECT", false)).thenReturn(work);
        when(context.provider.analyze(work.application(), work.document())).thenReturn(decision);

        context.service.process("tenant-1", "analysis-1", "system", null);

        verify(context.completion).completeDecision("tenant-1", "analysis-1", "system", decision, true);
    }

    private static StatementSubmissionPreparationService.PreparedSubmission work(boolean recoveryAttempt) {
        LoanRequestApplication application = new LoanRequestApplication();
        application.setTenantId("tenant-1");
        ApplicationDocument document = new ApplicationDocument();
        document.setTenantId("tenant-1");
        document.setApplicationId("app-1");
        return new StatementSubmissionPreparationService.PreparedSubmission(
                "analysis-1",
                "tenant-1",
                "app-1",
                "doc-1",
                application,
                document,
                new ApplicationStatementOtpService.ResolvedOtp("otp-1", "123456", "****56"),
                recoveryAttempt,
                false,
                Instant.parse("2026-09-07T10:00:00Z"));
    }

    private static StatementAnalysisSubmission submission() {
        return new StatementAnalysisSubmission(
                "CLADFY", "1", "55981", "57435", null,
                "Recovered", "{...}", null, null, null);
    }

    private static final class TestContext {
        private final StatementProviderRegistry registry = mock(StatementProviderRegistry.class);
        private final StatementAnalysisProvider provider = mock(StatementAnalysisProvider.class);
        private final StatementSubmissionPreparationService preparation = mock(StatementSubmissionPreparationService.class);
        private final StatementSubmissionCompletionService completion = mock(StatementSubmissionCompletionService.class);
        private final StatementAnalysisProcessingService service = new StatementAnalysisProcessingService(
                registry, preparation, completion);
    }
}
