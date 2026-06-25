ALTER TABLE tenants
    ADD COLUMN statement_analysis_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO' AFTER kyc_mode;
