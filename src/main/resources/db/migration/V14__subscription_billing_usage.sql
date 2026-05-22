ALTER TABLE subscription_plans
    ADD COLUMN kyc_success_cost DECIMAL(19,2) NOT NULL DEFAULT 0.00 AFTER interest_share_percentage,
    ADD COLUMN statement_success_cost DECIMAL(19,2) NOT NULL DEFAULT 0.00 AFTER kyc_success_cost;

ALTER TABLE tenant_subscriptions
    ADD COLUMN prepaid_balance DECIMAL(19,2) NOT NULL DEFAULT 0.00 AFTER operational_notes,
    ADD COLUMN total_credited DECIMAL(19,2) NOT NULL DEFAULT 0.00 AFTER prepaid_balance,
    ADD COLUMN total_debited DECIMAL(19,2) NOT NULL DEFAULT 0.00 AFTER total_credited,
    ADD COLUMN next_pricing_mode VARCHAR(20) NULL AFTER pricing_mode,
    ADD COLUMN next_pricing_mode_effective_at TIMESTAMP NULL AFTER switched_to_interest_share_at;

CREATE TABLE tenant_subscription_ledger (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    subscription_id VARCHAR(36) NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    charge_type VARCHAR(50) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(36) NULL,
    balance_before DECIMAL(19,2) NOT NULL,
    balance_after DECIMAL(19,2) NOT NULL,
    notes VARCHAR(1000),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_tenant_subscription_ledger_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_tenant_subscription_ledger_subscription FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions(id),
    CONSTRAINT uq_tenant_subscription_ledger_charge UNIQUE (subscription_id, entry_type, charge_type, reference_id)
);

CREATE INDEX idx_tenant_subscription_ledger_subscription_created
    ON tenant_subscription_ledger (subscription_id, created_at);
