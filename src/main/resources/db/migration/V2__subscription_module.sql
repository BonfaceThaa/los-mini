CREATE TABLE subscription_plans (
    id VARCHAR(36) PRIMARY KEY,
    plan_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    max_users INT NOT NULL,
    max_branches INT NOT NULL,
    monthly_application_limit INT NOT NULL,
    approved_application_threshold INT NOT NULL,
    monthly_fee DECIMAL(19,2) NOT NULL,
    interest_share_percentage DECIMAL(10,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE tenant_subscriptions (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    subscription_plan_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    pricing_mode VARCHAR(20) NOT NULL,
    current_period_start TIMESTAMP NOT NULL,
    current_period_end TIMESTAMP NOT NULL,
    switched_to_interest_share_at TIMESTAMP NULL,
    operational_notes VARCHAR(1000),
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_tenant_subscriptions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_tenant_subscriptions_plan FOREIGN KEY (subscription_plan_id) REFERENCES subscription_plans(id)
);

CREATE INDEX idx_subscription_plans_active ON subscription_plans (active);
CREATE INDEX idx_tenant_subscriptions_tenant_id ON tenant_subscriptions (tenant_id);
CREATE INDEX idx_tenant_subscriptions_status ON tenant_subscriptions (status);
