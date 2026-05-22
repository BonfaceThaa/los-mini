CREATE TABLE tenant_device_control_configs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    base_url VARCHAR(500) NOT NULL,
    client_code VARCHAR(100) NOT NULL,
    encrypted_username TEXT NOT NULL,
    encrypted_password TEXT NOT NULL,
    channel_code VARCHAR(100),
    payment_link_template VARCHAR(1000),
    lock_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    unlock_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    nudges_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    offline_pin_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    overdue_lock_cadence_minutes INT NOT NULL DEFAULT 60,
    predue_cadence_minutes INT NOT NULL DEFAULT 1440,
    last_overdue_lock_run_at TIMESTAMP NULL,
    last_predue_run_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_device_control_configs_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_tenant_device_control_configs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE TABLE tenant_device_control_notification_rules (
    id VARCHAR(36) PRIMARY KEY,
    tenant_config_id VARCHAR(36) NOT NULL,
    days_before_due INT NOT NULL,
    notification_code VARCHAR(100) NOT NULL,
    name VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tdcnr_config FOREIGN KEY (tenant_config_id) REFERENCES tenant_device_control_configs(id)
);

CREATE INDEX idx_tdcnr_config_active ON tenant_device_control_notification_rules(tenant_config_id, active);

CREATE TABLE tenant_device_control_nudge_rules (
    id VARCHAR(36) PRIMARY KEY,
    tenant_config_id VARCHAR(36) NOT NULL,
    days_before_due INT NOT NULL,
    nudge_code VARCHAR(100) NOT NULL,
    name VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tdcnur_config FOREIGN KEY (tenant_config_id) REFERENCES tenant_device_control_configs(id)
);

CREATE INDEX idx_tdcnur_config_active ON tenant_device_control_nudge_rules(tenant_config_id, active);

CREATE TABLE loan_device_control_states (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36) NOT NULL,
    fineract_loan_id VARCHAR(100) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    imei1 VARCHAR(100) NOT NULL,
    imei2 VARCHAR(100),
    current_state VARCHAR(50) NOT NULL,
    last_due_date DATE NULL,
    next_due_date DATE NULL,
    has_overdue_installment BOOLEAN NOT NULL DEFAULT FALSE,
    days_overdue BIGINT NULL,
    last_collections_evaluated_at TIMESTAMP NULL,
    last_lock_action_at TIMESTAMP NULL,
    last_unlock_action_at TIMESTAMP NULL,
    last_notification_action_at TIMESTAMP NULL,
    last_nudge_action_at TIMESTAMP NULL,
    last_provider_reference VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_loan_device_control_states_application UNIQUE (application_id),
    CONSTRAINT uq_loan_device_control_states_fineract_loan UNIQUE (tenant_id, fineract_loan_id),
    CONSTRAINT fk_ldcs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_ldcs_application FOREIGN KEY (application_id) REFERENCES loan_request_applications(id),
    CONSTRAINT fk_ldcs_device FOREIGN KEY (device_id) REFERENCES inventory_devices(id)
);

CREATE INDEX idx_ldcs_tenant_state ON loan_device_control_states(tenant_id, current_state);

CREATE TABLE device_control_action_logs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    application_id VARCHAR(36) NOT NULL,
    fineract_loan_id VARCHAR(100) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    imei1 VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    trigger_type VARCHAR(50) NOT NULL,
    rule_id VARCHAR(36) NULL,
    rule_day_offset INT NULL,
    due_date DATE NULL,
    provider_transaction_id VARCHAR(255),
    request_payload LONGTEXT,
    response_payload LONGTEXT,
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(1000),
    requested_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dcal_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_dcal_application FOREIGN KEY (application_id) REFERENCES loan_request_applications(id),
    CONSTRAINT fk_dcal_device FOREIGN KEY (device_id) REFERENCES inventory_devices(id)
);

CREATE INDEX idx_dcal_tenant_action_status ON device_control_action_logs(tenant_id, action_type, status);
CREATE INDEX idx_dcal_rule_dedupe ON device_control_action_logs(rule_id, application_id, due_date, action_type, status);
