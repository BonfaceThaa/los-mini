ALTER TABLE application_statement_analyses
    ADD COLUMN provider_status VARCHAR(100) NULL AFTER provider,
    ADD COLUMN external_client_id VARCHAR(100) NULL AFTER source_document_id,
    ADD COLUMN external_document_id VARCHAR(100) NULL AFTER external_client_id,
    ADD COLUMN external_business_id VARCHAR(100) NULL AFTER external_document_id,
    ADD COLUMN credit_score INT NULL AFTER affordability_score,
    ADD COLUMN risk_tier VARCHAR(100) NULL AFTER credit_score,
    ADD COLUMN raw_provider_response TEXT NULL AFTER recommendation;

CREATE TABLE cladfy_statement_transactions (
    id VARCHAR(36) PRIMARY KEY,
    statement_analysis_id VARCHAR(36) NOT NULL,
    external_transaction_id VARCHAR(100),
    transaction_type VARCHAR(50),
    transaction_amount DECIMAL(19,2),
    transaction_date VARCHAR(100),
    narration VARCHAR(1000),
    balance DECIMAL(19,2),
    currency VARCHAR(20),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_cladfy_transactions_analysis FOREIGN KEY (statement_analysis_id) REFERENCES application_statement_analyses(id)
);

CREATE INDEX idx_statement_analysis_external_ids ON application_statement_analyses (external_client_id, external_document_id);
CREATE INDEX idx_cladfy_transactions_analysis ON cladfy_statement_transactions (statement_analysis_id);
