ALTER TABLE application_statement_analyses
    ADD COLUMN next_status_check_at TIMESTAMP NULL AFTER raw_provider_response,
    ADD COLUMN last_status_check_at TIMESTAMP NULL AFTER next_status_check_at,
    ADD COLUMN status_check_attempts INT NOT NULL DEFAULT 0 AFTER last_status_check_at,
    ADD COLUMN completion_source VARCHAR(50) NULL AFTER status_check_attempts,
    ADD COLUMN completed_at TIMESTAMP NULL AFTER completion_source;

CREATE INDEX idx_statement_analysis_cladfy_due
    ON application_statement_analyses (provider, status, next_status_check_at);
