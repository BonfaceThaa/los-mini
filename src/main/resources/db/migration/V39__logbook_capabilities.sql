-- Capability records only: no existing applications/profiles are reclassified or activated.
ALTER TABLE loan_request_applications ADD CONSTRAINT uq_applications_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE application_documents ADD CONSTRAINT uq_documents_tenant_application_id UNIQUE (tenant_id, application_id, id);

CREATE TABLE logbook_vehicles (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, application_id VARCHAR(36) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, evidence_revision BIGINT NOT NULL DEFAULT 0,
    registration_number VARCHAR(20) NOT NULL, chassis_number VARCHAR(50) NOT NULL, engine_number VARCHAR(50) NOT NULL,
    make VARCHAR(100) NOT NULL, model VARCHAR(100) NOT NULL, manufacture_year INT NOT NULL,
    registered_owner VARCHAR(255) NOT NULL, logbook_number VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL, created_by VARCHAR(36) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, updated_by VARCHAR(36) NOT NULL,
    CONSTRAINT uq_logbook_vehicle_application UNIQUE (tenant_id, application_id),
    CONSTRAINT uq_logbook_vehicle_scope UNIQUE (tenant_id, application_id, id),
    CONSTRAINT fk_logbook_vehicle_application FOREIGN KEY (tenant_id, application_id) REFERENCES loan_request_applications(tenant_id, id)
);

CREATE TABLE logbook_valuations (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, application_id VARCHAR(36) NOT NULL,
    vehicle_id VARCHAR(36) NOT NULL, vehicle_revision BIGINT NOT NULL, recorded_version BIGINT NOT NULL,
    market_value DECIMAL(19,2) NOT NULL, forced_sale_value DECIMAL(19,2) NOT NULL, valued_on DATE NOT NULL,
    valuer_organization VARCHAR(255) NOT NULL, report_document_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL, reviewed_by VARCHAR(36) NULL, reviewed_at TIMESTAMP(6) NULL, review_reason VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL, created_by VARCHAR(36) NOT NULL,
    CONSTRAINT uq_logbook_valuation_sequence UNIQUE (tenant_id, application_id, recorded_version),
    CONSTRAINT ck_logbook_values CHECK (market_value > 0 AND forced_sale_value > 0 AND forced_sale_value <= market_value),
    CONSTRAINT ck_logbook_valuation_status CHECK (status IN ('PENDING','APPROVED','REJECTED','REVOKED')),
    CONSTRAINT fk_logbook_valuation_vehicle FOREIGN KEY (tenant_id, application_id, vehicle_id) REFERENCES logbook_vehicles(tenant_id, application_id, id),
    CONSTRAINT fk_logbook_valuation_document FOREIGN KEY (tenant_id, application_id, report_document_id) REFERENCES application_documents(tenant_id, application_id, id)
);

CREATE TABLE logbook_verifications (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, application_id VARCHAR(36) NOT NULL,
    vehicle_id VARCHAR(36) NOT NULL, vehicle_revision BIGINT NOT NULL, recorded_version BIGINT NOT NULL,
    kind VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL, reference_number VARCHAR(255) NOT NULL, document_id VARCHAR(36) NOT NULL,
    valid_from DATE NULL, valid_until DATE NULL, notes VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL, created_by VARCHAR(36) NOT NULL,
    CONSTRAINT uq_logbook_verification_sequence UNIQUE (tenant_id, application_id, recorded_version),
    CONSTRAINT ck_logbook_verification_status CHECK (status IN ('VERIFIED','REJECTED','REVOKED')),
    CONSTRAINT ck_logbook_verification_kind CHECK (kind IN ('OWNERSHIP','INSURANCE','SECURITY_REGISTRATION')),
    CONSTRAINT ck_logbook_coverage CHECK ((kind = 'INSURANCE' AND valid_from IS NOT NULL AND valid_until IS NOT NULL AND valid_until >= valid_from)
        OR (kind <> 'INSURANCE' AND valid_from IS NULL AND valid_until IS NULL)),
    CONSTRAINT fk_logbook_verification_vehicle FOREIGN KEY (tenant_id, application_id, vehicle_id) REFERENCES logbook_vehicles(tenant_id, application_id, id),
    CONSTRAINT fk_logbook_verification_document FOREIGN KEY (tenant_id, application_id, document_id) REFERENCES application_documents(tenant_id, application_id, id)
);

CREATE TABLE logbook_audits (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, application_id VARCHAR(36) NOT NULL,
    action VARCHAR(50) NOT NULL, record_id VARCHAR(36) NOT NULL, before_json JSON NULL, after_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL, created_by VARCHAR(36) NOT NULL,
    CONSTRAINT fk_logbook_audit_application FOREIGN KEY (tenant_id, application_id) REFERENCES loan_request_applications(tenant_id, id)
);
CREATE INDEX idx_logbook_audit_history ON logbook_audits(tenant_id, application_id, created_at);
