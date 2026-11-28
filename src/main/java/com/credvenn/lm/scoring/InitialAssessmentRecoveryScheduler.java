package com.credvenn.lm.scoring;

import com.credvenn.lm.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Finds committed extractions whose initial assessment snapshot was not captured. */
@Component @RequiredArgsConstructor
public class InitialAssessmentRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(InitialAssessmentRecoveryScheduler.class);
    private final TenantRepository tenants;
    private final JdbcTemplate jdbc;
    private final InitialAssessmentStartService assessments;

    @Scheduled(fixedDelayString = "${app.scoring.snapshot-recovery-ms:60000}", initialDelayString = "${app.scoring.snapshot-recovery-ms:60000}")
    public void recover() {
        for (var tenant : tenants.findAllByOrderByNameAsc()) {
            String tenantId = tenant.getId();
            try {
                var candidates = jdbc.query("""
                    SELECT a.application_id, a.id
                    FROM application_statement_analyses a
                    JOIN loan_request_applications p ON p.tenant_id = a.tenant_id AND p.id = a.application_id
                    JOIN scoring_scorecard_versions v ON v.tenant_id = p.tenant_id AND v.id = p.scorecard_version_id
                    WHERE a.tenant_id = ? AND a.provider = 'INTERNAL_MPESA'
                      AND a.status = 'MANUAL_REVIEW_REQUIRED' AND p.status = 'STATEMENT_MANUAL_REVIEW'
                      AND a.raw_provider_response IS NOT NULL AND v.status = 'PUBLISHED'
                      AND JSON_VALID(v.snapshot_json) = 1
                      AND JSON_TYPE(JSON_EXTRACT(v.snapshot_json, '$.configuration.statementPolicy')) = 'OBJECT'
                      AND NOT EXISTS (SELECT 1 FROM scoring_assessment_snapshots s
                                      WHERE s.tenant_id = a.tenant_id AND s.request_key = CONCAT('initial:', a.id))
                    ORDER BY a.created_at ASC LIMIT 25
                    """, (rs, row) -> new String[] { rs.getString(1), rs.getString(2) }, tenantId);
                for (var candidate : candidates) {
                    try { assessments.start(tenantId, candidate[0], candidate[1]); }
                    catch (RuntimeException ex) { log.warn("Assessment snapshot recovery failed for analysisId={}", candidate[1], ex); }
                }
            } catch (RuntimeException ex) {
                log.warn("Assessment snapshot recovery failed for tenantId={}", tenantId, ex);
            }
        }
    }
}
