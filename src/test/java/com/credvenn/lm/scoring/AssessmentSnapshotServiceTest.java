package com.credvenn.lm.scoring;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.credvenn.lm.application.*;
import com.credvenn.lm.applicationvariable.ApplicationVariableService;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.statement.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AssessmentSnapshotServiceTest {
    @Test void missingPinnedVersionAndWrongApplicationNeverCapture() {
        var apps = mock(LoanRequestApplicationRepository.class);
        var statements = mock(StatementAnalysisRepository.class);
        var versions = mock(ScorecardVersionRepository.class);
        var answers = mock(ApplicationVariableService.class);
        var jdbc = mock(JdbcTemplate.class);
        var service = new AssessmentSnapshotService(apps, statements, versions, answers, new ObjectMapper(), jdbc, new StatementPolicyValidator());
        var app = new LoanRequestApplication(); app.setOriginationProfileId("profile");
        when(apps.findForLogbookUpdateByTenantIdAndId("tenant", "application")).thenReturn(Optional.of(app));
        var statement = new StatementAnalysis(); statement.setApplicationId("other"); statement.setProvider("INTERNAL_MPESA");
        when(statements.findByIdAndTenantId("analysis", "tenant")).thenReturn(Optional.of(statement));
        assertThrows(ConflictException.class, () -> service.captureInitial("tenant", "application", "analysis", "key"));
        statement.setApplicationId("application");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String[]>>any(), any(), any())).thenReturn(List.of());
        assertThrows(ConflictException.class, () -> service.captureInitial("tenant", "application", "analysis", "key"));
        verifyNoInteractions(answers, versions);
    }

    @Test void repeatedCaptureKeepsTheOriginalAnswerSnapshot() {
        var apps = mock(LoanRequestApplicationRepository.class);
        var statements = mock(StatementAnalysisRepository.class);
        var versions = mock(ScorecardVersionRepository.class);
        var answers = mock(ApplicationVariableService.class);
        var policy = mock(StatementPolicyValidator.class);
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE scoring_assessment_snapshots (id VARCHAR(36), tenant_id VARCHAR(36), application_id VARCHAR(36), statement_analysis_id VARCHAR(36), scorecard_version_id VARCHAR(36), request_key VARCHAR(100), scorecard_snapshot_json CLOB, answers_snapshot_json CLOB, created_at TIMESTAMP, UNIQUE(tenant_id, request_key))");
        var service = new AssessmentSnapshotService(apps, statements, versions, answers, new ObjectMapper(), jdbc, policy);
        var app = new LoanRequestApplication(); app.setOriginationProfileId("profile"); app.setScorecardVersionId("version");
        when(apps.findForLogbookUpdateByTenantIdAndId("tenant", "application")).thenReturn(Optional.of(app));
        var statement = new StatementAnalysis(); statement.setApplicationId("application"); statement.setProvider("INTERNAL_MPESA");
        when(statements.findByIdAndTenantId("analysis", "tenant")).thenReturn(Optional.of(statement));
        var version = new ScorecardVersion(); version.setId("version"); version.setStatus("PUBLISHED");
        version.setSnapshotJson("{\"configuration\":{\"statementWeight\":60,\"questionnaireWeight\":40,\"questions\":[],\"statementPolicy\":null},\"questions\":[]}");
        when(versions.findByTenantIdAndOriginationProfileIdAndId("tenant", "profile", "version")).thenReturn(Optional.of(version));
        when(answers.answers("tenant", "application")).thenReturn(List.of());
        var first = service.captureInitial("tenant", "application", "analysis", "request");
        version.setSnapshotJson("changed");
        var second = service.captureInitial("tenant", "application", "analysis", "request");
        assertEquals(first, second);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM scoring_assessment_snapshots", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT scorecard_snapshot_json FROM scoring_assessment_snapshots WHERE id = ?", String.class, first).contains("statementWeight"));
        verify(answers, times(1)).answers("tenant", "application");
    }
}
