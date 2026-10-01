CREATE TABLE logbook_loan_operations (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, application_id VARCHAR(36) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, kind VARCHAR(20) NOT NULL, state VARCHAR(30) NOT NULL,
    requested_by VARCHAR(255) NOT NULL, attempts INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP(6) NULL, completed_at TIMESTAMP(6) NULL, last_error VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL, created_by VARCHAR(36) NOT NULL,
    CONSTRAINT uq_logbook_operation UNIQUE (tenant_id, application_id, kind),
    CONSTRAINT fk_logbook_operation_application FOREIGN KEY (tenant_id, application_id) REFERENCES loan_request_applications(tenant_id, id),
    CONSTRAINT ck_logbook_operation_kind CHECK (kind IN ('CREATE_LOAN','DISBURSE')),
    CONSTRAINT ck_logbook_operation_state CHECK (state IN ('QUEUED','RUNNING','SUCCEEDED','BLOCKED','REVIEW_REQUIRED'))
);
CREATE INDEX idx_logbook_operation_queue ON logbook_loan_operations(tenant_id,state,created_at);
