# New application: authentication, organizations, and staff MVP blueprint

Date: 2026-10-17. Status: proposed implementation specification.

This document defines the starting point for a **new application**. It combines useful implementation choices from the existing app with security and lifecycle controls from the supplied ADR 0001. It includes the agreed change to email/password login with automatic platform or organization account discovery.

Scope is identity, platform organization administration, organization profiles, staff, roles, permissions, sessions, invitations, and security audit. Domain workflows and permissions are added later. Organization and tenant mean the same isolation boundary; use `organization` consistently in this application's schema and API.

The [implementation comparison](auth-tenancy-platform-implementation-comparison.md) provides background. This blueprint is standalone: developers should implement the decisions below rather than reproduce the existing app's auth behavior or assume all ADR proposals remain selected.

## 1. Selected MVP decisions

| Topic | Selected approach | Basis and reason |
| --- | --- | --- |
| Application architecture | Java 21, Spring Boot modular monolith, Spring Security, JPA, MariaDB, Flyway | Existing app: one deployable application is practical for an MVP |
| Database | One shared application database, tenant-scoped authorization and organization records | Existing app: simpler transactions, migrations, pooling, and restore |
| Login | Email and password only; discover account scope and organization server-side | Requested change and ADR's global email policy |
| Email identity | Globally unique normalized email across platform and organization accounts | Makes account discovery deterministic |
| Membership | Each organization account belongs to exactly one immutable organization; platform accounts have none | ADR: simple identity and isolation rules |
| Platform access | Explicit protected account scope; initial platform super admin created through a deployment command | ADR: platform authority cannot be granted through tenant roles |
| Organization roles | Tenant-local custom roles and a backend-owned permission catalog | Both: customizable access without arbitrary permission keys |
| Direct grants | Additive direct staff grants; no deny rules or role inheritance | ADR: supports individual exceptions with simple union semantics |
| Authorization freshness | Load current account/session/organization and current grants on protected requests | ADR: immediate access changes and revocation |
| Browser sessions | Short access JWT in memory; opaque rotating refresh cookie | ADR: refresh credentials remain inaccessible to browser JavaScript |
| Account onboarding | Invitations and user-chosen passwords | ADR: explicit activation and no administrator-chosen staff passwords |
| Organization provisioning | Local transactional setup plus durable invitation outbox; no database provisioning engine | Existing app's simpler storage plus ADR's durable external delivery |
| Audit | Append-only security/admin events in the same database transaction as successful changes | ADR guarantees are easier to implement with shared storage |

Shared storage deliberately differs from ADR 0001's database-per-organization decision. It provides logical tenant isolation, not physical organization database separation. If separate databases become a confirmed product requirement, replace this topology decision and plan provisioning, migrations, routing, pools, and coordinated restore before implementation. Do not quietly treat it as an interchangeable deployment setting.

Direct grants are included to retain the notes' staff-specific exceptions. The rest of the design remains deliberately small: no multiple-organization membership, organization switching, impersonation, public signup, account deletion, organization deletion, email changes, platform-admin promotion API, or platform-staff role editor in this MVP.

## 2. Module boundaries

| Module | Responsibility |
| --- | --- |
| `identity` | Accounts, passwords, login, sessions, invitation acceptance, password recovery |
| `organization` | Organization registration, profile, lifecycle, initial administrator setup |
| `staff` | Organization staff profiles, invitations, suspension and reactivation |
| `authorization` | Permission catalog, roles, assignments, direct grants, delegation invariants |
| `platform` | Explicit cross-organization administration use cases |
| `audit` | Scoped security and administrative event recording/querying |
| `notifications` | Durable email outbox and delivery retries |

Controllers validate transport data and call use-case services. Business rules and transaction boundaries belong in services. Repository methods for organization data require organization ID. Platform cross-organization operations use separate service methods that validate platform scope and explicitly select the target organization.

Do not rely solely on URL prefixes, hidden buttons, or controller annotations. Services enforce scope, organization binding, grant delegation, and protected-administrator rules as well.

## 3. Storage model and constraints

Use UUIDs as opaque IDs and UTC timestamps. Manage schema changes through Flyway; disable automatic production schema modification.

| Table | Key fields and constraints |
| --- | --- |
| `organizations` | `id`, globally unique `normalized_slug`, `name`, profile fields, `status`, `version`, timestamps |
| `accounts` | `id`, globally unique `normalized_email`, display email, `display_name`, `scope`, nullable `organization_id`, nullable `password_hash` until activation, `status`, `version`, last login, timestamps |
| `permissions` | Backend-owned `key`, description, group, assignable scope, enabled flag |
| `roles` | `id`, `organization_id`, code, name, `normalized_name`, description, protected flag, `version`; unique organization/code and organization/normalized-name |
| `role_permissions` | Organization ID, role ID, permission key; unique membership |
| `account_roles` | Organization ID, account ID, role ID; unique assignment |
| `account_permissions` | Organization ID, account ID, permission key; unique direct grant |
| `sessions` | `id`, account ID, organization ID when applicable, creation/last-refresh times, inactivity expiry, absolute expiry, revoked time/reason, CSRF binding hash |
| `refresh_tokens` | `id`, session ID, unique token hash, created/consumed/revoked times, replacement ID; all tokens in one session form its refresh family |
| `invitations` | Account/organization IDs, unique token hash, expiry, consumed/invalidated times |
| `password_reset_tokens` | Account ID, unique token hash, expiry, consumed/invalidated times |
| `audit_events` | Event ID, scope, organization ID when applicable, actor ID/type, target, action, timestamp, trace ID, result, safe change details |
| `email_outbox` | Delivery ID, template and target reference, protected delivery payload, status, attempts, next attempt time, safe failure category |
| `idempotency_requests` | Actor, operation, key, canonical request hash, resource/result reference, creation/expiry; unique actor/operation/key |

Enforce `scope = PLATFORM` with null organization ID and `scope = ORGANIZATION` with non-null organization ID using database checks and service validation. Membership cannot be changed by update DTOs.

Use composite foreign keys or equivalent constraints to prevent cross-organization account/role assignments. For example, unique `(organization_id, id)` on organization-owned parent records supports join-table foreign keys that include organization ID. A valid role ID alone is insufficient.

Organization-owned tables, joins, audit queries, and future business records must carry and filter organization ID. Global login lookups, platform account operations, and the fixed permission catalog are intentional global access paths; isolate them from ordinary tenant repositories.

No separate local staff identity is needed in this shared database: the organization-scoped account is the staff identity. Future business records reference that account ID with organization binding.

## 4. Email/password login and account discovery

### Email policy

Normalize by trimming surrounding whitespace and lowercasing with a locale-independent implementation. Treat the complete email as case-insensitive as an explicit product policy. Preserve a display form if useful. Do not strip dots, plus suffixes, or apply provider-specific transformations. Use the same normalization in invitations, creation, login, recovery, and uniqueness enforcement.

One normalized email identifies one account globally. A platform administrator cannot simultaneously have an organization account with that email. An email already used by another organization is unavailable; do not disclose its owner. If multiple memberships are required later, redesign identity and membership discovery explicitly.

### Login contract

`POST /api/v1/auth/login`

```json
{
  "email": "person@example.com",
  "password": "user-supplied-password"
}
```

The backend:

1. Normalizes email and performs a global account lookup.
2. Verifies the adaptive password hash; uses a dummy verification path for unknown accounts to reduce timing differences.
3. Requires an active account. Organization accounts also require an active organization.
4. Resolves scope and organization from the stored account, never request parameters.
5. Creates a server session and hashed refresh token, binds CSRF protection, and issues an access JWT.
6. Sets the refresh cookie and returns safe identity context and current effective permissions.

```json
{
  "accessToken": "signed-jwt",
  "tokenType": "Bearer",
  "expiresIn": 600,
  "csrfToken": "session-bound-csrf-value",
  "user": {
    "id": "account-uuid",
    "email": "person@example.com",
    "displayName": "Example User",
    "scope": "ORGANIZATION",
    "organization": {
      "id": "organization-uuid",
      "slug": "example",
      "name": "Example Organization"
    },
    "roles": [],
    "directPermissions": [],
    "inheritedPermissions": [],
    "effectivePermissions": []
  }
}
```

For platform accounts, `scope` is `PLATFORM`, `organization` is null, and the response lists explicit platform capabilities instead of organization grants. Do not return raw refresh tokens, password hashes, session secrets, or infrastructure details.

Use generic `401 INVALID_CREDENTIALS` for unknown email, incorrect password, invited/suspended account, or an organization that cannot log in. Do not let discovery reveal whether an account is platform or tenant before successful authentication. Apply rate limiting and record safe security events.

The frontend has one login screen. Successful scope selects the platform or organization dashboard. The backend independently enforces that scope on every route.

## 5. Organization and staff lifecycle

Organization states: `INVITED`, `ACTIVE`, `SUSPENDED`. Account states: `INVITED`, `ACTIVE`, `SUSPENDED`. Failed email delivery is a delivery status, not a partially created organization state.

### Create an organization

A platform super admin submits the organization name/slug and initial administrator email/display name. In one transaction:

1. Reserve the globally unique email and organization slug.
2. Create the organization in `INVITED` state and its invited administrator account.
3. Create its protected `ORGANIZATION_ADMIN` role and initial assignment.
4. Create a hashed, expiring invitation and an email outbox item.
5. Append platform and organization audit events and save the idempotent result.

Return `201 Created`, `Location`, organization ID, initial administrator ID, and invitation-delivery status `PENDING`. Resource creation is complete; email delivery continues through the durable outbox. This MVP does not need a generic provisioning-operation polling API. The platform organization detail exposes safe delivery status and resend controls.

Invitation acceptance sets a user-chosen password, activates the account, and activates the organization if this is its initial administrator. These changes are transactional. Acceptance does not log the user in. The client navigates to the ordinary email/password login.

### Invite staff

Authorized organization staff invite an email/display name with role IDs and optional direct permission keys. Derive organization ID from the actor. Create the account, grants, invitation, audit, and outbox entry transactionally and return `201` with pending invitation status.

Globally occupied emails return `409 EMAIL_UNAVAILABLE` without identifying the existing owner or organization. Repeat requests with the same idempotency key and body return the saved result; the same key with different input returns `409`.

Invitations use cryptographically random single-use tokens, expire after 48 hours, and bind to the intended account and organization. Resending invalidates prior invitations atomically. Acceptance checks current invitation/account/organization state; a suspended organization cannot be activated through an old invitation. Invited accounts cannot be manually switched to active to bypass acceptance.

An email invitation necessarily carries a delivery secret. Keep only its hash in the invitation table. Protect the transient outbox delivery material with encryption and restricted access, exclude it from logs, and erase secret-bearing payloads after delivery/expiry. Retries must not create additional accounts or invitations.

### Suspend and reactivate

Account suspension revokes all account sessions in the same transaction. Organization suspension revokes all organization sessions and blocks new login/refresh. Reactivation permits a new login; it does not restore revoked sessions. It does not automatically reactivate individually suspended staff.

Require a reason and audit it. Never suspend or remove the final active protected organization administrator. An organization in `INVITED` may suspend provisioning activity, but cannot become `ACTIVE` through a status endpoint before initial administrator acceptance.

## 6. Authorization and staff management

Initial assignable organization permissions:

| Permission | Meaning |
| --- | --- |
| `organization.read`, `organization.update` | Read/edit the current organization's allowed profile fields |
| `staff.read`, `staff.invite`, `staff.update`, `staff.suspend` | View, invite, edit display names, suspend/reactivate staff |
| `role.read`, `role.manage` | View and manage custom organization roles |
| `staff.roles.manage` | Replace staff role assignments |
| `staff.permissions.manage` | Replace direct staff grants |
| `audit.read` | Read current-organization audit events |

Only seed permissions backed by implemented endpoints. Platform capabilities are a separate backend-owned set checked against platform scope; they are never tenant-assignable keys.

Generate an immutable opaque code for custom roles on creation; custom role names remain editable and case-insensitive unique within the organization. Organization profile edits initially allow `name`, `contactEmail`, and `contactPhone` only. These contact details do not change account login emails, slug, lifecycle, or ownership.

Effective permissions are the union of active assigned-role permissions and direct account permissions. There are no negative grants. Removing a direct grant preserves access inherited from a role. Empty roles and direct grants are allowed; such accounts retain self-service identity access only.

The protected `ORGANIZATION_ADMIN` role grants all implemented organization-management permissions. Its code, grants, protected flag, and definition cannot be edited or deleted through organization APIs. Update code-owned defaults through explicit migrations when capabilities change; do not rewrite every organization's roles on application startup.

Mandatory delegation controls:

- Role creation/editing needs `role.manage`; the actor must possess every permission in the resulting grant set. Editing assigned roles uses the same checks.
- Role assignment needs `staff.roles.manage`; validate tenant ownership and the complete resulting role grant sets against the actor's effective grants.
- Direct-grant assignment needs `staff.permissions.manage` and possession of every resulting grant.
- Staff creation additionally requires role/direct-grant management permissions when those sets are nonempty.
- Protected administrator assignment/removal requires an active protected administrator or an explicit platform recovery workflow. The platform action must record its reason.
- Serialize protected-administrator changes by locking the organization row before checking the final-active-admin invariant. Apply the same lock convention to suspension, role replacement, and recovery paths.
- Grant edits, profile edits, role changes, and status changes require a version precondition. Use JPA optimistic versions and return `412` for stale `If-Match` values; never silently overwrite concurrent changes.

Platform administrators do not receive unrestricted organization business access. Their cross-organization privileges are the explicit organization/staff/recovery APIs listed below. No impersonation is included.

## 7. JWT, session, and browser security

Access tokens expire after 10 minutes. Use RS256 with an explicitly pinned accepted algorithm, required issuer/audience/expiry/subject/session/scope claims, and keys from a trusted server key set. Keep signing keys outside source control. Use key IDs and overlapping verification keys only for the bounded token transition window.

Claims: `iss`, `aud`, `sub`, `iat`, `exp`, `jti`, `sid`, `scope`, and `organizationId` for organization accounts. Platform tokens use a different audience and contain no organization ID. JWTs do not carry authoritative permission lists.

Every protected request:

1. Validates signature, required claims, issuer, route-appropriate audience, expiry, and account scope.
2. Loads an active, unrevoked, unexpired server session and its active account.
3. Matches token account/scope/organization to authoritative state and requires an active organization where applicable.
4. Loads current grants and checks endpoint permission plus resource organization binding.
5. Establishes request tenant context and clears it in `finally`.

Refresh tokens are opaque CSPRNG values with at least 256 bits of randomness, stored only as hashes outside the protected delivery/browser channel. A session expires after 7 days without refresh and no later than 30 days after creation. Refresh consumes the current token atomically under a session/token lock, creates one successor, and never extends the absolute deadline. Reuse of a consumed token revokes the entire session/family. Commit replay revocation even though the response is an authentication error; do not accidentally roll it back by throwing inside the transaction.

Keep access tokens in browser memory. Set refresh cookies with `Secure`, `HttpOnly`, `SameSite=Lax`, and a path restricted to `/api/v1/auth`; omit the `Domain` attribute unless deployment requires a reviewed alternative. Assume frontend and API are same-site over HTTPS. Use an explicit CORS origin allowlist and credentials policy. A cross-site deployment requires an explicit cookie/CORS revision.

Login validates the browser Origin and establishes CSRF binding. Cookie-authenticated refresh/logout require an allowed Origin and a session-bound CSRF header. `GET /auth/csrf` uses the refresh cookie and validates Origin; it returns only the bound CSRF value, does not rotate tokens, and is not readable from untrusted origins. After reload, the client obtains CSRF before refreshing. Serialize refresh across tabs; treat replay detection as a security event even when accidental.

Logout revokes the current session and clears its cookie. Logout-all revokes every account session. Password reset/change and suspension revoke affected sessions. Existing access JWTs fail on their next protected use because session state is checked. Set `Cache-Control: no-store` on credential and identity-sensitive auth responses.

Passwords use Spring Security BCrypt with a benchmarked work factor and a documented policy that handles BCrypt's input-byte limit without silent truncation. Password recovery uses hashed, single-use CSPRNG tokens expiring after 30 minutes; issuance and responses do not disclose account existence. Password change requires the current password. Recovery does not automatically authenticate the account.

Apply configurable per-account and per-source limits to login, refresh, invitation acceptance/resend, and recovery. Use a shared limit store when running multiple replicas. Platform-admin MFA is a release requirement before public production; select its enrollment, challenge, recovery, and enforcement contract before claiming production readiness. Email/password remains the initial account discovery step.

## 8. API contract

All routes are under `/api/v1`. IDs are opaque and dates use ISO 8601 UTC. Collection responses use `items`, `page`, `size`, `total`, with zero-based pages and maximum size 100. Return `ETag` versions; require `If-Match` for existing-resource mutations and return `428` when missing, `412` when stale.

### Authentication and self-service

| Method / path | Access and behavior |
| --- | --- |
| `POST /auth/login` | Public, rate limited; email/password; `200` access JWT/context/CSRF plus refresh cookie |
| `GET /auth/csrf` | Refresh cookie and allowed Origin; `200` CSRF binding value |
| `POST /auth/refresh` | Refresh cookie, Origin, CSRF; `200` access JWT/expiry plus rotated cookie |
| `POST /auth/logout` | Refresh cookie, Origin, CSRF; `204`; idempotent cookie clearing/revocation |
| `POST /auth/logout-all` | Bearer session; `204`; revoke all sessions |
| `POST /auth/invitations/accept` | Public, rate limited; token/password; `204`; generic unusable-token error |
| `POST /auth/password/forgot` | Public, rate limited; email; generic `202` |
| `POST /auth/password/reset` | Public, rate limited; token/newPassword; `204` and session revocation |
| `POST /auth/password/change` | Bearer; currentPassword/newPassword; `204` and require login again |
| `GET /me` | Bearer; safe current identity, scope, organization, roles, source-separated grants |
| `PATCH /me` | Bearer and version; displayName only |

### Platform organization and staff administration

Every route requires an active platform-scoped super-admin session; organization permissions cannot satisfy this boundary. Record platform audit and organization audit where an organization is affected.

| Method / path | Contract |
| --- | --- |
| `POST /platform/organizations` | Idempotency key; name/slug/initialAdminEmail/initialAdminDisplayName; `201` plus initial invitation status |
| `GET /platform/organizations` | Paginated metadata/status summaries |
| `GET /platform/organizations/{id}` | Safe metadata and initial invitation delivery status |
| `PATCH /platform/organizations/{id}` | Version; name only; slug is immutable in MVP |
| `PUT /platform/organizations/{id}/status` | Version; status/reason; suspend or resume a previously activated organization |
| `GET /platform/organizations/{id}/roles` | Paginated organization-local roles for staff invitation/recovery |
| `GET /platform/organizations/{id}/staff` | Paginated safe account summaries |
| `POST /platform/organizations/{id}/staff` | Idempotency key; email/displayName/roleIds/directPermissionKeys; `201` invitation workflow |
| `POST /platform/organizations/{id}/staff/{accountId}/invitation` | Idempotency key; pending accounts only; `202` delivery queued |
| `PUT /platform/organizations/{id}/staff/{accountId}/status` | Version; status/reason; account lifecycle and final-admin rules apply |
| `PUT /platform/organizations/{id}/staff/{accountId}/roles` | Version; roleIds/reason; explicit audited recovery; same-organization role validation |
| `GET /platform/audit-events` | Paginated platform audit; filter organization/actor/action/time |

Platform staff creation may grant organization permissions but never changes account scope. No raw invitation tokens appear in API responses. The initial organization administrator uses the same invitation endpoints as other invited accounts.

### Organization profile, staff, and roles

Derive organization ID exclusively from authenticated identity. These routes accept no organization-routing header, query parameter, or body override.

| Method / path | Permission and contract |
| --- | --- |
| `GET /organization` | `organization.read`; current profile |
| `PATCH /organization` | `organization.update`; version; allowlisted profile fields |
| `GET /permissions` | `role.read` or `staff.permissions.manage`; implemented assignable organization keys |
| `GET /staff` | `staff.read`; paginated, filters status/email/role |
| `POST /staff` | `staff.invite` plus grant checks; idempotency key; email/displayName/roleIds/directPermissionKeys; `201` |
| `GET /staff/{id}` | `staff.read`; safe identity and direct/inherited/effective grants |
| `PATCH /staff/{id}` | `staff.update`; version; displayName only |
| `PUT /staff/{id}/status` | `staff.suspend`; version; status/reason |
| `POST /staff/{id}/invitation` | `staff.invite`; idempotency key; pending only; `202` |
| `PUT /staff/{id}/roles` | `staff.roles.manage`; version; complete roleIds replacement plus delegation checks |
| `PUT /staff/{id}/permissions` | `staff.permissions.manage`; version; complete directPermissionKeys replacement |
| `GET /roles` | `role.read`; paginated roles with permissions/protected flag/version |
| `POST /roles` | `role.manage`; name/description/permissionKeys plus grant checks; `201` |
| `GET /roles/{id}` | `role.read`; role and grants |
| `PATCH /roles/{id}` | `role.manage`; version; metadata and complete permissionKeys when supplied |
| `DELETE /roles/{id}` | `role.manage`; version; `204` only for unassigned custom roles |
| `GET /audit-events` | `audit.read`; current-organization events only |

Because roles derive their grants dynamically, use one account authorization version for role assignments and direct grants, and a role version for role definition edits. Return these with the corresponding GET resources. Mutating an assigned role must check the role version and delegation rules; it does not need to rewrite every holder's JWT or account row.

### Errors

Use a common safe envelope, including Spring Security/filter errors:

```json
{
  "code": "STALE_VERSION",
  "message": "This record has changed. Reload and try again.",
  "traceId": "request-trace-id",
  "fieldErrors": {}
}
```

`400` validation/unusable invitation, `401` invalid credentials/token/session, `403` valid identity lacks permission or organization access, `404` resource absent inside authorized scope, `409` duplicate/protected/final-admin/lifecycle/idempotency conflict, `412` stale version, `428` missing precondition, `429` rate limit with `Retry-After`, `503` required database/service unavailable. Database errors must fail closed. Never return another organization's existence, SQL, secrets, or raw tokens.

## 9. Bootstrap, audit, and operational behavior

Create the first platform super admin with a restricted, explicit deployment command that reads secrets securely and records an audit event. It must be safe to rerun and refuse implicit promotion of an existing organization account. Do not expose bootstrap through HTTP or run automatic password/account creation on every startup. Further platform account provisioning is outside this MVP's public API.

Audit organization/staff creation and status, invitation lifecycle, role definitions/assignments, direct grants, recovery actions, login security outcomes, refresh replay, and session revocation. Record actor, organization, target, action, timestamp, trace ID, result, and safe before/after changes. Security events with no authenticated actor use an appropriate anonymous/system actor; do not invent an account identity. Restrict and minimize failed-login identifiers according to retention policy.

Commit successful administrative audit events with their mutations. Persist security failures/replay revocation in transactions that survive the rejected request. Never log passwords, cookies, bearer tokens, invitation/reset URL tokens, or outbox secrets. Store token hashes in dedicated credential tables, not audit payloads.

Namespace future caches, files, exports, and queue payloads by organization ID. Background work receives explicit validated organization/account IDs; thread-local context and logging MDC are not durable authorization. Clear context after jobs as well as requests.

Back up the shared database with encryption and test restore. Keep signing/encryption keys recoverable through controlled secret management separately. Define retention, backup recovery objectives, monitoring, and dead-letter handling. Outbox retries use bounded backoff, safe delivery status, and operator-visible failures; an email outage must not duplicate staff or roll back already committed organization creation.

## 10. Frontend workflows

1. **Login:** submit email/password, retain access token in memory, use returned scope to select dashboard, and load `/me` for current grants.
2. **Onboard organization:** platform administrator creates it and sees pending/sent/failed invitation status. They can safely retry creation using the same idempotency key or resend an existing invitation.
3. **Activate:** recipient captures invitation token, removes it from browser URL history, sets a password, then logs in separately. Exclude token URLs from analytics/referrers and avoid third-party scripts on token exchange pages.
4. **Manage staff:** load roles/catalog, invite staff, and show invitation/account states distinctly. Display direct and inherited permissions separately.
5. **Edit access:** send current version; on `412`, reload and let the user reconcile. On permission-related `403`, reload `/me` and update navigation.
6. **Reload/expiry:** obtain CSRF through the refresh cookie, perform one serialized refresh, then load `/me`. On failed refresh, clear local identity and show login.
7. **Logout/recovery:** clear memory on logout; password reset/change requires a fresh login. UI hiding never substitutes for server checks.

## 11. Build sequence and acceptance criteria

| Stage | Deliverable and completion checks |
| --- | --- |
| 1. Foundation | Flyway schema, global email normalization/uniqueness, scope constraints, tenant-qualified repositories, safe errors, explicit bootstrap command |
| 2. Auth | Email-only discovery for both account scopes, active-state checks, JWT validation, sessions, refresh families, cookies/CSRF/Origin/CORS, logout/recovery, focused concurrency tests |
| 3. Organizations | Transactional organization/admin setup, idempotency, invitation acceptance, durable outbox, status and audit APIs |
| 4. Staff and grants | Tenant-local roles, direct grants, protected admin, delegation, version preconditions, staff invitations/suspension, platform recovery |
| 5. Release verification | Isolation suite, browser session tests, rate limits, key rotation exercise, backup restore, operational email failure tests, platform MFA decision/implementation for public production |

Minimum acceptance tests:

- The same login request shape works for platform and organization accounts; no tenant code or account-type selection is needed. Concurrent creation of the same normalized email yields one account and a safe conflict.
- Organization A cannot read/mutate B's staff, roles, audit IDs, or future resources through ID/header/body manipulation. Both the repository boundary and HTTP boundary are tested.
- Organization tokens fail on platform routes; platform tokens fail on organization routes. A permission key cannot override account scope. Missing tenant context never selects a default organization.
- Identically named roles in different organizations remain independent. Direct and inherited grants combine correctly and removing one source preserves the other.
- Delegated managers cannot create, edit, or assign grants they lack, including grants supplied during staff/role creation. Protected admin and platform privileges cannot be bypassed.
- Concurrent demotions/suspensions cannot remove the final active organization admin. Stale staff/role/profile/status changes return `412`.
- Logout, password changes/reset, and suspension invalidate existing sessions on the next protected request. Reactivation does not revive them.
- Concurrent refresh yields one successor; replay commits family revocation; absolute expiry never slides. JWT signature/algorithm/issuer/audience/required-claim/scope/binding failures are rejected.
- Cookie operations reject untrusted origins and missing/invalid CSRF. Refresh tokens never enter JSON responses, logs, or JavaScript-readable storage.
- Expired/reused/replaced invitation/reset tokens fail; pending users cannot log in or be manually activated. Initial admin acceptance activates only its own organization.
- Email retries and request retries do not duplicate organizations, accounts, grants, or invitations. A committed privilege change always has its audit event.
- Identity/database outages fail closed; background work preserves organization scope; encrypted backup restore and key rotation are exercised.

Expiry values and shared storage are explicit MVP defaults. Finalize deployment origins, password work factor, rate-limit thresholds, audit retention, recovery objectives, secret management, and platform MFA before public production. This blueprint is a development starting point, not production sign-off.
