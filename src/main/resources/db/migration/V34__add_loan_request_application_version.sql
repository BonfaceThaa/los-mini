ALTER TABLE loan_request_applications
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
