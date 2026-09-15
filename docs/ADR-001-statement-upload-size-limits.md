# ADRs

## Table Of Contents
1. [ADR-001: Increase Statement Upload Size Limits in Nginx and Spring Boot](#adr-001-increase-statement-upload-size-limits-in-nginx-and-spring-boot)
2. [ADR-002: Use Spring Config Import for Local .env Property Loading](#adr-002-use-spring-config-import-for-local-env-property-loading)
3. [ADR-003: Gate Offers Ready on Both KYC and Statement Completion](#adr-003-gate-offers-ready-on-both-kyc-and-statement-completion)
4. [ADR-004: Persist Multiple Statement OTP Candidates and Retry Statement Analysis Safely](#adr-004-persist-multiple-statement-otp-candidates-and-retry-statement-analysis-safely)
5. [ADR-005: Isolate Statement Provider Calls from Database Transactions](#adr-005-isolate-statement-provider-calls-from-database-transactions)
6. [ADR-006: Finalize Approved KYC After Commit with Lock Retries](#adr-006-finalize-approved-kyc-after-commit-with-lock-retries)

# ADR-001: Increase Statement Upload Size Limits in Nginx and Spring Boot

## Status
Accepted

## Date
2026-08-01

## Context
The inbound statement upload endpoint `POST /api/v1/service/statements/mpesa/upload` was failing when larger multipart files were sent through the Nginx reverse proxy.

The Nginx error log showed:

```text
client intended to send too large body: 1276582 bytes
```

This meant the request was being rejected by Nginx before it reached the Spring Boot application.

The affected endpoint is implemented in [ServiceStatementUploadController.java](../src/main/java/com/credvenn/lm/statementinbox/ServiceStatementUploadController.java).

## Decision
Increase request body limits at both layers that can reject multipart uploads:

- Nginx proxy layer using `client_max_body_size`
- Spring Boot multipart handling using:
  - `spring.servlet.multipart.max-file-size`
  - `spring.servlet.multipart.max-request-size`

This ensures upload behavior is consistent and avoids a situation where the proxy accepts the request but the application rejects it later.

## Changes Made
### Nginx
Configured a larger body size limit for the API host or upload route:

```nginx
client_max_body_size 10m;
```

### Spring Boot
Configured explicit multipart limits in `application.yaml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

## Rationale
- The failing upload size was approximately `1.22 MB`, which is larger than the common default `1m` Nginx limit.
- Explicit application-level limits make behavior predictable across environments.
- Keeping proxy and application limits aligned reduces operational confusion during troubleshooting.

## Verification
After applying the change:

1. Validate and reload Nginx

```bash
sudo nginx -t
sudo systemctl reload nginx
```

2. Restart the Spring Boot application if `application.yaml` changed

3. Test the upload endpoint through the public Nginx URL with a multipart file larger than the previously failing size

Example:

```bash
curl -i -X POST "https://api.credvenn.com/api/v1/service/statements/mpesa/upload" \
  -H "Authorization: Bearer <SERVICE_TOKEN>" \
  -F "destinationEmail=test@credvenn.com" \
  -F "messageId=test-msg-001" \
  -F "receivedAt=2026-08-01T10:00:00Z" \
  -F "file=@/path/to/test-statement.pdf"
```

Success criteria:

- No `413 Request Entity Too Large`
- No Nginx `client intended to send too large body` error
- Request reaches the backend and returns a normal application response

## Consequences
### Positive
- Mpesa statement uploads larger than the old proxy limit can now reach the backend
- Upload-related failures are easier to diagnose because both proxy and app limits are explicit

### Trade-offs
- Larger uploads increase bandwidth, memory, and storage pressure
- Future size changes must be made in both Nginx and Spring Boot if we want limits to remain aligned

## Operational Notes
- This endpoint is protected by `SERVICE_STATEMENT_UPLOAD`
- It requires a service-authenticated token, not a normal user token
- If upload failures recur, inspect both:
  - Nginx error logs
  - Spring Boot application logs

## References
- Endpoint: `POST /api/v1/service/statements/mpesa/upload`
- Controller: [ServiceStatementUploadController.java](../src/main/java/com/credvenn/lm/statementinbox/ServiceStatementUploadController.java)
- App config: [application.yaml](../src/main/resources/application.yaml)

# ADR-002: Use Spring Config Import for Local .env Property Loading

## Status
Accepted

## Date
2026-08-02

## Context
The application configuration was externalized into a local `.env` file for environment-specific settings such as credentials, URLs, ports, and API keys. For local execution, there was a need to let Spring Boot read those values without requiring every variable to be manually exported into the shell first.

## Decision
Use Spring Boot config import to load the `.env` file as a properties-style configuration source:

```properties
spring.config.import=optional:file:.env[.properties]
```

This allows Spring-managed configuration to resolve values from a local `.env` file when running the application from the project working directory.

## Rationale
- Keeps local environment-specific values out of `application.yaml`
- Reduces the need to manually export variables for ordinary local runs
- Uses Spring Boot's native config import mechanism instead of introducing a separate dotenv library

## Consequences
### Positive
- Local development setup becomes simpler when running from the project root
- Sensitive and environment-specific values can remain outside the committed Spring config

### Trade-offs
- `.env` must be formatted as a `.properties`-style file with plain `KEY=value` lines
- Relative `.env` loading depends on the process working directory
- Complex values are harder to manage safely in `.env` than in YAML

## Operational Notes
- `spring.config.import` makes values available to Spring property binding only
- It does not create real OS environment variables for the Java process
- If non-Spring scripts, libraries, or runtime hooks require true process environment variables, use the service manager to load them, for example with `EnvironmentFile=` in `systemd`
- For deployment, an absolute import path is safer than relying on the working directory

## Verification
- Start the application from the project root with the `.env` file present
- Confirm that datasource, JWT, and integration settings resolve without shell-exporting variables first
- Confirm that missing `.env` does not fail startup when `optional:` is used

## References
- App config: [application.yaml](../src/main/resources/application.yaml)
- Local env file: [.env](../.env)

# ADR-003: Gate Offers Ready on Both KYC and Statement Completion

## Status
Accepted

## Date
2026-08-04

## Context
The loan application workflow previously allowed statement analysis to auto-trigger and independently move the application toward `OFFERS_READY` even when KYC was still pending or in manual review.

This created two problems:
- application status could move into statement states before KYC approval and Fineract client provisioning were complete
- the frontend could receive no offers without a clear explanation of which prerequisite was still missing

The desired behavior is to keep the operational convenience of automatic statement processing while making offer availability depend on all mandatory prerequisites.

## Decision
- let KYC and statement analysis run independently
- make `OFFERS_READY` conditional on both being complete
- require a Fineract client to exist before offers become ready
- expose unmet prerequisites explicitly through the eligible-products API so the frontend can explain why offers are not yet available

## Rationale
- Statement upload should not be blocked just because KYC is still being reviewed.
- KYC approval, including manual approval, remains the authoritative trigger for client provisioning.
- `OFFERS_READY` should represent business readiness, not just statement success.
- Explicit readiness flags reduce ambiguity for frontend behavior and operational troubleshooting.

## Implementation Notes
- Statement success now records `STATEMENT_VERIFIED` and then evaluates overall readiness instead of unconditionally moving to `OFFERS_READY`.
- Client creation success now re-evaluates offer readiness so applications can recover when KYC is approved after statement analysis has already completed.
- Eligible-products responses include readiness requirements for:
  - KYC approval
  - Fineract client creation
  - statement approval
- Client-creation retry accepts manual KYC approval records even if the application status has moved into the statement flow.

## Consequences
### Positive
- KYC and statement analysis can progress independently without losing the readiness gate
- applications with late manual KYC approval can still recover automatically into `OFFERS_READY`
- the frontend receives explicit reasons when offers are not yet available

### Trade-offs
- application status alone is no longer sufficient to infer every workflow prerequisite
- callers that previously expected `/eligible-products` to return only a bare product list must adapt to the readiness wrapper response

## References
- Application service: [ApplicationService.java](../src/main/java/com/credvenn/lm/application/ApplicationService.java)
- Eligible-products API: [ApplicationController.java](../src/main/java/com/credvenn/lm/application/ApplicationController.java)
- KYC retry flow: [KycService.java](../src/main/java/com/credvenn/lm/kyc/KycService.java)

# ADR-004: Persist Multiple Statement OTP Candidates and Retry Statement Analysis Safely

## Status
Accepted

## Date
2026-08-15

## Context
Mpesa statements are delivered to a statement inbox email address and then uploaded into Mini-LOS through the service endpoint `POST /api/v1/service/statements/mpesa/upload`.

Operationally, statement delivery can be delayed. A user may request another statement before the first email arrives, which means multiple valid OTPs can exist for the same application window. The system previously stored only one OTP on `loan_request_applications.statement_otp`, which created three problems:

- the first OTP submitted with the loan request application was the only OTP the system knew about
- if that OTP did not unlock the uploaded statement, there was no safe way to add more OTP candidates later
- the statement analysis flow could not retry intelligently without risking duplicate submissions to Cladfy

At the same time, the product rule remained unchanged:
- only one statement document is uploaded into Mini-LOS for a given analysis attempt
- it is sufficient that an OTP unlocks that uploaded statement
- matching OTPs to statement metadata is not required because Mpesa OTPs are time-boxed and phone-number-boxed

## Decision
Adopt an OTP pool per application instead of relying only on the single `statement_otp` column.

The implementation uses a new table `application_statement_otps` to store multiple OTP candidates securely for one application. The first OTP supplied on loan application creation is inserted into this table automatically. Extra OTPs can then be added later through a new application endpoint:

- `POST /api/v1/applications/{applicationId}/statement-otps`

Statement analysis now consumes OTPs from this pool, one candidate per provider submission attempt. Each attempt records which OTP was used. Provider completion updates the OTP record to either successful or failed.

Retries are controlled rather than automatic duplication:
- if there is no stored statement document yet, added OTPs are only saved
- if there is a stored statement and no active analysis, adding OTPs can queue a retry
- if the latest provider analysis already passed, adding OTPs does not re-upload the statement

## Rationale
- Preserves the one-statement-upload rule while allowing multiple OTP candidates.
- Keeps tenant isolation explicit by storing `tenant_id` on OTP records.
- Avoids overwriting history when users provide more than one OTP.
- Makes retries deterministic because each statement analysis attempt points to the exact OTP candidate it used.
- Keeps the frontend unaware of provider-specific retry behavior; Mini-LOS remains the orchestration layer.

## Changes Made
### Data Model
Added Flyway migration [`V31__application_statement_otps.sql`](../src/main/resources/db/migration/V31__application_statement_otps.sql) with:

- `application_statement_otps`
- `statement_otp_id` on `application_statement_analyses`

Added new application OTP classes:

- [ApplicationStatementOtp.java](../src/main/java/com/credvenn/lm/application/ApplicationStatementOtp.java)
- [ApplicationStatementOtpRepository.java](../src/main/java/com/credvenn/lm/application/ApplicationStatementOtpRepository.java)
- [ApplicationStatementOtpService.java](../src/main/java/com/credvenn/lm/application/ApplicationStatementOtpService.java)
- [ApplicationStatementOtpStatus.java](../src/main/java/com/credvenn/lm/application/ApplicationStatementOtpStatus.java)
- [ApplicationStatementOtpSource.java](../src/main/java/com/credvenn/lm/application/ApplicationStatementOtpSource.java)

OTP values are encrypted at rest using the existing [SecretsEncryptionService.java](../src/main/java/com/credvenn/lm/security/SecretsEncryptionService.java).

### API
Extended [ApplicationDtos.java](../src/main/java/com/credvenn/lm/application/ApplicationDtos.java) with request and response models for extra OTP submission.

Extended [ApplicationController.java](../src/main/java/com/credvenn/lm/application/ApplicationController.java) with:

- `POST /api/v1/applications/{applicationId}/statement-otps`

This endpoint:
- validates tenant ownership through the authenticated user
- saves one or more additional OTP candidates
- returns masked OTP status information
- queues a statement-analysis retry only when safe to do so

### Application Creation Behavior
Updated [ApplicationService.java](../src/main/java/com/credvenn/lm/application/ApplicationService.java) so that the original `statementOtp` sent in `POST /api/v1/applications` still works, but now also populates `application_statement_otps` as the initial candidate with source `APPLICATION_CREATE`.

This means the first OTP is no longer only an application field. It is also part of the pooled retry model from the start.

### Statement Analysis and Retry Flow
Updated:

- [StatementAnalysisService.java](../src/main/java/com/credvenn/lm/statement/StatementAnalysisService.java)
- [StatementAnalysisProcessingService.java](../src/main/java/com/credvenn/lm/statement/StatementAnalysisProcessingService.java)
- [StatementAnalysis.java](../src/main/java/com/credvenn/lm/statement/StatementAnalysis.java)

Key behavior:
- statement analysis now checks for any active OTP candidate, not just `loan_request_applications.statement_otp`
- retry can reuse the latest uploaded `MPESA_STATEMENT` document already stored in Mini-LOS
- one pending OTP candidate is reserved per provider submission attempt
- the selected OTP candidate id is stored on the statement analysis record

### Provider Integration
Updated:

- [StatementAnalysisProvider.java](../src/main/java/com/credvenn/lm/statement/StatementAnalysisProvider.java)
- [CladfyGateway.java](../src/main/java/com/credvenn/lm/statement/CladfyGateway.java)
- [CladfyStatementAnalysisProvider.java](../src/main/java/com/credvenn/lm/statement/CladfyStatementAnalysisProvider.java)
- [HttpCladfyGateway.java](../src/main/java/com/credvenn/lm/statement/HttpCladfyGateway.java)
- [CladfyAnalysisCompletionService.java](../src/main/java/com/credvenn/lm/statement/CladfyAnalysisCompletionService.java)

The Cladfy submission path now receives the OTP selected for the current attempt instead of relying only on the legacy application field.

On completion:
- successful analysis marks the OTP candidate as `SUCCESSFUL`
- failed analysis marks the OTP candidate as `FAILED`

### Inbound Statement Matching
Updated [InboundStatementProcessor.java](../src/main/java/com/credvenn/lm/statementinbox/InboundStatementProcessor.java) so a matched application is considered statement-ready when it has any active OTP candidate in the pooled store.

## Consequences
### Positive
- the first OTP submitted with the application is now part of the same retryable OTP model as later OTPs
- extra OTPs can be added without resubmitting the loan request application
- only one uploaded statement document needs to exist in Mini-LOS
- retries are controlled by Mini-LOS and do not blindly duplicate successful Cladfy uploads
- OTP-attempt history becomes auditable

### Trade-offs
- the system now manages another tenant-scoped business table and state machine
- OTP lifecycle becomes slightly more complex than a single application column
- provider submission logic now depends on OTP reservation state

## Alternative Considered
Validate all candidate OTPs locally by opening the PDF inside Mini-LOS before selecting one to send to Cladfy.

This was not chosen in the implemented change because the current project does not already carry a PDF password-validation dependency. Adding one would have increased implementation scope and operational surface area.

Instead, the implemented version keeps the system lean by:
- storing OTP candidates locally
- submitting one candidate per analysis attempt
- letting the provider outcome determine whether that candidate succeeded or failed

## Operational Notes
- The legacy `loan_request_applications.statement_otp` field still exists, but the retryable source of truth is now the OTP pool.
- Added OTPs are stored encrypted and returned only in masked form.
- Retry behavior is intentionally idempotent at the application level: active `PENDING` or `IN_PROGRESS` analysis blocks duplicate retry queueing.
- This design still respects the architectural rule that Mini-LOS orchestrates external integrations and the frontend never talks to Cladfy directly.

## Verification
Focused unit tests passed for the new flow:

```bash
./mvnw.cmd -q "-Dtest=ApplicationServiceTest,StatementAnalysisServiceTest,StatementAnalysisProcessingServiceTest" test
```

Coverage from those tests includes:
- first OTP persistence during application creation
- statement analysis using a reserved OTP candidate for Cladfy submission
- manual statement approval behavior remaining intact

## References
- Application API: [ApplicationController.java](../src/main/java/com/credvenn/lm/application/ApplicationController.java)
- Application service: [ApplicationService.java](../src/main/java/com/credvenn/lm/application/ApplicationService.java)
- OTP persistence: [ApplicationStatementOtpService.java](../src/main/java/com/credvenn/lm/application/ApplicationStatementOtpService.java)
- Statement analysis: [StatementAnalysisService.java](../src/main/java/com/credvenn/lm/statement/StatementAnalysisService.java)
- Statement processing: [StatementAnalysisProcessingService.java](../src/main/java/com/credvenn/lm/statement/StatementAnalysisProcessingService.java)
- Cladfy completion: [CladfyAnalysisCompletionService.java](../src/main/java/com/credvenn/lm/statement/CladfyAnalysisCompletionService.java)
- Inbound statement routing: [InboundStatementProcessor.java](../src/main/java/com/credvenn/lm/statementinbox/InboundStatementProcessor.java)

# ADR-005: Isolate Statement Provider Calls from Database Transactions

## Status
Accepted

## Date
2026-09-08

## Context
Statement processing previously combined application and analysis updates with Cladfy create-client and document-upload calls in one database transaction. It therefore held locks on the shared loan application row during network I/O. Concurrent KYC processing updates the same row, increasing lock duration and producing MariaDB deadlocks.

Cladfy does not accept a caller-supplied idempotency key. A timeout after Cladfy accepts an upload can leave the provider operation successful while Mini-LOS has not stored its identifiers.

## Decision
Split submission into explicit phases:

```text
StatementAnalysisProcessingService.process()                    async coordinator, no TX
  -> StatementSubmissionPreparationService.prepare()            TX-1
  -> StatementAnalysisProvider.submit()                         no TX
  -> StatementSubmissionCompletionService.completeSubmission() TX-2
```

TX-1 locks the tenant-scoped analysis row, validates the application and document, reserves the OTP, marks the analysis `IN_PROGRESS`, and commits. Only `IN_PROGRESS` is used as the durable submission state; no submission-key table or additional pending state is introduced.

Cladfy create-client and upload calls then execute without an open database transaction. TX-2 locks the analysis row, stores external identifiers and the provider response, schedules polling, and commits.

Transient database and HTTP failures receive at most three attempts with short backoff. Before uploading again after an uncertain outcome, the adapter searches Cladfy for an existing submission. It recovers a document only when the provider/document type matches and creation time falls inside the analysis recovery window. If only TX-2 fails, the response retained in memory is saved again without another provider call.

## Rationale
- Network latency no longer extends database lock duration.
- KYC and statement workers contend on the application row for less time.
- Separate transactional beans give preparation and completion independent commits.
- Provider lookup reduces duplicate uploads without claiming unsupported Cladfy idempotency.

## Consequences

### Positive
- Cladfy calls hold no application or statement database locks.
- Transient lock failures restart from a clean transaction.
- Accepted provider work can often be recovered after a lost response.
- Stored external identifiers prevent another provider call.

### Trade-offs and residual risks
- TX-1, the provider call, and TX-2 are not atomic.
- Recovery is heuristic; Cladfy provides no unique Mini-LOS submission key.
- A crash after TX-1 or provider acceptance can leave the analysis `IN_PROGRESS` until retry/recovery.
- In-process retries are not durable across restarts.

## Alternatives Considered
- Keep Cladfy inside one transaction: rejected because it holds locks during network I/O.
- Remove `@Transactional` without phase services: rejected because repository writes would commit independently without a preparation/completion protocol.
- Add an outbox/submission table: deferred; the provider cannot enforce the corresponding idempotency key.

## References
- [StatementAnalysisProcessingService.java](../src/main/java/com/credvenn/lm/statement/StatementAnalysisProcessingService.java)
- [StatementSubmissionPreparationService.java](../src/main/java/com/credvenn/lm/statement/StatementSubmissionPreparationService.java)
- [StatementSubmissionCompletionService.java](../src/main/java/com/credvenn/lm/statement/StatementSubmissionCompletionService.java)
- [HttpCladfyGateway.java](../src/main/java/com/credvenn/lm/statement/HttpCladfyGateway.java)

# ADR-006: Finalize Approved KYC After Commit with Lock Retries

## Status
Accepted

## Date
2026-09-08

## Context
Automatic KYC, manual approval, and tenant bypass previously updated the shared application row directly from their caller transaction. Concurrent statement processing can update the same row. A deadlock could consequently roll back application finalization after SmileID had returned success. Retrying the whole method would call SmileID again.

A temporary recovery mechanism is required without adding an outbox table while the application state machine is redesigned.

## Decision
Use one event and retry boundary for every approval path:

```text
Automatic KYC -> SmileID assessment                              no retry

Automatic, manual, or bypass approval
  -> KycDecisionService.recordApprovedDecision()                 TX-1
       -> save approved KYC and publish KycApprovedEvent
       -> commit
  -> KycApprovedEventListener                                    AFTER_COMMIT
  -> KycApprovalRetryService                                     no TX
  -> KycApprovalService.approveAndRequestClientProvisioning()    TX-2 per attempt
       -> mark application KYC_PASSED
       -> publish ClientProvisioningRequestedEvent and commit
  -> existing client-provisioning listener                       AFTER_COMMIT
```

Retry only pessimistic and optimistic locking failures. Three retries after the initial call provide four total attempts with exponential delay and jitter. The retry coordinator and transactional finalizer are separate Spring beans. Because the listener runs after commit with no active transaction, each call to the finalizer starts a fresh transaction and rolls back before another attempt.

SmileID remains outside this boundary and is never repeated by a database-lock retry. Manual approval and disabled-tenant bypass use the same event flow.

## Rationale
- The provider decision commits before contended application finalization.
- Retrying only TX-2 avoids duplicate SmileID requests and charges.
- Separate proxies avoid self-invocation and guarantee a clean transaction per attempt.
- Client provisioning retains its existing after-commit boundary.

## Consequences

### Positive
- Transient application-row conflicts can recover automatically.
- SmileID is called once per automatic KYC run.
- Automatic, manual, and bypass callers behave consistently.
- Failed attempts cannot commit partial status/history changes.

### Trade-offs and residual risks
- The event is not durable. A crash after TX-1 but before TX-2 can leave KYC approved while application finalization is incomplete.
- Exhausted retries are logged and require operational retry or future reconciliation.
- This mitigates contention; it does not replace the shared application state machine.
- Unrelated transactions require their own retry and lock strategy.

## Alternatives Considered
- Combine `@Retryable` and `@Transactional` on one broadly called method: rejected because proxy ordering and self-invocation paths make transaction-per-attempt behavior harder to guarantee.
- Retry the complete KYC process: rejected because it repeats SmileID.
- Add a durable outbox now: deferred to the state-machine reliability redesign.

## Operational Recovery
- For `PASSED` or `MANUALLY_APPROVED` KYC without a Fineract client, call `POST /api/v1/applications/{applicationId}/kyc/retry-client-creation`.
- Do not use the general KYC retry merely to finalize an approved result; it reruns the configured provider.
- Alert on `KYC application finalization failed after retries`.

## References
- [KycDecisionService.java](../src/main/java/com/credvenn/lm/kyc/KycDecisionService.java)
- [KycApprovedEventListener.java](../src/main/java/com/credvenn/lm/kyc/KycApprovedEventListener.java)
- [KycApprovalRetryService.java](../src/main/java/com/credvenn/lm/kyc/KycApprovalRetryService.java)
- [KycApprovalService.java](../src/main/java/com/credvenn/lm/kyc/KycApprovalService.java)
- [KycController.java](../src/main/java/com/credvenn/lm/kyc/KycController.java)
