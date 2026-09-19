package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MARIADB_MIGRATION_TEST_URL", matches = ".+")
class OriginationBackfillMigrationTest {
    private static final String PHONE = """
        {"OFFER_SELECTION":["KYC_APPROVED","CLIENT_PROVISIONED","STATEMENT_ACCEPTED"],
         "INTERNAL_APPROVAL":["CONSENT_CAPTURED","CLIENT_PROVISIONED","DEVICE_ASSIGNED","FINANCING_CALCULATED","ACTIVE_PRODUCT_SELECTED"],
         "DISBURSEMENT":["PENDING_LOAN_CREATED","DEVICE_ASSIGNED","DEPOSIT_MATCHED"]}
        """;

    @Test void freshDatabaseAndEmptyTenantNeedNoPhoneDefault() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, c) -> {
            OriginationProfileMigrationTest.migrate(url, "37");
            sql(c, "INSERT INTO tenants (id,code,name,fineract_tenant_id,created_at,updated_at) VALUES ('empty','empty','Empty','default',NOW(),NOW())");
            OriginationProfileMigrationTest.migrate(url, "38");
            assertEquals(0, count(c, "SELECT COUNT(*) FROM origination_profiles"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM tenants WHERE default_origination_profile_id IS NULL"));
        });
    }

    @Test void seedsOnlyLegacyTenantsAndMatchesHistoricalIdsConservatively() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, c) -> {
            OriginationProfileMigrationTest.migrate(url, "37");
            String[] cases = {"selected", "approved", "agree", "conflict", "malformed", "decimal", "zero", "missing", "none", "whitespace", "inactive", "foreign", "huge"};
            for (String name : cases) OriginationProfileMigrationTest.legacyRows(c, name);
            for (String name : cases) setIds(c, name, "7", null);
            setIds(c, "approved", null, "7");
            setIds(c, "agree", "7", "7");
            setIds(c, "conflict", "7", "8");
            setIds(c, "malformed", "7junk", "7");
            setIds(c, "decimal", "7.0", null);
            setIds(c, "zero", "007", null);
            setIds(c, "missing", "99", null);
            setIds(c, "none", null, null);
            setIds(c, "whitespace", " 7 ", "7");
            setIds(c, "huge", "999999999999999999999999999999999", null);
            sql(c, "UPDATE loan_product_mappings SET active=FALSE WHERE tenant_id='inactive'");
            sql(c, "UPDATE loan_product_mappings SET fineract_product_id=8 WHERE tenant_id='foreign'");
            OriginationProfileMigrationTest.migrate(url, "38");
            assertEquals(cases.length, count(c, "SELECT COUNT(*) FROM origination_profiles WHERE code='PHONE_FINANCE' AND active=TRUE"));
            assertEquals(cases.length, count(c, "SELECT COUNT(*) FROM tenants WHERE default_origination_profile_id IS NOT NULL"));
            assertEquals(cases.length, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE origination_profile_id IS NOT NULL AND status='FINERACT_LOAN_ACTIVATED' AND fineract_loan_id='existing-loan' AND requested_amount=20000"));
            assertEquals(cases.length, count(c, "SELECT COUNT(*) FROM application_variable_definitions WHERE origination_profile_id IS NULL"));
            for (String name : new String[]{"selected", "approved", "agree", "whitespace", "inactive"}) {
                assertEquals(1, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE tenant_id='" + name + "' AND selected_loan_product_mapping_id='product-" + name + "' AND version=2"), name);
            }
            assertEquals(5, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE selected_loan_product_mapping_id IS NOT NULL"));
            assertEquals(cases.length, count(c, "SELECT COUNT(*) FROM origination_profile_audit WHERE action='MIGRATION_BACKFILL'"));
            OriginationProfileMigrationTest.migrate(url, "38");
            assertEquals(cases.length, count(c, "SELECT COUNT(*) FROM origination_profile_audit"));
        });
    }

    @Test void reusesCustomProfileAndPreservesDraftsDefaultsAndExplicitReferences() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, c) -> {
            OriginationProfileMigrationTest.migrate(url, "37");
            for (String tenant : new String[]{"custom", "draft", "defaulted", "explicit"}) OriginationProfileMigrationTest.legacyRows(c, tenant);
            phone(c, "custom", "existing-custom", true);
            phone(c, "draft", "existing-draft", false);
            phone(c, "defaulted", "first", true);
            phone(c, "defaulted", "second", true);
            sql(c, "UPDATE tenants SET default_origination_profile_id='second' WHERE id='defaulted'");
            phone(c, "explicit", "phone", true);
            sql(c, "INSERT INTO origination_profiles (id,tenant_id,code,display_name,requirements_json,created_by,updated_by,created_at,updated_at) VALUES ('other','explicit','OTHER','Other','{}','test','test',NOW(),NOW())");
            sql(c, "UPDATE tenants SET default_origination_profile_id='other' WHERE id='explicit'");
            sql(c, "UPDATE loan_product_mappings SET origination_profile_id='other' WHERE tenant_id='explicit'");
            sql(c, "UPDATE loan_request_applications SET selected_loan_product_mapping_id='product-explicit' WHERE tenant_id='explicit'");
            OriginationProfileMigrationTest.migrate(url, "38");
            assertEquals(6, count(c, "SELECT COUNT(*) FROM origination_profiles"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM tenants WHERE id='custom' AND default_origination_profile_id='existing-custom'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM tenants WHERE id='draft' AND default_origination_profile_id IS NULL"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE tenant_id='draft' AND origination_profile_id='existing-draft'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM origination_profiles WHERE tenant_id='draft' AND active=FALSE AND version=0"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM loan_product_mappings WHERE tenant_id='defaulted' AND origination_profile_id='second'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM tenants WHERE id='explicit' AND default_origination_profile_id='other'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE tenant_id='explicit' AND origination_profile_id='other' AND selected_loan_product_mapping_id='product-explicit'"));
        });
    }

    @Test void ambiguousOrUnsupportedProfilesFailBeforeAnyBackfillWrites() throws Exception {
        for (boolean ambiguous : new boolean[]{true, false}) OriginationProfileMigrationTest.withDatabase((url, c) -> {
            OriginationProfileMigrationTest.migrate(url, "37");
            OriginationProfileMigrationTest.legacyRows(c, "a-seed");
            OriginationProfileMigrationTest.legacyRows(c, "z-blocked");
            if (ambiguous) {
                phone(c, "z-blocked", "one", true); phone(c, "z-blocked", "two", true);
            } else sql(c, "INSERT INTO origination_profiles (id,tenant_id,code,display_name,requirements_json,created_by,updated_by,created_at,updated_at) VALUES ('unsupported','z-blocked','LOGBOOK','Logbook','{}','test','test',NOW(),NOW())");
            assertThrows(org.flywaydb.core.api.FlywayException.class, () -> OriginationProfileMigrationTest.migrate(url, "38"));
            assertEquals(0, count(c, "SELECT COUNT(*) FROM origination_profiles WHERE tenant_id='a-seed'"));
            assertEquals(0, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE origination_profile_id IS NOT NULL"));
            assertEquals(0, count(c, "SELECT COUNT(*) FROM origination_profile_audit"));
        });
    }

    @Test void matchesAlreadyClassifiedApplicationsButPreservesProfileMismatchesAndExplicitLinks() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, c) -> {
            OriginationProfileMigrationTest.migrate(url, "37");
            for (String tenant : new String[]{"classified", "mismatch", "preserved", "legacy"}) {
                OriginationProfileMigrationTest.legacyRows(c, tenant);
                phone(c, tenant, "profile-" + tenant, true);
                setIds(c, tenant, "7", null);
            }
            sql(c, "UPDATE loan_product_mappings SET origination_profile_id=CONCAT('profile-',tenant_id) WHERE tenant_id IN ('classified','mismatch','preserved')");
            sql(c, "UPDATE loan_request_applications SET origination_profile_id=CONCAT('profile-',tenant_id) WHERE tenant_id IN ('classified','mismatch','preserved')");
            phone(c, "mismatch", "different", true);
            sql(c, "UPDATE loan_product_mappings SET origination_profile_id='different' WHERE tenant_id='mismatch'");
            sql(c, "UPDATE loan_request_applications SET selected_loan_product_mapping_id='product-preserved', selected_fineract_product_id='99' WHERE tenant_id='preserved'");
            sql(c, """
                    UPDATE origination_profiles SET requirements_json=
                    '{"DISBURSEMENT":["DEPOSIT_MATCHED","DEVICE_ASSIGNED","INTERNAL_APPROVAL_VALID"],
                    "INTERNAL_APPROVAL":["FINANCING_ASSESSED","DEVICE_ASSIGNED","CONSENT_CAPTURED"],
                    "OFFER_SELECTION":["STATEMENT_ACCEPTED","CLIENT_PROVISIONED","KYC_APPROVED"]}'
                    WHERE tenant_id='legacy'
                    """);
            OriginationProfileMigrationTest.migrate(url, "38");
            assertEquals(1, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE tenant_id='classified' AND selected_loan_product_mapping_id='product-classified' AND version=1"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM origination_profile_audit WHERE tenant_id='classified' AND action='MIGRATION_MATCH'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE tenant_id='mismatch' AND selected_loan_product_mapping_id IS NULL AND version=0"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE tenant_id='preserved' AND selected_loan_product_mapping_id='product-preserved' AND selected_fineract_product_id='99' AND version=0"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE tenant_id='legacy' AND origination_profile_id='profile-legacy' AND selected_loan_product_mapping_id='product-legacy'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM origination_profiles WHERE tenant_id='legacy' AND requirements_json LIKE '%FINANCING_ASSESSED%' AND version=0"));
        });
    }

    @Test void rollsBackDataIfAWriteFailsAfterPreflight() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, c) -> {
            OriginationProfileMigrationTest.migrate(url, "37");
            OriginationProfileMigrationTest.legacyRows(c, "rollback");
            setIds(c, "rollback", "7", null);
            sql(c, "CREATE TRIGGER reject_migration_audit BEFORE INSERT ON origination_profile_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected test failure'");
            assertThrows(org.flywaydb.core.api.FlywayException.class, () -> OriginationProfileMigrationTest.migrate(url, "38"));
            assertEquals(0, count(c, "SELECT COUNT(*) FROM origination_profiles"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM tenants WHERE id='rollback' AND default_origination_profile_id IS NULL"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM loan_product_mappings WHERE tenant_id='rollback' AND origination_profile_id IS NULL"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM loan_request_applications WHERE tenant_id='rollback' AND origination_profile_id IS NULL AND selected_loan_product_mapping_id IS NULL AND version=0"));
        });
    }

    private static void phone(Connection c, String tenant, String id, boolean active) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO origination_profiles (id,tenant_id,code,display_name,active,requirements_json,created_by,updated_by,created_at,updated_at) VALUES (?,?,?,'Custom phone',?,?,'test','test',NOW(),NOW())")) {
            s.setString(1, id); s.setString(2, tenant); s.setString(3, id.replace('-', '_').toUpperCase()); s.setBoolean(4, active); s.setString(5, PHONE); s.executeUpdate();
        }
    }
    private static void setIds(Connection c, String tenant, String selected, String approved) throws SQLException {
        try (var s = c.prepareStatement("UPDATE loan_request_applications SET selected_fineract_product_id=?, approved_fineract_product_id=? WHERE tenant_id=?")) {
            s.setString(1, selected); s.setString(2, approved); s.setString(3, tenant); s.executeUpdate();
        }
    }
    private static void sql(Connection c, String sql) throws SQLException { OriginationProfileMigrationTest.execute(c, sql); }
    private static int count(Connection c, String sql) throws SQLException { return OriginationProfileMigrationTest.scalar(c, sql); }
}
