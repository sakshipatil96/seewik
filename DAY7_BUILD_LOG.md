# Seewik Day 7 Build Log

Date: 2026-08-26

Day 7 is implemented, deployed, evaluated, documented, and production-verified. Track B photograph evaluation remains intentionally pending because no civic photographs were supplied; it was not mixed into the scored text result.

## Release

| Item | Result |
|---|---|
| Implementation Git commit | `3f66e386369ae92a0cb86c03410df16dda444820` |
| Backend revision | `seewik-api-00014-txh` |
| Backend traffic | 100% |
| Backend region | `asia-south1` |
| Backend URL | `https://seewik-api-528138216934.asia-south1.run.app` |
| Frontend URL | `https://seewik.web.app` |
| Backend health | HTTP 200, `status: ok` |
| Cloud Run maximum instances | 20 |
| Cloud Run concurrency | 80 |

## Paid-endpoint authentication

`/api/civic/classify` and `/api/civic/draft-complaint` now require the same Firebase anonymous identity used for report ownership. The backend verifies the ID token before either paid model operation can begin.

Production verification confirmed:

- missing classification token: HTTP 401 `AUTHENTICATION_REQUIRED`;
- missing drafting token: HTTP 401 `AUTHENTICATION_REQUIRED`;
- authenticated classification: passed;
- authenticated complaint drafting: passed;
- authentication failures do not reach the model service;
- revision logs contained no Firebase token, test complaint text, or error entry;
- health and deterministic routing remain independent of paid model calls.

This closes the unauthenticated billing path while retaining the existing manual-category fallback.

## Initiate MVP

Initiate is available from desktop navigation, mobile navigation, the homepage, `/initiatives`, and `/initiatives/new`. Direct links and refreshes are supported.

The backend stores activities, participation, append-only initiative events, and compatible contribution-ledger entries. Creation validates the category, title, description, future time, public meeting-place name, optional needs, and coordinates. Nearby discovery uses Haversine distance, supports 2/5/10/25 km radii, defaults to 5 km, excludes past activities, orders nearest first, bounds the result set, and does not expose organiser identity or raw coordinates.

The creator is enrolled atomically as `ORGANISER`, so the first participant count is one. Joining is authenticated, transactional, and idempotent. The first join returns `JOINED`; a repeat returns `ALREADY_JOINED` without changing the count.

`INITIATIVE_CREATED` and `INITIATIVE_JOINED` are recorded, but unverified participation earns no points:

- `basePoints: 0`;
- `weight: 0`;
- `pointsAwarded: 0` and compatible `awardedPoints: 0`;
- `policyStatus: RECORDED_NOT_REWARDED`.

Reward weights will not be introduced until a trustworthy participation/completion verification state exists.

### Live two-user result

The production integration used two temporary anonymous identities:

1. user one created an activity and the count started at one;
2. user two discovered the activity nearby;
3. user two joined and the count became two;
4. user two joined again and received `ALREADY_JOINED`;
5. the count remained two;
6. the two ledger entries awarded zero points;
7. the activity, participation, events, ledger entries, and both users were removed.

The repeatable production verification is in `ProductionDay7ReleaseIT.java`. The captured structured result is `data/eval/results/day7-production-release-verification-2026-08-26.json`.

## Frozen Track A evaluation

The scored set is `classification-cases-v0.2`: exactly 60 synthetic multilingual text cases spanning all 11 Civic Pack issue types, four honest unknown controls, and one prompt-injection case. Image references are null.

Frozen contract:

- case-set SHA-256: `c68a3c6441745f9422544ccc833bc8ae3d0ba2c8c10408bfbb6555ea7f5302f0`;
- prompt: `classification-prompt-v0.1`;
- schema: `classification-v0.1`;
- Civic Pack: `v0.2`;
- model: `gemini-3.7-flash`;
- endpoint region: `asia-south1`;
- deployed implementation commit: `3f66e386369ae92a0cb86c03410df16dda444820`.

The evaluation policy was frozen before calls. Raw responses and failures were preserved, no scored failure was silently replaced, and the prompt was not changed after results were viewed.

### Scored results

| Measurement | Run 1 | Run 2 |
|---|---:|---:|
| Cases | 60 | 60 |
| Issue-type accuracy | 60/60 (100%) | 60/60 (100%) |
| Detected-language accuracy | 60/60 (100%) | 60/60 (100%) |
| Correct clarification controls | 4/4 | 4/4 |
| Model-call failures | 0 | 0 |
| Schema-validation failures | 0 | 0 |
| Transport/HTTP failures | 0 | 0 |
| Minimum latency | 2,221 ms | 2,352 ms |
| Median/p50 latency | 3,065 ms | 3,108.5 ms |
| p95 latency | 3,984 ms | 4,002 ms |
| Maximum latency | 6,602 ms | 4,886 ms |

Across the two runs, all 60 cases retained the same issue type, detected language, and classification/clarification status. Category stability was 60/60 (100%). Every category was 100% on this frozen synthetic set, so the confusion-pair list is empty. This is evidence for the frozen authored set, not a claim about all real citizen language.

Raw and summary evidence:

- `data/eval/results/classification-results-2026-08-26-day7-run1.ndjson`
- `data/eval/results/classification-results-2026-08-26-day7-run2.ndjson`
- `data/eval/results/classification-summary-2026-08-26-day7-run1.json`
- `data/eval/results/classification-summary-2026-08-26-day7-run2.json`
- `data/eval/results/classification-repeatability-summary-2026-08-26-day7.json`

The temporary evaluation identity was deleted after Run 2.

Track B remains a separate future photograph set. No photograph score is reported for Day 7 because no civic photographs were available.

## Deterministic routing

Routing was measured separately from model classification. The 12 frozen `routing-cases-v0.1` cases passed against Civic Pack `v0.2`: 12/12 expected route outcomes, including supported routes and the explicit unsupported case. Gemini does not select the authority, department, route, SLA, escalation, or filing channel.

## Human-baseline contract

The ten-scenario answer key was frozen before survey responses were opened. It includes the exact English, Marathi, and Hindi scenario text, accepted municipal-authority wording, exact-Nandurbar secondary scoring, councillor/corporator rules, ambiguity rules, and rejected authority tiers.

- answer key: `data/eval/human-baseline-answer-key-v0.1.json`;
- content SHA-256: `548ab65f2ef49ee8310e5a86af77d3d47c06bc5aa9730fdc8dad0be1a3eb662a`;
- integrity test: passed.

Survey response scoring remains a separate analysis step and cannot change this frozen key.

## BigQuery analytical evidence

Day 7 production and controlled-fixture evidence is kept separate in `DAY7_BIGQUERY_EVIDENCE.md`.

Production contained one intentionally labelled smoke row. The exclusion matched that one row and left zero product lifecycle rows and zero product dedupe rows. Consequently:

- reports-by-prabhag/category returned no product rows;
- resolution rate is null rather than a misleading 0%;
- median `FILED → VERIFIED_FIXED` duration is null;
- zero reports are overdue-eligible because Civic Pack `v0.2` has no verified SLA;
- dedupe-distance and points queries returned no product events.

The controlled in-query fixture independently proved the aggregation logic without inserting into production. Its results are not presented as citizen activity.

## Release gates

- Backend regression: 138 tests passed, 0 failures, 0 errors, 0 skipped.
- Dedicated live production integration: 1 test passed and cleaned up all temporary records/users.
- Frontend navigation/immutability: 3 tests passed.
- Frontend production build: passed.
- Refresh-safe Initiate routes: passed.
- Repository restricted-word audit: passed.
- Git whitespace check: passed.
- Backend health and 100% revision traffic: passed.
- Desktop and mobile browser layouts: passed with no console errors.
- Production logs: no release-revision errors and no sensitive test content found.

The frontend build currently reports an advisory large-bundle warning: approximately 802 kB minified and 239 kB gzip. It does not fail the production build and is recorded for later code splitting.

## Production screenshot

The Day 7 Initiate production screen is captured in `day7-initiatives-production-desktop.png`.

## Evidence index

- `data/eval/EVALUATION_POLICY.md`
- `data/eval/classification-cases-v0.2.json`
- `data/eval/human-baseline-answer-key-v0.1.json`
- `data/eval/results/classification-repeatability-summary-2026-08-26-day7.json`
- `data/eval/results/day7-production-release-verification-2026-08-26.json`
- `DAY7_BIGQUERY_EVIDENCE.md`
- `day7-initiatives-production-desktop.png`

## Day 7 disposition

Tasks 9 and 10 are complete: the backend and frontend are deployed, production checks pass, release evidence is recorded, the build log and project guide are updated, and the release is ready to push. The only intentionally unavailable Day 7 measurement is Track B photograph evaluation, because no photographs were supplied.
