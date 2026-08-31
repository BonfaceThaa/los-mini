package com.credvenn.lm.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.document.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class StatementAnalysisServiceTest {

    @Test
    void runStartsAnIndependentTransactionForAfterCommitDispatch() throws NoSuchMethodException {
        Method method = StatementAnalysisService.class.getMethod(
                "run",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class);

        assertEquals(Propagation.REQUIRES_NEW, method.getAnnotation(Transactional.class).propagation());
    }

    @Test
    void manualPassPreservesProviderAnalysisAndAddsSeparateReview() {
        TestContext context = new TestContext();
        var application = application("app-1", ApplicationStatus.STATEMENT_MANUAL_REVIEW);
        StatementAnalysis failedAnalysis = analysis("analysis-1", StatementAnalysisStatus.FAILED, "CLADFY", Instant.parse("2026-07-02T09:00:00Z"));
        StatementReview review = review(
                "review-1",
                "tenant-1",
                "app-1",
                "analysis-1",
                StatementReviewDecision.APPROVED,
                StatementReviewSource.USER,
                "approved after branch review",
                "officer",
                Instant.parse("2026-07-02T10:00:00Z"));

        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        when(context.statementAnalysisRepository.existsByApplicationIdAndStatusIn(anyString(), any())).thenReturn(false);
        when(context.statementAnalysisRepository.findAllByApplicationIdOrderByCreatedAtDesc("app-1")).thenReturn(List.of(failedAnalysis));
        when(context.statementReviewService.recordDecision(
                "tenant-1",
                "app-1",
                "analysis-1",
                StatementReviewDecision.APPROVED,
                StatementReviewSource.USER,
                "officer",
                "approved after branch review")).thenReturn(review);
        when(context.statementReviewService.getLatestForAnalysis("analysis-1")).thenReturn(Optional.of(review));
        when(context.transactionRepository.findAllByStatementAnalysisIdOrderByCreatedAtAsc("analysis-1")).thenReturn(List.of());
        doNothing().when(context.applicationService).handleStatementPassed("tenant-1", "app-1", "officer");

        StatementDtos.StatementAssessmentResponse response = context.service.manualPass(
                "tenant-1",
                "app-1",
                "officer",
                new StatementDtos.ManualStatementPassRequest("approved after branch review"));

        assertNotNull(response.analysis());
        assertEquals("analysis-1", response.analysis().id());
        assertEquals(StatementAnalysisStatus.FAILED, response.analysis().status());
        assertNotNull(response.review());
        assertEquals(StatementReviewDecision.APPROVED, response.review().decision());
        assertEquals(StatementEffectiveOutcome.APPROVED, response.effectiveOutcome());
        verify(context.statementAnalysisRepository, never()).save(any(StatementAnalysis.class));
        verify(context.applicationService).handleStatementPassed("tenant-1", "app-1", "officer");
    }

    @Test
    void queueRetryIfEligibleReturnsFalseWhenApplicationIsPastStatementInProgress() {
        TestContext context = new TestContext();
        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application("app-1", ApplicationStatus.FINERACT_LOAN_ACTIVATED));

        boolean queued = context.service.queueRetryIfEligible("tenant-1", "app-1", "officer");

        assertFalse(queued);
        verify(context.applicationStatementOtpService, never()).findFirstPendingOtpThatOpensDocument(anyString(), anyString(), anyString());
        verify(context.documentService, never()).findLatestByApplicationIdAndDocumentType(anyString(), anyString(), anyString());
        verify(context.applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void runRejectsApplicationsPastStatementInProgress() {
        TestContext context = new TestContext();
        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application("app-1", ApplicationStatus.STATEMENT_VERIFIED));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> context.service.run(
                "tenant-1",
                "app-1",
                "doc-1",
                "officer",
                null));

        assertEquals("Statement analysis is not allowed after status STATEMENT_VERIFIED", exception.getMessage());
        verify(context.documentService, never()).getRequired(anyString(), anyString());
        verify(context.applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void runRejectsWhenNoPendingOtpCanOpenDocument() {
        TestContext context = new TestContext();
        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application("app-1", ApplicationStatus.STATEMENT_PENDING));
        var document = new com.credvenn.lm.document.ApplicationDocument();
        setField(document, com.credvenn.lm.document.ApplicationDocument.class, "id", "doc-1");
        document.setApplicationId("app-1");
        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.statementProviderRegistry.currentProvider()).thenReturn(context.provider);
        when(context.provider.providerCode()).thenReturn("CLADFY");
        when(context.applicationStatementOtpService.findFirstPendingOtpThatOpensDocument("tenant-1", "app-1", "doc-1")).thenReturn(Optional.empty());

        BadRequestException exception = assertThrows(BadRequestException.class, () -> context.service.run(
                "tenant-1",
                "app-1",
                "doc-1",
                "officer",
                null));

        assertEquals("No pending statement OTP can open the statement document", exception.getMessage());
        verify(context.applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void runPublishesSavedAnalysisIdForAfterCommitProcessing() {
        TestContext context = new TestContext();
        var application = application("app-1", ApplicationStatus.STATEMENT_PENDING);
        var document = new com.credvenn.lm.document.ApplicationDocument();
        setField(document, com.credvenn.lm.document.ApplicationDocument.class, "id", "doc-1");
        document.setApplicationId("app-1");

        when(context.applicationService.getRequired("tenant-1", "app-1")).thenReturn(application);
        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.statementProviderRegistry.currentProvider()).thenReturn(context.provider);
        when(context.provider.providerCode()).thenReturn("CLADFY");
        when(context.applicationStatementOtpService.findFirstPendingOtpThatOpensDocument("tenant-1", "app-1", "doc-1"))
                .thenReturn(Optional.of(new com.credvenn.lm.application.ApplicationStatementOtpService.ResolvedOtp("otp-1", "123456", "****56")));
        when(context.statementAnalysisRepository.existsByApplicationIdAndStatusIn(anyString(), any())).thenReturn(false);
        when(context.statementAnalysisRepository.save(any(StatementAnalysis.class))).thenAnswer(invocation -> {
            StatementAnalysis analysis = invocation.getArgument(0);
            setField(analysis, StatementAnalysis.class, "id", "analysis-1");
            return analysis;
        });

        context.service.run("tenant-1", "app-1", "doc-1", "officer", null);

        ArgumentCaptor<StatementAnalysisRequestedEvent> eventCaptor = ArgumentCaptor.forClass(StatementAnalysisRequestedEvent.class);
        verify(context.applicationEventPublisher).publishEvent(eventCaptor.capture());
        StatementAnalysisRequestedEvent event = eventCaptor.getValue();
        assertEquals("tenant-1", event.tenantId());
        assertEquals("app-1", event.applicationId());
        assertEquals("doc-1", event.documentId());
        assertEquals("analysis-1", event.analysisId());
        assertEquals("officer", event.actor());
    }
    @Test
    void getReturnsReviewOnlyWhenManualApprovalExistsWithoutProviderAnalysis() {
        TestContext context = new TestContext();
        var application = application("app-2", ApplicationStatus.STATEMENT_VERIFIED);
        StatementReview review = review(
                "review-2",
                "tenant-1",
                "app-2",
                null,
                StatementReviewDecision.APPROVED,
                StatementReviewSource.USER,
                "approved without uploaded statement",
                "officer",
                Instant.parse("2026-07-02T11:00:00Z"));

        when(context.applicationService.getRequired("tenant-1", "app-2")).thenReturn(application);
        when(context.statementAnalysisRepository.findAllByApplicationIdOrderByCreatedAtDesc("app-2")).thenReturn(List.of());
        when(context.statementReviewService.getLatestForApplication("app-2")).thenReturn(Optional.of(review));

        StatementDtos.StatementAssessmentResponse response = context.service.get("tenant-1", "app-2");

        assertNull(response.analysis());
        assertNotNull(response.review());
        assertEquals("review-2", response.review().id());
        assertEquals(StatementEffectiveOutcome.APPROVED, response.effectiveOutcome());
    }

    private static com.credvenn.lm.application.LoanRequestApplication application(String id, ApplicationStatus status) {
        var application = new com.credvenn.lm.application.LoanRequestApplication();
        setField(application, com.credvenn.lm.application.LoanRequestApplication.class, "id", id);
        application.setTenantId("tenant-1");
        application.setStatus(status);
        return application;
    }

    private static StatementAnalysis analysis(String id, StatementAnalysisStatus status, String provider, Instant createdAt) {
        StatementAnalysis analysis = new StatementAnalysis();
        setField(analysis, StatementAnalysis.class, "id", id);
        setField(analysis, StatementAnalysis.class, "provider", provider);
        setField(analysis, StatementAnalysis.class, "status", status);
        setField(analysis, StatementAnalysis.class, "applicationId", "app-1");
        setField(analysis, StatementAnalysis.class, "tenantId", "tenant-1");
        setAuditField(analysis, "createdAt", createdAt);
        setAuditField(analysis, "updatedAt", createdAt);
        return analysis;
    }

    private static StatementReview review(
            String id,
            String tenantId,
            String applicationId,
            String analysisId,
            StatementReviewDecision decision,
            StatementReviewSource source,
            String reason,
            String reviewedBy,
            Instant reviewedAt) {
        StatementReview review = new StatementReview();
        setField(review, StatementReview.class, "id", id);
        setField(review, StatementReview.class, "tenantId", tenantId);
        setField(review, StatementReview.class, "applicationId", applicationId);
        setField(review, StatementReview.class, "statementAnalysisId", analysisId);
        setField(review, StatementReview.class, "decision", decision);
        setField(review, StatementReview.class, "decisionSource", source);
        setField(review, StatementReview.class, "reason", reason);
        setField(review, StatementReview.class, "reviewedBy", reviewedBy);
        setField(review, StatementReview.class, "reviewedAt", reviewedAt);
        setAuditField(review, "createdAt", reviewedAt);
        setAuditField(review, "updatedAt", reviewedAt);
        return review;
    }

    private static void setAuditField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getSuperclass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void setField(Object target, Class<?> type, String fieldName, Object value) {
        try {
            var field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class TestContext {
        private final StatementAnalysisRepository statementAnalysisRepository = mock(StatementAnalysisRepository.class);
        private final ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        private final StatementProviderRegistry statementProviderRegistry = mock(StatementProviderRegistry.class);
        private final StatementAnalysisProvider provider = mock(StatementAnalysisProvider.class);
        private final ApplicationService applicationService = mock(ApplicationService.class);
        private final DocumentService documentService = mock(DocumentService.class);
        private final StatementReviewService statementReviewService = mock(StatementReviewService.class);
        private final CladfyStatementTransactionRepository transactionRepository = mock(CladfyStatementTransactionRepository.class);
        private final com.credvenn.lm.application.ApplicationStatementOtpService applicationStatementOtpService = mock(com.credvenn.lm.application.ApplicationStatementOtpService.class);
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final StatementAnalysisService service = new StatementAnalysisService(
                statementAnalysisRepository,
                applicationEventPublisher,
                statementProviderRegistry,
                applicationService,
                documentService,
                statementReviewService,
                transactionRepository,
                applicationStatementOtpService,
                objectMapper);
    }
}
