CREATE TABLE scoring_assessment_snapshots (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36) NOT NULL,
    statement_analysis_id VARCHAR(36) NOT NULL,
    scorecard_version_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    scorecard_snapshot_json LONGTEXT NOT NULL,
    answers_snapshot_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_assessment_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_assessment_application FOREIGN KEY (application_id) REFERENCES loan_request_applications(id),
    CONSTRAINT fk_assessment_analysis FOREIGN KEY (statement_analysis_id) REFERENCES application_statement_analyses(id),
    CONSTRAINT fk_assessment_scorecard FOREIGN KEY (scorecard_version_id) REFERENCES scoring_scorecard_versions(id),
    CONSTRAINT uq_assessment_request UNIQUE (tenant_id, request_key)
);
CREATE INDEX idx_assessment_application ON scoring_assessment_snapshots (tenant_id, application_id);

CREATE TABLE scoring_scorecard_audit (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    origination_profile_id VARCHAR(36) NOT NULL,
    scorecard_version_id VARCHAR(36) NOT NULL,
    action VARCHAR(20) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    before_json LONGTEXT NULL,
    after_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_scorecard_audit_version FOREIGN KEY (tenant_id, origination_profile_id, scorecard_version_id)
        REFERENCES scoring_scorecard_versions (tenant_id, origination_profile_id, id)
);
CREATE INDEX idx_scorecard_audit_tenant_version ON scoring_scorecard_audit (tenant_id, scorecard_version_id, created_at);
