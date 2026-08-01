# Prompt To Build The Integration Mock Server

Build a production-quality local mock integrations server in a new folder named `mock-integrations` using `Express` and `TypeScript`.

## Product context

This repository is a Spring Boot modular monolith for a multi-tenant Loan Origination System.

Architecture:

- React frontend -> Mini-LOS backend -> external providers
- frontend must never call external providers directly
- this mock server represents external providers only
- keep Mini-LOS as the orchestration boundary

The mock server must simulate these external integrations used by Mini-LOS:

- Smile ID
- Cladfy
- M-PESA Daraja
- Datacultr

## Critical constraints

- Use Node.js + Express + TypeScript
- Keep code modular by provider
- Validate requests with `zod`
- Support JSON and multipart endpoints
- Use in-memory storage first
- Add deterministic fixtures and scenario switching
- Implement structured logging
- Add tests for the core flows
- Do not put business logic from Mini-LOS into the mock server
- Do not make the frontend depend on this mock directly

## Contract requirements from the current Mini-LOS codebase

### Smile ID

Implement:

- `POST /v2/verify`

Accept a JSON body with fields including:

- `source_sdk`
- `source_sdk_version`
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
- `partner_params`

The request body should match what Mini-LOS currently sends:

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

Smile ID request notes:

- `source_sdk` is `rest_api`
- `country` is `KE`
- `job_type` is `5`
- `timestamp` is generated in UTC with millisecond precision
- `signature` is HMAC-SHA256 over `timestamp + partner_id + sid_request`, base64-encoded
- `partner_params.job_id` is the application id
- `partner_params.user_id` is `<tenant_id>:<national_id>`

Return a body compatible with:

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

Support scenarios:

- pass
- partial-match
- fail
- invalid-id
- timeout

### Cladfy

Implement:

- `POST /clients`
- `POST /documents`
- `GET /documents/:documentId/status`
- `GET /clients/analysis_results`
- `GET /clients/:clientId/scoring`

Notes:

- `GET /clients/analysis_results` uses header `X-Client-Id`
- `POST /documents` is multipart
- multipart fields include `file`, `client_id`, `provider`, `webhook`, `national_id`, and optional `password`
- `POST /clients` must support success and `409 conflict` with `existing_client`
- Cladfy should support async processing and optional webhook callbacks

Use these concrete request and response shapes:

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

#### `GET /documents/:documentId/status`

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

This request uses header:

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

#### `GET /clients/:clientId/scoring`

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

Support scenarios:

- success
- client-conflict
- password-required
- analysis-failed
- webhook-delayed
- webhook-disabled

Webhook requirements:

- when `POST /documents` includes a `webhook` multipart field, store it as the callback target
- after async processing completes, POST to that webhook URL unless webhooks are disabled
- if the `webhook` field is missing, optionally fall back to `CLADFY_WEBHOOK_TARGET`
- store webhook delivery attempts in memory
- retry up to 3 times on network errors or `5xx`
- do not retry on `2xx` or `4xx`

Use this webhook body shape:

```json
{
  "document_id": 7001,
  "client_id": 1001,
  "business_id": 1
}
```

Add headers like:

- `X-Mock-Provider: cladfy`
- `X-Mock-Scenario: cladfy.success`
- `X-Mock-Delivery-Id: <uuid>`

### M-PESA Daraja

Implement:

- `GET /oauth/v1/generate?grant_type=client_credentials`
- `GET /oauth/v2/generate?grant_type=client_credentials`
- `POST /mpesa/stkpush/v1/processrequest`
- `POST /mpesa/c2b/v2/registerurl`

Access-token response shape:

```json
{
  "access_token": "mock-access-token",
  "expires_in": "3599"
}
```

STK push response shape:

```json
{
  "MerchantRequestID": "mock-merchant-001",
  "CheckoutRequestID": "ws_CO_001",
  "ResponseCode": "0",
  "ResponseDescription": "Success. Request accepted for processing",
  "CustomerMessage": "Success. Request accepted for processing"
}
```

C2B registration response shape:

```json
{
  "OriginatorCoversationID": "conv-001",
  "ResponseCode": "0",
  "ResponseDescription": "Success"
}
```

Support scenarios:

- stk.accepted
- stk.insufficient-funds
- stk.cancelled
- stk.timeout
- c2b.registered

Webhook requirements:

- after `POST /mpesa/stkpush/v1/processrequest`, return the normal immediate Daraja acknowledgement
- store the STK request as pending
- after async delay, POST callback payload to the incoming `CallBackURL`
- if `CallBackURL` is absent in a manual test, optionally fall back to `MPESA_CALLBACK_URL`
- support success and failure callback payloads
- store callback delivery attempts in memory
- retry up to 3 times on network errors or `5xx`
- do not retry on `2xx` or `4xx`

Use this STK callback payload shape for success:

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
          { "Name": "Amount", "Value": 1000 },
          { "Name": "MpesaReceiptNumber", "Value": "NLJ7RT61SV" },
          { "Name": "Balance" },
          { "Name": "TransactionDate", "Value": 20260714103045 },
          { "Name": "PhoneNumber", "Value": 254700000001 }
        ]
      }
    }
  }
}
```

For failure scenarios:

- return non-zero `ResultCode`
- set a failure `ResultDesc`
- omit `CallbackMetadata` when appropriate

Also support optional C2B deposit callback simulation:

- store `ConfirmationURL` and optional `ValidationURL` from `POST /mpesa/c2b/v2/registerurl`
- add `POST /__mock/mpesa/c2b/simulate-deposit-callback` so tests can trigger deposit validation then deposit confirmation callbacks
- require `tenantId` in the mock simulation request and use it only in the Mini-LOS path, not in the Daraja webhook body
- have the mock call Mini-LOS tenant endpoints in this order:
  1. `POST /api/v1/public/tenants/{tenantId}/collections/c2b/deposits/validate`
  2. `POST /api/v1/public/tenants/{tenantId}/collections/c2b/deposits/callback`
- optionally support the legacy generic callback endpoint `POST /api/v1/public/payments/mpesa/deposits/callback`
- use the same Daraja C2B payload for both validation and confirmation
- expect validation to return:

```json
{
  "ResultCode": "0",
  "ResultDesc": "Accepted"
}
```

- expect confirmation callback acknowledgement to return:

```json
{
  "ResultCode": 0,
  "ResultDesc": "Accepted"
}
```

Use this request payload to the mock simulation endpoint:

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

Use this payload when the mock calls Mini-LOS validation and confirmation endpoints:

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

Important matching note from the current code:

- Mini-LOS matches a deposit to an application using `BillRefNumber` normalized as a phone number
- `MSISDN` is not the matching field
- for a successful simulated match, set `BillRefNumber` to the applicant phone number

If possible, include internal control endpoints for forcing STK completion/failure.

### Datacultr

Implement:

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

Requirements:

- issue bearer tokens from `/token/`
- parse multipart uploads for bulk operations
- parse the uploaded CSV files
- return static or scenario-driven catalogs for notifications and nudges
- return operation receipts for lock/unlock/nudge/notification requests
- return `message` and `passcode` from offline PIN lookup

Expected CSV headers:

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

Support scenarios:

- catalog.default
- lock.accepted
- lock.partial-failure
- unlock.accepted
- notification.accepted
- offline-pin.available

## Implementation requirements

Create this project structure:

```text
mock-integrations/
  package.json
  tsconfig.json
  .env.example
  src/
    app.ts
    server.ts
    config/
    common/
    providers/
      smile-id/
      cladfy/
      mpesa/
      datacultr/
    state/
    fixtures/
  test/
```

Use this design:

- `app.ts` wires middleware and routes
- each provider has `routes`, `service`, `schemas`, and `store`
- add a shared error middleware
- add request-id middleware
- add scenario resolution middleware or helper
- add fixture seed loading

## Admin/test-control endpoints

Add a separate internal namespace:

- `GET /__mock/health`
- `POST /__mock/reset`
- `GET /__mock/state`
- `POST /__mock/scenarios/:provider/:scenario`
- `POST /__mock/mpesa/stk/:checkoutRequestId/complete`
- `POST /__mock/mpesa/stk/:checkoutRequestId/fail`
- `POST /__mock/mpesa/c2b/simulate-deposit-callback`

These endpoints must be clearly separated from provider endpoints and easy to disable via env vars.

## Environment variables

Add `.env.example` with at least:

```dotenv
PORT=4010
NODE_ENV=development
LOG_LEVEL=info
DEFAULT_DELAY_MS=0
ALLOW_ADMIN_ENDPOINTS=true
ENABLE_WEBHOOKS=true
CLADFY_WEBHOOK_TARGET=http://localhost:8082/api/v1/public/integrations/cladfy/webhook
CLADFY_ASYNC_DELAY_MS=3000
MPESA_CALLBACK_URL=http://localhost:8082/api/v1/public/payments/mpesa/callback
MPESA_ASYNC_DELAY_MS=3000
SMILE_ID_REQUIRE_SIGNATURE=false
DATACULTR_DEFAULT_CLIENT_CODE=demo
DATACULTR_DEFAULT_USERNAME=demo
DATACULTR_DEFAULT_PASSWORD=demo
```

## Quality bar

- Strong typing throughout
- No `any` unless truly unavoidable
- Clean separation of route handling from provider behavior
- Clear logs for local debugging
- Minimal duplication
- Small focused files
- Reasonable test coverage for happy path and failure path

## Deliverables

Generate:

1. the full `mock-integrations` project
2. provider route implementations
3. fixture-driven sample data
4. tests for each provider's primary flow
5. a `README.md` with run instructions and example curl commands

## Important implementation note for M-PESA

The current Spring code derives Safaricom base URLs internally. If you also touch the Mini-LOS side, prefer introducing a configurable Daraja base URL for local/test use. If you do not modify Mini-LOS, still keep the M-PESA mock implementation complete on its own.

## Definition of done

- the mock server starts locally with one command
- Smile ID flow can be exercised by Mini-LOS against the mock
- Cladfy async upload, polling, and webhook flow works
- M-PESA token and STK initiation endpoints return the expected contract
- Datacultr catalogs and bulk operations work with multipart uploads
- scenarios can be switched without code changes
- the project is documented and testable



