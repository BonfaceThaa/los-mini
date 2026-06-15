CREATE TABLE deposit_payments (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
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
    status VARCHAR(50) NOT NULL,
    matched_application_id VARCHAR(36),
    matched_fineract_client_id VARCHAR(100),
    raw_payload TEXT,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_deposit_payment_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_deposit_payment_application FOREIGN KEY (matched_application_id) REFERENCES loan_request_applications(id),
    CONSTRAINT uq_deposit_payment_receipt UNIQUE (mpesa_receipt_number)
);

CREATE INDEX idx_deposit_payment_tenant_created ON deposit_payments (tenant_id, created_at);
CREATE INDEX idx_deposit_payment_tenant_application ON deposit_payments (tenant_id, matched_application_id);
CREATE INDEX idx_deposit_payment_tenant_client ON deposit_payments (tenant_id, matched_fineract_client_id);
