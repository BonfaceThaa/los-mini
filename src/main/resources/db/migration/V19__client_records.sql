CREATE TABLE client_records (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36) NOT NULL,
    fineract_client_id VARCHAR(100) NOT NULL,
    account_no VARCHAR(100),
    external_id VARCHAR(100),
    status VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    firstname VARCHAR(255),
    middlename VARCHAR(255),
    lastname VARCHAR(255),
    display_name VARCHAR(255),
    mobile_no VARCHAR(100),
    office_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_client_records_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_client_records_application FOREIGN KEY (application_id) REFERENCES loan_request_applications(id),
    CONSTRAINT uq_client_records_tenant_application UNIQUE (tenant_id, application_id),
    CONSTRAINT uq_client_records_tenant_fineract_client UNIQUE (tenant_id, fineract_client_id)
);

CREATE INDEX idx_client_records_tenant_id ON client_records (tenant_id);
