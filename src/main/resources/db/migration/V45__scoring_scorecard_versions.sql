CREATE TABLE scoring_scorecard_versions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    origination_profile_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    draft_json LONGTEXT NOT NULL,
    snapshot_json LONGTEXT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_scorecard_profile FOREIGN KEY (tenant_id, origination_profile_id) REFERENCES origination_profiles (tenant_id, id),
    CONSTRAINT uq_scorecard_profile_version UNIQUE (tenant_id, origination_profile_id, version_number),
    CONSTRAINT uq_scorecard_profile_id UNIQUE (tenant_id, origination_profile_id, id)
);

CREATE TABLE scoring_active_scorecards (
    tenant_id VARCHAR(36) NOT NULL,
    origination_profile_id VARCHAR(36) NOT NULL,
    scorecard_version_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (tenant_id, origination_profile_id),
    CONSTRAINT fk_active_scorecard_version FOREIGN KEY (tenant_id, origination_profile_id, scorecard_version_id)
        REFERENCES scoring_scorecard_versions (tenant_id, origination_profile_id, id)
);

ALTER TABLE loan_request_applications ADD COLUMN scorecard_version_id VARCHAR(36) NULL;
ALTER TABLE loan_request_applications ADD CONSTRAINT fk_application_scorecard_version
    FOREIGN KEY (tenant_id, origination_profile_id, scorecard_version_id)
    REFERENCES scoring_scorecard_versions (tenant_id, origination_profile_id, id);
