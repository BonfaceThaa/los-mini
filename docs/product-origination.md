# Products, profiles and offer selection

## Database and compatibility

This step maps the existing V36 columns:

- `loan_product_mappings.origination_profile_id`
- `loan_request_applications.selected_loan_product_mapping_id`

No new migration is needed. V38 handles legacy backfill. Products without a profile are excluded from application offers; applications without a profile must be backfilled before offers can be listed/selected. Do not infer historical application profiles from a tenant's current default.

Each product mapping belongs to one profile. Eligibility uses the current catalog: a newly added product can be offered to an older application with the same saved profile. There is no version-to-product association table.

## Product creation and association

Add optional `originationProfileCode` to the existing `POST /api/v1/loan-products` payload:

```json
{ "originationProfileCode": "PHONE_FINANCE" }
```

This is an additional field; all existing product configuration fields are still required. Omission/null resolves the tenant's active default. An explicit code must belong to the authenticated tenant; inactive profiles are accepted so products for draft journeys can be prepared. Missing defaults, unknown/cross-tenant codes and invalid codes are rejected before Fineract product creation. The mapping saves the resolved profile and the catalog response includes `originationProfileId`.

Associate an existing product using a local-only endpoint:

```http
PUT /api/v1/loan-products/PHONE_12_WEEKS/origination-profile
Content-Type: application/json
```

```json
{ "originationProfileCode": "PHONE_FINANCE" }
```

Requires `LOAN_PRODUCT_UPDATE`. This changes the local association and `updatedBy`; it does not call Fineract or change the product's financial configuration. The existing PATCH-by-short-name endpoint preserves the association.

Setting the same profile is idempotent. An unselected product may move to another profile. A product already selected by an application cannot move (409), and referenced products cannot be deleted: deactivate them instead. Reference checks include both local mapping IDs and historical selected/approved Fineract IDs within the tenant. An unclassified product can be associated only if every existing reference already has the target application profile; otherwise reconcile the legacy data first. Existing unique constraints still prevent registering the same Fineract product twice in a tenant.

## Catalog filtering

```http
GET /api/v1/loan-products?originationProfileCode=PHONE_FINANCE
```

Returns that tenant/profile's products. `includeInactive=true` includes inactive mappings. Omitting the profile filter preserves the existing tenant-wide catalog listing. Catalog responses include `productCode`, `loanProductMappingId` and `originationProfileId`.

Application endpoints always scope to the application's saved profile:

- `GET /api/v1/applications/{id}/eligible-products`: existing KYC/client/statement readiness plus active product, same tenant/profile, and inclusive requested-amount bounds.
- `GET /api/v1/applications/{id}/active-loan-products`: active products for that tenant/profile; retains the manual listing's existing lack of amount/readiness filtering.

Example eligible product excerpt:

```json
{
  "id": "7",
  "productCode": "PHONE_12_WEEKS",
  "loanProductMappingId": "<local-mapping-uuid>",
  "originationProfileId": "<profile-uuid>",
  "name": "Phone finance - 12 weeks"
}
```

`id` is retained as a deprecated legacy remote ID. New clients select using `productCode`. Eligible offers are now built directly from local mappings, which preserves their product codes.

## Select an offer

```http
POST /api/v1/applications/{id}/offers/select
Content-Type: application/json
```

```json
{ "productCode": "PHONE_12_WEEKS" }
```

The backend trims the code and matches it case-insensitively within the eligible catalog. It resolves the Fineract ID internally and saves both the local mapping ID and the existing remote ID/name fields. Application responses include `selectedLoanProductMappingId`.

The deprecated `{ "fineractProductId": "7" }` request remains supported with the same profile, tenant, readiness and amount checks. Exactly one selector must be supplied. Missing, blank, conflicting and unknown selectors are rejected; an unknown code never falls back to remote-ID interpretation.

Selection locks and refreshes the mapping before rechecking current eligibility, preventing an offer listed earlier from bypassing intervening product changes. Reassociation and deletion check current references while holding the product lock. A different product cannot replace an offer after pricing has been calculated; selecting the same priced offer preserves its workflow state.

Device pricing and internal approval also enforce the application's product/profile association and reject disagreement between the selected and financing product IDs. Historical selections without a local ID can still resolve their exact remote ID within the saved profile.

## Rollout and limits

Deploy after V36-V38. New applications and products now populate their profile references, and new selections save the local mapping ID. Records written by older application instances between rollout stages may still require reconciliation. Use the association endpoint to classify unassigned products, and the documented backfill process for missing application profiles or unresolved historical selections.

Profile deactivation blocks new applications; existing applications retain their saved profile. Product deactivation removes it from offers and approval eligibility. This step does not enable logbook workflow execution or implement a generic requirements evaluator. The new association/selection paths are local operations; existing catalog provisioning continues through the existing Fineract gateway integration.

## Tests

`ProductOriginationApiTest` covers defaults, explicit draft profiles, authorization, cross-tenant rejection, reassignment guards and rejection before remote creation. `PhoneOriginationBaselineTest` covers code/legacy selection, amount boundaries, prerequisites and rechecking changed products. `ProductProfilePersistenceTest` runs on disposable MariaDB and verifies real profile-filtered queries, local selection persistence and historical-reference guards.

Swagger includes a complete illustrative logbook creation example, profile filtering/default behavior, association conflicts, and mutually exclusive offer selectors. OriginationOpenApiTest fetches the generated /v3/api-docs document and validates its schemas and example payload against the request DTO. Run it with: ./mvnw.cmd -Dtest=OriginationOpenApiTest test. The generated document is written to target/openapi-origination.json.

## Local diagnosis of product creation failures

The generic HTTP 500 response deliberately hides internal exception details. The global exception handler logs the stack trace at ERROR; Fineract POST failures are also logged by the gateway. The standard application configuration logs to the console.

For VS Code, the local `Debug LM with file logs` launch directs output to the Debug Console and `target/lm-debug.log`. Stop the existing process before starting it on the same port. Workspace settings disable continuing after a build failure so debugging does not silently proceed with stale classes. These local `.vscode` files are ignored by Git.

An equivalent Maven launch is:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--logging.file.name=target/lm-debug.log --logging.level.com.credvenn.lm=INFO --logging.level.com.credvenn.lm.common.exception.GlobalExceptionHandler=ERROR'
```

`ProductOriginationApiTest` exercises the exact Swagger logbook example with tenant GL lookup through the real controller and services. It verifies a successful inactive product mapping and a 400 when required GL configuration is absent. External Fineract and repository operations are mocked; passing this test does not prove live integration success.
