package com.credvenn.lm.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class CladfyStatusPollingServiceTest {

    @Test
    void scheduleInitialStatusCheckStartsPollingWindowFiveMinutesOut() {
        CladfyStatusPollingService service = new CladfyStatusPollingService(
                mock(StatementAnalysisRepository.class),
                mock(CladfyGateway.class),
                mock(CladfyAnalysisCompletionService.class),
                transactionManager());
        StatementAnalysis analysis = new StatementAnalysis();

        Instant before = Instant.now();
        service.scheduleInitialStatusCheck(analysis);

        assertEquals(0, analysis.getStatusCheckAttempts());
        assertNotNull(analysis.getNextStatusCheckAt());
        assertTrue(!analysis.getNextStatusCheckAt().isBefore(before.plusSeconds(299)));
        assertEquals(null, analysis.getCompletionSource());
        assertEquals(null, analysis.getCompletedAt());
    }

    @Test
    void claimDueAnalysesAdvancesAttemptsAndNextCheckTime() {
        StatementAnalysisRepository repository = mock(StatementAnalysisRepository.class);
        CladfyStatusPollingService service = new CladfyStatusPollingService(
                repository,
                mock(CladfyGateway.class),
                mock(CladfyAnalysisCompletionService.class),
                transactionManager());
        StatementAnalysis analysis = new StatementAnalysis();
        analysis.assignId();
        analysis.setStatus(StatementAnalysisStatus.IN_PROGRESS);
        analysis.setProvider("CLADFY");
        analysis.setExternalDocumentId("doc-1");
        when(repository.findDueCladfyStatusChecks(eq(Instant.parse("2026-06-20T09:00:00Z")), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(analysis));

        List<String> claimed = service.claimDueAnalyses(Instant.parse("2026-06-20T09:00:00Z"));

        assertEquals(List.of(analysis.getId()), claimed);
        assertEquals(1, analysis.getStatusCheckAttempts());
        assertEquals(Instant.parse("2026-06-20T09:05:00Z"), analysis.getNextStatusCheckAt());
        assertEquals("STATUS_POLLING", analysis.getProviderStatus());
    }

    @Test
    void pollAnalysisCompletesWhenDocumentIsAnalyzed() {
        StatementAnalysisRepository repository = mock(StatementAnalysisRepository.class);
        CladfyGateway gateway = mock(CladfyGateway.class);
        CladfyAnalysisCompletionService completionService = mock(CladfyAnalysisCompletionService.class);
        CladfyStatusPollingService service = new CladfyStatusPollingService(
                repository,
                gateway,
                completionService,
                transactionManager());
        StatementAnalysis analysis = analysis("analysis-1");
        when(repository.findById("analysis-1")).thenReturn(Optional.of(analysis));
        when(gateway.fetchDocumentStatus("doc-1"))
                .thenReturn(new CladfyDtos.DocumentStatusResponse(57417L, "analyzed", null, true));

        service.pollAnalysis("analysis-1");

        verify(completionService).completeAnalyzed(analysis, "cladfy-status-poller", "STATUS_POLLER", null);
        verify(completionService, never()).completeFailedStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void pollAnalysisMarksFailedWhenDocumentStatusIsFailed() {
        StatementAnalysisRepository repository = mock(StatementAnalysisRepository.class);
        CladfyGateway gateway = mock(CladfyGateway.class);
        CladfyAnalysisCompletionService completionService = mock(CladfyAnalysisCompletionService.class);
        CladfyStatusPollingService service = new CladfyStatusPollingService(
                repository,
                gateway,
                completionService,
                transactionManager());
        StatementAnalysis analysis = analysis("analysis-2");
        CladfyDtos.DocumentStatusResponse response = new CladfyDtos.DocumentStatusResponse(57417L, "failed", "bad password", true);
        when(repository.findById("analysis-2")).thenReturn(Optional.of(analysis));
        when(gateway.fetchDocumentStatus("doc-1")).thenReturn(response);

        service.pollAnalysis("analysis-2");

        verify(completionService).completeFailedStatus(analysis, response, "cladfy-status-poller", "STATUS_POLLER");
        verify(completionService, never()).completeAnalyzed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pollAnalysisKeepsInProgressWhenDocumentIsStillAnalyzing() {
        StatementAnalysisRepository repository = mock(StatementAnalysisRepository.class);
        CladfyGateway gateway = mock(CladfyGateway.class);
        CladfyAnalysisCompletionService completionService = mock(CladfyAnalysisCompletionService.class);
        CladfyStatusPollingService service = new CladfyStatusPollingService(
                repository,
                gateway,
                completionService,
                transactionManager());
        StatementAnalysis analysis = analysis("analysis-3");
        CladfyDtos.DocumentStatusResponse response = new CladfyDtos.DocumentStatusResponse(57417L, "analyzing", null, true);
        when(repository.findById("analysis-3")).thenReturn(Optional.of(analysis));
        when(gateway.fetchDocumentStatus("doc-1")).thenReturn(response);

        service.pollAnalysis("analysis-3");

        assertEquals("analyzing", analysis.getProviderStatus());
        assertTrue(analysis.getRawProviderResponse().contains("documentStatus="));
        verify(completionService, never()).completeAnalyzed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(completionService, never()).completeFailedStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private StatementAnalysis analysis(String id) {
        StatementAnalysis analysis = new StatementAnalysis();
        analysis.assignId();
        setId(analysis, id);
        analysis.setTenantId("tenant-1");
        analysis.setApplicationId("app-1");
        analysis.setProvider("CLADFY");
        analysis.setStatus(StatementAnalysisStatus.IN_PROGRESS);
        analysis.setExternalClientId("client-1");
        analysis.setExternalDocumentId("doc-1");
        return analysis;
    }

    private void setId(StatementAnalysis analysis, String id) {
        try {
            var field = StatementAnalysis.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(analysis, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        doNothing().when(transactionManager).commit(transactionStatus);
        doNothing().when(transactionManager).rollback(transactionStatus);
        return transactionManager;
    }
}
