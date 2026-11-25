# Statement subscription policy: phase 5 manual test

Use a non-production MariaDB database, apply Flyway migration V42, and authenticate as a platform administrator. These policies apply to the internal M-Pesa provider. Cladfy's existing eligibility and billing behavior is unchanged. Simulated analyses are also excluded.

Existing plans default to allowing negative balances and `CHARGE_EVERY_ANALYSIS`. A tenant subscription inherits its current plan's defaults unless it has overrides. Plan changes affect future submissions; an analysis already queued retains its captured terms. Changing a subscription to a different plan clears its overrides so the new plan's defaults apply.

1. Set the plan defaults with `PUT /api/v1/internal/subscription/plans/{planId}/statement-policy`, requiring `SUBSCRIPTION_PLAN_MANAGE`:

   ```json
   { "allowNegative": false, "repeatPolicy": "CHARGE_EVERY_ANALYSIS" }
   ```

   `repeatPolicy` may also be `CHARGE_ONCE_PER_DOCUMENT`. A plan's current values appear as `statementAllowNegative` and `statementRepeatPolicy` in its response.

2. Assign the plan using the existing tenant subscription endpoint, with a current billing period and `initialPrepaidAmount`. Use `GET /api/v1/internal/tenants/{tenantId}/subscriptions/current` to find the subscription ID and check its effective policy. The period is inclusive of its start and exclusive of its end; `ACTIVE` status alone is insufficient when outside that period.

3. Set an override with `PUT /api/v1/internal/tenants/{tenantId}/subscriptions/{subscriptionId}/statement-policy`, requiring `TENANT_SUBSCRIPTION_MANAGE`:

   ```json
   { "allowNegativeOverride": true, "repeatPolicyOverride": "CHARGE_ONCE_PER_DOCUMENT" }
   ```

   The response shows both override fields and `effectiveStatementAllowNegative` / `effectiveStatementRepeatPolicy`. This PUT replaces both overrides: use `null` for either field to inherit that value from the plan. Changing one tenant's subscription must not change another tenant's effective policy.

4. Configure a tenant for `INTERNAL_MPESA` and `MANUAL` statement analysis, and use an eligible application and synthetic M-Pesa PDF. Without a currently active subscription, calling `POST /api/v1/applications/{applicationId}/statement-analysis/run?documentId={documentId}` must return 403 and must not create an analysis. Repeat with a suspended, cancelled, expired or future subscription.
5. With `allowNegative` false and a prepaid balance below the plan's statement price, the same request must return 403. Top up the subscription using its existing `/{subscriptionId}/top-ups` endpoint, then submit again. With `allowNegative` true, submission is accepted even if the balance is below the price. Existing pending and running internal analyses on the same subscription count against available balance when negative balances are disabled.
6. In an administrator-controlled database session, inspect the submitted analysis by tenant and analysis ID. Confirm `billing_subscription_id`, `billing_plan_id`, `billing_amount`, `billing_currency`, `billing_allow_negative`, `billing_repeat_policy` and `billing_captured_at` match the terms at submission. Change the plan price, currency, policy or subscription status afterwards; the recorded snapshot must remain unchanged. Do not modify the database manually.
7. Verify an ordinary tenant user cannot change plan defaults or tenant subscription overrides. Verify the same tenant cannot run or inspect another tenant's application.

Phase 6 debits successful extraction using the captured terms; see `statement-subscription-phase-6-test.md`. Existing KYC and Cladfy charges can reduce the balance while an internal analysis is pending. If the captured policy disallows a negative balance, the result remains available and its charge enters `PENDING_FUNDS` until an administrator retries it after top-up. `CHARGE_ONCE_PER_DOCUMENT` uses the LOS document ID; detecting identical reuploads needs a separate decision and implementation. Outstanding jobs interrupted by a restart may continue to hold admission capacity until recovered; durable recovery is planned for phase 7.

Focused tests:

```powershell
.\mvnw.cmd clean test "-Dtest=StatementSubscriptionEligibilityServiceTest,StatementSubscriptionPolicyTest,SubscriptionBillingLockTest,StatementAnalysisServiceTest,StatementAnalysisProcessingServiceTest,InternalMpesaCompletionServiceTest,StatementAnalysisResponseApiTest,CladfyStatusPollingServiceTest"
```

The existing H2 test configuration cannot run the MariaDB-specific Flyway callback. Run migration and concurrent-admission verification against MariaDB; the focused tests alone do not validate database locking.
