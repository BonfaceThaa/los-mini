# Profile-scoped application questions

## Scope and compatibility

`application_variable_definitions.origination_profile_id` (added by V36) now has an entity mapping and is used by listing and answer validation:

- Null: shared question, applicable to every profile in that tenant.
- Non-null: applicable only to that profile in the same tenant.
- Question codes remain unique across the tenant; scope does not create another code namespace.
- Existing definitions stay shared. No migration or backfill is required for this step.

Application creation first resolves the profile, then loads active shared questions plus active questions for that saved profile. Required questions are enforced only within that set. Answers referencing another profile, another tenant, an inactive definition or an unknown definition are rejected before application persistence and workflow events.

Definition changes take the same tenant lock as application creation. Creation reads current definitions with locking reads, so questionnaire edits cannot interleave with validation and answer persistence. The form may still become stale between GET and POST; the server validates the current questionnaire on submission.

## Endpoints

| Endpoint | Purpose | Permission |
| --- | --- | --- |
| `GET /api/v1/application-variable-definitions?originationProfileCode=PHONE_FINANCE` | Shared plus selected profile's questions; `activeOnly=true` by default | `LOAN_CREATE` or `APPLICATION_VARIABLE_MANAGE` |
| `GET /api/v1/application-variable-definitions` | Shared plus tenant-default profile's questions | Same as above |
| `GET /api/v1/application-variable-definitions/all` | All tenant definitions across profiles; `activeOnly=false` by default | `APPLICATION_VARIABLE_MANAGE` |
| `GET /api/v1/application-variable-definitions/all?originationProfileCode=LOGBOOK` | Admin preview: shared plus logbook questions, including draft profile configuration | `APPLICATION_VARIABLE_MANAGE` |
| `POST /api/v1/application-variable-definitions` | Create a definition | `APPLICATION_VARIABLE_MANAGE` |
| `PUT /api/v1/application-variable-definitions/{id}` | Replace a definition, including its scope | `APPLICATION_VARIABLE_MANAGE` |
| `POST /api/v1/application-variable-definitions/{id}/activate` | Activate definition | `APPLICATION_VARIABLE_MANAGE` |
| `POST /api/v1/application-variable-definitions/{id}/deactivate` | Deactivate definition | `APPLICATION_VARIABLE_MANAGE` |

The loan-form GET uses the same active-profile/default rules as application creation. An omitted/null code uses the default; a blank code is invalid. A missing default produces a validation error. The admin `/all` endpoint needs no default and allows filtering by inactive profiles. Both paths always use the authenticated tenant.

`activeOnly=false` includes inactive definitions for the requested scope; those definitions remain invalid for new submitted answers. Administrative clients that previously used the unfiltered GET should switch to `/all` to manage every scope.

## Create or replace a logbook-specific definition

```http
POST /api/v1/application-variable-definitions
Content-Type: application/json
```

```json
{
  "code": "VEHICLE_REGISTRATION",
  "label": "Vehicle registration number",
  "sectionName": "Vehicle details",
  "fieldType": "TEXT",
  "required": true,
  "displayOrder": 10,
  "options": [],
  "originationProfileCode": "LOGBOOK"
}
```

Response excerpt:

```json
{
  "id": "<definition-id>",
  "code": "VEHICLE_REGISTRATION",
  "required": true,
  "active": true,
  "definitionVersion": 1,
  "originationProfileId": "<tenant-logbook-profile-id>"
}
```

Use `originationProfileCode: null`, or omit it, to create a shared definition. On PUT, omission/null also means shared because PUT replaces the definition; clients must keep sending the profile code to retain a scoped question. Scope changes increment the existing definition version. Blank, unknown and other-tenant profile codes are rejected. Inactive profiles may receive definitions so administrators can prepare future journeys.

## Data flow

For a tenant with shared `NEXT_OF_KIN`, phone-specific `PHONE_USAGE`, and logbook-specific `VEHICLE_REGISTRATION`:

1. The phone form GET returns `NEXT_OF_KIN` and `PHONE_USAGE`.
2. A phone application validates required answers for those two questions only. Sending `VEHICLE_REGISTRATION` is rejected.
3. Admin logbook preview returns `NEXT_OF_KIN` and `VEHICLE_REGISTRATION`.
4. Once the logbook workflow is implemented and activatable, its applications will use that scoped questionnaire. This change alone does not enable the logbook workflow.

Answer payloads remain unchanged (`definitionId`, `textValue`, `selectedValues`). The frontend does not submit a profile ID per answer. The application profile decides which definitions apply.

Historical answers are read from their saved definition version, label, code, field type and option snapshots. Moving, editing or deactivating the current definition does not rewrite or hide those answers.

## Validation

`ApplicationVariableScopeApiTest` checks JSON contracts, scope creation/replacement, default/explicit form selection, draft administration and method-security permissions. `ApplicationVariableScopePersistenceTest` runs Flyway and real Hibernate repositories on disposable MariaDB, checking tenant/profile filtering, required questions, inactive/out-of-profile answer rejection and historical snapshots after scope changes. Existing answer-type and application-creation tests remain part of the regression suite.
