ALTER TABLE client_records
    ADD COLUMN national_id VARCHAR(100) NULL AFTER fineract_client_id;

CREATE INDEX idx_client_records_tenant_national_id ON client_records (tenant_id, national_id);
