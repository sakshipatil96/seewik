# Day 8 Set 1 cleanup contract v0.2

Frozen before cleanup deployment and cold-start measurement on 2026-08-27. This addendum preserves the v0.1 thresholds and algorithm names while closing the remaining acceptance gaps.

## BigQuery deadline rationale

The BigQuery deadline remains 1,500 milliseconds. It was chosen conservatively before the new deployed measurement; it was not derived from that measurement. The first deployed warm run later measured p95 at 592 milliseconds, so the existing deadline is approximately 2.5 times that p95. Three genuine cold-start measurements must be recorded separately before this cleanup is complete.

## Snapshot integrity and degraded startup

Failing startup when packaged civic data is missing or corrupt is a defensible default because silently using incomplete civic data can mislead citizens. Seewik has a complete and honest manual Prabhag 1-20 path, however, so unavailable snapshot data must not make the entire service unavailable.

The architecture decision is therefore:

- snapshot checksum and structure validation still run during startup;
- missing, corrupt or checksum-mismatched snapshot data starts the service in an explicit degraded mode;
- degraded mode disables automatic coordinate-based prabhag suggestions for every resolution request;
- degraded resolution returns `MANUAL_SELECTION_REQUIRED` with no prabhag identifier;
- the citizen is directed to manual Prabhag 1-20 selection;
- no nearest polygon or other guessed prabhag is returned;
- `prabhag.snapshot_unavailable` and `prabhag.manual_resolution_required` count degraded requests;
- a generic startup warning is logged without civic payload, coordinates or exception details.

Disabling the primary lookup while the packaged fallback is unavailable is deliberate: the service remains useful through manual selection without operating an automatic resolver that has lost its required failure path.

## Resolution algorithm names

The existing names are preserved because they identify the actual algorithm:

- `BIGQUERY_ST_COVERS` — BigQuery geography containment;
- `SNAPSHOT_POINT_IN_POLYGON` — packaged in-memory polygon containment;
- `MANUAL_SELECTION_REQUIRED` — automatic resolution unavailable, with no suggested prabhag.

## Metric mapping

Product wording that says “ward” maps to the implementation's more precise “prabhag” terminology:

| Write-up concept | Deployed metric | Meaning |
|---|---|---|
| BigQuery prabhag resolutions | `prabhag.bigquery_resolution` | Primary `ST_COVERS` returned a candidate prabhag |
| Fallback attempts | `bigquery.fallback` | A primary failure or open circuit entered snapshot resolution |
| Open-circuit fallbacks | `bigquery.circuit_open_fallback` | The breaker skipped BigQuery and entered snapshot resolution |
| Primary timeout failures | `bigquery.timeout` | BigQuery exceeded the 1,500 ms deadline |
| Primary unavailable failures | `bigquery.failure` | BigQuery raised an unavailable or interrupted failure |
| Invalid primary responses | `bigquery.invalid_response` | BigQuery returned an invalid or unusable response |
| Snapshot candidate resolutions | `prabhag.snapshot_resolution` | Point-in-polygon returned a snapshot candidate |
| Snapshot misses | `prabhag.snapshot_outside` | Snapshot returned no candidate and no prabhag was guessed |
| Snapshot unavailable | `prabhag.snapshot_unavailable` | Packaged snapshot failed startup validation |
| Manual fallback required | `prabhag.manual_resolution_required` | Automatic resolution was disabled and manual selection was requested |
| BigQuery latency | `bigquery.resolution` | Primary success or timeout latency distribution |
| Snapshot latency | `snapshot.resolution` | In-memory fallback latency distribution |
| Unsupported deterministic route | `unsupported_route_total` | Civic Pack issue or prabhag could not produce a supported route |
| Low-confidence clarification | `low_confidence_clarification_total` | A validated classification below 0.80 requested clarification |

The counters remain fixed names in revision-tagged privacy-safe metric snapshots. They contain no UID, civic text, category, coordinates or model response identifier as labels. No alert policy is created.

## Required forced-failure cases

1. Healthy primary: `BIGQUERY_ST_COVERS`, synthetic candidate, citizen confirmation required.
2. Forced BigQuery timeout with a valid snapshot: the same prabhag through `SNAPSHOT_POINT_IN_POLYGON`, citizen confirmation required.
3. Missing or corrupt snapshot: service starts, returns `MANUAL_SELECTION_REQUIRED`, returns no prabhag, and counts the degraded request.
