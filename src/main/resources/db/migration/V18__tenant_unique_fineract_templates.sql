ALTER TABLE accounting_rule_templates
    DROP FOREIGN KEY fk_accounting_rule_templates_debit_template;

ALTER TABLE accounting_rule_templates
    DROP FOREIGN KEY fk_accounting_rule_templates_credit_template;

ALTER TABLE gl_account_templates
    DROP INDEX template_code;

ALTER TABLE gl_account_templates
    DROP INDEX fineract_gl_account_id;

ALTER TABLE accounting_rule_templates
    DROP INDEX template_code;

ALTER TABLE accounting_rule_templates
    DROP INDEX fineract_rule_id;

CREATE UNIQUE INDEX uq_gl_account_templates_tenant_template_code
    ON gl_account_templates (tenant_id, template_code);

CREATE UNIQUE INDEX uq_gl_account_templates_tenant_fineract_gl_account_id
    ON gl_account_templates (tenant_id, fineract_gl_account_id);

CREATE UNIQUE INDEX uq_accounting_rule_templates_tenant_template_code
    ON accounting_rule_templates (tenant_id, template_code);

CREATE UNIQUE INDEX uq_accounting_rule_templates_tenant_fineract_rule_id
    ON accounting_rule_templates (tenant_id, fineract_rule_id);
