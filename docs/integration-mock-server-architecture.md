# Integration Mock Server Architecture

## Purpose

Build a local/mock integrations server for the LOS backend using `Express` and `TypeScript`.

This server should simulate external providers used by Mini-LOS:

- Smile ID for KYC
- Cladfy for statement analysis
- M-PESA Daraja for payments
- Datacultr for device control

The mock server exists to support:

- local backend development
- frontend demo environments routed through Mini-LOS
- automated integration tests
- failure-path and retry-path testing

## Architectural Boundary

This mock server represents external providers only.

The React frontend must still call Mini-LOS only.

Flow:

`Frontend -> Mini-LOS -> Mock Integration Server`

The frontend must never call this mock server directly in normal product flows.

## Goals

- Mimic the provider contracts already used by the Spring Boot app
- Be deterministic by default, but configurable for failure simulation
- Support async/webhook-style flows where the real provider is async
- Keep provider state in memory first, with optional file persistence
- Be easy to run locally with one command
- Be easy to extend as the real integrations evolve

## Non-Goals

- Do not rebuild Mini-LOS business logic in the mock server
- Do not introduce tenant business rules owned by Mini-LOS
- Do not persist production-grade data
- Do not expose provider secrets beyond local test defaults
- Do not duplicate Fineract behavior here

## Tech Stack

- Node.js 20+
- Express
- TypeScript
- Zod for request validation
- Multer for multipart uploads
- UUID for ids
- Pino or Winston for structured logs
- dotenv for environment variables

Optional:

- `tsx` for local execution
- `vitest` or `jest` for tests

## High-Level Design

The server should be modular by provider.

Suggested structure:

```text
mock-integrations/
  src/
    app.ts
    server.ts
    config/
      env.ts
    common/
      errors.ts
      logger.ts
      request-id.ts
      delay.ts
      scenario.ts
    providers/
      smile-id/
        smileId.routes.ts
        smileId.service.ts
        smileId.schemas.ts
        smileId.store.ts
      cladfy/
        cladfy.routes.ts
        cladfy.service.ts
        cladfy.schemas.ts
        cladfy.store.ts
        cladfy.webhook.ts
      mpesa/
        mpesa.routes.ts
        mpesa.service.ts
        mpesa.schemas.ts
        mpesa.store.ts
        mpesa.webhook.ts
      datacultr/
        datacultr.routes.ts
        datacultr.service.ts
        datacultr.schemas.ts
        datacultr.store.ts
    state/
      memory-store.ts
      seed.ts
    fixtures/
      smile-id.json
      cladfy.json
      mpesa.json
      datacultr.json
```

## Runtime Modes

Support these modes:

- `happy-path`: default successful responses
- `manual`: behavior chosen per request using headers or query params
- `scenario-driven`: named scenarios loaded from fixtures

Recommended controls:

- `x-mock-scenario`
- `x-mock-delay-ms`
- `x-mock-fail`

## Cross-Cutting Rules

### 1. Provider isolation

Each provider should have:

- routes
- request/response schemas
- in-memory store
- scenario-aware service layer

### 2. Structured logging

Log:

- request id
- provider
- route
- scenario
- status code
- payload summary

### 3. Deterministic ids

When possible, allow ids to be seeded from fixtures so test runs stay stable.

### 4. Explicit async simulation

For providers with async behavior, store initial state and transition later via:

- delayed timers
- webhook callback dispatch
- polling endpoints

### 5. Safe local behavior

Webhook targets should default to localhost or configured local Mini-LOS URLs only.

## Provider Contract Requirements

The mock server should match the Spring Boot integration points already present in this repository.

---

## Smile ID Mock

### Real app contract

Mini-LOS posts to:

- `POST /v2/verify`

The app sends fields like:

- `partner_id`
- `signature`
- `timestamp`
- `country`
- `id_type`
- `id_number`
- `first_name`
- `middle_name`
- `last_name`
- `dob`
- `gender`
- `phone_number`
- `job_type`
- `partner_params.job_id`
- `partner_params.user_id`

### Smile ID request payload

The current Mini-LOS gateway builds this request body shape:

```json
{
  "source_sdk": "rest_api",
  "source_sdk_version": "1.0.0",
  "partner_id": "8763",
  "signature": "base64-hmac-signature",
  "timestamp": "2026-07-15T09:00:00.000Z",
  "country": "KE",
  "id_type": "NATIONAL_ID",
  "id_number": "12345678",
  "first_name": "Jane",
  "middle_name": "K",
  "last_name": "Doe",
  "dob": "1995-06-12",
  "gender": "FEMALE",
  "phone_number": "254700000001",
  "job_type": 5,
  "partner_params": {
    "job_id": "application-uuid",
    "user_id": "tenant-uuid:12345678"
  }
}
```

Request notes:

- `source_sdk` is sent as `rest_api`
- `country` is sent as `KE`
- `job_type` is sent as `5`
- `timestamp` is generated in UTC with millisecond precision
- `signature` is generated using HMAC-SHA256 over `timestamp + partner_id + sid_request`, then base64-encoded
- `id_type` comes from the application's applicant ID type enum
- `partner_params.job_id` is the application id
- `partner_params.user_id` is built as `<tenant_id>:<national_id>`

The app expects a response shaped like:

```json
{
  "SmileJobID": "job-123",
  "ResultText": "Verification Success",
  "ResultCode": "1012",
  "Actions": {
    "Verify_ID_Number": "PASSED",
    "Names": "PASSED",
    "FirstName": "PASSED",
    "LastName": "PASSED",
    "OtherNames": "PASSED",
    "DOB": "PASSED",
    "Gender": "PASSED",
    "Phone_Number": "PASSED",
    "ID_Verification": "PASSED",
    "Return_Personal_Info": "PASSED"
  },
  "signature": "mock-signature",
  "timestamp": "2026-07-14T09:00:00.000Z"
}
```

### Mock behavior

Support scenarios:

- `smileid.pass`
- `smileid.partial-match`
- `smileid.fail`
- `smileid.invalid-id`
- `smileid.timeout`

Recommended rules:

- return `200` with provider-style body for pass/fail business outcomes
- return `4xx` only for malformed requests
- optionally validate HMAC format, but do not require real cryptographic verification unless enabled

---

## Cladfy Mock

### Real app contract

Mini-LOS uses:

- `POST /clients`
- `POST /documents`
- `GET /documents/:documentId/status`
- `GET /clients/analysis_results` with header `X-Client-Id`
- `GET /clients/:clientId/scoring`

`POST /clients` may return:

- `200/201` with new client
- `409` with an existing client payload

The document upload is multipart and includes:

- `file`
- `client_id`
- `provider`
- `webhook`
- `national_id`
- optional `password`

### Cladfy endpoint summary

The current Mini-LOS gateway calls these Cladfy endpoints:

- `POST /clients`
- `POST /documents`
- `GET /documents/{documentId}/status`
- `GET /clients/analysis_results`
- `GET /clients/{clientId}/scoring`

### Request and response shapes

#### `POST /clients`

Request payload:

```json
{
  "first_name": "Jane",
  "last_name": "Doe",
  "phone_number": "254700000001",
  "national_id": "12345678"
}
```

Success response:

```json
{
  "id": 1001,
  "first_name": "Jane",
  "last_name": "Doe",
  "full_name": "Jane Doe",
  "phone_number": "254700000001"
}
```

Conflict response (`409`):

```json
{
  "detail": "Client already exists",
  "message": "Conflict",
  "existing_client": {
    "id": 1001,
    "first_name": "Jane",
    "last_name": "Doe",
    "full_name": "Jane Doe",
    "phone_number": "254700000001",
    "email": null,
    "national_id": "12345678"
  }
}
```

#### `POST /documents`

This request is `multipart/form-data` with fields:

- `file`
- `client_id`
- `provider`
- `webhook`
- `national_id`
- optional `password`

Example form fields:

```text
file=<statement.pdf>
client_id=1001
provider=mpesa
webhook=https://your-mini-los/api/v1/public/integrations/cladfy/webhook
national_id=12345678
password=1234
```

Example response:

```json
{
  "id": 7001,
  "url": "https://cladfy.example/documents/7001",
  "client_id": 1001,
  "provider": "mpesa",
  "status": 1,
  "created_at": "2026-07-16T09:00:00Z",
  "client": {
    "id": 1001,
    "full_name": "Jane Doe",
    "national_id": "12345678",
    "scoring": {
      "score": 642,
      "risk_tier": {
        "tier": "B",
        "color": "yellow",
        "min_score": 600,
        "risk": "medium"
      },
      "scored_at": "2026-07-16T09:10:00Z",
      "features": {
        "scored_at": "2026-07-16T09:10:00Z"
      }
    }
  }
}
```

#### `GET /documents/{documentId}/status`

Example response:

```json
{
  "id": 7001,
  "status": "success",
  "fail_reason": null,
  "password_provided": true
}
```

#### `GET /clients/analysis_results`

This request sends header:

```text
X-Client-Id: 1001
```

Example response:

```json
{
  "document": {
    "id": 7001,
    "status": "success",
    "provider": "mpesa",
    "currency": "KES",
    "created_at": "2026-07-16T09:00:00Z",
    "last_analyzed_on": "2026-07-16T09:10:00Z"
  },
  "client": {
    "id": 1001,
    "full_name": "Jane Doe",
    "national_id": "12345678"
  },
  "summary": {
    "total_in": 42000.00,
    "total_out": 31500.00,
    "transaction_count": 24,
    "zero_balance_rate_percentage": 8.50
  },
  "transactions": [
    {
      "id": 1,
      "type": "credit",
      "amount": 1000.00,
      "date": "2026-07-01",
      "narration": "M-PESA RECEIVED",
      "balance": 5400.00,
      "currency": "KES"
    }
  ],
  "cashflow": {},
  "spending": {},
  "loans": {}
}
```

#### `GET /clients/{clientId}/scoring`

Example response:

```json
{
  "score": 642,
  "risk_tier": {
    "tier": "B",
    "color": "yellow",
    "min_score": 600,
    "risk": "medium"
  },
  "features": {},
  "scored_at": "2026-07-16T09:10:00Z"
}
```

### Mock behavior

Store:

- clients
- documents
- analysis results
- scorecards
- webhook dispatch history

Suggested state flow:

1. `POST /clients` creates or reuses a client
2. `POST /documents` creates a document with initial status like `processing`
3. after delay, document becomes `success` or `failed`
4. mock server optionally POSTs webhook to Mini-LOS
5. polling endpoints return final status and analysis data

### Cladfy webhook logic

The current Mini-LOS configuration expects Cladfy callbacks to reach:

- `POST /api/v1/public/integrations/cladfy/webhook` on Mini-LOS

The mock server should treat the uploaded `webhook` form field on `POST /documents` as the primary callback target.

Rules:

- if the multipart `webhook` field is present, use it
- if it is absent, fall back to `CLADFY_WEBHOOK_TARGET`
- only dispatch when `ENABLE_WEBHOOKS=true`
- persist webhook attempts in memory for inspection
- include retry metadata on each attempt

Recommended webhook lifecycle:

1. accept document upload and return immediate upload response
2. store document as `processing`
3. wait for `CLADFY_ASYNC_DELAY_MS` or request-specific delay
4. transition to `success` or `failed` based on scenario
5. POST webhook to Mini-LOS
6. mark webhook attempt as delivered or failed

Recommended webhook request body:

```json
{
  "document_id": 7001,
  "client_id": 1001,
  "business_id": 1
}
```

Recommended webhook request headers:

- `Content-Type: application/json`
- `X-Mock-Provider: cladfy`
- `X-Mock-Scenario: cladfy.success`
- `X-Mock-Delivery-Id: <uuid>`

Recommended retry behavior:

- retry up to 3 times for network errors or `5xx`
- no retry for `2xx` or `4xx`
- exponential backoff such as `1s`, `3s`, `10s`

Recommended stored webhook delivery record:

- `deliveryId`
- `provider`
- `documentId`
- `clientId`
- `targetUrl`
- `requestBody`
- `attemptCount`
- `lastHttpStatus`
- `lastError`
- `delivered`
- `deliveredAt`

### Example responses

Create client success:

```json
{
  "id": 1001,
  "first_name": "Jane",
  "last_name": "Doe",
  "full_name": "Jane Doe",
  "phone_number": "254700000001"
}
```

Create client conflict:

```json
{
  "detail": "Client already exists",
  "message": "Conflict",
  "existing_client": {
    "id": 1001,
    "first_name": "Jane",
    "last_name": "Doe",
    "full_name": "Jane Doe",
    "phone_number": "254700000001",
    "email": null,
    "national_id": "12345678"
  }
}
```

Document status:

```json
{
  "id": 7001,
  "status": "success",
  "fail_reason": null,
  "password_provided": true
}
```

Credit score:

```json
{
  "score": 642,
  "risk_tier": {
    "tier": "B",
    "color": "yellow",
    "min_score": 600,
    "risk": "medium"
  },
  "features": {
    "average_monthly_inflow": 42000
  },
  "scored_at": "2026-07-14T09:10:00Z"
}
```

### Scenarios

- `cladfy.success`
- `cladfy.client-conflict`
- `cladfy.password-required`
- `cladfy.analysis-failed`
- `cladfy.webhook-delayed`
- `cladfy.webhook-disabled`
- `cladfy.webhook-retry`
- `cladfy.webhook-failed`

---

## M-PESA Daraja Mock

### Real app contract

Mini-LOS uses:

- `GET /oauth/v1/generate?grant_type=client_credentials`
- `GET /oauth/v2/generate?grant_type=client_credentials`
- `POST /mpesa/stkpush/v1/processrequest`
- `POST /mpesa/c2b/v2/registerurl`

Access-token response must include:

```json
{
  "access_token": "mock-access-token",
  "expires_in": "3599"
}
```

STK push response must include:

```json
{
  "MerchantRequestID": "mock-merchant-001",
  "CheckoutRequestID": "ws_CO_001",
  "ResponseCode": "0",
  "ResponseDescription": "Success. Request accepted for processing",
  "CustomerMessage": "Success. Request accepted for processing"
}
```

C2B registration response must include:

```json
{
  "OriginatorCoversationID": "conv-001",
  "ResponseCode": "0",
  "ResponseDescription": "Success"
}
```

### Mock behavior

Support:

- OAuth token issuance
- STK push initiation
- optional callback simulation to Mini-LOS
- C2B registration acknowledgement

### M-PESA webhook logic

The mock should support two outbound webhook styles:

- STK callback after `POST /mpesa/stkpush/v1/processrequest`
- optional C2B confirmation/validation callback simulation after URL registration

For STK callbacks, Mini-LOS should remain the receiver. The mock should never require the frontend to consume M-PESA callbacks directly.

#### STK callback flow

Recommended lifecycle:

1. accept STK push request
2. return immediate Daraja acknowledgement with `ResponseCode = "0"` for accepted requests
3. store the checkout request state as `PENDING`
4. after `MPESA_ASYNC_DELAY_MS`, transition the request based on scenario
5. if callbacks are enabled, POST callback payload to the `CallBackURL` from the request body

Recommended callback target resolution:

- first use `CallBackURL` from the incoming STK request
- if absent in manual tests, optionally fall back to `MPESA_CALLBACK_URL`

Recommended callback request payload:

```json
{
  "Body": {
    "stkCallback": {
      "MerchantRequestID": "mock-merchant-001",
      "CheckoutRequestID": "ws_CO_001",
      "ResultCode": 0,
      "ResultDesc": "The service request is processed successfully.",
      "CallbackMetadata": {
        "Item": [
          {
            "Name": "Amount",
            "Value": 1000
          },
          {
            "Name": "MpesaReceiptNumber",
            "Value": "NLJ7RT61SV"
          },
          {
            "Name": "Balance"
          },
          {
            "Name": "TransactionDate",
            "Value": 20260714103045
          },
          {
            "Name": "PhoneNumber",
            "Value": 254700000001
          }
        ]
      }
    }
  }
}
```

For failed scenarios, return a callback payload with:

- non-zero `ResultCode`
- failure `ResultDesc`
- omit `CallbackMetadata` when appropriate

Recommended callback headers:

- `Content-Type: application/json`
- `X-Mock-Provider: mpesa`
- `X-Mock-Scenario: mpesa.stk.accepted`
- `X-Mock-Delivery-Id: <uuid>`

Recommended STK callback scenarios:

- `mpesa.stk.accepted`: callback with success receipt data
- `mpesa.stk.insufficient-funds`: callback with failure code for insufficient funds
- `mpesa.stk.cancelled`: callback with user-cancelled failure
- `mpesa.stk.timeout`: no callback or delayed callback based on config

#### Optional C2B confirmation and validation simulation

The current Mini-LOS code registers C2B URLs via `POST /mpesa/c2b/v2/registerurl`.

The mock should:

- store `ConfirmationURL`
- store optional `ValidationURL`
- store `ShortCode` and `ResponseType`
- optionally expose internal control endpoints to simulate an inbound payment event

Suggested internal event endpoint:

- `POST /__mock/mpesa/c2b/simulate-deposit-callback`

Suggested request body sent to the mock:

```json
{
  "tenantId": "tenant-uuid",
  "shortCode": "123456",
  "transactionType": "Pay Bill",
  "transId": "NLJ7RT61SV",
  "transTime": "20260721103045",
  "transAmount": 1500.00,
  "billRefNumber": "254700000001",
  "invoiceNumber": "",
  "orgAccountBalance": "",
  "thirdPartyTransID": "",
  "msisdn": "254700000001",
  "firstName": "Jane",
  "middleName": "K",
  "lastName": "Doe"
}
```

How `tenantId` should be used:

- `tenantId` is required by the mock simulation endpoint for tenant-scoped C2B deposit tests
- the mock should use `tenantId` to build the Mini-LOS tenant endpoints it calls
- the mock should not send `tenantId` inside the Daraja webhook payload body
- Mini-LOS uses the path tenant id together with `BusinessShortCode` to resolve the active tenant payment channel
- tenant-specific callbacks are preferred over the generic callback because they keep the collection flow explicitly tenant-scoped

Mini-LOS endpoints the mock should call:

- Validation: `POST /api/v1/public/tenants/{tenantId}/collections/c2b/deposits/validate`
- Confirmation callback: `POST /api/v1/public/tenants/{tenantId}/collections/c2b/deposits/callback`
- Legacy generic callback: `POST /api/v1/public/payments/mpesa/deposits/callback`

Recommended behavior:

1. receive `POST /__mock/mpesa/c2b/simulate-deposit-callback`
2. build the Daraja C2B payload from the mock request
3. call Mini-LOS validation endpoint using the `tenantId` path value
4. if validation returns accepted, call the tenant deposit callback endpoint using the same `tenantId`
5. store request, response, and delivery history in the mock state

Validation request payload the mock should send to Mini-LOS:

```json
{
  "TransactionType": "Pay Bill",
  "TransID": "NLJ7RT61SV",
  "TransTime": "20260721103045",
  "TransAmount": 1500.00,
  "BusinessShortCode": "123456",
  "BillRefNumber": "254700000001",
  "InvoiceNumber": "",
  "OrgAccountBalance": "",
  "ThirdPartyTransID": "",
  "MSISDN": "254700000001",
  "FirstName": "Jane",
  "MiddleName": "K",
  "LastName": "Doe"
}
```

Validation response Mini-LOS returns:

```json
{
  "ResultCode": "0",
  "ResultDesc": "Accepted"
}
```

Or on rejection:

```json
{
  "ResultCode": "C2B00011",
  "ResultDesc": "Rejected"
}
```

Confirmation callback payload the mock should send to Mini-LOS:

```json
{
  "TransactionType": "Pay Bill",
  "TransID": "NLJ7RT61SV",
  "TransTime": "20260721103045",
  "TransAmount": 1500.00,
  "BusinessShortCode": "123456",
  "BillRefNumber": "254700000001",
  "InvoiceNumber": "",
  "OrgAccountBalance": "",
  "ThirdPartyTransID": "",
  "MSISDN": "254700000001",
  "FirstName": "Jane",
  "MiddleName": "K",
  "LastName": "Doe"
}
```

Callback acknowledgement Mini-LOS returns:

```json
{
  "ResultCode": 0,
  "ResultDesc": "Accepted"
}
```

Suggested response from the mock simulation endpoint:

```json
{
  "status": "completed",
  "tenantId": "tenant-uuid",
  "validationCalled": true,
  "validationAccepted": true,
  "callbackCalled": true,
  "validationEndpoint": "/api/v1/public/tenants/tenant-uuid/collections/c2b/deposits/validate",
  "callbackEndpoint": "/api/v1/public/tenants/tenant-uuid/collections/c2b/deposits/callback",
  "transId": "NLJ7RT61SV",
  "billRefNumber": "254700000001"
}
```

Deposit callback matching note from the current code:

- Mini-LOS normalizes `BillRefNumber` as the phone number and matches it against the application phone
- `MSISDN` is provider data but is not the field used for application matching
- use the applicant phone number in `BillRefNumber` when simulating a successful match

Recommended retry behavior:

- retry up to 3 times for network errors or `5xx`
- no retry for `2xx` or `4xx`

Recommended extra endpoints for test control:

- `POST /__mock/mpesa/stk/:checkoutRequestId/complete`
- `POST /__mock/mpesa/stk/:checkoutRequestId/fail`
- `POST /__mock/mpesa/c2b/simulate-deposit-callback`

If you do not want control endpoints, drive completion via request scenario headers.

### Scenarios

- `mpesa.stk.accepted`
- `mpesa.stk.insufficient-funds`
- `mpesa.stk.cancelled`
- `mpesa.stk.timeout`
- `mpesa.c2b.registered`
- `mpesa.c2b.confirmation-delivered`
- `mpesa.c2b.validation-rejected`

---

## Datacultr Mock

### Real app contract

Mini-LOS uses:

- `POST /token/`
- `GET /v2/lifecycle/dem_:clientCode/get_all_notifications/`
- `GET /v2/lifecycle/dem_:clientCode/get_nudges/`
- `PUT /v3/lifecycle/dem_:clientCode/bulkapplylock/`
- `POST /v3/lifecycle/dem_:clientCode/bulk_custom_notification/`
- `PUT /v3/lifecycle/dem_:clientCode/bulkapplynudge/`
- `PUT /v3/lifecycle/dem_:clientCode/bulkapplyunlock/`
- `PUT /v3/dem_:clientCode/auto_lock_activate/`
- `POST /v3/lifecycle/dem_:clientCode/applyunlock/`
- `POST /v2/lifecycle/dem_:clientCode/get_device_passcode/`

Login response must include:

```json
{
  "access": "mock-datacultr-token"
}
```

Notification catalog response shape:

```json
{
  "notifications": [
    {
      "notification_code": "PAY_REMINDER",
      "title": "Payment Reminder"
    }
  ]
}
```

Nudge catalog response shape:

```json
{
  "nudges": [
    {
      "code": "LOCK_WARNING",
      "title": "Lock Warning"
    }
  ]
}
```

Offline PIN response shape:

```json
{
  "message": "Passcode fetched successfully",
  "passcode": "1234"
}
```

### Multipart bulk operations

The app sends multipart forms with:

- `TransactionId`
- `TransactionID`
- `file`
- optional `code` for custom notification

The CSV column expectations from the current app are:

Lock:

```text
IMEI,TriggerID,Channel_Code,Link
```

Nudge:

```text
IMEI,TriggerID
```

Unlock:

```text
IMEI,TriggerID,Channel_Code
```

Auto-lock:

```text
IMEI,DueDate,Time
```

### Mock behavior

Support:

- token issuance
- static notification and nudge catalogs
- multipart CSV parsing
- operation receipts for bulk actions
- per-row success/failure simulation

Suggested response format for bulk actions:

```json
{
  "status": "accepted",
  "transactionId": "txn-001",
  "processed": 2,
  "failed": 0
}
```

### Scenarios

- `datacultr.catalog.default`
- `datacultr.lock.accepted`
- `datacultr.lock.partial-failure`
- `datacultr.unlock.accepted`
- `datacultr.notification.accepted`
- `datacultr.offline-pin.available`

## Internal Admin Endpoints

Add non-provider test-control endpoints under a separate namespace:

- `GET /__mock/health`
- `POST /__mock/reset`
- `GET /__mock/state`
- `POST /__mock/scenarios/:provider/:scenario`

Rules:

- keep them disabled outside local/test mode
- never mix them with provider namespaces

## Configuration

Recommended `.env`:

```dotenv
PORT=4010
NODE_ENV=development
LOG_LEVEL=info
DEFAULT_DELAY_MS=0
ENABLE_WEBHOOKS=true
ALLOW_ADMIN_ENDPOINTS=true

SMILE_ID_REQUIRE_SIGNATURE=false

CLADFY_WEBHOOK_TARGET=http://localhost:8082/api/v1/public/integrations/cladfy/webhook
CLADFY_ASYNC_DELAY_MS=3000

MPESA_CALLBACK_URL=http://localhost:8082/api/v1/public/payments/mpesa/callback
MPESA_ASYNC_DELAY_MS=3000

DATACULTR_DEFAULT_CLIENT_CODE=demo
DATACULTR_DEFAULT_USERNAME=demo
DATACULTR_DEFAULT_PASSWORD=demo
```

## Suggested Local Wiring For Mini-LOS

Use local URLs like:

- Smile ID: `http://localhost:4010`
- Cladfy: `http://localhost:4010`
- Datacultr: `http://localhost:4010`

For M-PESA, the current Spring code derives Safaricom URLs internally. To use the mock cleanly, either:

1. add a configurable Daraja base URL in Mini-LOS for local/test mode, or
2. stub the gateway in tests, or
3. run a local HTTP proxy that maps Safaricom hostnames to the mock server

Option 1 is the cleanest long-term approach.

## Testing Strategy

### Unit tests

- schema validation
- scenario resolution
- CSV parsing
- async transition scheduling
- webhook retry behavior

### Integration tests

- Smile ID pass/fail
- Cladfy create client then upload then webhook
- M-PESA token then STK push then callback
- M-PESA C2B register URL then simulated confirmation
- Datacultr login then bulk action

### Contract tests

Create tests that assert mock responses still satisfy the current Spring DTOs.

## Recommended Build Order

1. bootstrap Express + TypeScript project
2. add shared logging, validation, error handling
3. implement Smile ID
4. implement Cladfy with async state and webhook delivery
5. implement M-PESA auth, STK flows, and callbacks
6. implement Datacultr token, catalogs, and bulk endpoints
7. add admin endpoints and fixtures
8. add tests and seed scenarios

## Acceptance Criteria

- Mini-LOS can point Smile ID calls to the mock and complete KYC tests
- Mini-LOS can point Cladfy calls to the mock and complete statement-analysis tests
- the mock supports M-PESA token and STK initiation responses expected by the app
- the mock can deliver Cladfy webhooks and M-PESA callbacks back to Mini-LOS
- the mock supports Datacultr token, catalogs, and device-control flows used by the app
- async provider behavior can be simulated deterministically
- failure scenarios can be switched without code changes
- logs are clear enough to debug cross-service flows locally




