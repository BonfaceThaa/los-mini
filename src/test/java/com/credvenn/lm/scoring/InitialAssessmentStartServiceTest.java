package com.credvenn.lm.scoring;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.statement.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InitialAssessmentStartServiceTest {
    @Test void onlyStartsAfterInternalExtractionAndUsesStableRequestKey() {
        var snapshots = mock(AssessmentSnapshotService.class);
        var statements = mock(StatementAnalysisRepository.class);
        var analysis = new StatementAnalysis(); analysis.setApplicationId("app"); analysis.setProvider("INTERNAL_MPESA");
        analysis.setStatus(StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED);
        when(statements.findByIdAndTenantId("analysis", "tenant")).thenReturn(Optional.of(analysis));
        when(snapshots.captureInitial("tenant", "app", "analysis", "initial:analysis")).thenReturn("snapshot");
        var service = new InitialAssessmentStartService(snapshots, statements);
        assertEquals("snapshot", service.start("tenant", "app", "analysis"));
        analysis.setStatus(StatementAnalysisStatus.PROCESSING_FAILED);
        assertThrows(ConflictException.class, () -> service.start("tenant", "app", "analysis"));
        verify(snapshots, times(1)).captureInitial("tenant", "app", "analysis", "initial:analysis");
    }
}
