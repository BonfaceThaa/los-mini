package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Frozen V38 rules: deliberately independent of evolving application validators. */
public class V38__seed_and_backfill_origination_profiles extends BaseJavaMigration {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ACTOR = "migration:V38";
    private static final String PHONE = """
        {"OFFER_SELECTION":["KYC_APPROVED","CLIENT_PROVISIONED","STATEMENT_ACCEPTED"],
         "INTERNAL_APPROVAL":["CONSENT_CAPTURED","CLIENT_PROVISIONED","DEVICE_ASSIGNED","FINANCING_CALCULATED","ACTIVE_PRODUCT_SELECTED"],
         "DISBURSEMENT":["PENDING_LOAN_CREATED","DEVICE_ASSIGNED","DEPOSIT_MATCHED"]}
        """;
    private static final String LEGACY = """
        {"OFFER_SELECTION":["KYC_APPROVED","CLIENT_PROVISIONED","STATEMENT_ACCEPTED"],
         "INTERNAL_APPROVAL":["CONSENT_CAPTURED","DEVICE_ASSIGNED","FINANCING_ASSESSED"],
         "DISBURSEMENT":["INTERNAL_APPROVAL_VALID","DEVICE_ASSIGNED","DEPOSIT_MATCHED"]}
        """;

    @Override public Integer getChecksum() { return 38001; }

    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean ownTransaction = connection.getAutoCommit();
        if (ownTransaction) connection.setAutoCommit(false);
        try {
            backfill(connection);
            if (ownTransaction) connection.commit();
        } catch (Exception failure) {
            if (ownTransaction) connection.rollback();
            throw failure;
        } finally {
            if (ownTransaction) connection.setAutoCommit(true);
        }
    }

    private void backfill(Connection connection) throws Exception {
        // Resolve every tenant before any write. Never choose arbitrarily between profiles.
        List<Plan> plans = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT t.id, t.default_origination_profile_id FROM tenants t
                WHERE EXISTS (SELECT 1 FROM loan_request_applications a WHERE a.tenant_id = t.id AND a.origination_profile_id IS NULL)
                   OR EXISTS (SELECT 1 FROM loan_product_mappings p WHERE p.tenant_id = t.id AND p.origination_profile_id IS NULL)
                ORDER BY t.id
                """)) {
            while (rows.next()) plans.add(resolve(connection, rows.getString(1), rows.getString(2)));
        }
        for (Plan plan : plans) apply(connection, plan);
        // Also cover tenants whose profiles were already populated before V38.
        List<String[]> scopes = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT DISTINCT tenant_id, origination_profile_id FROM loan_request_applications
                WHERE origination_profile_id IS NOT NULL AND selected_loan_product_mapping_id IS NULL
                """)) {
            while (rows.next()) scopes.add(new String[]{rows.getString(1), rows.getString(2)});
        }
        for (var scope : scopes) {
            int matched = matchSelections(connection, scope[0], scope[1]);
            if (matched > 0) update(connection, """
                    INSERT INTO origination_profile_audit
                      (id, tenant_id, profile_id, action, changed_by, after_json, created_at)
                    VALUES (?, ?, ?, 'MIGRATION_MATCH', ?, ?, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID().toString(), scope[0], scope[1], ACTOR,
                    JSON.writeValueAsString(Map.of("selectionsMatched", matched)));
        }
    }

    private Plan resolve(Connection connection, String tenant, String defaultId) throws Exception {
        List<Profile> all = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT id, active, requirements_json, configuration_json FROM origination_profiles WHERE tenant_id = ?")) {
            statement.setString(1, tenant);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) all.add(new Profile(rows.getString(1), rows.getBoolean(2),
                        compatible(rows.getString(3), rows.getString(4))));
            }
        }
        if (all.isEmpty()) return new Plan(tenant, UUID.randomUUID().toString(), true, true, defaultId);
        var candidates = all.stream().filter(Profile::compatible).toList();
        var preferred = candidates.stream().filter(p -> p.id().equals(defaultId)).findFirst();
        Profile chosen;
        if (preferred.isPresent()) chosen = preferred.get();
        else if (candidates.size() == 1) chosen = candidates.getFirst();
        else throw new SQLException("V38 cannot resolve legacy phone profile for tenant " + tenant
                + "; configure one compatible phone profile or select it as the tenant default before retrying. No backfill writes have started.");
        return new Plan(tenant, chosen.id(), false, chosen.active(), defaultId);
    }

    private boolean compatible(String requirements, String configuration) throws Exception {
        JsonNode config = configuration == null ? null : JSON.readTree(configuration);
        if (config != null && !config.isNull()) {
            if (!config.isObject()) return false;
            var values = config.elements();
            while (values.hasNext()) if (!values.next().isNull()) return false;
        }
        JsonNode actual = JSON.readTree(requirements);
        return sameRequirements(actual, JSON.readTree(PHONE)) || sameRequirements(actual, JSON.readTree(LEGACY));
    }

    private boolean sameRequirements(JsonNode actual, JsonNode expected) {
        if (actual == null || !actual.isObject() || actual.size() != expected.size()) return false;
        var stages = expected.fieldNames();
        while (stages.hasNext()) {
            String stage = stages.next();
            JsonNode values = actual.get(stage);
            if (values == null || !values.isArray() || values.size() != expected.get(stage).size()) return false;
            Set<JsonNode> actualSet = new HashSet<>();
            values.forEach(actualSet::add);
            Set<JsonNode> expectedSet = new HashSet<>();
            expected.get(stage).forEach(expectedSet::add);
            if (!actualSet.equals(expectedSet)) return false;
        }
        return true;
    }

    private void apply(Connection connection, Plan plan) throws Exception {
        if (plan.seed()) update(connection, """
                INSERT INTO origination_profiles
                  (id, tenant_id, code, display_name, description, active, requirements_json,
                   created_by, updated_by, created_at, updated_at, version)
                VALUES (?, ?, 'PHONE_FINANCE', 'Phone financing', 'Compatibility profile for the pre-profile phone journey',
                        TRUE, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, plan.profile(), plan.tenant(), PHONE, ACTOR, ACTOR);
        int defaults = 0;
        if (plan.active() && plan.defaultId() == null) defaults = update(connection, """
                UPDATE tenants SET default_origination_profile_id = ?
                WHERE id = ? AND default_origination_profile_id IS NULL
                """, plan.profile(), plan.tenant());
        int products = update(connection, """
                UPDATE loan_product_mappings SET origination_profile_id = ?
                WHERE tenant_id = ? AND origination_profile_id IS NULL
                """, plan.profile(), plan.tenant());
        // Honour a pre-existing explicit local selection before using the legacy tenant profile.
        int applications = update(connection, """
                UPDATE loan_request_applications a
                LEFT JOIN loan_product_mappings p ON p.tenant_id = a.tenant_id AND p.id = a.selected_loan_product_mapping_id
                SET a.origination_profile_id = COALESCE(p.origination_profile_id, ?), a.version = a.version + 1
                WHERE a.tenant_id = ? AND a.origination_profile_id IS NULL
                """, plan.profile(), plan.tenant());
        int selections = matchSelections(connection, plan.tenant(), plan.profile());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profileId", plan.profile()); result.put("seeded", plan.seed());
        result.put("defaultAssigned", defaults == 1); result.put("productsBackfilled", products);
        result.put("applicationsBackfilled", applications); result.put("selectionsMatched", selections);
        update(connection, """
                INSERT INTO origination_profile_audit
                  (id, tenant_id, profile_id, action, changed_by, before_json, after_json, created_at)
                VALUES (?, ?, ?, 'MIGRATION_BACKFILL', ?, ?, ?, CURRENT_TIMESTAMP)
                """, UUID.randomUUID().toString(), plan.tenant(), plan.profile(), ACTOR,
                JSON.writeValueAsString(Collections.singletonMap("defaultProfileId", plan.defaultId())), JSON.writeValueAsString(result));
    }

    private int matchSelections(Connection connection, String tenant, String profile) throws SQLException {
        // Compare text to text: MariaDB numeric coercion could wrongly match '7junk' or '7.0' to 7.
        // Approved ID is only a fallback when no selection was recorded; disagreement is unresolved.
        return update(connection, """
                UPDATE loan_request_applications a
                JOIN loan_product_mappings p ON p.tenant_id = a.tenant_id
                  AND BINARY CAST(p.fineract_product_id AS CHAR) = BINARY COALESCE(
                    NULLIF(TRIM(a.selected_fineract_product_id), ''), NULLIF(TRIM(a.approved_fineract_product_id), ''))
                  AND p.origination_profile_id = a.origination_profile_id
                SET a.selected_loan_product_mapping_id = p.id, a.version = a.version + 1
                WHERE a.tenant_id = ? AND a.origination_profile_id = ? AND a.selected_loan_product_mapping_id IS NULL
                  AND (NULLIF(TRIM(a.selected_fineract_product_id), '') IS NULL
                    OR NULLIF(TRIM(a.approved_fineract_product_id), '') IS NULL
                    OR BINARY TRIM(a.selected_fineract_product_id) = BINARY TRIM(a.approved_fineract_product_id))
                """, tenant, profile);
    }

    private int update(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement.executeUpdate();
        }
    }

    private record Profile(String id, boolean active, boolean compatible) {}
    private record Plan(String tenant, String profile, boolean seed, boolean active, String defaultId) {}
}
