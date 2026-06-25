ALTER TABLE loan_request_applications
    ADD COLUMN applicant_id_type VARCHAR(30) NOT NULL DEFAULT 'NATIONAL_ID' AFTER national_id;
