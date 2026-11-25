# Internal statement results: phase 4 manual test

Use a configured non-production environment and follow the phase 3 guide to create an `INTERNAL_MPESA` analysis using the synthetic fixture `src/test/resources/fixtures/balance-example.pdf`. Sign in as a user of that tenant with `CREDIT_CHECK_VIEW`.

Call `GET /api/v1/applications/{applicationId}/statement-analysis`. The existing `analysis`, `review` and `effectiveOutcome` wrapper and legacy analysis fields remain. A completed internal result additionally contains:

- `analysis.statement`: currency, statement period, extracted and rejected counts, and total inflow and outflow. For the fixture, verify total inflow is `32076.81` and total outflow is `6600.00`.
- `analysis.monthlySummary`: averages over complete, reconciled months when available. An unavailable average remains `null`; it is not zero.
- `analysis.monthlyMetrics`: per-month coverage, status, credits, debits, eligible and excluded amounts, balances, warnings and income calculation. For the fixture, verify monthly balance `11900.00` and closing balance `26476.81`.
- `analysis.categories`: all extracted transactions grouped by main and subcategory, with counts, total amounts, credits and debits. Loan repayment provider totals include classified debit repayments only. Uncategorized transactions and unidentified lenders have explicit labels. Category totals include partial months and excluded transactions.
- `analysis.quality`: extraction status, parsing confidence, warnings, reconciliation errors and analyzer versions.

The category count must match `statement.transactionCount`. Category amounts are gross amounts, so `totalAmount` is credits plus debits. Category data reflects classifier output; it is not a verified credit decision. No raw PDF lines, transaction narrations or individual transaction records are returned in these new sections. A missing result while processing, or a section unavailable for the provider, is omitted; an available section with no entries uses an empty list.

Verify access with a second tenant's credentials and with a user lacking `CREDIT_CHECK_VIEW`: the request must be rejected or the application must not be found. Test a Cladfy analysis and confirm its existing fields, values and provider behavior are unchanged; internal sections are omitted for it. Test a processing failure and a pending analysis to confirm no summary is fabricated.

Run the focused tests with Java 21 and Maven:

```powershell
.\mvnw.cmd clean test "-Dtest=InternalStatementResultMapperTest,StatementAnalysisResponseApiTest,InternalMpesaCompletionServiceTest,InternalMpesaProcessingServiceTest,StatementAnalysisServiceTest,StatementAnalysisProcessingServiceTest,CladfyStatusPollingServiceTest"
```

The general H2 context test has pre-existing configuration limitations described in the phase 3 guide; a configured MariaDB environment is needed for an end-to-end HTTP check. See the phase 6 guide for billing. Durable job recovery remains a later phase.
