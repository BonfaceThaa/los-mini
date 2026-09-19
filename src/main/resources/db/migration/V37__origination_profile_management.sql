-- Technical optimistic locking, not business policy versioning.
ALTER TABLE origination_profiles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE origination_profile_audit (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    profile_id VARCHAR(36) NOT NULL,
    action VARCHAR(30) NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_profile_audit_profile FOREIGN KEY (tenant_id, profile_id)
        REFERENCES origination_profiles (tenant_id, id)
);
CREATE INDEX idx_profile_audit_tenant_profile_time ON origination_profile_audit (tenant_id, profile_id, created_at);
