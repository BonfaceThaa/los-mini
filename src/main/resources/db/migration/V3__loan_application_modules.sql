CREATE TABLE loan_request_applications (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    applicant_first_name VARCHAR(255) NOT NULL,
    applicant_last_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50) NOT NULL,
    national_id VARCHAR(100) NOT NULL,
    requested_amount DECIMAL(19,2) NOT NULL,
    requested_term_months INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    fineract_client_id VARCHAR(100),
    fineract_loan_id VARCHAR(100),
    selected_fineract_product_id VARCHAR(100),
    selected_fineract_product_name VARCHAR(255),
    selected_offer_at TIMESTAMP NULL,
    consent_captured BOOLEAN NOT NULL DEFAULT FALSE,
    consent_captured_by VARCHAR(255),
    consent_captured_at TIMESTAMP NULL,
    consent_text_version VARCHAR(100),
    internal_approved BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by VARCHAR(255),
    approved_at TIMESTAMP NULL,
    approval_reason VARCHAR(1000),
    approved_amount DECIMAL(19,2) NULL,
    approved_term_months INT NULL,
    approved_fineract_product_id VARCHAR(100),
    approved_fineract_product_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_loan_applications_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE TABLE application_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id VARCHAR(36) NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_status_history_application FOREIGN KEY (application_id) REFERENCES loan_request_applications(id)
);

CREATE TABLE application_documents (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    relative_path VARCHAR(1000) NOT NULL,
    content_type VARCHAR(255),
    file_size BIGINT NOT NULL,
    public_url VARCHAR(1000) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_documents_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_documents_application FOREIGN KEY (application_id) REFERENCES loan_request_applications(id)
);

CREATE TABLE application_kyc_checks (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(255),
    summary VARCHAR(1000),
    reviewed_by VARCHAR(255),
    review_reason VARCHAR(1000),
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_kyc_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_kyc_application FOREIGN KEY (application_id) REFERENCES loan_request_applications(id)
);

CREATE TABLE application_statement_analyses (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    source_document_id VARCHAR(36) NOT NULL,
    summary VARCHAR(1000),
    average_monthly_inflow DECIMAL(19,2),
    average_monthly_outflow DECIMAL(19,2),
    affordability_score DECIMAL(10,2),
    recommendation VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_statement_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_statement_application FOREIGN KEY (application_id) REFERENCES loan_request_applications(id),
    CONSTRAINT fk_statement_document FOREIGN KEY (source_document_id) REFERENCES application_documents(id)
);

CREATE TABLE inventory_devices (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    device_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_inventory_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_inventory_serial UNIQUE (tenant_id, serial_number)
);

CREATE TABLE inventory_device_assignments (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    assigned_by VARCHAR(255) NOT NULL,
    assigned_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_inventory_assignment_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_inventory_assignment_application FOREIGN KEY (application_id) REFERENCES loan_request_applications(id),
    CONSTRAINT fk_inventory_assignment_device FOREIGN KEY (device_id) REFERENCES inventory_devices(id),
    CONSTRAINT uq_inventory_assignment_application UNIQUE (application_id)
);

CREATE INDEX idx_loan_request_applications_tenant ON loan_request_applications (tenant_id);
CREATE INDEX idx_loan_request_applications_status ON loan_request_applications (status);
CREATE INDEX idx_documents_application ON application_documents (application_id);
CREATE INDEX idx_kyc_application ON application_kyc_checks (application_id);
CREATE INDEX idx_statement_application ON application_statement_analyses (application_id);
CREATE INDEX idx_inventory_devices_tenant ON inventory_devices (tenant_id);
