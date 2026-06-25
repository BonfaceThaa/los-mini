ALTER TABLE loan_request_applications
    ADD COLUMN applicant_middle_name VARCHAR(255) NULL AFTER applicant_first_name,
    ADD COLUMN dob DATE NULL AFTER applicant_id_type,
    ADD COLUMN gender VARCHAR(50) NULL AFTER dob,
    ADD COLUMN statement_otp VARCHAR(100) NULL AFTER gender;
