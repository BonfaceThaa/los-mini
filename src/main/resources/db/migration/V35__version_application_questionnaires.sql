ALTER TABLE application_variable_definitions
    ADD COLUMN definition_version INT NOT NULL DEFAULT 1 AFTER display_order,
    ADD COLUMN minimum_selections INT NULL AFTER definition_version,
    ADD COLUMN maximum_selections INT NULL AFTER minimum_selections;

ALTER TABLE application_variables
    ADD COLUMN definition_version INT NOT NULL DEFAULT 1 AFTER definition_id;