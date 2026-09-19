-- Expand-only migration: existing writers may omit every new reference.
-- NULL question profile means shared; other NULL references await backfill.
CREATE TABLE origination_profiles (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    code VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    requirements_json JSON NOT NULL,
    configuration_json JSON NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_origination_profiles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_origination_profiles_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_origination_profiles_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX idx_origination_profiles_tenant_active ON origination_profiles (tenant_id, active);

ALTER TABLE tenants ADD COLUMN default_origination_profile_id VARCHAR(36) NULL;
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_default_origination_profile
    FOREIGN KEY (id, default_origination_profile_id) REFERENCES origination_profiles (tenant_id, id);

ALTER TABLE loan_product_mappings ADD COLUMN origination_profile_id VARCHAR(36) NULL;
ALTER TABLE loan_product_mappings ADD CONSTRAINT uq_loan_products_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE loan_product_mappings ADD CONSTRAINT fk_loan_products_origination_profile
    FOREIGN KEY (tenant_id, origination_profile_id) REFERENCES origination_profiles (tenant_id, id);

ALTER TABLE loan_request_applications ADD COLUMN origination_profile_id VARCHAR(36) NULL;
ALTER TABLE loan_request_applications ADD COLUMN selected_loan_product_mapping_id VARCHAR(36) NULL;
ALTER TABLE loan_request_applications ADD CONSTRAINT fk_applications_origination_profile
    FOREIGN KEY (tenant_id, origination_profile_id) REFERENCES origination_profiles (tenant_id, id);
ALTER TABLE loan_request_applications ADD CONSTRAINT fk_applications_selected_product_mapping
    FOREIGN KEY (tenant_id, selected_loan_product_mapping_id) REFERENCES loan_product_mappings (tenant_id, id);

ALTER TABLE application_variable_definitions ADD COLUMN origination_profile_id VARCHAR(36) NULL;
ALTER TABLE application_variable_definitions ADD CONSTRAINT fk_variable_definitions_origination_profile
    FOREIGN KEY (tenant_id, origination_profile_id) REFERENCES origination_profiles (tenant_id, id);
CREATE INDEX idx_variable_definitions_profile_active
    ON application_variable_definitions (tenant_id, origination_profile_id, active, display_order);
