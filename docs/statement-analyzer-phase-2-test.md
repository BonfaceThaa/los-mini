# Internal statement analyzer: phase 2 verification

> Phase 3 connects this core to the tenant workflow. Use `statement-analyzer-phase-3-test.md` to test the application flow.

Phase 2 added the standalone analyzer core and synthetic fixtures to the LOS build. Phase 3 connects it to the tenant statement workflow.

From the repository root, using Java 21 and Maven, run:

```powershell
.\mvnw.cmd clean test "-Dtest=StatementParsingTest,SharedReceiptRulesTest,SameTimeBalanceTest,ReconciliationToleranceTest,PdfExtractionTest,PartialMonthStatusTest,LoanProviderTest,IncludedReceiptsTest,FinancialAnalysisTest,DetailedStatementParsingTest,CoreSmokeRunnerTest,ClassificationTest,AnalysisContractsTest,PdfBoxStatementPdfPasswordVerifierTest,ApplicationStatementOtpServiceTest"
```

On systems where the Maven wrapper is unavailable, use `mvn` with the same arguments. A successful run ends with `BUILD SUCCESS`. Maven's test reports are in `target/surefire-reports`.

The tests exercise selectable-text extraction and source coordinates, encrypted PDFs with correct and incorrect passwords, malformed and unsupported inputs, extraction limits, parsing, classification, reconciliation, monthly metrics, and loan-provider detection. They also verify the LOS PDF password verifier with PDFBox 3. The PDFs in `src/test/resources/fixtures` are synthetic; see the fixture README for their intended scope.

For closer inspection, run individual test classes, for example:

```powershell
.\mvnw.cmd test "-Dtest=PdfExtractionTest,FinancialAnalysisTest,ClassificationTest,LoanProviderTest"
```

Review the assertions and corresponding fixtures under `src/test/java/com/credvenn/stmt/analyzer/tests` and `src/test/resources/fixtures`. The synthetic fixtures establish expected behavior for their layouts; they do not establish compatibility with every actual M-Pesa export. Do not place customer statements or passwords in the repository or test reports. Real-world validation should use authorized, protected test data in a suitable environment.
