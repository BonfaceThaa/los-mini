# Identity, tenancy, and platform administration: implementation versus ADR 0001

Review date: 2026-10-17. Reference: the supplied **ADR 0001: Multitenant organizations, identity, and access**, dated 2026-10-17, status Proposed, for phase one of RMS.

This is a source-code review of the current Mini-LOS repository, not a claim about a running deployment or a security certification. The ADR describes a different product domain; organization maps to LOS tenant for this comparison. Its rental permissions are examples, not a proposed replacement for the LOS permission catalog. No application behavior was changed for this review.

## Overall assessment

The app provides a useful MVP foundation: a Spring Boot modular monolith, shared relational storage, tenant-local custom roles, a fixed permission catalog, BCrypt passwords, hashed refresh tokens, and explicit platform tenant-management workflows. The notes offer stronger session, account lifecycle, delegation, and operational contracts.

The best default for this LOS MVP is to retain the shared database and existing API/service structure while adopting the notes' security invariants. Database-per-organization is a separate product and operations decision; it is not a prerequisite for correcting authorization. If physical organization database separation is a confirmed requirement for the target product, the current topology does not satisfy it and this recommendation must change.

The main gaps are substantive: permissions are trusted from access JWTs, refresh does not recheck active account/tenant state, refresh rotation lacks concurrency and family-replay controls, platform scope is not a universal route boundary, and delegated administrators can grant permissions they do not hold. The internal subscription routes contain a concrete tenant-boundary weakness described below.

## Evidence map

Links are relative to this document and identify the implementation reviewed.

| Area | Primary evidence |
| --- | --- |
| Auth API and payloads | [AuthController](../src/main/java/com/credvenn/lm/auth/AuthController.java), [AuthDtos](../src/main/java/com/credvenn/lm/auth/AuthDtos.java), [AuthService](../src/main/java/com/credvenn/lm/auth/AuthService.java) |
| JWT and request identity | [JwtService](../src/main/java/com/credvenn/lm/security/JwtService.java), [JwtAuthenticationFilter](../src/main/java/com/credvenn/lm/security/JwtAuthenticationFilter.java), [TenantContext](../src/main/java/com/credvenn/lm/security/TenantContext.java), [SecurityConfig](../src/main/java/com/credvenn/lm/security/SecurityConfig.java) |
| Refresh persistence | [RefreshTokenSession](../src/main/java/com/credvenn/lm/auth/RefreshTokenSession.java), [repository](../src/main/java/com/credvenn/lm/auth/RefreshTokenSessionRepository.java) |
| Bootstrap and defaults | [BootstrapDataInitializer](../src/main/java/com/credvenn/lm/security/BootstrapDataInitializer.java), [RoleTemplateService](../src/main/java/com/credvenn/lm/security/RoleTemplateService.java) |
| Users and roles | [UserService](../src/main/java/com/credvenn/lm/user/UserService.java), [UserDtos](../src/main/java/com/credvenn/lm/user/UserDtos.java), [RoleService](../src/main/java/com/credvenn/lm/security/RoleService.java), [PermissionCatalog](../src/main/java/com/credvenn/lm/security/PermissionCatalog.java) |
| Platform administration | [TenantController](../src/main/java/com/credvenn/lm/tenant/TenantController.java), [TenantService](../src/main/java/com/credvenn/lm/tenant/TenantService.java), [InternalTenantUserController](../src/main/java/com/credvenn/lm/platform/InternalTenantUserController.java), [InternalTenantRoleController](../src/main/java/com/credvenn/lm/platform/InternalTenantRoleController.java) |
| Storage/configuration | [V1 foundation migration](../src/main/resources/db/migration/V1__auth_and_tenant_foundation.sql), [application.yaml](../src/main/resources/application.yaml), [pom.xml](../pom.xml) |
| Adjacent platform surface | [TenantSubscriptionController](../src/main/java/com/credvenn/lm/subscription/TenantSubscriptionController.java), [TenantSubscriptionService](../src/main/java/com/credvenn/lm/subscription/TenantSubscriptionService.java), [CurrentTenantSubscriptionController](../src/main/java/com/credvenn/lm/subscription/CurrentTenantSubscriptionController.java) |

## Current request and identity model

1. A client posts `identifier`, `password`, and optionally `tenantCode` to `/api/v1/auth/login`.
2. With a tenant code, the service resolves an active tenant and searches that tenant's users by username or email. Without it, the service searches platform users, whose `tenant_id` is null.
3. Login checks the user's active flag and BCrypt password, updates last login, creates a hashed refresh-token record, and returns access token, raw refresh token, expiry, and profile JSON.
4. The bearer filter verifies the JWT and constructs the principal and authorities from its claims. It sets Spring Security context, `TenantContext`, and logging context.
5. Controller method security checks authorities. Tenant user/role services derive the tenant from the principal and perform tenant-qualified lookups.
6. The filter clears security, tenant, and logging context in `finally`.

The filter does **not** load the current user, tenant, session, or permissions. `TenantContext` is a thread-local holder, not a database router or automatic query filter. Shared-table isolation depends on each access path enforcing tenant scope.

The login tenant code is a discovery input validated by the server; it is different from permitting an arbitrary tenant override on authenticated business requests. However, authoritative account-to-tenant binding is not reloaded on each request as the ADR proposes.

## Shared database and ownership

| Topic | Current app | ADR | MVP assessment |
| --- | --- | --- | --- |
| Topology | One configured application datasource; identity, roles, refresh sessions, tenants, and LOS business tables share storage | Shared control DB plus one authorization/business DB per organization | App is simpler to operate; notes provide stronger database credential boundaries |
| Identity | `app_users.tenant_id` is nullable for platform accounts; tenant users have one tenant | Explicit platform/organization account scope, immutable organization membership | Retain one-tenant membership; make scope an enforced invariant |
| Relationships | Local foreign keys between tenants, users, roles, permissions, refresh sessions | Cross-database account references need reconciliation | Shared transactions and FKs are valuable MVP simplifications |
| Tenant isolation | Explicit predicates, such as `findByIdAndTenantId`; repositories also expose generic JPA methods | Verified identity selects a tenant database, with no fallback | Current approach needs systematic coverage; no universal isolation guarantee follows from the context holder |
| Credentials | One datasource credential configured for the app | Separate tenant runtime credentials and privileged provisioning credential | Current runtime DB access spans tenant data; DB separation would reduce this exposure |
| Migrations | Versioned Flyway SQL in one migration stream; runtime enablement is configuration-dependent | Per-organization migration registry and compatibility gates | Keep the current model unless separate databases are required |
| Backup/restore | Source review does not establish backup objectives or tested restore | Coordinated control/tenant restore and reconciliation | Define and exercise restore for either topology |

The foundation uses tenant-scoped username/email uniqueness, not globally unique email. The same email can therefore identify separate tenant accounts. MariaDB nullable composite uniqueness does not by itself ensure platform uniqueness where `tenant_id` is null; bootstrap checks are application checks, not an equivalent database constraint.

Roles and users are joined by IDs. Foreign keys alone do not prove that an assigned role belongs to the user's tenant; `resolveTenantRoles` supplies that application validation. Strengthen database constraints where practical and test every alternate write path.

Fineract remains a separate downstream accounting system. The LOS tenant's `fineractTenantId` is used by the backend gateway for the `Fineract-Platform-TenantId` header. This mapping does not imply a separate LOS database per tenant. Keep the adapter boundary required by AGENTS.md; do not expose Fineract credentials or move routing to the frontend.

## Authentication, JWTs, and sessions

| Capability | Current implementation | Notes / recommended direction |
| --- | --- | --- |
| Login discovery | Tenant code plus username/email; omitted code means platform login | Globally unique email and one login entry without tenant selection. This is a product tradeoff, not inherently a security upgrade |
| Password hashing | BCrypt via Spring Security | Keep |
| Account lifecycle | Active boolean; administrators supply passwords during account creation | Invitations, activation, recovery, explicit lifecycle states |
| Access claims | `sub`, `iss`, `iat`, `exp`, tenant ID, username, email, roles, authorities | Minimal identity/session/scope claims; current grants loaded server-side |
| Signing | HMAC keys; JJWT selects a compatible signing algorithm via `signWith(key)` | Explicit accepted algorithm policy; proposed RS256, trusted key IDs and rotation |
| Validation | Signature and library time validation; configured issuer checked when nonblank | Also require audience, scope, session, tenant binding, and required claims |
| Token lifetime | Access and refresh durations supplied through configuration | Proposed 10-minute access, 7-day inactivity, 30-day absolute refresh lifetime; values remain decisions |
| Browser transport | Raw refresh token returned in JSON and accepted in request JSON | Access in memory; refresh in Secure/HttpOnly cookie, with Origin/CSRF protections |
| Refresh storage | SHA-256 hash of an opaque value made from two UUIDs; tracks expiry, revocation, replacement, IP, user agent | Good hash-at-rest basis; add session/family lifecycle and atomic consumption |
| Rotation | Revoke old row and insert replacement in a transaction; expiry restarts at refresh time | Consume atomically; cap absolute lifetime; revoke family on replay |
| Revocation | Logout revokes the submitted refresh record only | Check linked session on every protected request; logout invalidates issued access JWTs |
| User/tenant suspension | Login checks state; request filter and refresh do not | Recheck state during refresh and protected requests; revoke affected sessions |
| Permission updates | Existing JWT retains old grants until expiry; refresh issues a new snapshot | Changes effective on next request |
| Recovery/change | Admin password-update routes exist; no corresponding session revocation | Self-service reset/change, hashed single-use tokens, revoke sessions |
| MFA/rate limiting | No dedicated MFA or auth rate-limiting implementation found in reviewed modules | Notes recommend platform MFA before production and limits on sensitive auth routes |

Important consequences:

- Logout does not invalidate an existing access JWT. It has no session ID to check against the revoked row.
- A deactivated user or tenant can continue refreshing a valid refresh token: `loadUser` checks identity/tenant association, but not either active flag. Suspension is therefore weaker than merely waiting for access expiry.
- A transaction around refresh does not serialize concurrent requests by itself. The repository has no refresh-row lock, version, or conditional consume operation. Concurrent refreshes may create multiple valid successors; this is a code-level concurrency risk, not a tested exploit in this review.
- Reuse of a revoked token returns an error but does not revoke its successor/family. Replacement linkage is not family-replay detection.
- The login/refresh response aggregates all assigned roles, while JWT generation filters inactive roles. Responses can advertise permissions the JWT does not grant. `/auth/me` reflects the JWT snapshot rather than authoritative current state.

CSRF is disabled globally. That is consistent with the current manually supplied bearer/body-token protocol; changing refresh to cookies must include the notes' CSRF and Origin design in the same change. CORS is configurable, but its code fallback permits all origins and headers with credentials off unless configured otherwise. Deployment origin policy and actual browser storage cannot be confirmed here: no frontend implementation was found in the reviewed tree. Do not assume refresh tokens are already stored safely in the browser.

HMAC can be adequate for a single-service MVP with strong managed secrets and an explicit validation policy. RS256 is useful for verification-key distribution and rotation, but changing algorithms alone does not solve missing session or scope checks. Never copy development secrets into documentation.

## Super admin and platform boundaries

The app models `SUPER_ADMIN` as a seeded platform role on an `app_users` record with no tenant. It receives every permission in `PermissionCatalog`. `INTERNAL_STAFF` is a second platform role with a smaller onboarding, subscription-view, and statement-inbox permission set.

Startup seeds permissions/platform roles, reprovisions predefined roles for all tenants, and creates a bootstrap super admin if the configured platform email or username is absent. Existing matching users are not automatically reset or promoted by this routine. There is no public super-admin registration endpoint in the reviewed auth/controller surface.

This is convenient for an MVP, but differs from a controlled, one-time deployment bootstrap. Tenant role provisioning replaces predefined permissions and reactivates those roles on every startup, so edits to their grants do not persist reliably across restart. Document defaults as code-owned or stop overwriting tenant-managed state.

Platform scope is not an explicit JWT claim/audience checked on every internal route. Most platform management endpoints rely on authorities such as `TENANT_MANAGE_ALL`. Ordinary user/role services require a non-null tenant, which prevents a null-tenant super admin from simply using those tenant APIs. This is useful, but does not establish the ADR's universal two-way scope boundary.

Two concrete boundary concerns require attention:

1. **Internal subscriptions:** `TENANT_ADMIN` and `READ_ONLY` receive `TENANT_SUBSCRIPTION_VIEW`. Internal subscription history/current endpoints accept `{tenantId}` and authorize that permission. `TenantSubscriptionService.history/getCurrent` use the supplied tenant without comparing it with the actor's tenant. A tenant user holding this permission can reach another tenant's subscription data if its ID is known. The notes-update route also accepts the view permission and the service does not enforce actor tenant equality. Fix platform-scope checks and reserve ordinary tenant access for `/subscription/current`.
2. **Catalog boundary:** `RoleService` excludes four platform permissions from tenant assignment, but does not exclude `SUBSCRIPTION_PLAN_MANAGE`. A tenant role manager can potentially place this permission on a tenant role, and internal subscription-plan mutations check that authority. Use an explicit permission scope/assignability catalog and enforce platform account scope independently of grants.

These are findings from controller-to-service tracing; no live requests were made. An `/internal` path name is not a security boundary.

## API comparison

All paths below are relative to `/api/v1`. Current create operations generally return synchronous `200` in the identity/tenant controllers; the notes specify more differentiated statuses.

| Concern | App today | Notes |
| --- | --- | --- |
| Login / refresh / logout | `POST /auth/login`, `/auth/refresh`, `/auth/logout`; refresh/logout take JSON token | Same route names, cookie/CSRF protocol |
| Current identity | `GET /auth/me` | `GET /me`, `PATCH /me`, grants separated by source |
| Additional auth APIs | No invitation acceptance, forgot/reset password, self-service password change, logout-all, or CSRF endpoint found | Explicit contracts for each |
| Tenant onboarding | `POST /internal/tenants`: code, name, Fineract mapping, initial admin credentials; local transactional creation | `POST /platform/organizations`: async provisioning and invitation, operation ID |
| Tenant metadata | Internal list/get, status, KYC mode, statement-analysis mode | Platform metadata/status plus organization self-service profile |
| Platform user administration | `/internal/tenants/{tenantId}/users`: list/get/create/update, roles, password, status, DELETE as deactivation | Scoped platform invitation/status/recovery workflows; no administrator-chosen password |
| Platform role administration | `/internal/tenants/{tenantId}/roles`: list/get/create/update, permission replacement, deactivate | Narrower platform role listing; most role management stays organization-local |
| Tenant users | `/users`: paged list/get/create/update, role replacement, password/status changes, soft deactivation | Invitation-based creation, source-separated grants, pending lifecycle |
| Tenant roles | `/roles`: paged list/get/create/metadata update, permission replacement, deactivate | Protected administrator role; guarded custom role deletion and delegation |
| Permission catalog | `GET /permissions`, fixed uppercase LOS codes | Backend-owned implemented permission catalog with grouping/descriptions |
| Direct user grants | No direct-grant model/API found | Union of role grants and direct user grants |
| Async operations | No identity provisioning operation resource or idempotency-key handling found | `202`, durable operation polling, idempotency conflicts |
| Audit API | No general platform/tenant identity audit endpoint found | Scoped searchable audit events |

Tenant user/role lists are paginated; platform tenant/user/role lists return unpaginated lists. The current pagination helper caps size at 200, versus 100 in the notes. API spelling and pagination limits are lower-priority alignment choices than authorization semantics.

## Roles, grants, and privilege delegation

Tenant custom roles and fixed permission keys are already strong matches. Role resolution rejects cross-tenant and inactive roles; ordinary tenant user/role lookups pair resource ID with the actor's tenant. Role code uniqueness is tenant-local. The app does not enforce the notes' case-insensitive unique role display names; the unique identity is the code.

The following protections are missing or partial:

- `ROLE_CREATE` can create a role with permission codes without requiring the separate permission-assignment authority or possession of every grant. `ROLE_ASSIGN_PERMISSIONS` can replace grants without checking the actor possesses them.
- `USER_CREATE` accepts initial role IDs without also requiring `USER_ASSIGN_ROLES`, and role assignment does not validate the actor's complete effective grant set against the role's grants.
- `systemRole` prevents deactivation, but does not prevent metadata or permission edits. `TENANT_ADMIN` assignment/removal has no special existing-admin requirement.
- There is no transactional final-active-administrator invariant in the reviewed identity services. Administrators can be deactivated or lose their role without a protected recovery guard.
- User creation/role replacement require nonempty role IDs. There is no equivalent of a role-free authenticated account with no business access through those requests.
- User updates permit username/email changes, whereas the notes defer email change until verified ownership/recovery rules exist.
- No identity role/grant `If-Match` contract or optimistic version check was found. Concurrent replacements may overwrite each other.

The notes' delegation and administrator protections are good MVP controls even if direct grants are deferred. Role-only access is a reasonable MVP simplification only when user-specific exceptions are not a confirmed requirement. If they are required, implement the additive direct-grant union and return direct/inherited/effective sets distinctly.

## Provisioning, operations, and audit

Tenant creation currently saves the tenant, seeds local roles, and creates the initial admin in one local transaction. It stores a supplied Fineract tenant mapping; this code does not provision a separate LOS database or establish that the downstream Fineract tenant exists. There is no invitation delivery or external call in this local onboarding method, so making this specific operation synchronous is a reasonable shared-DB MVP choice.

The ADR's durable provisioning state machine becomes necessary when onboarding spans databases, credentials, or email delivery. Introduce an outbox/idempotency mechanism when adding those side effects; do not promise durable progress from an in-memory async task alone.

The existing async executor's decorator copies logging MDC, not `TenantContext` or Spring authentication. Jobs must receive and validate tenant identity explicitly; logging context is not authorization. This review does not certify tenant handling for every existing job, cache, export, or storage operation.

`AuditableEntity` records creation/update timestamps; that does not provide actor, target, result, reason, or before/after grant history. Domain-specific origination/logbook audits exist, but do not establish identity/platform audit coverage. Adopt durable audit records for user/role changes, suspensions, authentication security events, and platform recovery. With shared storage these can commit in the same transaction as the privilege change.

Database availability, credential boundaries, encrypted backups, restore drills, key rotation, retention, and MFA deployment are not established by this source review. The notes' explicit failure and recovery requirements remain useful acceptance criteria for either topology.

## Error and frontend contract differences

The global handler maps invalid login/refresh conditions raised as `ForbiddenOperationException` to `403`, unlike the notes' generic authentication `401`. Tenant discovery can also reveal not-found/inactive states before password validation. JWT filter failures return `401` with a small message object. Global errors use timestamp/status/error/message/path/validationErrors; they do not match the notes' stable code/traceId/fieldErrors envelope. Unexpected controller exceptions receive a generic client message, which is a useful existing safeguard.

There is no identity-specific `412` stale-edit contract, `429` rate-limit contract, or explicit `503` unavailable-identity contract in the reviewed modules. Spring filter failures may have a different envelope from controller errors. Standardize these before the frontend depends on refresh-on-401 behavior.

For the current app, the frontend must supply tenant code for tenant login and use `/auth/me`; it must not assume the notes' `/me`, invitations, cookies, scope field, or operation polling already exist. UI visibility can use returned authorities, but must not replace backend authorization. After adopting authoritative grants, reload the current profile after access changes and relevant denials.

## Recommended MVP decisions

| Decision | Best source | Rationale / condition |
| --- | --- | --- |
| Keep one Spring Boot modular monolith | Both | Fits the app's existing boundaries and avoids premature service operations |
| Keep shared LOS tables initially | App | Lowest provisioning/migration/transaction overhead; conditional on no physical-separation requirement and verified isolation |
| Keep tenant-local roles and backend-owned keys | Both | Already supports different grants for identically named roles |
| Keep tenant-code login initially | App | Preserves tenant-scoped email identities; change only with an explicit global-email product decision |
| Enforce account scope independently of permissions | Notes | Prevent platform authority from becoming a tenant-assignable capability |
| Check current account/tenant/session/grants | Notes | Makes suspension, logout, and access changes meaningful |
| Harden refresh families and browser transport | Notes | Existing hashes/rotation are a useful starting point but incomplete |
| Protect delegation and final administrator | Notes | Prevent privilege escalation and administrative lockout |
| Keep local transactional onboarding while entirely local | App | Async database provisioning adds no value until there are durable external steps |
| Add invitations and recovery for self-service rollout | Notes | Removes administrator-selected passwords and establishes activation ownership |
| Defer direct grants if not required | App's simpler model | Additive direct grants are justified when individual exceptions are confirmed |
| Add consistent errors, audit, and stale-write checks | Notes | Makes access changes reviewable and frontend behavior predictable |

Suggested implementation order:

1. Close internal subscription tenant overrides; enforce platform scope across internal routes; classify every catalog permission by scope and assignability.
2. Link JWTs to revocable sessions, check active user/tenant on refresh and requests, load current grants, and revoke sessions on password/status changes.
3. Make refresh consumption atomic, implement family replay revocation and absolute expiry, and align browser transport/CORS/CSRF as one contract.
4. Enforce grant-subset checks at creation, editing, and assignment; protect the administrator role and final active administrator transactionally; add identity audit and stale-write protection.
5. Add invitation/recovery flows, rate limits, safe authentication errors, and an explicit production platform-MFA decision.
6. Revisit separate tenant databases only with confirmed isolation requirements and a provisioning/migration/pooling/restore plan. Decide global email uniqueness and direct grants separately.

## Verification scope and follow-up acceptance checks

This document was checked against source, migrations, DTOs, controller annotations, and configuration wiring. No runtime credentials, production configuration, or database contents were used. No application tests were run for this documentation-only change. The test tree contains domain/API tests, but no dedicated auth/JWT/refresh suite was identified by filename; this is not a claim that all existing tests lack incidental security assertions.

Add focused integration/concurrency coverage for:

- Tenant A cannot use B's user/role IDs or internal subscription paths; tenant tokens cannot mutate platform plans, regardless of assigned authorities.
- Platform tokens fail on organization-only routes; missing context fails closed; service tokens cannot substitute for user/platform scope.
- Logout, password change, and account/tenant suspension reject old access and refresh credentials on the next relevant request.
- Concurrent refresh produces one successor; replay revokes its family; absolute session lifetime cannot slide indefinitely.
- Delegated managers cannot grant absent permissions, alter protected administrators, or concurrently remove the final active administrator.
- Current-profile and JWT responses agree on inactive roles; stale grant replacement is rejected; successful privilege changes have durable audit events.
- JWT algorithm/issuer/audience/scope/tenant-binding failures are rejected; cookie auth, if adopted, enforces Origin and CSRF checks.
- Tenant scoping remains correct for background jobs, storage, exports, caches, and restore workflows.

The ADR's remaining deployment and operational acceptance criteria should be retained as a backlog, with database-routing-specific checks applied only if that topology is selected.
