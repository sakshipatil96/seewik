# Seewik Day 5 Build Log

Date: 2026-08-25

## Outcome

Day 5 adds the first complete report-outcome path after complaint drafting: authenticated citizens can record filing, follow a guarded lifecycle, record a repair claim, verify the outcome, reopen a failed repair or recurrent issue, and receive points from an append-only ledger. The deployed UI also includes a clearly labelled browser-local 90-second demo that changes no real report, analytics, or points.

Implementation commit: `379ba39` (`feat: add report lifecycle and outcome rewards`).

## Lifecycle contract

- Schema: `report-lifecycle-v0.1`
- States: `DRAFT -> FILED -> OVERDUE -> CLAIMED_FIXED -> VERIFIED_FIXED`
- Explicit reopen events:
  - `CLAIMED_FIXED -> REOPENED` by `REPAIR_CLAIM_REJECTED`
  - `VERIFIED_FIXED -> REOPENED` by `ISSUE_RECURRED`
- `VERIFIED_FIXED` is unreachable without first passing through `CLAIMED_FIXED`.
- Verification basis enum: `CITIZEN_ATTESTATION`, `CITIZEN_PHOTO`, `MUNICIPAL_ACKNOWLEDGEMENT`, `NONE`.
- `VERIFIED_FIXED` rejects `NONE`.
- There is no verified SLA in Civic Pack v0.2. No deadline is invented; these cases report `OVERDUE_UNKNOWN`, and the verified-overdue analytic returns zero eligible records.
- The first filing freezes the route facts and `packVersion` in an immutable `route-snapshot-v0.1`; a later Civic Pack cannot rewrite what an already-filed report used.
- The optional acknowledgement/tracking ID is captured at filing when supplied.
- Transition requests are authenticated with a Firebase ID token, checked against the report owner, guarded server-side, fingerprinted, and idempotent.

## Points contract

- Schema: `points-ledger-v0.1`
- Ledger is append-only; totals are derived and are never stored as a mutable user total.
- First accepted filing: base `5`, weight `1`, awarded `5`.
- Duplicate override: base `5`, weight `0`, awarded `0`.
- First `FIX_VERIFIED`: base `40`, weight `1`, awarded `40`.
- Reopening and re-verification award no additional points.
- Every entry records its triggering event, base points, weight, awarded points, versions, and time.

## Dedupe contract and adversarial evidence

- Schema: `dedupe-evaluation-v0.1`
- Heuristic: same issue type, same prabhag, active report, within `75 m`.
- Version: `same-category-75m-v0.1`.
- `measuredDistanceMeters` is recorded for both matches and non-matches.
- Missing coordinates produce `DEDUPE_NOT_EVALUATED`; they are not guessed.
- A possible duplicate blocks filing unless the citizen explicitly overrides; an override receives zero filing points.

Live four-pothole test with nudged coordinates:

| Attempt | Measured distance | Result | Filing points |
|---|---:|---|---:|
| Original | no candidate | accepted | 5 |
| Nudge 1 | 11.12 m | possible duplicate, blocked | 0 |
| Nudge 2 | 33.36 m | possible duplicate, blocked | 0 |
| Nudge 3 | 60.05 m | possible duplicate, blocked | 0 |

Result: only five total points were derivable. All four disposable reports and their Firestore outboxes were removed.

## Firestore ownership and runtime IAM

Deployed rules enforce:

- owner-only report reads;
- owner-only edits/deletion while a report is still `DRAFT`;
- owner-only reads of lifecycle events, dedupe evaluations, and ledger entries;
- no client writes to lifecycle events, dedupe evaluations, points, or the analytics outbox.

`roles/datastore.user` was granted to the current Cloud Run identity because Firestore IAM has no sub-project/document-path scoping for this predefined role; ownership is enforced server-side by Firebase ID token verification and an explicit report-owner check before every transition.

- Principal: `528138216934-compute@developer.gserviceaccount.com`
- Scope: Google Cloud project `seewik`
- Reason: Firebase Admin/server client operations bypass client Security Rules and require IAM-authorized Firestore transactions.
- Known risk: the role can read and write all Firestore documents in the project; it does not grant project administration, IAM administration, database administration, BigQuery access, or Vertex AI access.

Live two-anonymous-user verification passed: the owner could read the report and derived records; the second user was denied; all attempted client writes to server-owned records were denied. The disposable report, event, dedupe evaluation, points entry, and both users were removed.

## BigQuery lifecycle analytics

- Project/dataset: `seewik.seewik_civic`
- Immutable dataset location: `asia-south1`
- Tables:
  - `report_lifecycle_events`
  - `report_dedupe_evaluations`
  - `analytics_event_exclusions`
- Queries: resolution rate by prabhag, median filed-to-verified duration, open-report age by category, verified overdue count, derived points, and dedupe-distance distribution.
- Exported identifiers are SHA-256 hashes. Draft body, photo, acknowledgement ID, and raw report/user IDs are not exported.
- Visible synthetic demo records are excluded.
- Cloud Run has table-scoped `roles/bigquery.dataEditor` only on the two runtime output tables.

Production-path smoke evidence:

- Backend transition response: `TRANSITION_RECORDED`, `DRAFT -> FILED`, `+5`.
- Firestore analytics outbox: `SENT`.
- BigQuery row found: yes.
- Raw identifiers exported: no.
- Temporary Firestore records and anonymous user: removed.
- BigQuery blocks DML deletion of a freshly streamed row. Event `evt_ac7b1f01b8f5d06f5dd8fd0514e3255cabc83af11d942f2cfe696a6f313d7e7d` is explicitly marked `PRODUCTION_SMOKE_TEST` in `analytics_event_exclusions` and therefore cannot affect product metrics; delete it after the streaming buffer clears.

Final analytics check:

- raw lifecycle rows: `1` temporary smoke-evidence row;
- explicit test exclusions: `1`;
- product lifecycle rows: `0`;
- product dedupe rows: `0`;
- verified overdue events: `0`.

## UI and deployment

- Public frontend: https://seewik.web.app
- Public backend: https://seewik-api-528138216934.asia-south1.run.app
- Cloud Run revision: `seewik-api-00012-xvk` at 100% traffic
- Health: HTTP `200`, `application/json`, `{"status":"ok","service":"seewik-api"}`
- Firebase Hosting deployment: PASS
- Desktop production browser check: PASS, no console errors, `Cloud API: ok`.
- Mobile `390 x 844` production browser check: PASS, no console errors.
- Desktop evidence: `day5-lifecycle-demo-desktop.png`
- Mobile evidence: `day5-lifecycle-demo-mobile.png`

The real report panel supports filing channel and optional acknowledgement, duplicate warning/override, honest overdue-unknown messaging, repair claim, verification/reopen actions, derived points, and an immutable timeline. The demo walks `DRAFT -> FILED -> OVERDUE -> CLAIMED_FIXED -> VERIFIED_FIXED -> REOPENED` using a synthetic clock and demo points only.

## Release gates

- Backend regression suite: `128` passed, `0` failed, `0` errors, `0` skipped.
- Frontend TypeScript and production build: PASS.
- Firestore rules compile/deploy: PASS.
- Live ownership and server-transition test: PASS.
- Live four-report dedupe adversarial test: PASS.
- Deployed lifecycle-to-BigQuery smoke path: PASS; BigQuery row cleanup deferred by the documented streaming-buffer restriction and neutralized through explicit exclusion.

## What broke and what the tests caught

Adding Firebase Admin pulled in Jackson XML support and silently changed Spring content negotiation, causing `/health` to return XML instead of JSON.
The full regression suite caught the unrelated SDK side effect; `/health` now declares `application/json`, and a regression assertion locks the response type.

The first deployed lifecycle request returned `503` because the Cloud Run identity had no Firestore data-plane role. Granting `roles/datastore.user` fixed the real request path after IAM propagation; client ownership remains enforced through verified Firebase identity and the explicit owner check.

BigQuery accepted the privacy-safe runtime row but rejected immediate DML cleanup because legacy streaming inserts remain in a streaming buffer. The smoke event is explicitly excluded from every product lifecycle query until deletion is available.

The local sandbox blocked Mockito's Java test agent from attaching; the unchanged suite passed when run with the required process permission. This was a test-runner restriction, not an application failure.

## Known limitations and next evidence

- No Civic Pack v0.2 route has a verified `dueAt`; the system therefore reports `OVERDUE_UNKNOWN` rather than fabricating civic deadlines.
- The `75 m` threshold is an MVP heuristic, not a civic boundary. Recorded distances are the evidence for later calibration.
- Precise coordinates remain in owner-protected Firestore for dedupe and are not exported to BigQuery.
- The frontend bundle is about `772 kB` minified; Vite's size warning is advisory and code splitting is deferred.
- Department facts and routes remain `REVIEW_PENDING` until Nandurbar Municipal Council/domain review returns.
- The exact locked real civic example is still pending from the citizen; no substitute example was fabricated.
- The temporary BigQuery smoke row must be physically deleted after its streaming buffer clears; product analytics are already protected by the explicit exclusion.
