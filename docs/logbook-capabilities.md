# Logbook capabilities (V39)

This is the capability-records step. It implements vehicle capture, human ownership/insurance/security checks, valuation submission and independent review, and computed LTV readiness. It does **not** enable logbook profile activation, change the phone-finance workflow, create a Fineract loan, move money, call a registry/insurer, or provide a general requirements evaluator. Existing profile activation guards remain in place until the workflow integration step.

The endpoints operate only on an application's saved tenant profile containing `VALUATION_APPROVED`, regardless of the profile's code. A code named `LOGBOOK` by itself grants no capabilities. Existing compatible applications can use these endpoints; ordinary API creation of new logbook applications remains blocked by the inactive-profile rule at this rollout stage. Do not bypass that rule or manually reclassify historical phone applications to try the endpoints. Automated tests use isolated logbook fixtures.

## Database and rollout

V39 adds:

| Table | Purpose |
| --- | --- |
| `logbook_vehicles` | One vehicle per application, optimistic version and evidence revision |
| `logbook_valuations` | Valuer's immutable amounts/report, dated review outcome |
| `logbook_verifications` | Append-only ownership, insurance and security decisions |
| `logbook_audits` | Actor, timestamp and before/after snapshots for every write |

IDs are `VARCHAR(36)`. Composite foreign keys require the vehicle and documents to belong to the same tenant **and application**. Referenced evidence documents cannot be deleted from the database. Monetary values are KES with two decimal places. No core Fineract product configuration is copied into these tables.

No historical application/product backfill is required. Migration creates no vehicles, valuations, or verified flags and changes no profile defaults or activation states. Startup provisions the five permission catalog entries and grants them to system `TENANT_ADMIN` roles through the existing role-template mechanism. Custom roles must receive explicit grants. Obtain a new login token after permissions change.

## Permissions

| Action | Permission |
| --- | --- |
| Read summary / audit | `LOGBOOK_VIEW` |
| Capture / replace vehicle | `LOGBOOK_VEHICLE_MANAGE` |
| Submit valuation | `LOGBOOK_VALUATION_SUBMIT` |
| Approve / reject / revoke valuation | `LOGBOOK_VALUATION_REVIEW` |
| Record ownership / insurance / security decisions | `LOGBOOK_VERIFICATION_MANAGE` |

Use a valuer role with view + valuation-submit and a separate reviewer with view + valuation-review. A user cannot review their own submission even if they hold both permissions. Actor IDs and tenant IDs are derived from the authenticated user. Authorization is enforced on service methods as well as tenant-scoped repository access.

## API walkthrough

Base: `/api/v1/applications/{applicationId}/logbook`. All examples require a bearer token and a compatible saved application profile. Substitute real document IDs returned by the existing application document-upload API; documents cannot come from another application.

### 1. Capture the vehicle

`PUT {base}/vehicle`

```json
{
  "registrationNumber": "KDA 123A",
  "chassisNumber": "EXAMPLECHASSIS123",
  "engineNumber": "EXAMPLEENGINE123",
  "make": "Toyota",
  "model": "Fielder",
  "manufactureYear": 2016,
  "registeredOwner": "Example Borrower",
  "logbookNumber": "EXAMPLE-LB-123"
}
```

Omit `expectedVersion` only for first capture. The response includes `vehicle.version` (initially 0). For every subsequent write, send the latest returned version. Stale or omitted versions on existing records return 409. Replacement is full, normalizes identifier casing/whitespace, advances the evidence revision, and makes prior valuations and checks inapplicable without deleting their history. The year cannot be in the future. Identifier formats are not treated as proof of registration or ownership.

### 2. Submit valuation as the valuer

`POST {base}/valuations`

```json
{
  "expectedVersion": 0,
  "marketValue": 1000000,
  "forcedSaleValue": 800000,
  "valuedOn": "2026-09-20",
  "valuerOrganization": "Example Independent Valuers",
  "reportDocumentId": "<uploaded-report-document-id>"
}
```

Both values must be positive; forced-sale value must not exceed market value. The date cannot be in the future. Submission records the authenticated valuer's user ID and starts `PENDING`. A new report supersedes all prior reports for readiness immediately, even while pending. Values cannot be edited; submit a replacement report.

### 3. Independently review the latest valuation

`POST {base}/valuations/{valuationId}/review`

```json
{
  "expectedVersion": 1,
  "decision": "APPROVED",
  "reason": "Report reviewed and accepted"
}
```

A different user with review permission must act. Allowed transitions: `PENDING -> APPROVED/REJECTED`, `APPROVED -> REVOKED`. No resurrection of rejected/revoked reports; submit a new valuation. Superseded or outdated-vehicle reports cannot be reviewed. Expired reports cannot be approved.

### 4. Record ownership and security evidence

`POST {base}/verifications/OWNERSHIP`

```json
{
  "expectedVersion": 2,
  "status": "VERIFIED",
  "referenceNumber": "EXAMPLE-OWNERSHIP-SEARCH-123",
  "documentId": "<uploaded-ownership-evidence-id>",
  "notes": "Registered owner and applicant relationship checked against the attached evidence"
}
```

For security registration, use `/verifications/SECURITY_REGISTRATION` with the actual registration reference and its document. These are staff attestations, not confirmations obtained directly from a government registry. References in this guide are placeholders, not prescribed Kenyan regulatory formats.

### 5. Record insurance coverage

`POST {base}/verifications/INSURANCE`

```json
{
  "expectedVersion": 3,
  "status": "VERIFIED",
  "referenceNumber": "EXAMPLE-POLICY-123",
  "documentId": "<uploaded-policy-document-id>",
  "validFrom": "2026-09-20",
  "validUntil": "2027-09-19",
  "notes": "Coverage period checked against the attached policy"
}
```

Insurance requires a valid period. Future/expired policies may be recorded but are not currently valid. Coverage dates are inclusive, evaluated in Africa/Nairobi. Other verification kinds do not accept coverage dates. `REJECTED` or `REVOKED` evidence supersedes earlier verification; history is retained.

### 6. Read summary and audit

`GET {base}` returns `vehicle`, `valuations`, `verifications`, and `readiness`. Each write returns the same summary. No mutation changes the application's loan workflow status.

Example readiness fragment with forced-sale basis, 60% LTV, KES 800,000 forced-sale value, and KES 480,000 requested:

```json
{
  "vehicleCaptured": true,
  "ownershipVerified": true,
  "valuationApproved": true,
  "insuranceValid": true,
  "securityRegistrationConfirmed": true,
  "currencyCode": "KES",
  "maximumSecuredAmount": 480000.00,
  "requestedAmountWithinLimit": true,
  "missingCapabilities": []
}
```

The maximum is `valuation basis value * profile maxLtvRatio`, rounded **down** to two decimals. Readiness re-evaluates the saved profile's current configuration on every read. It is not an approved loan amount, product eligibility result, or affordability decision. `missingCapabilities` reports all capability checks, not stage-specific requirements.

A valuation dated September 20 with validity 30 days is valid through October 19; it expires at the start of October 20 in Africa/Nairobi. Approved-but-expired reports remain in history but no longer supply an LTV limit. A changed vehicle or new pending valuation also removes the effective limit.

`GET {base}/audit` returns actor IDs, timestamps and before/after snapshots. Writes are blocked after internal approval, Fineract loan creation, rejection or closure. Future servicing changes require a separate controlled process.

## Validation

- `LogbookApiTest`: actual controller/services with mocked persistence; access control, evidence scope, stale versions, review separation, LTV boundary, expiry, revocation and revision invalidation.
- `LogbookPersistenceTest`: opt-in disposable MariaDB, fresh/upgrade Flyway V39, actual Hibernate lifecycle and version increments, audit persistence, cross-tenant FK failures and document-deletion protection.
- `OriginationOpenApiTest`: generated endpoint and schema coverage.

Run unit/API tests with `./mvnw.cmd -Dtest=LogbookApiTest,OriginationOpenApiTest,PhoneOriginationBaselineTest test`. For MariaDB, use the isolated container procedure in `origination-migration-tests.md` with `-Dtest=LogbookPersistenceTest`.
