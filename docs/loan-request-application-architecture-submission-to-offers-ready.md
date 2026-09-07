# Loan Request Application Architecture: Submission to Offers Ready

Updated: 2026-08-31

## Scope

This document describes the architecture of the loan request application workflow from the moment an application is submitted until it reaches `OFFERS_READY`.

It focuses on:
- application submission
- KYC initiation and completion
- Fineract client provisioning
- statement document intake and statement OTP handling
- statement analysis completion
- readiness evaluation for offers

It does not cover:
- offer selection
- consent capture
- internal approval
- device assignment after offer readiness
- Fineract loan creation or activation

## High-Level Architecture

### Business flow

The path to `OFFERS_READY` is built from three parallel business requirements that must all become true:
- KYC is approved
- a Fineract client exists
- statement approval exists, unless statement analysis is disabled for the tenant

The loan application moves through a state machine owned by Mini-LOS. Fineract is not called directly by the frontend. External providers are orchestrated asynchronously by Mini-LOS.

### System context

```text
Frontend
  -> ApplicationController
  -> ApplicationService
  -> loan_request_applications + related LOS tables
  -> publishes domain events

After commit async/event-driven processing:
  -> KycService / KycProcessingService
  -> ClientProvisioningService
  -> InboundStatementProcessor
  -> StatementAnalysisService / StatementAnalysisProcessingService
  -> Cladfy webhook / polling completion

External systems:
  -> KYC provider
  -> Apache Fineract
  -> Cladfy statement analysis provider
  -> document storage
```

### Core architectural rules in this flow

- `ApplicationService` owns application status transitions.
- Long-running or external operations do not block the original submission request.
- Tenant isolation is enforced in all application-facing queries and writes.
- Statement OTPs are stored separately from the application record and encrypted at rest.
- Statement analysis only runs for statuses up to `STATEMENT_IN_PROGRESS`; it is blocked once the application has moved beyond statement processing.

## Status Progression

### Primary application statuses in scope

```text
SUBMITTED
-> PENDING_KYC
-> KYC_IN_PROGRESS
-> KYC_PASSED
-> CLIENT_CREATION_IN_PROGRESS
-> KYC_PASSED_CLIENT_CREATED
-> STATEMENT_PENDING
-> STATEMENT_IN_PROGRESS
-> STATEMENT_VERIFIED
-> OFFERS_READY
```

### Alternate statuses in scope

```text
KYC_MANUAL_REVIEW
KYC_FAILED
CLIENT_CREATION_FAILED
STATEMENT_FAILED
STATEMENT_MANUAL_REVIEW
```

### What makes `OFFERS_READY`

`OFFERS_READY` is reached only when `ApplicationService.assessOfferReadiness(...)` evaluates all of the following as true:
- latest KYC result is `PASSED` or `MANUALLY_APPROVED`
- `loan_request_applications.fineract_client_id` is populated
- statement is approved, either by provider outcome or manual review, unless tenant statement analysis mode is `DISABLED`

## Low-Level Architecture

## 1. Submission Layer

### Entry point

- `ApplicationController`
- `ApplicationService.create(...)`

### Responsibilities

- validate and persist the initial application
- set status to `SUBMITTED`
- optionally create the initial statement OTP
- immediately transition to `PENDING_KYC`
- publish `ApplicationCreatedEvent`

### Main tables touched

- `loan_request_applications`
- `application_status_history`
- `application_statement_otps` when an OTP is supplied on creation

### Notes

The create request is synchronous only for local persistence. Provider work starts after commit via events.

## 2. Application-Created Event Fan-Out

### Listeners

- `ApplicationCreatedKycListener`
- `ApplicationCreatedInboundStatementRetryListener`

### Responsibilities

`ApplicationCreatedKycListener`:
- receives the committed application-created event
- delegates to `KycService.handleApplicationCreated(...)`

`ApplicationCreatedInboundStatementRetryListener`:
- retries unmatched inbound statement receipts for the same phone number
- can attach a statement later if the application now exists and has a pending OTP

This means statement arrival and application submission are loosely coupled and can happen in either order.

## 3. KYC Subsystem

### Main classes

- `KycService`
- `KycProcessingService`
- `KycApprovalService`

### Modes

KYC behavior depends on tenant configuration:
- `AUTO`: run provider KYC asynchronously
- `MANUAL`: wait for tenant action
- `DISABLED`: bypass provider KYC and continue

### Flow

```text
ApplicationCreatedEvent
-> KycService.handleApplicationCreated(...)
-> if AUTO, KycService.run(...)
-> KycProcessingService.process(...)
-> provider decision
-> KycApprovalService.approveAndRequestClientProvisioning(...) when approved
```

### Status ownership

- `ApplicationService.markKycInProgress(...)` moves the application to `KYC_IN_PROGRESS`
- `ApplicationService.markKycPassed(...)` moves it to `KYC_PASSED`
- `ApplicationService.handleKycManualReview(...)` moves it to `KYC_MANUAL_REVIEW`
- `ApplicationService.handleKycFailed(...)` moves it to `KYC_FAILED`

### Persistence

- `application_kyc_checks` stores provider/manual KYC results
- `application_status_history` stores user-readable transition history

## 4. Fineract Client Provisioning

### Main classes

- `KycApprovalService`
- `ClientProvisioningService`
- `FineractGateway`

### Flow

After KYC approval, `KycApprovalService` publishes `ClientProvisioningRequestedEvent`.
`ClientProvisioningService.process(...)` then:
- checks if the application already has a `fineractClientId`
- tries to reuse an existing Fineract client by external id
- otherwise creates a new Fineract client through `FineractGateway`
- writes the resulting client id back through `ApplicationService.handleClientCreated(...)`

### Resulting application states

- `CLIENT_CREATION_IN_PROGRESS`
- `CLIENT_CREATION_FAILED`
- `KYC_PASSED_CLIENT_CREATED`

### Important boundary

Mini-LOS owns the workflow state. Fineract only owns the client record; it does not decide application readiness.

## 5. Statement Intake and Matching

### Two ways a statement can enter

- direct document upload to the application
- inbound emailed statement routed through the statement inbox

### Inbound flow classes

- `InboundStatementAcceptedListener`
- `InboundStatementProcessor`
- `DocumentService`

### Matching logic

`InboundStatementProcessor`:
- resolves the tenant inbox from destination email
- extracts a phone token from the filename
- finds recent candidate applications for the tenant in pre-statement-completion statuses
- filters by phone-number match
- requires exactly one match
- requires the matched application to have at least one `PENDING` statement OTP

If matched, it creates an `application_documents` row and stores the statement as `MPESA_STATEMENT`.

### Important safeguards

- more than one matching application results in `AMBIGUOUS`
- no matching application results in `WAITING_FOR_APPLICATION`
- no pending OTP results in `BLOCKED_MISSING_STATEMENT_OTP`

## 6. Statement OTP Architecture

### Main classes

- `ApplicationStatementOtpService`
- `StatementPdfPasswordVerifier`
- `PdfBoxStatementPdfPasswordVerifier`

### Storage model

OTPs are stored in `application_statement_otps` with:
- encrypted OTP value
- masked OTP display value
- source
- status
- document association
- tested and used timestamps
- failure reason

### OTP lifecycle

```text
PENDING
-> SUBMITTED
-> SUCCESSFUL
```

or

```text
PENDING
-> FAILED
```

### Current selection behavior

Before statement analysis is submitted to Cladfy, the system now:
- loads the stored PDF document
- checks pending OTPs in creation order
- attempts to open the PDF with each pending OTP
- picks the first OTP that successfully opens the document
- marks wrong OTPs as `FAILED` with reason `PDF password did not open document`
- marks the selected OTP as `SUBMITTED`
- later marks it `SUCCESSFUL` or `FAILED` based on provider completion

This prevents sending the wrong password to the provider and reduces OTP ambiguity.

### Trigger behavior for newly added OTPs

When a new OTP is added, retry logic may attempt to submit statement analysis again, but only if:
- the application is not past statement processing
- there is no statement analysis already `PENDING` or `IN_PROGRESS`
- the latest statement analysis is not already passed
- a stored statement document exists
- one pending OTP can actually open that document

## 7. Statement Analysis Subsystem

### Main classes

- `StatementAnalysisService`
- `StatementAnalysisProcessingService`
- `StatementAutoTriggerListener`
- `CladfyStatementAnalysisProvider`
- `HttpCladfyGateway`
- `CladfyWebhookService`
- `CladfyAnalysisCompletionService`

### Trigger points

A statement analysis run can be initiated when:
- a statement document is uploaded or attached and auto-triggering is allowed
- retry logic runs after a new OTP is added
- a user manually starts statement analysis

### Submission path

```text
StatementAnalysisService.run(...)
-> validate application status
-> validate document belongs to application
-> validate at least one pending OTP can open the PDF
-> create PENDING analysis row
-> StatementAnalysisProcessingService.process(...)
-> move application to STATEMENT_IN_PROGRESS
-> reserve first matching OTP
-> submit to Cladfy
-> save provider correlation ids
```

### Completion path

For Cladfy, completion is asynchronous:
- webhook or polling locates the in-progress analysis
- `CladfyAnalysisCompletionService` fetches results and credit score
- statement transactions are summarized and stored
- analysis status is finalized
- the reserved OTP is marked `SUCCESSFUL` or `FAILED`
- `ApplicationService` applies the application outcome

### Statement analysis states

`application_statement_analyses.status` uses:
- `PENDING`
- `IN_PROGRESS`
- `PASSED`
- `FAILED`
- `MANUAL_REVIEW_REQUIRED`

### Application states driven by statement outcome

- `STATEMENT_IN_PROGRESS`
- `STATEMENT_VERIFIED`
- `STATEMENT_FAILED`
- `STATEMENT_MANUAL_REVIEW`

## 8. Transition to Offers Ready

### Main owner

- `ApplicationService.maybeMoveToOffersReady(...)`

### Evaluation logic

`maybeMoveToOffersReady(...)` calls `assessOfferReadiness(...)`, which checks:
- latest KYC approval status in `application_kyc_checks`
- presence of `fineract_client_id` on `loan_request_applications`
- statement approval from `application_statement_reviews` first, then `application_statement_analyses`

If all conditions are true:
- the application is transitioned to `OFFERS_READY`
- the reason logged is `Eligible products ready`

If not, the service logs which requirement is still missing.

### Why this matters

`OFFERS_READY` is not owned by KYC, Fineract, or statement services individually. It is a convergence status computed centrally by `ApplicationService` after any prerequisite completes.

## Data Model in Scope

### Core tables

- `loan_request_applications`: root business record and current status
- `application_status_history`: auditable state transitions
- `application_kyc_checks`: provider and manual KYC outcomes
- `application_documents`: stored statement documents and other uploads
- `application_statement_otps`: encrypted OTP inventory and OTP state
- `application_statement_analyses`: provider submission and result record
- `application_statement_reviews`: final human/system approval interpretation
- `inbound_statement_receipts`: email-ingestion and routing state

### Status source of truth

- current application state: `loan_request_applications.status`
- history: `application_status_history`
- KYC state: latest `application_kyc_checks`
- statement provider state: latest `application_statement_analyses`
- statement approval override/final review: latest `application_statement_reviews`

## Sequence Views

### Normal happy path

```text
User submits application
-> ApplicationService.create
-> application saved
-> status PENDING_KYC
-> ApplicationCreatedEvent
-> KYC async processing
-> KYC approved
-> client provisioning requested
-> Fineract client created or reused
-> status KYC_PASSED_CLIENT_CREATED
-> statement available with valid pending OTP
-> statement analysis submitted
-> status STATEMENT_IN_PROGRESS
-> Cladfy completes analysis
-> status STATEMENT_VERIFIED
-> ApplicationService.maybeMoveToOffersReady
-> status OFFERS_READY
```

### Happy path when statement analysis is disabled

```text
User submits application
-> KYC approved
-> Fineract client created
-> ApplicationService records disabled statement analysis as passed
-> ApplicationService.maybeMoveToOffersReady
-> status OFFERS_READY
```

### Inbound statement arrives before application exists

```text
Inbound statement received
-> receipt WAITING_FOR_APPLICATION
-> application later submitted
-> ApplicationCreatedInboundStatementRetryListener retries receipt
-> document attached if one application matches and a pending OTP exists
-> statement analysis auto-triggered after commit
```

## Failure and Control Points

### Duplicate application match during inbound routing

If two recent applications match the same phone token, the inbound statement is not attached automatically. The receipt is marked ambiguous for manual review.

### Invalid OTPs

Wrong pending OTPs are tested against the PDF locally before provider submission. They are marked failed without leaking the plaintext OTP to logs or downstream services unnecessarily.

### Duplicate active analyses

A new statement submission is blocked when another analysis for the same application is already `PENDING` or `IN_PROGRESS`.

### Past-statement statuses

Statement analysis is not allowed once the application has moved past the statement-processing phase, including:
- `STATEMENT_VERIFIED`
- `OFFERS_READY`
- and later downstream statuses

## Design Summary

From submission to `OFFERS_READY`, the application workflow behaves like an event-driven orchestration pipeline centered on `ApplicationService`.

- Submission creates the root record and starts KYC.
- KYC approval unlocks Fineract client provisioning.
- Statement intake and OTP verification unlock statement analysis.
- `ApplicationService` recomputes readiness whenever a prerequisite completes.
- `OFFERS_READY` is reached only when KYC, client creation, and statement approval have all converged.

This keeps Mini-LOS as the workflow system of record while using external providers only for specialized tasks.
