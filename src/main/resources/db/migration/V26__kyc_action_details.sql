ALTER TABLE application_kyc_checks
    ADD COLUMN action_names VARCHAR(100) NULL AFTER summary,
    ADD COLUMN action_first_name VARCHAR(100) NULL AFTER action_names,
    ADD COLUMN action_last_name VARCHAR(100) NULL AFTER action_first_name,
    ADD COLUMN action_other_names VARCHAR(100) NULL AFTER action_last_name,
    ADD COLUMN action_dob VARCHAR(100) NULL AFTER action_other_names,
    ADD COLUMN action_gender VARCHAR(100) NULL AFTER action_dob,
    ADD COLUMN action_phone_number VARCHAR(100) NULL AFTER action_gender,
    ADD COLUMN action_verify_id_number VARCHAR(100) NULL AFTER action_phone_number,
    ADD COLUMN action_id_verification VARCHAR(100) NULL AFTER action_verify_id_number;
