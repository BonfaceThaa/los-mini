ALTER TABLE application_statement_analyses
    ADD CONSTRAINT uq_scoring_analysis_tenant_app_id UNIQUE (tenant_id, application_id, id);
ALTER TABLE scoring_scorecard_versions
    ADD CONSTRAINT uq_scoring_version_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE scoring_assessment_snapshots
    ADD CONSTRAINT fk_assessment_tenant_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES loan_request_applications (tenant_id, id),
    ADD CONSTRAINT fk_assessment_tenant_application_analysis FOREIGN KEY (tenant_id, application_id, statement_analysis_id)
        REFERENCES application_statement_analyses (tenant_id, application_id, id),
    ADD CONSTRAINT fk_assessment_tenant_scorecard FOREIGN KEY (tenant_id, scorecard_version_id)
        REFERENCES scoring_scorecard_versions (tenant_id, id);
