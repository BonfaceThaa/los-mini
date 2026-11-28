# INTERNAL_MPESA scoring: questionnaire configuration test

This guide covers question weights, answer points, and the implemented scorecard draft and publication endpoints. Live scoring, assessment initiation and recommendations are not connected yet. Do not treat the illustrative values below as an approved lending policy.

## Preparation

Start the application with Flyway migrations through V47 applied. Use an access token for a tenant user with `APPLICATION_VARIABLE_MANAGE`. Use a fresh test question code. These examples use POSIX shell syntax; on Windows use Git Bash or adapt the quoting for PowerShell. Replace the token and profile code with values from your environment.

```sh
BASE_URL='http://localhost:8080'
TOKEN='YOUR_ACCESS_TOKEN'
PROFILE='YOUR_EXISTING_PROFILE_CODE'
```

All requests derive tenant from the bearer token; do not send a tenant ID. A profile code must belong to that tenant. `PUT` replaces the complete definition, so include the profile code and every option you wish to retain. Missing or null `scoringWeight` or option `points` removes that value on update; existing clients can omit both.

## Create a question without a weight

```sh
curl -i -X POST "$BASE_URL/api/v1/application-variable-definitions" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"code":"SCORING_TEST_INCOME","label":"Main income source?","sectionName":"Credit assessment","fieldType":"SINGLE_SELECT","required":true,"displayOrder":1,"options":[{"value":"SALARY","label":"Salary","points":100},{"value":"BUSINESS","label":"Business","points":80}]}'
```

Expect `200 OK`; the JSON contains the generated `id`, `definitionVersion: 1`, `scoringWeight: null`, and the two configured options with their points. Save the `id` as `QUESTION_ID`. This example is shared; it does not create a scorecard or assign it to a profile.

```sh
QUESTION_ID='REPLACE_WITH_ID_FROM_RESPONSE'
```

## Add a weight and update answer points

```sh
curl -i -X PUT "$BASE_URL/api/v1/application-variable-definitions/$QUESTION_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"code":"SCORING_TEST_INCOME","label":"Main income source?","sectionName":"Credit assessment","fieldType":"SINGLE_SELECT","required":true,"displayOrder":1,"scoringWeight":10,"options":[{"value":"SALARY","label":"Salary","points":100},{"value":"BUSINESS","label":"Business","points":90}]}'
```

Expect `200 OK`, `definitionVersion: 2`, `scoringWeight: 10.00` (JSON numeric formatting may vary), and business points `90`. These numbers are examples, not recommended lending rules.

## Retrieve the question

```sh
curl -i "$BASE_URL/api/v1/application-variable-definitions/all" \
  -H "Authorization: Bearer $TOKEN"
```

Expect `200 OK` and an array containing the updated definition. To restrict the preview to a profile and shared questions, call `/all?originationProfileCode=YOUR_EXISTING_PROFILE_CODE`. The regular GET endpoint also allows `LOAN_CREATE`; for callers without `APPLICATION_VARIABLE_MANAGE`, its response leaves scoring weights and answer points null.

## Invalid values and tenant isolation

Repeat the create request with a unique code and a negative `scoringWeight`, or an option with `points` above 100. Expect `400 Bad Request`; no definition is created. A request without a valid bearer token or without the required management permission is rejected. A different tenant cannot update the saved `QUESTION_ID`; expect a not-found response rather than another tenant's question.

## Create a scorecard draft

Use the ID of a question with all option points and a positive weight. The question must be shared or assigned to this profile. The following draft is intentionally incomplete and is useful for testing draft creation; to publish, add the statement policy shown below. The component weights must be 60 and 40.

```sh
curl -i -X POST "$BASE_URL/api/v1/origination-profiles/$PROFILE/scorecards" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"statementWeight\":60,\"questionnaireWeight\":40,\"questions\":[{\"definitionId\":\"$QUESTION_ID\",\"condition\":null}]}"
```

Expect `200 OK` with an `id`, `version: 1` for the first scorecard on that profile, `status: "DRAFT"`, and `snapshot: null`. Save its ID:

```sh
SCORECARD_ID='REPLACE_WITH_ID_FROM_RESPONSE'
```

Use `PUT /api/v1/origination-profiles/$PROFILE/scorecards/$SCORECARD_ID` with the same payload shape to replace a draft. To condition a question, supply `"condition":{"questionCode":"PARENT_CODE","applicableValues":["YES"]}` on its rule. The selected parent question must be in the same scorecard and the option code must exist.

## Validate and publish

```sh
curl -i -X POST "$BASE_URL/api/v1/origination-profiles/$PROFILE/scorecards/$SCORECARD_ID/validate" \
  -H "Authorization: Bearer $TOKEN"
curl -i -X POST "$BASE_URL/api/v1/origination-profiles/$PROFILE/scorecards/$SCORECARD_ID/publish" \
  -H "Authorization: Bearer $TOKEN"
```

The incomplete draft above returns `400` because it does not contain a statement policy. Replace it using the following **test-only** policy; its flat 50-point bands are for exercising the endpoint and are not approved risk rules:

```sh
curl -i -X PUT "$BASE_URL/api/v1/origination-profiles/$PROFILE/scorecards/$SCORECARD_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"statementWeight\":60,\"questionnaireWeight\":40,\"questions\":[{\"definitionId\":\"$QUESTION_ID\"}],\"statementPolicy\":{\"minimumCompleteMonths\":4,\"uncertainClassificationScore\":10,\"factors\":[{\"code\":\"DEPOSIT_COUNT\",\"enabled\":true,\"weight\":1,\"bands\":[{\"lower\":0,\"upper\":null,\"points\":50}]},{\"code\":\"WITHDRAWAL_COUNT\",\"enabled\":true,\"weight\":1,\"bands\":[{\"lower\":0,\"upper\":null,\"points\":50}]},{\"code\":\"MONTHLY_SURPLUS\",\"enabled\":true,\"weight\":1,\"bands\":[{\"lower\":null,\"upper\":null,\"points\":50}]},{\"code\":\"TRANSACTION_COUNT\",\"enabled\":true,\"weight\":1,\"bands\":[{\"lower\":0,\"upper\":null,\"points\":50}]},{\"code\":\"FULIZA_DISBURSEMENT_COUNT\",\"enabled\":true,\"weight\":1,\"bands\":[{\"lower\":0,\"upper\":null,\"points\":50}]}],\"tiers\":[{\"tier\":\"VERY POOR\",\"minimum\":0,\"maximum\":39,\"recommendation\":\"DECLINE\"},{\"tier\":\"POOR\",\"minimum\":40,\"maximum\":54,\"recommendation\":\"DECLINE\"},{\"tier\":\"FAIR\",\"minimum\":55,\"maximum\":69,\"recommendation\":\"REVIEW\"},{\"tier\":\"GOOD\",\"minimum\":70,\"maximum\":84,\"recommendation\":\"APPROVE\"},{\"tier\":\"EXCELLENT\",\"minimum\":85,\"maximum\":100,\"recommendation\":\"APPROVE\"}]}}"
```

Run the validate and publish requests again. Expect `204 No Content` from validation and `200 OK` from publication. Publication returns `status: "PUBLISHED"` and a `snapshot` containing the configuration and copies of the question definitions and their option points. Invalid or incomplete policy, missing question weights or option points return `400`; updating a published version returns `409`. A foreign or unknown profile/version returns `404`.

Debt payments are optional. To enable them, add an enabled `DEBT_PAYMENTS` factor with equal weight and `measure` set to `AVERAGE_MONTHLY_AMOUNT` or `PERCENT_ELIGIBLE_INFLOWS`, plus contiguous bands. A factor band includes `lower` and excludes `upper`; use `null` for an unbounded end. Count and debt bands must start at zero, while monthly surplus must start unbounded below. Every enabled factor must end unbounded above. The example uncertainty score and factor points must be approved before operational use.

Create a new draft to change a published version. Publishing that draft makes it the active version for subsequent applications; the prior version and its snapshot remain readable. Use `GET /api/v1/origination-profiles/$PROFILE/scorecards` to list versions, or append `/$SCORECARD_ID` to fetch one. Creating an application pins the active version ID internally; existing applications keep their original pin.

After a successful internal statement extraction and its billing transaction completes, an internal service attempts to capture the pinned scorecard and saved answers. Snapshot errors leave the statement in manual review and do not reverse extraction or billing. Snapshot creation is idempotent for an analysis. A scheduled recovery scan retries missing snapshots for applications still in statement manual review with a complete pinned policy; its interval defaults to 60 seconds and is configured by `app.scoring.snapshot-recovery-ms`. No statement score or recommendation is calculated by publishing this snapshot. Existing scorecards published before statement policy validation are not modified and cannot be used for initial assessment snapshots. Publish a complete new version for new applications; an existing application pinned to an older incomplete version would require the future rescore workflow, which is not implemented yet.
