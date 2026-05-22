CREATE TABLE tenant_branding (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    display_name VARCHAR(255),
    logo_url VARCHAR(1000),
    support_phone VARCHAR(50),
    payment_instructions VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_tenant_branding_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_tenant_branding_tenant UNIQUE (tenant_id)
);

CREATE TABLE mpesa_stk_push_requests (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36),
    fineract_loan_id VARCHAR(100),
    requested_phone_number VARCHAR(50) NOT NULL,
    normalized_phone_number VARCHAR(50) NOT NULL,
    installment_amount DECIMAL(19,2),
    business_short_code VARCHAR(50),
    bill_ref_number VARCHAR(100),
    service_name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    provider_request_id VARCHAR(100),
    provider_response_code VARCHAR(50),
    provider_response_description VARCHAR(500),
    failure_reason VARCHAR(1000),
    initiated_at TIMESTAMP NULL,
    raw_provider_response TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_mpesa_stk_request_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_mpesa_stk_request_application FOREIGN KEY (application_id) REFERENCES loan_request_applications(id)
);

CREATE INDEX idx_mpesa_stk_request_tenant_created ON mpesa_stk_push_requests (tenant_id, created_at);
CREATE INDEX idx_mpesa_stk_request_status ON mpesa_stk_push_requests (status);
