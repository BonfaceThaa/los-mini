# Origination profile schema (V36)

V36 adds the unversioned `origination_profiles` table and nullable references:

- `tenants.default_origination_profile_id`
- `loan_product_mappings.origination_profile_id`
- `loan_request_applications.origination_profile_id`
- `loan_request_applications.selected_loan_product_mapping_id`
- `application_variable_definitions.origination_profile_id`

Composite foreign keys prevent references to another tenant's profiles or products. Profile codes are unique per tenant. Profiles start inactive. A null question profile denotes a shared question. Other null references allow existing writers to continue until defaults and backfills are implemented.

This migration creates no profiles, performs no backfill, and changes no service or API behavior. Product/profile compatibility within a tenant must be enforced by the later service implementation; these foreign keys enforce tenant membership, not matching profile membership. Existing application optimistic locking is unchanged.

## MariaDB validation

`OriginationProfileMigrationTest` is opt-in because it needs a real MariaDB server. It creates randomly named test databases and drops only those databases after each test. Use a dedicated disposable server, never the application's database server. No new Maven dependencies are needed.

Example in PowerShell (Docker must be running):

```powershell
$testContainer = 'lm-origination-migration-test'
docker run --detach --rm --name $testContainer -e MARIADB_ROOT_PASSWORD=migration-test-only -p 127.0.0.1::3306 mariadb:11.5.2
# Wait until this command succeeds before running Maven:
docker exec $testContainer mariadb-admin ping -uroot -pmigration-test-only
$testBinding = docker port $testContainer 3306/tcp
$testPort = ($testBinding -split ':')[-1]
$env:MARIADB_MIGRATION_TEST_URL = "jdbc:mariadb://127.0.0.1:$testPort/"
$env:MARIADB_MIGRATION_TEST_PASSWORD = 'migration-test-only'
try {
    .\mvnw.cmd '-Dtest=ProductProfilePersistenceTest,ProductOriginationApiTest,ApplicationVariableScopeApiTest,ApplicationVariableScopePersistenceTest,ApplicationOriginationProfileTest,ApplicationProfilePersistenceTest,OriginationBackfillMigrationTest,OriginationProfileMigrationTest,OriginationProfileApiTest,PhoneOriginationBaselineTest,ApplicationServiceTest,ApplicationVariableServiceTest' test
} finally {
    docker stop $testContainer
    Remove-Item Env:MARIADB_MIGRATION_TEST_URL
    Remove-Item Env:MARIADB_MIGRATION_TEST_PASSWORD
}
```

Coverage includes a fresh V1-V36 migration, V35-to-V36 upgrade with legacy records, old-style inserts after migration, nullable columns, valid tenant-local references, rejected cross-tenant references, duplicate profile codes, JSON validation, deletion protection, and repeat Flyway execution.

The existing H2 application-context test cannot substitute for this test: it already fails on the MariaDB ALTER syntax in V5.

V37 management coverage also validates Hibernate mappings for profiles, audit records and tenant defaults, and confirms stale concurrent profile updates are rejected. Profile CRUD contracts are documented in [the API guide](origination-profile-api.md).

V38 seeding and historical selection matching are documented in [the backfill guide](origination-backfill.md).

ApplicationProfilePersistenceTest validates application profile persistence with real Hibernate repositories and confirms locking profile reads see a concurrent deactivation despite an older MariaDB transaction snapshot.

ApplicationVariableScopePersistenceTest validates shared/profile-specific definition queries, required-answer validation, tenant isolation and immutable answer snapshots on MariaDB. See [questionnaire scopes](application-variable-scopes.md).

ProductProfilePersistenceTest checks profile-filtered product queries, selected mapping persistence and historical selection guards using real Hibernate repositories on MariaDB.
