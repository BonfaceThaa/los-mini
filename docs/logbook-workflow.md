# Logbook workflow integration (V40)

## Rollout

Apply Flyway V40 after V39. It adds `logbook_loan_operations`, a durable queue with a unique operation per tenant/application/kind. It does not backfill applications, change defaults, or activate profiles. Existing phone applications continue their device/deposit flow. Keep the phone tenant default to preserve existing callers; logbook callers explicitly send `originationProfileCode`.

The supported logbook contract below can now be activated. Arbitrary combinations remain unsupported. A profile already used by applications cannot switch between phone and logbook families. Policy and product changes still apply to existing applications; these are not immutable policy versions.

**Deployment check:** the Fineract adapter now sends the configured `tenants.fineract_tenant_id` instead of the previously hard-coded `default`. Ensure each tenant references its actual deployed Fineract tenant before rollout. No tenant configuration is changed by this migration.

## 1. Configure the profile and product

`POST /api/v1/origination-profiles`:

```json
{
  "code": "LOGBOOK",
  "displayName": "Logbook cash lending",
  "requirements": {
    "OFFER_SELECTION": ["KYC_APPROVED", "CLIENT_PROVISIONED", "STATEMENT_ACCEPTED", "VEHICLE_OWNERSHIP_VERIFIED", "VALUATION_APPROVED"],
    "INTERNAL_APPROVAL": ["KYC_APPROVED", "CLIENT_PROVISIONED", "STATEMENT_ACCEPTED", "CONSENT_CAPTURED", "FINANCING_CALCULATED", "ACTIVE_PRODUCT_SELECTED", "VEHICLE_OWNERSHIP_VERIFIED", "VALUATION_APPROVED"],
    "DISBURSEMENT": ["INTERNAL_APPROVAL_VALID", "PENDING_LOAN_CREATED", "VEHICLE_OWNERSHIP_VERIFIED", "VALUATION_APPROVED", "SECURITY_REGISTRATION_CONFIRMED", "INSURANCE_VALID"]
  },
  "configuration": {
    "valuationBasis": "FORCED_SALE_VALUE",
    "maxLtvRatio": 0.6,
    "valuationValidityDays": 30
  }
}
```

These values illustrate a tenant policy, not prescribed lending terms. Creation returns an inactive profile. Activate using `PATCH /api/v1/origination-profiles/LOGBOOK` with `{"expectedVersion":0,"active":true}`, substituting the actual returned version. Existing draft profiles must first have their requirements updated to this complete contract.

Create/associate and activate the loan product using the existing product API and `originationProfileCode: "LOGBOOK"`. Initial execution supports KES, monthly repayments, positive repayment counts/intervals and a total term up to 360 months. Other products are excluded from eligible offers. The adapter uses the product's transaction processing strategy. Advanced payment allocation configuration is not introduced here; use an appropriate configured strategy such as `mifos-standard-strategy` for the previously discussed example.

## 2. Application and evidence

Create through `POST /api/v1/applications` using the existing application fields plus `"originationProfileCode":"LOGBOOK"`. Creation saves the tenant profile; existing asynchronous client creation, KYC and statement processing continue. A tenant with statement analysis disabled retains that existing bypass.

Use [the capability endpoints](logbook-capabilities.md) to capture a vehicle, document ownership, submit a valuation and have a different user approve it. With forced-sale value KES 800,000 and LTV 0.60, the maximum secured amount is KES 480,000. A requested amount of KES 300,000 passes that limit.

`GET /api/v1/applications/{id}/eligible-products` now includes capability checks and missing requirements. Offers require approved KYC, a provisioned client, an accepted statement (or tenant bypass), verified ownership, a current approved valuation and requested amount within LTV.

Select with `POST /api/v1/applications/{id}/offers/select`:

```json
{"productCode":"LOGBOOK_12_MONTHS"}
```

Capture consent through the existing `/consent` endpoint. Fetch `GET /api/v1/applications/{id}/logbook/workflow` for the current application version, stage checks and operation history.

## 3. Assess financing and approve

`POST /api/v1/applications/{id}/logbook/financing` (LOAN_CREATE):

```json
{"expectedApplicationVersion":7,"amount":300000}
```

Use the current version, not the illustrative value 7. A stale version returns 409. The response contains `applicationVersion`, `approvedAmount`, `termMonths`, `productCode` and `approvalReadiness` (`stage`, `ready`, `checks`, `missingRequirements`). Assessment validates current evidence and product bounds and records FINANCING_CALCULATED. Fineract calculates the repayment schedule; this assessment does not use the phone installment formula.

`POST /api/v1/applications/{id}/internal-approval` uses the existing approval payload and CREDIT_MANUAL_APPROVE permission. It rechecks requirements, saves internal approval and queues CREATE_LOAN. HTTP 202 returns an application with status LOAN_CREATION_QUEUED. The worker creates the pending Fineract loan and moves to FINERACT_LOAN_CREATED_PENDING_SECURITY.

Vehicle identity is frozen after internal approval. Valuation and verification evidence can still be renewed while waiting for security completion. They are locked while disbursement is queued, after activation, or after rejection/closure.

## 4. Complete security and disburse

Record verified security registration and valid insurance through the capability endpoints. Then `POST /api/v1/applications/{id}/activate-loan` (LOAN_CREATE) checks internal approval and disbursement requirements, including current valuation, ownership, product, KYC and amount limits. It queues DISBURSE and returns HTTP 202 with DISBURSEMENT_QUEUED. The worker rechecks before external writes and records FINERACT_LOAN_ACTIVATED after success. No device or deposit is required for this journey.

## Background operations and recovery

`GET /api/v1/applications/{id}/logbook/workflow` requires LOAN_VIEW and returns:

```json
{
  "applicationVersion":10,
  "stages": {
    "DISBURSEMENT": {
      "stage":"DISBURSEMENT",
      "ready":false,
      "checks":{"INSURANCE_VALID":false},
      "missingRequirements":["INSURANCE_VALID"]
    }
  },
  "operations":[]
}
```

This abbreviated example omits the other stages/checks. Operation records include id, kind, state, attempts, timestamps and sanitized lastError. States are QUEUED, RUNNING, SUCCEEDED, BLOCKED (requirements failed), and REVIEW_REQUIRED (external or unexpected failure). Failures set application status LOAN_OPERATION_REVIEW_REQUIRED.

After correcting evidence or configuration, an authorized reviewer uses `POST /api/v1/applications/{id}/logbook/operations/{operationId}/retry` (CREDIT_MANUAL_APPROVE). Failed operations are not retried automatically. A RUNNING operation can be reclaimed after 15 minutes for crash recovery. The worker reconciles remote state before another write: a remotely completed creation/disbursement is attached locally even if the previous local transaction failed. Already completed disbursements are reconciled without pretending that expired evidence can undo money movement.

Creation uses deterministic external ID `los-<application UUID>` and verifies remote client, product and principal before adopting a match. The [Fineract API contract](https://fineract.apache.org/docs/legacy/) documents unique loan external IDs and the externalId loan filter. Actual deployed-server compatibility must be verified in staging.

The worker defaults to enabled, polling every 5 seconds. Configuration: `app.logbook.workflow-worker-enabled` and `app.logbook.workflow-poll-ms`. Operations commit before external execution; external calls occur in background processing, not application HTTP requests.

## Code and persistence data flow

```text
ApplicationController -> ApplicationService -> LogbookWorkflowService
  -> LogbookWorkflowPolicy: saved tenant profile / supported requirements
  -> LogbookService: current vehicle revision, valuation and verification evidence
  -> tenant-scoped KYC, statement and product repositories
  -> application approved fields + status history + logbook_loan_operations

LogbookLoanScheduler -> LogbookLoanProcessor
  -> claim operation in committed transaction
  -> lock application/product and recheck gates
  -> FineractGateway -> HttpFineractGateway -> Fineract
  -> persist remote loan ID, operation result, status and activation event
```

V39 retains evidence and audit records; V40 adds only operation state. Application optimistic locking remains the existing `@Version`. Product codes resolve to tenant-scoped mappings; frontend callers do not need Fineract IDs. Ordinary phone applications continue the original ApplicationService path.

## Validation

Regression tests cover stage gates, LTV, stale evidence/version, tenant and role isolation, renewal locks, unchanged phone behavior, HTTP adapter payloads and retry reconciliation. Disposable MariaDB tests migrate to V40 and simulate remote success followed by local failure, proving durable retry state and avoiding duplicate gateway writes. External services are mocked: these tests do not constitute a live Fineract or registry/insurer integration check.
