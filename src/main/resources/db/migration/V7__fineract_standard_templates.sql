CREATE TABLE gl_account_templates (
    id VARCHAR(36) PRIMARY KEY,
    template_code VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    business_purpose VARCHAR(100) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    management_mode VARCHAR(50) NOT NULL,
    fineract_gl_account_id BIGINT NOT NULL UNIQUE,
    fineract_gl_code VARCHAR(100) NOT NULL,
    account_category VARCHAR(50) NOT NULL,
    usage_type VARCHAR(50) NOT NULL,
    manual_entries_allowed BOOLEAN NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE accounting_rule_templates (
    id VARCHAR(36) PRIMARY KEY,
    template_code VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    business_event VARCHAR(100) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    management_mode VARCHAR(50) NOT NULL,
    fineract_rule_id BIGINT NOT NULL UNIQUE,
    fineract_rule_name VARCHAR(255) NOT NULL,
    debit_gl_account_template_code VARCHAR(100) NOT NULL,
    credit_gl_account_template_code VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_accounting_rule_templates_debit_template
        FOREIGN KEY (debit_gl_account_template_code) REFERENCES gl_account_templates(template_code),
    CONSTRAINT fk_accounting_rule_templates_credit_template
        FOREIGN KEY (credit_gl_account_template_code) REFERENCES gl_account_templates(template_code)
);

CREATE INDEX idx_gl_account_templates_purpose ON gl_account_templates (business_purpose);
CREATE INDEX idx_accounting_rule_templates_event ON accounting_rule_templates (business_event);
