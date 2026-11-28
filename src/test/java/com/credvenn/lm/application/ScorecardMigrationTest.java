package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MARIADB_MIGRATION_TEST_URL", matches = ".+")
class ScorecardMigrationTest {
    @Test void upgradesExistingRowsAndInstallsTenantConstraints() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, connection) -> {
            OriginationProfileMigrationTest.migrate(url, "43");
            OriginationProfileMigrationTest.legacyRows(connection, "a");
            OriginationProfileMigrationTest.legacyRows(connection, "b");
            OriginationProfileMigrationTest.migrate(url, "47");
            assertEquals(2, OriginationProfileMigrationTest.scalar(connection, "SELECT COUNT(*) FROM loan_request_applications WHERE tenant_id IN ('a','b') AND scorecard_version_id IS NULL"));
            assertEquals(1, OriginationProfileMigrationTest.scalar(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='application_variable_definitions' AND column_name='scoring_weight'"));
            assertEquals(3, OriginationProfileMigrationTest.scalar(connection, "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name IN ('fk_assessment_tenant_application','fk_assessment_tenant_application_analysis','fk_assessment_tenant_scorecard')"));
            assertEquals(0, OriginationProfileMigrationTest.scalar(connection, "SELECT COUNT(*) FROM scoring_scorecard_versions"));
            OriginationProfileMigrationTest.execute(connection, "INSERT INTO origination_profiles (id,tenant_id,code,display_name,requirements_json,created_by,updated_by,created_at,updated_at) VALUES ('profile-a','a','SCORE_A','A','{}','test','test',NOW(),NOW()),('profile-b','b','SCORE_B','B','{}','test','test',NOW(),NOW())");
            OriginationProfileMigrationTest.execute(connection, "INSERT INTO scoring_scorecard_versions (id,tenant_id,origination_profile_id,version_number,status,draft_json,created_by,updated_by,created_at,updated_at) VALUES ('version-b','b','profile-b',1,'PUBLISHED','{}','test','test',NOW(),NOW())");
            assertThrows(java.sql.SQLException.class, () -> OriginationProfileMigrationTest.execute(connection,
                    "UPDATE loan_request_applications SET origination_profile_id='profile-a', scorecard_version_id='version-b' WHERE tenant_id='a'"));
            try (var query = connection.prepareStatement("""
                    SELECT a.application_id, a.id FROM application_statement_analyses a
                    JOIN loan_request_applications p ON p.tenant_id=a.tenant_id AND p.id=a.application_id
                    JOIN scoring_scorecard_versions v ON v.tenant_id=p.tenant_id AND v.id=p.scorecard_version_id
                    WHERE a.tenant_id=? AND a.provider='INTERNAL_MPESA' AND a.status='MANUAL_REVIEW_REQUIRED'
                    AND p.status='STATEMENT_MANUAL_REVIEW' AND a.raw_provider_response IS NOT NULL
                    AND v.status='PUBLISHED' AND JSON_VALID(v.snapshot_json)=1
                    AND JSON_TYPE(JSON_EXTRACT(v.snapshot_json,'$.configuration.statementPolicy'))='OBJECT'
                    AND NOT EXISTS (SELECT 1 FROM scoring_assessment_snapshots s WHERE s.tenant_id=a.tenant_id AND s.request_key=CONCAT('initial:',a.id))
                    ORDER BY a.created_at ASC LIMIT 25
                    """)) {
                query.setString(1, "a");
                try (var rows = query.executeQuery()) { assertFalse(rows.next()); }
            }
        });
    }
}
