# Sanitized extraction fixtures

`SyntheticPdfs` builds minimal PDFBox documents in memory: selectable text on two pages, password-encrypted/restricted documents, an image-only page, a blank page, and a mixed text/blank document. Tests compare extracted text against `expected-page-1.txt` and assert page numbers, coordinates, errors, and limits. These are extraction fixtures, not a supported M-Pesa transaction layout yet.

Encrypted fixtures use fresh random passwords in memory; no passwords or customer statements are stored. To inspect the non-encrypted generated PDFs, run the SyntheticPdfs main class after building; it writes only into analyzer-tests/target/fixtures. See the root README for commands.

## Provisional table layout v1

`mpesa-table-v1.pdf` is a committed, entirely synthetic two-page table produced by StatementPdfFixtures. `mpesa-table-v1.expected.json` independently specifies three normalized drafts, including a narration crossing a page boundary. StatementParsingTest compares the PDF-to-parser output to that JSON and separately checks coordinates, raw source preservation, error cases, and year resolution. Scenario variants are generated in memory; no real identities or passwords are used. This fixture establishes the provisional supported schema, not compatibility with an actual Safaricom export.

## Financial example

`balance-example.pdf` contains entirely synthetic classified-narration candidates, exact summary totals/count, and running balances. `balance-example.expected.json` independently records the documented 11,900 monthly surplus and 26,476.81 wallet closing balance. FinancialAnalysisTest verifies it through the default PDF-to-analysis entry point, and exercises mismatch, duplicate, missing-balance, partial/empty month, cross-month, unresolved classification, negative/zero balance, and average-rounding scenarios. Parser v2 adds summary metadata; the earlier layout fixture's expected parser version was updated accordingly without changing its transaction oracle.

The synthetic balance-duplicate.pdf variant repeats a receipt reference while preserving all original movements/totals. MariaDB integration tests verify that all nine classified rows and the UNDETERMINED review result are retained instead of failing a unique receipt-reference constraint.
