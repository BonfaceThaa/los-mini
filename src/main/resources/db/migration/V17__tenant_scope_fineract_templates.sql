ALTER TABLE gl_account_templates
    ADD COLUMN tenant_id VARCHAR(36) NULL;

ALTER TABLE gl_account_templates
    ADD CONSTRAINT fk_gl_account_templates_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE INDEX idx_gl_account_templates_tenant_id ON gl_account_templates (tenant_id);

ALTER TABLE accounting_rule_templates
    ADD COLUMN tenant_id VARCHAR(36) NULL;

ALTER TABLE accounting_rule_templates
    ADD CONSTRAINT fk_accounting_rule_templates_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE INDEX idx_accounting_rule_templates_tenant_id ON accounting_rule_templates (tenant_id);
