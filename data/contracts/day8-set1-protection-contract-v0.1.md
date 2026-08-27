# Day 8 Set 1 protection contract v0.1

Frozen before implementation on 2026-08-27. A changed threshold or response contract requires a new version and new evidence.

## Paid endpoint limits

Protected endpoints:

- `POST /api/civic/classify`
- `POST /api/civic/draft-complaint`

The verified Firebase UID is irreversibly SHA-256 hashed before it is used as a limiter document key. Raw UIDs, tokens, complaint text and image bytes must not be stored or logged by the limiter.

Per verified user, separately for each protected endpoint:

- burst: 4 accepted calls in any rolling 10 seconds;
- minute: 20 accepted calls in any rolling 60 seconds;
- longer window: 60 accepted calls in any rolling 60 minutes.

Project-wide across both protected endpoints:

- burst: 12 accepted calls in any rolling 10 seconds;
- minute: 60 accepted calls in any rolling 60 seconds;
- longer window: 300 accepted calls in any rolling 60 minutes.

The per-user and project counters are checked and appended atomically in Firestore. An exceeded limit returns HTTP 429 with `errorCode: RATE_LIMITED` and a whole-second `Retry-After` between 1 and 3,600 seconds. A limiter storage failure fails closed with HTTP 503 and `errorCode: BILLING_PROTECTION_UNAVAILABLE`. Neither path may invoke the model.

## Model deadlines

- Classification model deadline: 15 seconds.
- Complaint-drafting model deadline: 20 seconds.
- No automatic retry.
- Timed-out work is cancelled and the HTTP model request also carries the same deadline.
- Timeout, upstream/model failure and response-schema failure remain separate error codes and metrics.

Classification timeout returns HTTP 504 with `MODEL_TIMEOUT` and directs the citizen to manual category selection. Drafting timeout returns HTTP 504 with `MODEL_TIMEOUT`, preserves deterministic route facts in the browser, and directs the citizen to write or copy their own complaint.

## BigQuery boundary resolution

- BigQuery deadline: 1,500 milliseconds.
- Failure threshold: 3 consecutive failed calls.
- Open-state duration: 30 seconds.
- Half-open probes: one request; other concurrent requests use the snapshot.
- A successful half-open probe closes the circuit and resets consecutive failures.
- A failed half-open probe reopens the circuit for 30 seconds.
- Timeout, exception, interrupted execution, empty response and invalid response count as failures.
- A valid outside-supported-area result is successful and does not open the circuit.

The last-known-good snapshot is the packaged `synthetic-boundaries-v0.1` dataset, checksum `059533c8988334e7a268482c83bac9693e74783081c5b3a8cb51061bda4e100a`. Snapshot results use `SNAPSHOT_POINT_IN_POLYGON`, remain `UNSOURCED + REVIEW_PENDING`, state `SYNTHETIC_BOUNDARY`, include dataset version and provenance, and always require citizen confirmation. A snapshot miss returns `OUTSIDE_SUPPORTED_AREA`; it must never guess the nearest prabhag. Manual `SELF_REPORTED` Prabhag 1–20 selection remains available.

## Metrics and privacy

Counters and latency distributions contain only fixed operational names and outcomes. They never use UID, issue category, content, coordinates, model response ID or token values as labels. Periodic sanitized metric snapshots are written to structured service logs and are distinguishable by the platform revision metadata. No alert policy is created.
