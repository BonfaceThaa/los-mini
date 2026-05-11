CREATE TABLE tenant_payment_channels (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    channel_type VARCHAR(50) NOT NULL,
    short_code VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_payment_channel_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_payment_channel_short_code UNIQUE (short_code)
);

CREATE TABLE mpesa_payment_receipts (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36),
    business_short_code VARCHAR(50) NOT NULL,
    bill_ref_number VARCHAR(100) NOT NULL,
    normalized_phone_number VARCHAR(50),
    transaction_amount DECIMAL(19,2) NOT NULL,
    transaction_time TIMESTAMP NOT NULL,
    mpesa_receipt_number VARCHAR(100) NOT NULL,
    msisdn VARCHAR(50),
    payer_first_name VARCHAR(255),
    payer_middle_name VARCHAR(255),
    payer_last_name VARCHAR(255),
    processing_status VARCHAR(50) NOT NULL,
    matched_application_id VARCHAR(36),
    matched_fineract_client_id VARCHAR(100),
    matched_fineract_loan_id VARCHAR(100),
    fineract_transaction_id VARCHAR(100),
    raw_payload TEXT,
    failure_reason VARCHAR(1000),
    processing_started_at TIMESTAMP NULL,
    processed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_mpesa_receipt_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_mpesa_receipt_application FOREIGN KEY (matched_application_id) REFERENCES loan_request_applications(id),
    CONSTRAINT uq_mpesa_receipt_number UNIQUE (mpesa_receipt_number)
);

CREATE INDEX idx_payment_channel_tenant ON tenant_payment_channels (tenant_id);
CREATE INDEX idx_mpesa_receipt_tenant_status ON mpesa_payment_receipts (tenant_id, processing_status);
