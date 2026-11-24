# Internal M-Pesa provider: phase 3 manual test

Use a non-production tenant and synthetic statements. Configure its `statementProvider` as `INTERNAL_MPESA` and `statementAnalysisMode` as `MANUAL` to control when analysis starts. The test account needs `CREDIT_CHECK_RUN`, `CREDIT_CHECK_VIEW` and the permissions required to upload documents and manage statement OTPs. Create an eligible application and authenticate as its tenant user. No tenant ID is supplied to the statement endpoints.

1. Upload `src/test/resources/fixtures/balance-example.pdf` to `POST /api/v1/applications/{applicationId}/documents` as multipart fields `documentType=MPESA_STATEMENT` and `file`. Save the returned document ID.
2. Call `POST /api/v1/applications/{applicationId}/statement-analysis/run?documentId={documentId}`. The response identifies provider `INTERNAL_MPESA`; processing runs asynchronously. Poll `GET /api/v1/applications/{applicationId}/statement-analysis` until it reaches `MANUAL_REVIEW_REQUIRED`. Verify `effectiveOutcome` and the system review also indicate manual review, the recommendation is `REVIEW`, and the application reaches `STATEMENT_MANUAL_REVIEW`. A score must not be invented.
3. The full extracted result, including transactions and monthly metrics, is stored against the tenant's analysis for the next phase. The existing endpoint does not yet expose those internal details; the focused tests validate the stored extraction. No internal statement debit is created in this phase; billing is planned for phase 6.
4. Repeat with a synthetic malformed PDF or a PDF without extractable text. Poll for `PROCESSING_FAILED`, with `effectiveOutcome=PROCESSING_FAILED` and a safe error code in the summary. Verify there is no system rejection review and the application is in `STATEMENT_FAILED`, from which another analysis can be requested.
5. For a password-protected synthetic PDF, add its correct password through `POST /api/v1/applications/{applicationId}/statement-otps` with `{"otps":["<password>"]}`, then run the analysis. Verify manual review and that the matching OTP becomes successful. If an encrypted PDF is processed without a matching pending OTP, verify `PROCESSING_FAILED` with `INCORRECT_PASSWORD`; adding the correct OTP can queue a retry through the existing endpoint. Do not include real passwords in logs or test documentation.
6. As a second tenant, try to access or run analysis for the first tenant's application or document. Verify access is denied or the resource is not found. Test a Cladfy tenant separately and verify its existing submission and completion flow still works.

For automated verification run:

```powershell
.\mvnw.cmd clean test "-Dtest=InternalMpesaProcessingServiceTest,InternalMpesaCompletionServiceTest,StatementAnalysisProcessingServiceTest,StatementAnalysisServiceTest,StatementProviderRegistryTest,CladfyStatusPollingServiceTest"
```

The repository's general context test requires a configured environment: its MariaDB-specific Flyway callback does not run on H2, and the test configuration also lacks KYC settings. Use a configured MariaDB test environment to validate application startup and migrations.

The current asynchronous executor is in-process. Durable recovery after a process restart is scheduled for phase 7.
