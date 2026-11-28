# INTERNAL_MPESA credit scoring: implementation plan

## 1. Purpose and scope

Implement a configurable, explainable assessment for INTERNAL_MPESA that combines a statement score and a questionnaire score. The assessment is selected using the tenant and the application's origination profile. It returns `creditScore`, `riskTier` and `recommendation` through the existing application statement-analysis endpoint.

This document is a proposed implementation plan, not a description of functionality already delivered. Each phase has a testable outcome. Do not change CLADFY behavior, create product-specific scorecards, or change statement billing eligibility as part of this work.

### Agreed decisions

- Statement component: 60%; questionnaire component: 40%. Enabled statement factors have equal relative weights of 1 each, subject to data availability and the approved scoring rules.
- Exclude Q3, the self-reported monthly income question.
- When Q4 is `NO`, Q5 is not applicable. Q7 applies only when Q1 is business/self-employment.
- Questions are configured by the tenant; do not automatically create the listed questions or preset scores. A question's weight is optional at creation and can be added or changed when the question is updated. Answer points are configured when answer options are created or updated.
- A tenant sets whether each question is required when creating or updating it. A missing required applicable answer results in `REVIEW`; a missing optional answer is excluded from the questionnaire numerator and denominator. Inapplicable questions do not trigger review regardless of required status. If no positive applicable answered weight remains, return `REVIEW`.
- For Q5, use the lowest **answer point score** among selected credit types as that question's score. Q5 has one question weight; selected answers do not each have a separate question weight.
- Remove `Other` from Q1 and Q5. Exclude Q5 and Q7 without triggering review when their applicability conditions are not met.
- Insufficient information leads to `REVIEW`.
- Require at least four usable complete months and use all non-partial months. The five non-debt statement factors are required; observed debt payments are optional and disabled until configured.
- Assign a low factor score where classification is uncertain but the factor can still be calculated; if a required factor is unavailable, return `REVIEW`. The low score must be configured and approved.
- Implement the observed debt-payment scoring measure and configurable bands in the current phase. The factor is optional per scorecard: when disabled, exclude it without triggering `REVIEW` and give each of the five required factors an equal one-fifth share of the statement component. When enabled, include it at the same weight as the other five; if its required data is unavailable, return `REVIEW`.
- Use the approved internal score bands and tier-to-recommendation mapping in phase 4. Apply automatic statement workflow effects like the current CLADFY integration: pass advances to statement verification and offer-readiness checks, decline marks the statement failed, and review requires manual review. This does not itself constitute final loan approval or disbursement.
- Scorecards are scoped to a tenant and origination profile, not a product.
- Pin the scorecard version active for the resolved origination profile when the application is created. Save the answers used by each assessment as a reproducible snapshot at assessment initiation.
- Allow users with `CREDIT_CHECK_RUN` to rescore for free using the existing extracted statement. A rescore uses the scorecard version active when the rescore starts and the current saved answers; record both in a new assessment. Preserve the application's original pinned version and earlier results. Reject a rescore if the application has moved past statement analysis.
- Defer applicant age eligibility, DOB verification, gender collection purpose and fairness reporting to the next phase. The existing application fields remain available; do not make these deferred policies prerequisites for the current scoring work. The recommended future policy is age eligibility only when justified, and gender excluded from scoring and decisions; it still requires approval in that phase.
- A statement analysis remains chargeable once transactions are successfully extracted, including where scoring results in `REVIEW`. Existing plan, repeat-charge and negative-balance policies continue to apply.

### Decisions still to approve

1. Validate and approve the illustrative factor bands, the configurable low score for uncertain classification, and the observed debt-payment measure and bands for scorecards that enable it. Implement debt-payment scoring in the current phase; its exact measure and scoring bands still require approval.
Age eligibility policy, DOB verification requirements, gender collection purpose and fairness reporting are deferred to the next phase and are not current-phase approval items.

Obtain lending, data-protection and legal review before enabling decision automation. Proposed scores and cutoffs are not probabilities of default and should be calibrated against repayment outcomes.

## 2. Current project behavior

- `ApplicationService` resolves the application profile and saves `applicationVariables`. `V35__version_application_questionnaires.sql` adds definition versions and selection limits. Reuse and extend this questionnaire machinery; do not create a competing answer store.
- `InternalMpesaCompletionService` stores the raw analyzer result. If there are no extracted transactions, it marks processing failed. Otherwise it invokes billing, sets `MANUAL_REVIEW_REQUIRED` and `REVIEW`, and records a system review.
- `AnalysisResult` provides normalized transactions, coverage and status, monthly totals, eligible inflows, debt payments, spending, `monthlyBalance`, and monthly averages. `averageMonthlyBalance` is the average of the analyzer's monthly balance measure; it is not automatically an average daily account balance.
- `StatementAnalysisService` exposes the statement-analysis response and computes `effectiveOutcome`. Existing `StatementAnalysis` already has `creditScore`, `riskTier` and `recommendation` fields.
- CLADFY maps provider risk tiers `EXCELLENT`/`GOOD` to `PASSED` and `APPROVE`, `FAIR` to manual review, and `POOR`/`VERY POOR` to `FAILED` and `DECLINE`. Unknown tiers require review. Its numerical score cutoffs are not defined by this mapping, so do not infer CLADFY's thresholds from these labels.

Relevant code: `application/ApplicationService`, `applicationvariable`, `origination`, `statement/InternalMpesaCompletionService`, `statement/StatementAnalysisService`, `statement/StatementReviewService`, `statement/CladfyStatementAnalysisProvider`, and `stmt/analyzer/core/AnalysisResult`.

## 3. End-to-end design

1. A tenant administrator configures questions, optional question weights and response options with points using the existing questionnaire capability, then publishes a scorecard version for an origination profile. The system does not create a default set of questions.
2. The application records its resolved profile, the scorecard version active at application creation and versioned questionnaire answers.
3. INTERNAL_MPESA extracts transactions and persists its result. Charge according to the existing billing rules once extraction succeeds; a later missing answer or scoring failure must not undo or duplicate that charge.
4. A scoring worker loads the tenant-scoped application, answers, pinned scorecard version and statement evidence. It evaluates data quality, applicable questions, statement factors and eligibility rules.
5. It persists a versioned assessment with the scores, evidence, reasons and recommendation. It then updates the existing statement analysis and review/workflow consistently with the approved decision policy.
6. The existing GET statement-analysis endpoint returns the current assessment and its explanation. Historical assessments remain reproducible and distinguishable from later rescoring.

Scoring may run when extraction finishes only if the chosen answer snapshot is available. Otherwise persist the extracted result and a review/pending-scoring reason, then let an authorized action or event trigger a separate idempotent assessment. Do not rerun extraction or rebill merely because answers or scorecard configuration changed.

## 4. Phase 1 — Scorecard configuration and versioning

1. Inspect the existing application-variable definitions, required/optional settings, allowed options and profile associations. Extend their create and update APIs so an administrator can configure whether a question is required, optionally provide or update its scoring weight, and configure or update points for each answer option. Reuse existing required settings where suitable rather than duplicating them. Reference immutable question/option versions by stable codes; preserve existing application capture contracts. Do not seed questions, answer points or weights.
2. Add tenant-scoped Flyway schema for scorecard, immutable published scorecard version, profile assignment, question rules, answer points, conditional applicability, component weights, statement factor rules, tier boundaries, decision policy and audit metadata. Exact tables should follow existing schema conventions.
3. Enforce one selected published version per tenant/profile at a time, while retaining previous versions. Define activation and effective dates and reject cross-tenant references.
4. Add tenant-scoped, permission-checked create/edit-draft, validate, publish and read APIs. Published versions cannot be edited; create a new version instead. Derive tenant from authentication, never from the frontend.
5. Allow an incomplete draft, including a question without a weight, but prevent publication of a scoring version until every configured scored question has a valid weight and every selectable scored answer has points. Validate nonnegative weights, a positive total applicable weight, answer scores in 0–100, unambiguous option codes, valid condition references, nonoverlapping/exhaustive tier and decision ranges, and statement/questionnaire component weights totaling 100%. Validate conditional paths at publication; if an unexpected runtime path has no positive applicable weight, return `REVIEW`.
6. At application creation, atomically pin the published scorecard version active for the resolved tenant/profile. The initial assessment uses this pinned version; each explicit rescore selects the published scorecard version active at rescore initiation. At each assessment initiation, atomically snapshot the selected scorecard version and current saved answers and their versions. Concurrent configuration or answer edits cannot silently change that run. If no scorecard is active at creation, record that fact and return `REVIEW`; do not silently assign a version retroactively. An authorized rescore may use a subsequently activated version. If no scorecard is active at rescore initiation, return `REVIEW` with an explicit reason and do not fall back silently.

**Test:** Confirm a new tenant receives no automatically created questionnaire. Create a question without a weight, add its weight later, and create or update answer options with points; confirm an incomplete draft cannot be published. Create two profiles for one tenant and publish distinct versions. Reject invalid weights, ranges, cross-tenant references and edits to a published version. Verify another tenant cannot read or modify them. Create version 2 and confirm version 1 remains unchanged and retrievable.

## 5. Phase 2 — Questionnaire rules and calculation

The tenant configures the questions and answer options using the existing questionnaire capability and stable codes. The system must not automatically create the following questions, options, weights or points. The following table describes the requested questionnaire and applicability rules; Q3 is not part of it. Q1 and Q5 must not offer an `Other` option in this questionnaire.

| Question | Conditional rule |
|---|---|
| Q1 main income source | Applicable to all; no `Other` option |
| Q2 duration of main income source | Applicable to all |
| Q4 outstanding loans | Applicable to all |
| Q5 types of credit | Applicable only if Q4 is yes; multi-select; no `Other` option |
| Q6 short-term credit use | Applicable to all |
| Q7 business operating duration | Applicable only if Q1 is business/self-employment |
| Q8 residential duration | Applicable to all |

Each scored question has a tenant-configured relative weight. The weight is optional on question creation and can be supplied or changed on update. Each answer option has tenant-configured points on a 0–100 scale, supplied at answer creation or update. A draft can be incomplete, but all required weights and selectable-option points must exist before its scoring version is published. Changes create a new version for future assessments and cannot alter already published versions or historical assessments.

For Q5, select the lowest configured answer point score among the chosen credit types. For example, if an applicant selects answers worth 80 and 40 points, Q5 contributes a response score of 40; the question's configured weight is applied once. If Q4 is no, Q5 is not applicable. If Q1 is not business/self-employment, Q7 is not applicable. Neither condition triggers review. Any unexpected missing or unrecognized applicable answer must not be treated as zero.

For each applicable answered question, `weighted = weight × answerScore`. Then `questionnaireScore = sum(weighted) / sum(applicable answered weights)`. Both score and answer values are on a 0–100 scale. Exclude genuinely inapplicable questions and unanswered optional questions from numerator and denominator. A missing required applicable answer makes the component insufficient and the overall recommendation `REVIEW`; never score the missing answer as zero. If no positive applicable answered weight remains, return `REVIEW`.

For example, if Q1 is business and required Q2 is missing, return `REVIEW`. Q5 has no effect if Q4 is no, even if Q5 is marked required. Q7 has no effect when Q1 is not business/self-employment, even if Q7 is marked required. Existing application-submission validation may reject missing required answers before creation; retain the scoring check for older applications, updated definitions and rescoring against a newer scorecard version. If a new version introduces a required question without a saved answer, return `REVIEW` until a valid answer is available.

Illustrative calculation only, not defaults: suppose a tenant configures respective Q1, Q2, Q4, Q5, Q6, Q7 and Q8 weights of 10, 15, 10, 10, 20, 5 and 5, and a business owner's configured answer points are 100, 80, 50, 80, 50, 80 and 80. The weighted sum is 5,300 and applicable weight is 75, yielding **70.67/100** before final rounding. If Q4 is `NO`, Q5 is excluded entirely; if Q1 is not business, Q7 is excluded entirely. The tenant must explicitly configure its own values.

### Reference example for tenant setup

The following example gives an administrator values to consider when manually configuring a questionnaire. It is not seed data, an automatic default, an approved credit policy, or a statistically validated scoring model. Tenants choose their own question weights and answer points. The business should validate the chosen values and review outcomes before relying on them for lending decisions.

| Question | Example relative weight | Example answer options and points |
|---|---:|---|
| Q1: Main source of income | 10 | Salary/employment: 100; business/self-employment: 100; casual/contract: 60; farming/agriculture: 60 |
| Q2: Duration earning from main source | 15 | More than 3 years: 100; 2–3 years: 80; 1–2 years: 60; 6–12 months: 40; less than 6 months: 20 |
| Q4: Outstanding loans or credit | 10 | No: 100; yes: 50 |
| Q5: Types of credit | 10 | Bank: 80; SACCO: 80; employer: 80; hire purchase/asset finance: 70; M-Shwari/KCB M-PESA or similar: 60; digital/mobile lender: 50; Fuliza/overdraft: 40; informal loan: 40 |
| Q6: Frequency of short-term credit use for normal expenses | 20 | Never: 100; rarely: 80; sometimes: 50; often: 25; very often: 0 |
| Q7: Business operating duration | 5 | More than 3 years: 100; 2–3 years: 80; 1–2 years: 60; 6–12 months: 40; less than 6 months: 20 |
| Q8: Time living in current area | 5 | More than 5 years: 100; 3–5 years: 80; 1–3 years: 60; 6–12 months: 40; less than 6 months: 20 |

The administrator must define nonoverlapping duration ranges and their boundary values when creating the options. For example, specify whether an exact duration of two years belongs in the `1–2 years` or `2–3 years` option. Define the period represented by frequency choices such as `rarely` and `often` so applicants can answer consistently. Q1 and Q5 do not include `Other`; Q3 is excluded. Configure Q5 as multi-select with lowest selected answer points, and set the Q5 and Q7 applicability rules described above.

Implement a pure calculation service that returns applicable factors, source answer/version, response points, weight, component result and reason codes. Set rounding explicitly: use decimal arithmetic, keep intermediate precision, and round the final displayed score once using a documented rule. Avoid hard-coding question text or tenant-specific answers in the calculator.

**Test:** Verify administrator-created and updated weights and option points, every configured answer option, both conditional branches, lowest-point Q5 multi-select behavior, all-inapplicable protection, missing answers, invalid option/version, and a reproducible calculation. Confirm Q5 and Q7 exclusion does not trigger review, Q1 and Q5 do not offer `Other`, Q3 never contributes, and different tenants can configure different published rules. Confirm question or answer updates do not mutate existing assessment results.

## 6. Phase 3 — Statement factors and data quality

Build a versioned factor-extraction layer from the saved analyzer result and normalized transactions. Keep metric calculation separate from the score band configured for a metric. Record data period, exclusions, quality status, extractor version and evidence references. Use **all non-partial months** in the statement period and require at least **four usable complete months**. A covered zero-activity month must remain distinguishable from missing data. If fewer than four months are usable, return `REVIEW`. For an uncertain classification where a factor remains calculable, apply a configurable low factor score and record the reason and evidence; do not treat an unknown measurement as zero. An undetermined or unreconciled month does not automatically qualify as usable. If any required factor cannot be calculated, return `REVIEW`.

| Requested factor | Availability and proposed work |
|---|---|
| Deposits per month | Count transactions with narration `Deposit of Funds` in each non-partial month. Use normalized case and whitespace and confirm the actual statement variants and whether the text is an exact value or a prefix before publishing matching rules. Count only valid, non-duplicated rows; retain source evidence. |
| Withdrawals per month | Count transactions with narration `Customer Withdrawal At` in each non-partial month. Normalize case and whitespace and confirm whether matching is a prefix, as statement narrations may contain additional details. Count only valid, non-duplicated rows; retain source evidence. |
| Number of transactions per month | Derivable by counting normalized rows; define included statuses and handling of linked Fuliza rows. |
| Monthly surplus | Use the analyzer's `monthlyBalance` measure, not account closing balance or an average daily balance. The existing `averageMonthlyBalance` provides its average across eligible complete months, subject to analyzer quality and reconciliation rules. |
| Number of Fuliza loans | Count **observed Fuliza disbursement transactions**, not inferred distinct loans. Use the existing classification where it reliably identifies disbursements; validate the classification and prevent linked or duplicate rows from being counted twice. |
| Observed debt payments | Implement in this phase using analyzer eligible debt-payment amounts. Decide and approve whether to score average monthly amount, a ratio to eligible inflows, or another measure, then configure its bands and points. If enabled but required inputs or approved rules are unavailable, return `REVIEW`; a disabled factor does not trigger review. |
| Maximum days in arrears | Not reliably established by transaction dates alone. Requires trustworthy due dates, loan balances and repayment allocation; otherwise mark unavailable. |
| Previous and second-last Fuliza days in arrears | Require ordered, reliably matched loan episodes with due dates and repayments. An unreconciled or statement-boundary-truncated episode is unknown, not zero days. |

Existing monthly averages and cash-flow measures can be candidate factors, subject to approved thresholds and coverage. Do not assert that this analyzer can currently establish verified arrears or repayment rates. No unverified or missing factor should automatically become a favorable value. The five non-debt factors are required. Debt payments are optional at scorecard configuration time: a disabled factor is excluded, while an enabled factor must have a valid score; any enabled factor that is unavailable results in `REVIEW`. A calculable factor affected by uncertain classification receives the configured low score, with an explicit reason.

#### Fuliza disbursement example

Suppose a statement contains three identifiable Fuliza disbursement transactions in its complete months. The observed disbursement count is **3**, even if the transactions may belong to one continuing borrowing arrangement. A repayment transaction is not itself a disbursement and is not added to that count. Linked or duplicate records must not inflate it. The count describes activity **visible in the supplied statement period**; it does not prove the number of distinct loans, a balance outstanding, or whether any borrowing was repaid on time. If classification is uncertain but the factor is calculable, apply the configured low score; if it cannot be calculated, return `REVIEW`. Configure the count-to-points bands in the current phase once approved.

#### Equal factor weighting

Configure equal relative weights of `1` for each enabled factor. With debt payments disabled, the statement score is the sum of the five required 0–100 factor scores divided by five; each contributes 20% of the statement component or 12% of the combined score. With debt payments enabled, divide the sum of all six factor scores by six; each contributes one-sixth of the statement component or 10% of the combined score. This follows the existing 60% statement and 40% questionnaire weights. Store whether debt payments were enabled in the immutable scorecard version. Do not silently disable an enabled but unavailable factor: return `REVIEW`. Do not implement numeric bands as defaults.

#### Illustrative ranges for policy design

Use the per-month average across all usable complete months for count factors and monthly surplus. The following are **hypothetical, unvalidated reference ranges**, not Kenyan population averages, recommended approval criteria or automatically configured rules. No source reviewed establishes these cutoffs or their relationship to repayment. They are suitable for testing factor configuration and shadow evaluation only; credit and fairness review and outcome-based calibration are required before lending use. Each range is lower-inclusive and upper-exclusive; counts may be fractional after averaging. All amounts are KES per month.

| Factor | Illustrative average ranges for configuration testing |
|---|---|
| Deposits | `[0, 1)`, `[1, 3)`, `[3, 6)`, `[6, 10)`, `[10, ∞)` |
| Withdrawals | `[0, 1)`, `[1, 3)`, `[3, 6)`, `[6, 10)`, `[10, ∞)` |
| Monthly surplus | `(-∞, 0)`, `[0, 5,000)`, `[5,000, 10,000)`, `[10,000, 20,000)`, `[20,000, ∞)` |
| Transactions | `[0, 5)`, `[5, 15)`, `[15, 30)`, `[30, 60)`, `[60, ∞)` |
| Observed Fuliza disbursements | `[0, 1)`, `[1, 3)`, `[3, 6)`, `[6, 10)`, `[10, ∞)` |
| Observed debt payments | Included in the current phase; the measure, ranges and points require approval before enabling it. |

These ranges have **no assigned risk points**: more deposits, withdrawals, transactions or Fuliza events do not by themselves establish repayment risk, and absolute surplus depends on circumstances and requested loan obligations. The tenant must not infer a high or low credit score from these examples. Using arbitrary points would create unsupported automated lending decisions. The 2024 [FinAccess household survey](https://www.centralbank.go.ke/wp-content/uploads/2024/12/2024-FINACCESS-HOUSEHOLD-SURVEY-MAIN-REPORT.pdf) reports frequency of mobile-money use, not individual monthly counts for these narration-defined factors. [CBK aggregate mobile-payment statistics](https://www.centralbank.go.ke/national-payments-system/mobile-payments/) are system-level volumes and registered-account totals, not per-borrower distributions. Neither source establishes typical values for these specific factors or validates their predictive power. Calibrate using properly authorized, tenant-isolated historical statements linked to repayment outcomes, without exporting identifiable data unnecessarily.

**Test:** Use fixtures covering complete and partial months, no activity versus missing records, duplicate/linked Fuliza entries, unreconciled transactions, a statement beginning or ending during an episode, and missing due dates. Confirm factor evidence can be traced to source transactions and unsupported arrears remain unknown.

## 7. Phase 4 — Combined score, tiers and recommendation

1. Require both components and all mandatory quality checks. Calculate `combined = statementScore × 0.60 + questionnaireScore × 0.40` from unrounded or consistently rounded component values according to a published rounding policy.
2. Map the final integer combined score to the following approved INTERNAL_MPESA tiers and recommendations. Store the ranges and mapping in the published scorecard version. These are internal thresholds; CLADFY's numerical cutoffs are not established by its tier mapping and the providers' scores should not be assumed comparable.

| Final internal score | Risk tier | Recommendation |
|---:|---|---|
| 85–100 | `EXCELLENT` | `APPROVE` |
| 70–84 | `GOOD` | `APPROVE` |
| 55–69 | `FAIR` | `REVIEW` |
| 40–54 | `POOR` | `DECLINE` |
| 0–39 | `VERY POOR` | `DECLINE` |
| Insufficient information | No reliable tier | `REVIEW` |

3. For these integer bands, calculate using decimal arithmetic, round the final combined score once to the nearest whole number with `HALF_UP`, then select the inclusive range. For example, 84.49 rounds to 84 (`GOOD`), while 84.50 rounds to 85 (`EXCELLENT`). Keep the unrounded combined value in the assessment evidence so the result can be reproduced. The `APPROVE`/`REVIEW`/`DECLINE` mapping and statement workflow effects align with the current CLADFY integration.
4. If a mandatory answer, statement metric, quality check, active scorecard or eligibility result is missing or uncertain, return `REVIEW` with a reason; never fabricate a score or treat missing as zero. A technical processing failure is distinct from insufficient information.
5. Store component scores, combined score, tier, recommendation, reasons, evidence references, scorecard version, answer snapshot references, calculation version and assessment timestamp. Save assessment results atomically and make retries idempotent.

Example: statement 70 and questionnaire 80 produce `(70 × 0.60) + (80 × 0.40) = 74`, which maps to `GOOD` and `APPROVE` and triggers the statement-passed workflow when the assessment is eligible for automatic action.

**Test:** Check 0, 100 and both sides of every configured boundary, rounding boundaries, incomplete components, invalid scorecards, and repeated processing. Compare the configured mapping with CLADFY without altering CLADFY tests or behavior.

## 8. Phase 5 — Async integration, billing and workflow

1. Introduce a tenant-scoped assessment orchestration service after successful extraction. Use an idempotency key for each assessment run and a durable event/job or equivalent recoverable mechanism; avoid blocking an HTTP request.
2. Preserve the existing extraction/billing transaction and exactly-once charge safeguards. Scoring retries, review, missing data, later answer changes, and rescore requests must not independently rebill. Exercise the existing repeat-analysis policy only when a new billable analysis is submitted.
3. Separate extraction completion from scoring progress. A scoring exception must not lose the successfully extracted result or reverse its billable charge. Surface a recoverable scoring error or `REVIEW` with an operational reason, and allow safe retry.
   Allow an authorized user with `CREDIT_CHECK_RUN` to initiate a free rescore reusing the existing extracted statement. Use the version active for the application's tenant/profile and the current saved answers at rescore initiation; snapshot both for that run without changing the application's pinned original version. Verify tenant and ownership rules and check the application is still at the statement-analysis stage; reject requests once it has moved past that stage. Record a new assessment and preserve earlier snapshots and results. Apply the stage check atomically with initiation and recheck before applying an asynchronous result, so the application cannot advance concurrently and then receive an invalid rescore outcome. Define the precise allowed application statuses from the existing workflow and test each boundary.
4. Align status transitions with the existing CLADFY statement workflow, without changing CLADFY. Record a `SYSTEM` statement review decision and invoke the corresponding existing application handler. A passed statement changes the application to `STATEMENT_VERIFIED` and checks offer readiness; it does not itself approve or disburse a loan. A failed statement changes the application to `STATEMENT_FAILED`, not necessarily a terminal loan rejection. A review changes the application to `STATEMENT_MANUAL_REVIEW`.

| Recommendation | Candidate analysis status | Candidate effective outcome without overriding review |
|---|---|---|
| `APPROVE` | `PASSED` | `APPROVED` |
| `DECLINE` | `FAILED` | `REJECTED` |
| `REVIEW` | `MANUAL_REVIEW_REQUIRED` | `MANUAL_REVIEW_REQUIRED` |

The current `effectiveOutcome` gives a recorded review precedence over analysis status. `InternalMpesaCompletionService` currently records a system manual review, so implementation must explicitly handle that record when an assessment finishes; otherwise it can mask a later `PASSED` or `FAILED` status. Preserve genuine human decisions and audit history. Do not delete or overwrite human reviews on retry or rescore. Confirm the correct behavior of application workflow handlers before enabling automatic advancement or rejection.

**Test:** Simulate worker restart, duplicate event, concurrent completion, missing scorecard, scoring failure and later retry. Confirm one charge, retained extraction, correct review precedence, preserved human overrides and no changes to CLADFY.

## 9. Phase 6 — Existing endpoint and explanation

Extend `GET /api/v1/applications/{applicationId}/statement-analysis` in a backward-compatible way. Existing fields stay in place; add an optional INTERNAL_MPESA assessment object if needed. The exact response contract must be confirmed against `StatementDtos` before implementation. Example below is illustrative, not the current payload:

```json
{
  "analysis": {
    "creditScore": 74,
    "riskTier": "GOOD",
    "recommendation": "APPROVE",
    "scoring": {
      "scorecardVersion": 1,
      "statementScore": 70,
      "questionnaireScore": 80,
      "statementWeightPercent": 60,
      "questionnaireWeightPercent": 40,
      "assessedAt": "2026-11-28T10:00:00Z",
      "reasonCodes": []
    }
  },
  "effectiveOutcome": "APPROVED"
}
```

The example uses the approved score range and illustrates the agreed automated statement workflow outcome. A recorded human review may instead determine `effectiveOutcome`. For insufficient data, use `recommendation: "REVIEW"`, reason codes, and null for a score or tier that cannot be determined. Do not invent a numeric score.

Protect detailed factor evidence and questionnaire answers with tenant isolation, ownership checks and appropriate permissions; do not expose sensitive raw statement transactions or personal fields through scoring details by default. Keep provider-specific details optional and preserve CLADFY output.

**Test:** Retrieve approved, declined, review, missing-data and processing-failed examples. Check null behavior, recorded-review precedence, authorization, backward compatibility and unchanged CLADFY responses. Confirm the endpoint shows existing summary, monthly metrics and categories as before.

## 10. Phase 7 — Age and gender (deferred to the next phase)

The application already has nullable `dob` and `gender` fields. Reuse these fields rather than asking duplicate questions. `gender` currently accepts free text, so agree on supported self-reported values and handling of existing values; allow a non-response where appropriate. Never infer gender from name or appearance.

Calculate age from DOB as at application submission using the approved tenant/business timezone and a documented leap-day policy. Save the calculated age, as-of date, source/verification status and eligibility-rule version with the assessment; avoid duplicating DOB in scoring evidence. Reject future DOBs. If an approved age eligibility rule requires verified DOB and it is unavailable, return `REVIEW`.

Recommended default: age is an eligibility input only when justified; gender is excluded from questionnaire points, tier and lending decisions. Consider separately permissioned, aggregated fairness reporting if a lawful purpose is established. Any age-based risk scoring would require separate policy approval, empirical validation and fairness review.

**Test:** Birthday immediately before/on/after assessment date, leap-day DOB, missing/unverified/future DOB, each profile's policy, gender missing or self-reported, and tenant/permission isolation. Confirm gender does not change a score or recommendation.

## 11. Phase 8 — Pilot and rollout

Run the internal score in a non-decision pilot first, with explicit permissions and a defined retention period. Compare factor quality and suggested outcomes with authorized officer reviews; review missing-data rates, false approvals/declines, operational failures, relevant group-level fairness measures and subsequent repayment outcomes. Do not treat observed correlations as proof of causality.

Validate the approved thresholds and test the agreed workflow before enabling decisions for a limited tenant/profile. Monitor billing correctness, retries, score distribution, review overrides and model drift. Retain a configuration switch and rollback path to manual review. Publish a new immutable scorecard version for changes and never silently recalculate historical assessments.

**Release acceptance:** all phase tests pass; security and Flyway migration tests pass; supported API contract is documented; audit and billing reconciliation are verified; operational ownership, decision authority, legal review, monitoring and rollback are agreed.
