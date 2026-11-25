ALTER TABLE subscription_plans
    ADD COLUMN statement_allow_negative BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN statement_repeat_policy VARCHAR(40) NOT NULL DEFAULT 'CHARGE_EVERY_ANALYSIS';

ALTER TABLE tenant_subscriptions
    ADD COLUMN statement_allow_negative_override BOOLEAN NULL,
    ADD COLUMN statement_repeat_policy_override VARCHAR(40) NULL;

ALTER TABLE application_statement_analyses
    ADD COLUMN billing_subscription_id VARCHAR(36) NULL,
    ADD COLUMN billing_plan_id VARCHAR(36) NULL,
    ADD COLUMN billing_amount DECIMAL(19,2) NULL,
    ADD COLUMN billing_currency VARCHAR(10) NULL,
    ADD COLUMN billing_allow_negative BOOLEAN NULL,
    ADD COLUMN billing_repeat_policy VARCHAR(40) NULL,
    ADD COLUMN billing_captured_at TIMESTAMP NULL,
    ADD CONSTRAINT fk_statement_billing_subscription FOREIGN KEY (billing_subscription_id) REFERENCES tenant_subscriptions(id),
    ADD CONSTRAINT fk_statement_billing_plan FOREIGN KEY (billing_plan_id) REFERENCES subscription_plans(id);

CREATE INDEX idx_statement_billing_pending ON application_statement_analyses (tenant_id, billing_subscription_id, status);
