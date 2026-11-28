package com.credvenn.lm.scoring;

import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.applicationvariable.ApplicationVariableService;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.statement.StatementAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal snapshot operation. The caller must authorize the application and start an assessment transaction. */
@Service @RequiredArgsConstructor
public class AssessmentSnapshotService {
    private final LoanRequestApplicationRepository applications;
    private final StatementAnalysisRepository statements;
    private final ScorecardVersionRepository versions;
    private final ApplicationVariableService answers;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final StatementPolicyValidator policyValidator;

    @Transactional(propagation = Propagation.MANDATORY)
    public String captureInitial(String tenantId, String applicationId, String analysisId, String requestKey) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 100) throw new IllegalArgumentException("A valid request key is required");
        var app = applications.findForLogbookUpdateByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));
        var analysis = statements.findByIdAndTenantId(analysisId, tenantId).orElseThrow(() -> new NotFoundException("Statement analysis not found"));
        if (!applicationId.equals(analysis.getApplicationId()) || !"INTERNAL_MPESA".equals(analysis.getProvider()))
            throw new ConflictException("Statement analysis is not an internal analysis of this application");
        var existing = jdbc.query("SELECT id, application_id, statement_analysis_id FROM scoring_assessment_snapshots WHERE tenant_id = ? AND request_key = ?",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, tenantId, requestKey);
        if (!existing.isEmpty()) {
            var prior = existing.get(0);
            if (!applicationId.equals(prior[1]) || !analysisId.equals(prior[2])) throw new ConflictException("Assessment request key already used");
            return prior[0];
        }
        if (app.getScorecardVersionId() == null) throw new ConflictException("No scorecard was pinned at application creation; review required");
        var version = versions.findByTenantIdAndOriginationProfileIdAndId(tenantId, app.getOriginationProfileId(), app.getScorecardVersionId())
                .orElseThrow(() -> new ConflictException("Pinned scorecard unavailable; review required"));
        if (!"PUBLISHED".equals(version.getStatus()) || version.getSnapshotJson() == null)
            throw new ConflictException("Pinned scorecard is not published; review required");
        try {
            var snapshot = json.readValue(version.getSnapshotJson(), ScorecardDtos.Snapshot.class);
            policyValidator.validate(snapshot.configuration().statementPolicy());
            String answerJson = json.writeValueAsString(answers.answers(tenantId, applicationId));
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO scoring_assessment_snapshots (id,tenant_id,application_id,statement_analysis_id,scorecard_version_id,request_key,scorecard_snapshot_json,answers_snapshot_json,created_at) VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                    id, tenantId, applicationId, analysisId, version.getId(), requestKey, version.getSnapshotJson(), answerJson);
            return id;
        } catch (com.credvenn.lm.common.exception.BadRequestException ex) {
            throw new ConflictException("Pinned scorecard has incomplete statement policy; review required");
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to capture assessment snapshot", ex);
        }
    }
}
