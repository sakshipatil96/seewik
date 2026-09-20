# Day 8 Set 1 build log

Date: 2026-08-27  
Initial implementation commit: `a2e3e23b32fb60c627db0a4021f605ed40843e23`

Cleanup implementation commit: `627547befa4b5038af6888ac856ba18113a615fc`

Contracts: `day8-set1-protection-contract-v0.1`, `day8-set1-cleanup-contract-v0.2`

Backend revision: `seewik-api-00021-rat`

Rollback revision: `seewik-api-00015-hir`

## Outcome

Set 1 is complete in production. Paid classification and drafting now have transactional per-user and project-wide protection, explicit model deadlines, and controlled citizen fallbacks. BigQuery prabhag resolution has a bounded deadline, a closed/open/half-open circuit breaker, and a checksum-verified packaged snapshot. Revision-tagged privacy-safe metrics are visible, the before/after log audit found no citizen content or identity material, and expired hashed limiter records are covered by an active Firestore TTL policy.

No alert policy was created because no alert recipient has been defined.

## Final cleanup acceptance

The initial release was reopened after three evidence gaps were identified. The final cleanup contract is frozen in [data/contracts/day8-set1-cleanup-contract-v0.2.md](data/contracts/day8-set1-cleanup-contract-v0.2.md).

### Degraded startup is an architecture decision

Failing startup when packaged civic data is missing or corrupt is not inherently wrong: silently running with incomplete civic data could mislead citizens. Seewik already has a complete and honest manual Prabhag 1-20 path, however. Making the entire service unavailable would remove that safe path.

Snapshot checksum and structure validation therefore still run at startup, but a missing, corrupt or checksum-mismatched snapshot starts explicit degraded mode. Automatic coordinate suggestions are disabled, every resolution returns `MANUAL_SELECTION_REQUIRED`, no prabhag is returned or guessed, and the citizen is directed to the existing manual selector. `prabhag.snapshot_unavailable` and `prabhag.manual_resolution_required` count these requests. A generic startup warning contains no civic evidence or exception details.

This is deliberately stricter than using BigQuery alone: once the required fallback loses integrity, automated resolution stops while the honest manual product path remains available.

The three forced cases now pass:

1. healthy primary returns `BIGQUERY_ST_COVERS`;
2. forced timeout returns the same `PRABHAG-11` through `SNAPSHOT_POINT_IN_POLYGON` and still requires confirmation;
3. missing or corrupt snapshot starts degraded, returns `MANUAL_SELECTION_REQUIRED`, returns no prabhag, makes no nearest guess, and increments both degraded-mode counters.

Evidence: [data/eval/results/day8-set1-cleanup-forced-failure-2026-08-27.json](data/eval/results/day8-set1-cleanup-forced-failure-2026-08-27.json).

### Metric names and write-up mapping

The algorithm names remain unchanged because they describe the actual operation rather than a generic box:

- `BIGQUERY_ST_COVERS` — primary geography containment;
- `SNAPSHOT_POINT_IN_POLYGON` — packaged in-memory containment;
- `MANUAL_SELECTION_REQUIRED` — no automatic suggestion is available.

Likewise, existing metric names remain precise. Legacy “ward” wording maps to `prabhag.bigquery_resolution`, `bigquery.fallback`, `bigquery.circuit_open_fallback`, the separate `bigquery.timeout | failure | invalid_response` failure counters, and `bigquery.resolution` latency. The full mapping is in the v0.2 cleanup contract.

Two genuinely missing product counters were added:

- `unsupported_route_total`;
- `low_confidence_clarification_total` for validated confidence below `0.80` that requests clarification.

The final production snapshot from revision `seewik-api-00021-rat` contained one `prabhag.bigquery_resolution` and one `unsupported_route_total`. Low-confidence wiring is covered by the regression suite; no paid production call was manufactured solely to force a low-confidence outcome.

### Deadline rationale and deployed measurements

The 1,500 ms BigQuery deadline was chosen conservatively before the new measurement; it was not derived from it. The initial deployed warm p95 was 592 ms, so the existing deadline was later confirmed at approximately 2.5 times that p95.

An exact cleanup series then explicitly pre-warmed the deployed revision before ten measured calls:

| Series | Samples | Min | p50 | p95 | Max |
|---|---:|---:|---:|---:|---:|
| Warm BigQuery query | 10 | 335 ms | 431.5 ms | 485 ms | 485 ms |
| Warm endpoint wall time | 10 | 680 ms | 792.5 ms | 1,026 ms | 1,026 ms |

The first un-warmed ten-call series was preserved rather than relabelled: its first query was 1,353 ms and its query p95 was 1,353 ms. After explicit pre-warming, all ten measured calls used `BIGQUERY_ST_COVERS` and the 1,500 ms deadline was approximately 3.1 times p95.

Three genuine cold starts used isolated zero-traffic revisions of the exact production image. Each revision remained idle for at least fifteen minutes and emitted a new `AUTOSCALING` startup event for its single request:

| Revision | Idle before startup | Wall time | BigQuery query | Result |
|---|---:|---:|---:|---|
| `seewik-api-00018-sir` | 1,002 s | 6,831 ms | 969 ms | `BIGQUERY_ST_COVERS` |
| `seewik-api-00019-ten` | 971 s | 7,719 ms | 769 ms | `BIGQUERY_ST_COVERS` |
| `seewik-api-00020-tis` | 930 s | 7,437 ms | 870 ms | `BIGQUERY_ST_COVERS` |

Cold endpoint wall time includes container startup before application handling. Cold BigQuery query p95 was 969 ms, below the 1,500 ms dependency deadline. All three results remained synthetic candidates requiring confirmation. The temporary sample tags were removed after evidence capture.

Final cleanup production evidence: [data/eval/results/day8-set1-cleanup-production-verification-2026-08-27.json](data/eval/results/day8-set1-cleanup-production-verification-2026-08-27.json).

## Frozen protection contract

The thresholds and response behavior were frozen before implementation in [data/contracts/day8-set1-protection-contract-v0.1.md](data/contracts/day8-set1-protection-contract-v0.1.md).

Per verified user, separately for classification and drafting:

- 4 accepted calls in any rolling 10 seconds;
- 20 accepted calls in any rolling 60 seconds;
- 60 accepted calls in any rolling 60 minutes.

Project-wide across both endpoints:

- 12 accepted calls in any rolling 10 seconds;
- 60 accepted calls in any rolling 60 seconds;
- 300 accepted calls in any rolling 60 minutes.

The limiter hashes the verified Firebase UID before using it as a Firestore document key. The transaction checks the user and project windows together. A limit rejection returns HTTP 429 `RATE_LIMITED` with a bounded `Retry-After`; limiter storage failure fails closed with HTTP 503 `BILLING_PROTECTION_UNAVAILABLE`. Neither path reaches the model.

The Firestore collection group `operationalRateLimitsV1` has an active TTL policy on `expiresAt` so expired hashed records are removed automatically.

## Deployed rate-limit verification

The staged production revision was checked with temporary anonymous identities and invalid evidence so no paid call was generated:

- classification calls 1-4 reached validation as `EMPTY_EVIDENCE`;
- classification call 5 returned `RATE_LIMITED` with `Retry-After: 5`;
- after 11 seconds, classification again reached `EMPTY_EVIDENCE`;
- a separate user remained isolated and reached `EMPTY_EVIDENCE`;
- drafting calls 1-4 reached validation as `CATEGORY_CONFIRMATION_REQUIRED`;
- drafting call 5 returned `RATE_LIMITED` with `Retry-After: 8`;
- all temporary identities were deleted.

The project-wide ceiling, concurrent requests, endpoint isolation and rolling-window recovery are covered by the regression suite. Production was not intentionally driven to the global ceiling.

## Model deadlines and citizen fallback

- Classification deadline: 15 seconds.
- Drafting deadline: 20 seconds.
- There is no automatic retry.
- Timed-out tasks are cancelled, and the outbound HTTP request carries the same deadline.
- Timeout, model failure and schema failure have separate responses and metric names.

A classification timeout returns HTTP 504 `MODEL_TIMEOUT` while preserving manual category selection. A drafting timeout returns HTTP 504 `MODEL_TIMEOUT`; the confirmed deterministic route remains on screen and the citizen can write and copy a manual complaint.

Delayed test gateways confirmed timeout handling and that late work cannot create a second response.

## BigQuery circuit breaker and snapshot

- BigQuery deadline: 1,500 ms.
- Failure threshold: 3 consecutive failures.
- Open duration: 30 seconds.
- Half-open behavior: one probe; concurrent requests use the snapshot.
- Timeout, exception, interruption, empty response and invalid response all count as failures.
- A valid outside-area response is a successful dependency result and does not open the circuit.

The packaged snapshot is an exact checksum-verified copy of `synthetic-boundaries-v0.1`:

- checksum: `059533c8988334e7a268482c83bac9693e74783081c5b3a8cb51061bda4e100a`;
- polygons: 20;
- method: `SNAPSHOT_POINT_IN_POLYGON`;
- status: `UNSOURCED + REVIEW_PENDING`;
- quality: `SYNTHETIC_BOUNDARY`.

The snapshot performs point-in-polygon resolution only. It never guesses the nearest prabhag. Every candidate requires citizen confirmation, manual Prabhag 1-20 selection remains available, and no synthetic result is presented as official geography.

Forced local failure evidence is preserved in [data/eval/results/day8-set1-local-forced-failure-latency-2026-08-27.json](data/eval/results/day8-set1-local-forced-failure-latency-2026-08-27.json):

| Path | Samples | Min | p50 | p95 | Max |
|---|---:|---:|---:|---:|---:|
| Timeout to snapshot fallback | 5 | 1,502 ms | 1,510 ms | 1,510 ms | 1,510 ms |
| Open circuit to snapshot | 20 | 0 ms | 0 ms | 0 ms | 0 ms |

The timeout, unavailable-service and invalid/empty-response forced tests all passed. These are deliberately delayed local stubs and are reported separately from production BigQuery timings.

## Production latency

Healthy prabhag resolution used one fixed synthetic development test point. No app-level result cache was added.

| Measurement | Samples | Min | p50 | p95 | Max |
|---|---:|---:|---:|---:|---:|
| First measured healthy lookup | 1 | 868 ms | 868 ms | 868 ms | 868 ms |
| Warm healthy BigQuery lookups | 19 | 374 ms | 460 ms | 592 ms | 592 ms |
| Classification wall time | 5 | 2,865 ms | 3,453 ms | 8,314 ms | 8,314 ms |
| Classification model time | 5 | 2,186 ms | 2,914 ms | 4,755 ms | 4,755 ms |
| Drafting wall time | 5 | 3,033 ms | 3,599 ms | 4,849 ms | 4,849 ms |
| Drafting model time | 5 | 2,603 ms | 3,109 ms | 4,324 ms | 4,324 ms |

All 20 healthy prabhag calls succeeded through `BIGQUERY_ST_COVERS`; all 20 required confirmation. One outside-area control returned no prabhag. The previous five production lookup timings were `534, 540, 675, 687, 6014 ms`, with p50 `675 ms`.

## Privacy-safe operational metrics

The service emits fixed counter names and min/p50/p95/max latency summaries every 60 seconds. The platform revision is included. UID, content, category, coordinates, token values and response IDs are never metric labels.

The sanitized production snapshot included:

- 21 BigQuery successes;
- 20 BigQuery prabhag resolutions;
- 1 outside-area result;
- 6 successful classification model calls;
- 5 successful drafting model calls;
- BigQuery p50 460 ms and p95 592 ms;
- classification endpoint p50 3,046 ms and p95 7,949 ms;
- drafting endpoint p50 3,174 ms and p95 4,484 ms.

The staged verification instance separately recorded two per-user rate-limit rejections and two authentication rejections. It contained no identity or content label.

## Production log privacy audit

Only pattern counts were preserved. Raw matching log text was not copied into release evidence.

| Pattern | Revision 00014 | Revision 00015 |
|---|---:|---:|
| Authorization or bearer content | 0 | 0 |
| ID-token structure | 0 | 0 |
| Known test complaint phrases | 0 | 0 |
| Image or multipart content | 0 | 0 |
| Raw UID indicators | 0 | 0 |
| Stack traces | 0 | 0 |
| Error-severity entries | 0 | 0 |

One generic framework warning recorded an unsupported content type from the intentional malformed request. It contained no citizen content, identity, token or stack trace.

## Capacity and quota confirmation

Cloud Run production configuration:

- region: `asia-south1`;
- maximum instances: 20;
- concurrency: 80;
- timeout: 300 seconds;
- memory: 512 MiB;
- CPU: 1.

The deployed environment records implementation SHA `a2e3e23`. Application deadlines are much shorter than the platform timeout. The project-wide paid limit is 60 accepted calls per minute and 300 per hour, with a 12-call short burst.

The deployed `gemini-3.7-flash` global model has no model-specific numeric bucket in the project's Service Usage quota metadata. Current Google Cloud documentation describes newer pay-as-you-go Gemini models as using Dynamic Shared Quota, so temporary capacity responses remain possible. Seewik does not retry them automatically, and the limiter plus deadlines bound application demand.

The effective BigQuery project query-usage limit is 209,715,200 MiB per day, or 200 TiB per day. Daily request metadata is unlimited. The 1,500 ms application deadline, breaker and snapshot bound dependency failure time. The exact project limits were read from Service Usage; general BigQuery concurrency limits remain platform-managed.

References:

- [Vertex AI throughput quota](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/resources/throughput-quota)
- [BigQuery quotas and limits](https://docs.cloud.google.com/bigquery/quotas)
- [Cloud Run quotas](https://docs.cloud.google.com/run/quotas)
- [Cloud Run concurrency](https://docs.cloud.google.com/run/docs/about-concurrency)

## Release and gates

The initial backend was deployed first with zero traffic and the `day8-set1` tag. Health, authentication, rate limiting, deterministic routing and one authenticated call per paid endpoint passed before traffic moved. Cleanup revision `seewik-api-00021-rat` now receives 100% traffic. Revision `seewik-api-00015-hir` remains ready for rollback. The tested frontend was published only after backend health was confirmed.

Final gates:

- backend: 171 tests passed, 0 failures, 0 errors, 0 skipped;
- frontend: 3 tests passed;
- production frontend build: passed;
- restricted-word repository audit: passed;
- whitespace check: passed;
- production health: HTTP 200 JSON;
- frontend refresh routes: six of six returned HTTP 200;
- temporary production identities: deleted.

Detailed sanitized production evidence is in [data/eval/results/day8-set1-production-verification-2026-08-27.json](data/eval/results/day8-set1-production-verification-2026-08-27.json).

Final cleanup evidence is in [data/eval/results/day8-set1-cleanup-production-verification-2026-08-27.json](data/eval/results/day8-set1-cleanup-production-verification-2026-08-27.json).

## What broke and what was corrected

- New constructor overloads initially left Spring wiring ambiguous. The production constructors were marked explicitly and the full application context test passed.
- The first focused tests relied on a dynamic mock attachment that was unavailable in one local process. The new tests were changed to concrete stubs; the complete suite then passed.
- One new expected streetlight route identifier did not match the frozen Civic Pack. The test expectation was corrected to the existing deterministic route; Civic Pack data was not changed.
- A malformed authentication probe without multipart content was rejected as unsupported media before reaching the controller. The production check was repeated using the real multipart request shape and returned the expected HTTP 401.

## Remaining risks and boundaries

- The distributed limiter is exact because Firestore transactions coordinate it; operational metrics and circuit state are in-memory per Cloud Run instance.
- The circuit breaker protects dependency failure and latency, not successful-request volume. BigQuery usage should continue to be reviewed as real traffic grows.
- Dynamic Shared Quota does not guarantee model capacity; controlled failure and manual citizen paths remain necessary.
- The JavaScript bundle remains approximately 803.67 kB minified and 238.95 kB gzip. Route splitting is reserved for Day 8 Set 3.
- Synthetic prabhag geometry remains replaceable development data. Official geometry has not been found.
- No Civic Pack route has a verified SLA, so overdue status remains unknown.
