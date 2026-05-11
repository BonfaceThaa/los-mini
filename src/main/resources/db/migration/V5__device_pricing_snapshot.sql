ALTER TABLE inventory_devices
    ADD COLUMN imei1 VARCHAR(100) NULL,
    ADD COLUMN imei2 VARCHAR(100) NULL,
    ADD COLUMN cash_price DECIMAL(19,2) NULL,
    ADD COLUMN deposit_type VARCHAR(30) NULL,
    ADD COLUMN deposit_value DECIMAL(19,2) NULL;

ALTER TABLE loan_request_applications
    ADD COLUMN assigned_device_id VARCHAR(36) NULL,
    ADD COLUMN assigned_device_name VARCHAR(255) NULL,
    ADD COLUMN assigned_device_imei1 VARCHAR(100) NULL,
    ADD COLUMN assigned_device_imei2 VARCHAR(100) NULL,
    ADD COLUMN assigned_device_cash_price DECIMAL(19,2) NULL,
    ADD COLUMN deposit_type VARCHAR(30) NULL,
    ADD COLUMN deposit_value DECIMAL(19,2) NULL,
    ADD COLUMN deposit_amount DECIMAL(19,2) NULL,
    ADD COLUMN installment_amount DECIMAL(19,2) NULL,
    ADD COLUMN total_repayments DECIMAL(19,2) NULL,
    ADD COLUMN total_payment DECIMAL(19,2) NULL,
    ADD COLUMN margin_amount DECIMAL(19,2) NULL;
