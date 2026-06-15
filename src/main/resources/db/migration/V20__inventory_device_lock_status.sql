ALTER TABLE inventory_devices
    ADD COLUMN lock_status VARCHAR(50) NOT NULL DEFAULT 'CLEAR' AFTER status;
