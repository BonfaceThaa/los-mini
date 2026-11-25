package com.credvenn.lm.statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.ApplicationStatementOtpService;
import com.credvenn.stmt.analyzer.core.AnalysisInput;
import com.credvenn.stmt.analyzer.core.AnalysisResult;
import com.credvenn.stmt.analyzer.core.DefaultStatementAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;

class InternalMpesaCompletionServiceTest {
    private final StatementAnalysisRepository repository = mock(StatementAnalysisRepository.class);
    private final StatementReviewService reviews = mock(StatementReviewService.class);
    private final ApplicationService applications = mock(ApplicationService.class);
    private final ApplicationStatementOtpService otps = mock(ApplicationStatementOtpService.class);
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final com.credvenn.lm.subscription.InternalStatementBillingService billing = mock(com.credvenn.lm.subscription.InternalStatementBillingService.class);
    private final InternalMpesaCompletionService service = new InternalMpesaCompletionService(repository, reviews, applications, otps, json, billing);

    @Test
    void storesTransactionsAndMovesToReviewOnce() throws Exception {
        var analysis = analysis();
        when(billing.lockForAnalysis("tenant-1", "analysis-1")).thenReturn(new com.credvenn.lm.subscription.InternalStatementBillingService.LockedBilling(analysis, null));
        AnalysisResult result;
        try (var input = getClass().getResourceAsStream("/fixtures/balance-example.pdf")) {
            result = new DefaultStatementAnalyzer().analyze(new AnalysisInput(input.readAllBytes(), null, "MPESA", "analysis-1"));
        }
        service.complete("tenant-1", "analysis-1", "officer", result, null);
        assertEquals(StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED, analysis.getStatus());
        verify(billing).charge(any(), eq("officer"));
        assertEquals(result.transactions().size(), json.readTree(analysis.getRawProviderResponse()).get("transactions").size());
        verify(applications).handleStatementManualReview(eq("tenant-1"), eq("app-1"), eq("officer"), anyString());
        verify(reviews).recordDecision(eq("tenant-1"), eq("app-1"), eq("analysis-1"),
                eq(StatementReviewDecision.MANUAL_REVIEW_REQUIRED), eq(StatementReviewSource.SYSTEM), eq("officer"), anyString());
        service.complete("tenant-1", "analysis-1", "officer", result, null);
        verify(applications, times(1)).handleStatementManualReview(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void failureIsNotAReviewOrRejection() {
        var analysis = analysis();
        when(repository.findForSubmissionUpdate("analysis-1", "tenant-1")).thenReturn(Optional.of(analysis));
        service.fail("tenant-1", "analysis-1", "officer", "INCORRECT_PASSWORD", null);
        assertEquals(StatementAnalysisStatus.PROCESSING_FAILED, analysis.getStatus());
        assertEquals(StatementEffectiveOutcome.PROCESSING_FAILED, StatementAnalysisService.effectiveOutcome(analysis, null));
        assertNull(analysis.getRecommendation());
        verifyNoInteractions(reviews);
        verify(applications).handleStatementFailed(eq("tenant-1"), eq("app-1"), eq("officer"), anyString());
    }

    @Test
    void emptyExtractionDoesNotCreateAReview() {
        var analysis = analysis();
        when(billing.lockForAnalysis("tenant-1", "analysis-1")).thenReturn(new com.credvenn.lm.subscription.InternalStatementBillingService.LockedBilling(analysis, null));
        var result = new AnalysisResult("analysis-1", AnalysisResult.Status.UNDETERMINED, null, "KES",
                List.of(), null, null, List.of(), null, List.of(), List.of(), null,
                new AnalysisResult.Versions("parser", "classifier", "policy"));
        service.complete("tenant-1", "analysis-1", "officer", result, null);
        assertEquals(StatementAnalysisStatus.PROCESSING_FAILED, analysis.getStatus());
        assertEquals("NO_TRANSACTIONS_EXTRACTED", analysis.getProviderStatus());
        verifyNoInteractions(reviews);
        verify(billing, never()).charge(any(), anyString());
        verify(applications).handleStatementFailed(eq("tenant-1"), eq("app-1"), eq("officer"), anyString());
    }

    private StatementAnalysis analysis() {
        var analysis = new StatementAnalysis();
        org.springframework.test.util.ReflectionTestUtils.setField(analysis, "id", "analysis-1");
        analysis.setTenantId("tenant-1");
        analysis.setApplicationId("app-1");
        analysis.setSourceDocumentId("doc-1");
        analysis.setProvider("INTERNAL_MPESA");
        analysis.setStatus(StatementAnalysisStatus.IN_PROGRESS);
        return analysis;
    }
}
