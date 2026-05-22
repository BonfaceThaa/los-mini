CREATE TABLE tenant_device_control_custom_notification_rules (
    id VARCHAR(36) PRIMARY KEY,
    tenant_config_id VARCHAR(36) NOT NULL,
    days_before_due INT NOT NULL,
    notification_code VARCHAR(100) NOT NULL,
    name VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tdccnr_config FOREIGN KEY (tenant_config_id) REFERENCES tenant_device_control_configs(id)
);

CREATE INDEX idx_tdccnr_config_active ON tenant_device_control_custom_notification_rules(tenant_config_id, active);

CREATE TABLE tenant_device_control_custom_notification_rule_fields (
    id VARCHAR(36) PRIMARY KEY,
    custom_rule_id VARCHAR(36) NOT NULL,
    column_name VARCHAR(100) NOT NULL,
    source_field VARCHAR(50) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tdccnrf_rule FOREIGN KEY (custom_rule_id) REFERENCES tenant_device_control_custom_notification_rules(id)
);

CREATE INDEX idx_tdccnrf_rule_order ON tenant_device_control_custom_notification_rule_fields(custom_rule_id, display_order);
