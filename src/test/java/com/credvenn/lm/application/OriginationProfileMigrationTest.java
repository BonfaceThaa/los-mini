package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Runs real Flyway migrations on a disposable MariaDB server; see docs/origination-migration-tests.md. */
@EnabledIfEnvironmentVariable(named = "MARIADB_MIGRATION_TEST_URL", matches = ".+")
class OriginationProfileMigrationTest {
    private static final String USER = "root";

    @Test
    void freshDatabaseMigratesThroughProfiles() throws Exception {
        withDatabase((url, connection) -> {
            migrate(url, "36");
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM origination_profiles"));
            assertEquals(5, scalar(connection, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND is_nullable = 'YES'
                      AND ((table_name = 'tenants' AND column_name = 'default_origination_profile_id')
                        OR (table_name = 'loan_product_mappings' AND column_name = 'origination_profile_id')
                        OR (table_name = 'loan_request_applications' AND column_name IN
                            ('origination_profile_id', 'selected_loan_product_mapping_id'))
                        OR (table_name = 'application_variable_definitions' AND column_name = 'origination_profile_id'))
                    """));
        });
    }

    @Test
    void upgradePreservesLegacyRowsAndEnforcesTenantRelationships() throws Exception {
        withDatabase((url, connection) -> {
            migrate(url, "35");
            legacyRows(connection, "a");
            legacyRows(connection, "b");
            migrate(url, "36");

            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM tenants WHERE default_origination_profile_id IS NULL"));
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM loan_product_mappings WHERE origination_profile_id IS NULL"));
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM application_variable_definitions WHERE origination_profile_id IS NULL"));
            assertEquals(2, scalar(connection, """
                    SELECT COUNT(*) FROM loan_request_applications
                    WHERE origination_profile_id IS NULL AND selected_loan_product_mapping_id IS NULL
                      AND status = 'FINERACT_LOAN_ACTIVATED' AND requested_amount = 20000
                      AND fineract_loan_id = 'existing-loan' AND version = 0
                    """));
            // Old-style inserts still work after migration without any new fields.
            legacyRows(connection, "c");
            profile(connection, "profile-a", "a", "PHONE_FINANCE");
            profile(connection, "profile-b", "b", "PHONE_FINANCE");
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM origination_profiles WHERE active = TRUE"));
            assertConstraint(() -> profile(connection, "duplicate", "a", "PHONE_FINANCE"));
            assertConstraint(() -> profile(connection, "orphan", "missing", "PHONE_FINANCE"));
            assertThrows(SQLException.class, () -> execute(connection,
                    "UPDATE origination_profiles SET requirements_json = 'invalid-json' WHERE tenant_id = 'a'"));

            execute(connection, "UPDATE tenants SET default_origination_profile_id = 'profile-a' WHERE id = 'a'");
            execute(connection, "UPDATE loan_product_mappings SET origination_profile_id = 'profile-a' WHERE tenant_id = 'a'");
            execute(connection, "UPDATE loan_request_applications SET origination_profile_id = 'profile-a', selected_loan_product_mapping_id = 'product-a' WHERE tenant_id = 'a'");
            execute(connection, "UPDATE application_variable_definitions SET origination_profile_id = 'profile-a' WHERE tenant_id = 'a'");

            assertConstraint(() -> execute(connection, "UPDATE tenants SET default_origination_profile_id = 'profile-b' WHERE id = 'a'"));
            assertConstraint(() -> execute(connection, "UPDATE loan_product_mappings SET origination_profile_id = 'profile-b' WHERE tenant_id = 'a'"));
            assertConstraint(() -> execute(connection, "UPDATE loan_request_applications SET origination_profile_id = 'profile-b' WHERE tenant_id = 'a'"));
            assertConstraint(() -> execute(connection, "UPDATE loan_request_applications SET selected_loan_product_mapping_id = 'product-b' WHERE tenant_id = 'a'"));
            assertConstraint(() -> execute(connection, "UPDATE application_variable_definitions SET origination_profile_id = 'profile-b' WHERE tenant_id = 'a'"));
            assertConstraint(() -> execute(connection, "DELETE FROM origination_profiles WHERE tenant_id = 'a' AND id = 'profile-a'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM loan_request_applications WHERE tenant_id = 'a' AND origination_profile_id = 'profile-a' AND selected_loan_product_mapping_id = 'product-a'"));
            // A question may return to shared scope without losing the definition.
            execute(connection, "UPDATE application_variable_definitions SET origination_profile_id = NULL WHERE tenant_id = 'a'");
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM application_variable_definitions WHERE tenant_id = 'a' AND origination_profile_id IS NULL"));
            assertEquals(0, Flyway.configure().dataSource(url, USER, password()).target("36").load().migrate().migrationsExecuted);
        });
    }

    @Test
    void managementMigrationMapsEntitiesAndDetectsConcurrentUpdates() throws Exception {
        withDatabase((url, connection) -> {
            migrate(url, "36");
            legacyRows(connection, "a");
            profile(connection, "profile-a", "a", "PHONE_FINANCE");
            migrate(url, "37");
            assertEquals(0, scalar(connection, "SELECT version FROM origination_profiles WHERE tenant_id = 'a' AND id = 'profile-a'"));
            var configuration = new org.hibernate.cfg.Configuration()
                    .addAnnotatedClass(com.credvenn.lm.origination.OriginationProfile.class)
                    .addAnnotatedClass(com.credvenn.lm.origination.OriginationProfileAudit.class)
                    .addAnnotatedClass(com.credvenn.lm.tenant.Tenant.class)
                    .setProperty("hibernate.connection.url", url)
                    .setProperty("hibernate.connection.username", USER)
                    .setProperty("hibernate.connection.password", password())
                    .setProperty("hibernate.hbm2ddl.auto", "validate");
            try (var factory = configuration.buildSessionFactory()) {
                try (var first = factory.openSession(); var second = factory.openSession()) {
                    var tx1 = first.beginTransaction();
                    var tx2 = second.beginTransaction();
                    var query = "from OriginationProfile where tenantId = :tenant and id = :id";
                    var a = first.createQuery(query, com.credvenn.lm.origination.OriginationProfile.class)
                            .setParameter("tenant", "a").setParameter("id", "profile-a").getSingleResult();
                    var b = second.createQuery(query, com.credvenn.lm.origination.OriginationProfile.class)
                            .setParameter("tenant", "a").setParameter("id", "profile-a").getSingleResult();
                    a.setDisplayName("Updated name");
                    var audit = new com.credvenn.lm.origination.OriginationProfileAudit();
                    audit.setTenantId("a"); audit.setProfileId("profile-a"); audit.setAction("UPDATE");
                    audit.setChangedBy("test"); audit.setBeforeJson("{}"); audit.setAfterJson("{\"name\":\"Updated name\"}");
                    first.persist(audit);
                    tx1.commit();
                    b.setDisplayName("Stale name");
                    assertThrows(jakarta.persistence.OptimisticLockException.class, second::flush);
                    tx2.rollback();
                }
                try (var session = factory.openSession()) {
                    var tx = session.beginTransaction();
                    var tenant = session.createQuery("from Tenant where id = :tenant", com.credvenn.lm.tenant.Tenant.class)
                            .setParameter("tenant", "a").getSingleResult();
                    tenant.setDefaultOriginationProfileId("profile-a");
                    tx.commit();
                }
            }
            assertEquals(1, scalar(connection, "SELECT version FROM origination_profiles WHERE tenant_id = 'a' AND id = 'profile-a'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM origination_profile_audit WHERE tenant_id = 'a' AND profile_id = 'profile-a'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM tenants WHERE id = 'a' AND default_origination_profile_id = 'profile-a'"));
        });
    }
    private static void legacyRows(Connection connection, String tenant) throws SQLException {
        // Test-controlled identifiers only; never populated from application input.
        execute(connection, """
                INSERT INTO tenants (id, code, name, fineract_tenant_id, created_at, updated_at)
                VALUES ('%1$s', '%1$s', 'Test lender', 'default', NOW(), NOW())
                """.formatted(tenant));
        execute(connection, """
                INSERT INTO loan_product_mappings
                  (id, tenant_id, product_code, display_name, short_name, currency_code,
                   principal_min, principal_default_amount, principal_max, number_of_repayments,
                   repayment_every, repayment_frequency, interest_rate_per_period, interest_type,
                   interest_calculation_period_type, interest_rate_frequency, amortization_type,
                   transaction_processing_strategy_code, accounting_template_code, fineract_product_id,
                   created_by, updated_by, created_at, updated_at)
                VALUES ('product-%1$s', '%1$s', 'PHONE', 'Phone', 'PH', 'KES', 1000, 20000, 50000,
                        4, 1, 'MONTHS', 0, 'FLAT', 'SAME_AS_REPAYMENT_PERIOD', 'MONTHS',
                        'EQUAL_INSTALLMENTS', 'mifos-standard-strategy', 'TEST', 7,
                        'test', 'test', NOW(), NOW())
                """.formatted(tenant));
        execute(connection, """
                INSERT INTO loan_request_applications
                  (id, tenant_id, applicant_first_name, applicant_last_name, phone_number, national_id,
                   requested_amount, status, fineract_loan_id, created_at, updated_at)
                VALUES ('application-%1$s', '%1$s', 'Mary', 'Test', '0700000000', '12345678',
                        20000, 'FINERACT_LOAN_ACTIVATED', 'existing-loan', NOW(), NOW())
                """.formatted(tenant));
        execute(connection, """
                INSERT INTO application_variable_definitions
                  (id, tenant_id, code, label, field_type, created_at, updated_at)
                VALUES ('question-%1$s', '%1$s', 'EMPLOYMENT', 'Employment', 'TEXT', NOW(), NOW())
                """.formatted(tenant));
    }

    private static void profile(Connection connection, String id, String tenant, String code) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO origination_profiles
                  (id, tenant_id, code, display_name, requirements_json, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, 'Phone finance', '{}', 'test', 'test', NOW(), NOW())
                """)) {
            statement.setString(1, id);
            statement.setString(2, tenant);
            statement.setString(3, code);
            statement.executeUpdate();
        }
    }

    private static void migrate(String url, String target) {
        Flyway.configure().dataSource(url, USER, password()).locations("classpath:db/migration")
                .target(target).load().migrate();
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static int scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void assertConstraint(SqlAction action) {
        assertEquals("23000", assertThrows(SQLException.class, action::run).getSQLState());
    }

    private static String password() { return System.getenv("MARIADB_MIGRATION_TEST_PASSWORD"); }

    private static void withDatabase(DatabaseAction action) throws Exception {
        String baseUrl = System.getenv("MARIADB_MIGRATION_TEST_URL");
        if (!baseUrl.matches("jdbc:mariadb://(127\\.0\\.0\\.1|localhost):[0-9]+/")) {
            throw new IllegalArgumentException("Use a dedicated local MariaDB server URL ending in / and without a database name");
        }
        String database = "origination_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(baseUrl, USER, password())) {
            execute(admin, "CREATE DATABASE " + database);
            try (Connection connection = DriverManager.getConnection(baseUrl + database, USER, password())) {
                action.run(baseUrl + database, connection);
            } finally {
                execute(admin, "DROP DATABASE " + database);
            }
        }
    }

    @FunctionalInterface private interface SqlAction { void run() throws SQLException; }
    @FunctionalInterface private interface DatabaseAction { void run(String url, Connection connection) throws Exception; }
}
