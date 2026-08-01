CREATE TABLE application_statement_reviews (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36) NOT NULL,
    statement_analysis_id VARCHAR(36) NULL,
    decision VARCHAR(50) NOT NULL,
    decision_source VARCHAR(20) NOT NULL,
    reason VARCHAR(1000) NULL,
    reviewed_by VARCHAR(100) NOT NULL,
    reviewed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_statement_reviews_analysis
        FOREIGN KEY (statement_analysis_id) REFERENCES application_statement_analyses(id)
);

CREATE INDEX idx_statement_reviews_application_created
    ON application_statement_reviews (application_id, created_at);

CREATE INDEX idx_statement_reviews_analysis_created
    ON application_statement_reviews (statement_analysis_id, created_at);

INSERT INTO application_statement_reviews (
    id,
    tenant_id,
    application_id,
    statement_analysis_id,
    decision,
    decision_source,
    reason,
    reviewed_by,
    reviewed_at,
    created_at,
    updated_at
)
SELECT
    UUID(),
    analysis.tenant_id,
    analysis.application_id,
    NULL,
    'APPROVED',
    'USER',
    analysis.summary,
    'legacy-manual-override',
    analysis.created_at,
    analysis.created_at,
    analysis.updated_at
FROM application_statement_analyses analysis
WHERE analysis.provider = 'MANUAL_OVERRIDE';
