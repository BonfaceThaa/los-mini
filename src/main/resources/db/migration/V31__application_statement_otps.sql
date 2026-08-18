CREATE TABLE application_statement_otps (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36) NOT NULL,
    otp_encrypted VARCHAR(512) NOT NULL,
    otp_masked VARCHAR(32) NOT NULL,
    status VARCHAR(30) NOT NULL,
    source VARCHAR(30) NOT NULL,
    used_for_document_id VARCHAR(36) NULL,
    failure_reason VARCHAR(500) NULL,
    tested_at TIMESTAMP NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_application_statement_otps_application
        FOREIGN KEY (application_id) REFERENCES loan_request_applications (id)
);

ALTER TABLE application_statement_analyses
    ADD COLUMN statement_otp_id VARCHAR(36) NULL AFTER source_document_id;
