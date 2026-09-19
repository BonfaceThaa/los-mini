# Origination profile management

Profiles belong to the authenticated tenant. Clients never supply a tenant ID. Platform principals without a tenant cannot use these endpoints. Tenant administrators receive the new permissions through the existing permission/role bootstrap; refresh authentication after role changes to receive updated JWT authorities.

## Endpoints

| Method | Path | Permission |
| --- | --- | --- |
| POST | `/api/v1/origination-profiles` | `ORIGINATION_PROFILE_CREATE` |
| GET | `/api/v1/origination-profiles?active=true` | `ORIGINATION_PROFILE_VIEW`, `LOAN_VIEW` or `LOAN_CREATE` |
| GET | `/api/v1/origination-profiles/{code}` | Same read permissions |
| PATCH | `/api/v1/origination-profiles/{code}` | `ORIGINATION_PROFILE_UPDATE` |
| DELETE | `/api/v1/origination-profiles/{code}?expectedVersion=0` | `ORIGINATION_PROFILE_UPDATE` |
| GET | `/api/v1/origination-profiles/{code}/history?page=0&size=20` | `ORIGINATION_PROFILE_VIEW` |
| GET | `/api/v1/tenant/origination-default` | Same profile read permissions |
| PUT | `/api/v1/tenant/origination-default` | `ORIGINATION_PROFILE_UPDATE` |

List responses use `{ "items": [...] }`; history is paginated, maximum 100 records per page. DELETE is soft deletion (deactivation), returns 204, and preserves references and audit history. Codes cannot be edited. Codes are normalized to uppercase and must start with a letter, followed by letters, digits or underscores (maximum 100 characters).

## Create the current phone journey

POST creates an inactive profile and returns 201 with a Location header:

```json
{
  "code": "PHONE_FINANCE",
  "displayName": "Phone finance",
  "description": "Existing device-finance journey",
  "requirements": {
    "OFFER_SELECTION": ["KYC_APPROVED", "CLIENT_PROVISIONED", "STATEMENT_ACCEPTED"],
    "INTERNAL_APPROVAL": ["CONSENT_CAPTURED", "CLIENT_PROVISIONED", "DEVICE_ASSIGNED", "FINANCING_CALCULATED", "ACTIVE_PRODUCT_SELECTED"],
    "DISBURSEMENT": ["PENDING_LOAN_CREATED", "DEVICE_ASSIGNED", "DEPOSIT_MATCHED"]
  }
}
```

The response includes `id`, `code`, `displayName`, `description`, `active`, `isDefault`, `version`, `requirements`, `configuration`, creator/editor and timestamps.

Activate with PATCH using the version returned by GET/create:

```json
{ "expectedVersion": 0, "active": true }
```

`version` is an optimistic-lock token, not business-policy versioning. A stale token returns 409. Profile mutations also lock the tenant row, serializing profile creation, deactivation and default changes. Audit records commit in the same transaction as the change.

PATCH semantics:

- Omitted/null fields remain unchanged.
- Supplied `requirements` and `configuration` objects replace those objects completely.
- An empty description clears it.
- `{}` clears valuation configuration when paired with requirements that no longer require valuation.
- Blank names, empty changes, missing/invalid stages, duplicates and requirements in an invalid stage are rejected.

## Tenant default

PUT:

```json
{ "originationProfileCode": "PHONE_FINANCE" }
```

Response:

```json
{ "defaultOriginationProfileCode": "PHONE_FINANCE" }
```

GET returns a null code if no default has been configured. Setting the same default is idempotent. A default must be active and supported. Select another default before deactivating the current one. Deactivating a non-default profile does not alter applications or products.

## Logbook configuration

Inactive profiles may describe the planned vehicle workflow using `VEHICLE_OWNERSHIP_VERIFIED`, `VALUATION_APPROVED`, `SECURITY_REGISTRATION_CONFIRMED`, and `INSURANCE_VALID` in applicable stages. A valuation requirement must include complete configuration:

```json
{
  "valuationBasis": "FORCED_SALE_VALUE",
  "maxLtvRatio": 0.6,
  "valuationValidityDays": 30
}
```

Allowed bases are MARKET_VALUE and FORCED_SALE_VALUE. LTV must be greater than zero and at most one; validity must be 1-3650 days. These bounds validate configuration shape, not a recommended credit policy.

The corrected phone requirement set above and the complete legacy phone set can currently be activated. Vehicle workflow execution and a general requirement evaluator are not implemented yet; the API rejects activation rather than accepting configuration that the application flow cannot honor. This restriction is capability-based, not based on the profile code.

## Scope of this increment

V37 adds technical profile locking and append-only profile/default change history. No profiles or defaults are automatically seeded, and existing applications/products/questions are not backfilled. Application creation and offer selection do not consume profiles yet: that is the next integration step.

Existing KYC and statement modes continue to govern the phone journey. `STATEMENT_ACCEPTED` denotes the existing accepted-or-configured-bypass behavior; storing the profile does not override those tenant settings.

## Validation

`OriginationProfileApiTest` exercises real controller JSON binding, Bean Validation and method-security proxies with mocked persistence. `OriginationProfileMigrationTest` additionally validates V37 on disposable MariaDB, Hibernate mappings, tenant-default persistence, audit insertion and optimistic-lock conflicts. See [MariaDB test setup](origination-migration-tests.md).

## Requirement vocabulary correction and compatibility

The recommended phone configuration now describes the checks actually made by the existing services:

- `CLIENT_PROVISIONED` also appears at internal approval because that method checks the client reference again.
- `FINANCING_CALCULATED` represents populated financing amount and term from device pricing; it is not a separate credit assessment.
- `ACTIVE_PRODUCT_SELECTED` represents a populated approved-product reference resolving to an active tenant product.
- `PENDING_LOAN_CREATED` represents the existing Fineract loan reference required before activation. It does not claim that internal approval is independently revalidated.

`FINANCING_ASSESSED` and `INTERNAL_APPROVAL_VALID` remain readable and accepted for older clients and stored profiles. The complete old phone set remains activatable, editable and usable as a default; this compatibility allowance does not make those names aliases or add new application checks. Partial mixtures do not qualify for activation.

No automatic database rewrite is performed. To migrate an existing profile deliberately:

1. GET the profile and its current technical `version`.
2. PATCH with `expectedVersion` and the complete corrected `requirements` object shown above.
3. Check the returned configuration and `/history` for the before/after audit record.

For example, replace `0` with the version returned by GET:

```json
{
  "expectedVersion": 0,
  "requirements": {
    "OFFER_SELECTION": ["KYC_APPROVED", "CLIENT_PROVISIONED", "STATEMENT_ACCEPTED"],
    "INTERNAL_APPROVAL": ["CONSENT_CAPTURED", "CLIENT_PROVISIONED", "DEVICE_ASSIGNED", "FINANCING_CALCULATED", "ACTIVE_PRODUCT_SELECTED"],
    "DISBURSEMENT": ["PENDING_LOAN_CREATED", "DEVICE_ASSIGNED", "DEPOSIT_MATCHED"]
  }
}
```

The profile can remain active during this update. Historical audit entries are preserved. Application services still execute their existing hardcoded checks; a requirement evaluator is a separate implementation step.
