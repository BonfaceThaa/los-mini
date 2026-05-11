CREATE TABLE tenant_statement_inboxes (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    email_address VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_statement_inbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_statement_inbox_email UNIQUE (email_address)
);

CREATE TABLE inbound_statement_receipts (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36),
    destination_email VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    relative_path VARCHAR(1000) NOT NULL,
    content_type VARCHAR(255),
    file_size BIGINT NOT NULL,
    message_id VARCHAR(255),
    received_at TIMESTAMP NOT NULL,
    extracted_phone_token VARCHAR(100),
    match_status VARCHAR(50) NOT NULL,
    matched_application_id VARCHAR(36),
    matched_document_id VARCHAR(36),
    failure_reason VARCHAR(1000),
    review_notes VARCHAR(1000),
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMP NULL,
    processing_started_at TIMESTAMP NULL,
    processed_at TIMESTAMP NULL,
    background_error VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_inbound_receipt_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_inbound_receipt_application FOREIGN KEY (matched_application_id) REFERENCES loan_request_applications(id),
    CONSTRAINT fk_inbound_receipt_document FOREIGN KEY (matched_document_id) REFERENCES application_documents(id)
);

CREATE INDEX idx_statement_inbox_tenant ON tenant_statement_inboxes (tenant_id);
CREATE INDEX idx_inbound_receipt_tenant_status ON inbound_statement_receipts (tenant_id, match_status);
CREATE INDEX idx_inbound_receipt_destination_email ON inbound_statement_receipts (destination_email);
