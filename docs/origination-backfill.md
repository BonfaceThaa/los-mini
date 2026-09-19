# V38: seed profiles and backfill legacy origination data

## Does every tenant need a phone profile?

No. A default is a compatibility choice for a tenant's existing integration, not a platform-wide loan type. New tenants should explicitly configure the profile appropriate to their business. A future multi-profile UI can require profile selection rather than rely on a default.

V38 applies the known pre-profile phone journey to existing, unclassified applications and product mappings. It does not infer a lending type from product names. Review this assumption before deployment if data was imported from other lending workflows.

## Profile resolution

For each tenant with a null application/product profile:

1. Reuse its default if that profile has one of the two supported phone requirement sets.
2. Otherwise reuse its only compatible phone profile, regardless of its code.
3. If there are no profiles at all, seed active `PHONE_FINANCE` with the canonical requirements.
4. If profiles exist but none are compatible, or several are compatible without a matching default, fail preflight before any backfill writes. Configure the intended profile using the existing CRUD API before retrying. The migration never replaces an existing profile's requirements.

Both canonical and legacy requirement sets are recognised, ignoring array order. Valuation configuration is incompatible. Existing drafts stay inactive; they can classify historical records but will not become the default. Activate the intended phone profile through the API when ready.

Only set a missing default when the chosen profile is active. Preserve existing defaults, including a different profile. Do not seed profiles for empty tenants or automatically seed newly onboarded tenants. Profile creation and default assignment remain available through the existing APIs.

The data writes run in a single transaction; a write failure rolls back profile seeding, defaults, reference changes and audit records together. Application writers must be stopped for deployment to prevent changes between preflight and backfill.

This is a Java Flyway migration in `src/main/java/db/migration`, using the existing `classpath:db/migration` discovery location. Its compatibility rules are frozen independently of future service validators. No additional tables or dependencies are introduced.

## Backfill rules

- Fill only null product and application profile references.
- Prefer an application's already selected local product profile when present; otherwise use the resolved legacy phone profile.
- Leave application variable definitions shared (`origination_profile_id IS NULL`); questions cannot safely be classified as phone-specific from their names. Preserve answer snapshots.
- Preserve all existing local product selections and historical remote IDs, names, amounts, statuses, approval fields and loan IDs.
- Increment the existing application optimistic-lock version when modifying references. Do not add a business policy version.
- Record per-tenant/profile counts and default assignment in `origination_profile_audit`, actor `migration:V38`. Already classified tenants that only need selection matching receive `MIGRATION_MATCH` records when matches are made.

## Safe historical product matching

The database already has `UNIQUE (tenant_id, fineract_product_id)`. Match within the same tenant and same application/product profile. Include inactive products because availability today does not invalidate a historical selection.

Use the selected Fineract product ID when present; use the approved product ID only when the selected ID is absent/blank. If both exist they must agree. Trim surrounding ordinary spaces, then compare the stored text against the product BIGINT rendered as text. Never cast arbitrary application text to a number and never match by display name.

| Historical selected ID | Approved ID | Tenant-local mapping | Result |
| --- | --- | --- | --- |
| `7` | `7` or absent | product 7 | Link local mapping |
| absent | `7` | product 7 | Link using approved fallback |
| `7` | `8` | either exists | Leave unresolved |
| `7junk`, `7.0`, `007` | any | product 7 | Leave unresolved |
| `99` | absent | no product 99 | Leave unresolved |
| `7` | absent | product 7 only in another tenant | Leave unresolved |
| absent | absent | any | No historical selection to reconstruct |

An unresolved link remains null; the migration must not invent history. Product/profile disagreement is also unresolved. Existing explicit local links are preserved, even if historical IDs disagree; review those separately.

## Deployment and reconciliation

1. Review the tenant's profile through `GET /api/v1/origination-profiles` and default through `GET /api/v1/tenant/origination-default`. A pre-existing custom code is supported. Configure an unambiguous compatible profile before rollout.
2. Back up the target database and stop application writers during migration. Run the disposable MariaDB tests first.
3. Deploy V38 through Flyway. It runs once and a subsequent normal Flyway invocation makes no changes. If preflight fails, correct profile configuration on the previous application version and follow Flyway's failed-migration repair procedure after verifying that no V38 data writes occurred.
4. Review the migration audit and unresolved historical selections per tenant. The following is an operator query; bind the tenant ID explicitly:

```sql
SELECT id, origination_profile_id, selected_fineract_product_id,
       approved_fineract_product_id, selected_loan_product_mapping_id
FROM loan_request_applications
WHERE tenant_id = :tenantId
  AND selected_loan_product_mapping_id IS NULL
  AND (NULLIF(TRIM(selected_fineract_product_id), '') IS NOT NULL
    OR NULLIF(TRIM(approved_fineract_product_id), '') IS NOT NULL);

SELECT profile_id, action, before_json, after_json
FROM origination_profile_audit
WHERE tenant_id = :tenantId AND changed_by = 'migration:V38'
ORDER BY created_at;
```

This change is a one-time data migration. Application creation now resolves and saves its profile reference. Product creation still omits the product profile reference and needs its later service integration. Records created by older application instances after V38, and products created before product integration, may still need a follow-up catch-up backfill before these fields become mandatory. Requirements JSON still does not execute the application workflow.

## Tests

Use the disposable MariaDB instructions in [the migration test guide](origination-migration-tests.md), including `OriginationBackfillMigrationTest` in the Maven test list. No application database is used. Coverage includes custom profiles, inactive drafts, seeded profiles, default preservation, preflight failures, exact product matching and repeat Flyway execution.
