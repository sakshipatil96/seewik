# Seewik Day 2 build log

Date: 2026-08-22 (America/Los_Angeles)

## Outcome

Built and deployed Civic Pack `v0.1`, a deterministic Spring Boot civic router, an evidence-traceable manual ward/prabhag dataset, and an `asia-south1` BigQuery ward table. No Gemini call is made by the civic router. No ward coordinates or geometry were fabricated.

## Prior ward research checked

- Inspected `research/nandurbar_ward_maps_2026-08-21/SOURCE_LOG.md` and its saved official downloads before implementation.
- Confirmed the prior negative result: no downloadable official Nandurbar Municipal Council ward-boundary map, boundary annexure, polygon dataset, or credible locality-per-ward spatial reference was available.
- Kept the Nandurbar taluka map as context only and excluded Zilla Parishad/Panchayat Samiti material.

## Important source discrepancy discovered

The prior source log treated the SEC summary's `40` as 40 citizen-selectable wards. Direct inspection of the official 2025 member-results PDF found a conflict:

- SEC 2025 election summary, page 6: Nandurbar displays `40` under `TOTAL WARDS` and `41` under `TOTAL SEATS`.
- SEC 2025 member results, PDF pages 369-372: Nandurbar records enumerate prabhag numbers `1` through `20`, with seat IDs `1A`-`19B` and `20A`-`20C`, totaling 41 seats.

The data pack preserves both observations. It stores the 20 directly enumerated prabhag IDs and all 41 seat IDs, and it does **not** fabricate `PRABHAG-21` through `PRABHAG-40`. A domain/product decision is still required on the preferred citizen-facing term and selector.

## Ward source dataset

- Version: `v0.1`
- Method: `MANUAL_SELECTION`
- Records: 20 official result-enumerated prabhags
- Referenced seat IDs: 41
- Geometry: none
- `lat` / `lng`: null for every record
- `geometryType`: `NONE` for every record
- GPS auto-resolution: `UNAVAILABLE_NO_SPATIAL_DATA`
- Files:
  - `data/wards/nandurbar-ward-source-v0.1.json`
  - `data/wards/wards.ndjson`
  - `data/wards/bigquery-schema.json`
  - `data/wards/WARD_SOURCE_NOTES.md`

## Civic Pack `v0.1`

Ten deterministic MVP routes:

1. `GARBAGE_SOLID_WASTE`
2. `ILLEGAL_DUMPING`
3. `POTHOLE_ROAD_DAMAGE`
4. `STREETLIGHT`
5. `DRAINAGE_SEWAGE`
6. `WATER_SUPPLY`
7. `PUBLIC_TOILET_SANITATION`
8. `MOSQUITO_FOGGING`
9. `DEAD_ANIMAL_REMOVAL`
10. `PUBLIC_ROAD_OBSTRUCTION`

Evidence and review counts:

- `OFFICIAL_SOURCE`: 10
- `UNSOURCED`: 0
- `DOMAIN_REVIEWED`: 0
- `REVIEW_PENDING`: 10

Each route uses Nandurbar Municipal Council as the authority based on the Maharashtra Municipal Councils, Nagar Panchayats and Industrial Townships Act, 1965. Official channels are the official council contact email, the DMA complaint form, and the district-listed municipal office. No phone number was invented. Internal department is `UNVERIFIED_INTERNAL_DESK`; SLA and escalation are `NOT_VERIFIED`.

## Router implementation

- Endpoint: `POST /api/civic/route`
- Input: `issueType`, `wardId`
- Success: `SUPPORTED_ROUTE`
- Unknown issue or missing ward mapping: `UNSUPPORTED_ROUTE`
- Returns: `routeId`, `wardId`, authority, department status, official channels, SLA status, escalation status, official source, `sourceStatus`, `reviewStatus`, and `packVersion`.
- The router only indexes Civic Pack data; it contains no Gemini call and makes no model-based authority decision.

## Tests and useful output

Final Maven result:

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Covered:

- supported deterministic route
- unknown issue -> `UNSUPPORTED_ROUTE`
- missing ward mapping -> `UNSUPPORTED_ROUTE`
- simultaneous `OFFICIAL_SOURCE` + `REVIEW_PENDING`
- Civic Pack version, count, uniqueness, source/review states, and unverified department integrity

Deployed route smoke:

```text
issueType=streetlight, wardId=PRABHAG-01
status=SUPPORTED_ROUTE
routeId=NMC-PW-STREETLIGHT-v0.1
sourceStatus=OFFICIAL_SOURCE
reviewStatus=REVIEW_PENDING
packVersion=v0.1
```

## Firestore and BigQuery

- Firestore `(default)` location deliberately verified first: `asia-south1`
- Firestore type: `FIRESTORE_NATIVE`
- BigQuery dataset created: `seewik.seewik_civic`
- BigQuery location: `asia-south1`
- Dataset description: `Seewik civic routing and ward source data; Day 2 v0.1`
- Table: `seewik.seewik_civic.wards`
- Table rows: 20
- Rows with no geometry: 20
- Referenced seat IDs: 41
- No `GEOGRAPHY` column was created because no geometry is defensible.

Verification query result:

```text
row_count=20
no_geometry_count=20
seat_id_count=41
```

## Spatial lookup, outside-city behavior, and latency

- `ST_COVERS`: not implemented; no polygons exist.
- `ST_DISTANCE`: not implemented; no defensible centroids exist.
- GPS auto-resolution: unavailable rather than guessed.
- Outside-Nandurbar test: no auto-resolver exists, so coordinates cannot resolve to any ward and therefore cannot incorrectly map a Dhule point to Nandurbar. `OUTSIDE_SUPPORTED_AREA` becomes required when a spatial resolver is added.
- Five BigQuery spatial latency samples: **blocked pending defensible geometry**. No lookup query exists to time, and no latency numbers were fabricated.

## Deployment

- Production health before deploy: PASS
- Cloud Run service: `seewik-api`
- Region: `asia-south1`
- Revision: `seewik-api-00003-zp5`
- Traffic: 100%
- Public service URL: `https://seewik-api-528138216934.asia-south1.run.app`
- Public health: `https://seewik-api-528138216934.asia-south1.run.app/health`
- Public civic route: `https://seewik-api-528138216934.asia-south1.run.app/api/civic/route`
- Production health after deploy: PASS
- Supported and unsupported deployed route smokes: PASS
- Existing service access policy was preserved unchanged.

## Problems encountered

- The official summary/results ward-identifier conflict was discovered during direct PDF inspection. It is preserved as a blocker instead of being silently normalized.
- Deployment preserved the existing IAM access policy.
- Day 2 production health and route checks passed. The existing Day 1 deployed screenshot remains in the repository.
- The repository was initialized on 2026-08-22 so Civic Pack `v0.1` could be pinned reproducibly.

## Not built (locked out of Day 2)

No full Gemini structured classification/confidence flow, Marathi complaint generation, voice, lifecycle, points, Initiate, leaderboard, BigQuery memory circuit-breaker, MCP, ADK, or heavy UI polish was added.

## Unresolved blockers

1. Product/domain decision: use the result-enumerated 20 prabhags for citizen manual selection, or hold the selector until the final ward-formation annexure explains the SEC summary's `40`.
2. Official final ward map/boundary descriptions or credible locality-per-ward reference are still missing; GPS lookup and its five latency samples remain blocked.
3. Exact locked real civic example image/location is not present; the worked-example test was not substituted with fake Nandurbar evidence.
4. Domain review is pending for all ten routes, including the municipality's internal desk, any verified SLA, and any escalation path.
5. Baseline survey and Nagar Parishad/domain-review outreach remain human tasks.

## Same-day reproducibility and prabhag correction

Added after the initial Day 2 closeout:

- Initialized a Git repository on branch `main`.
- Pinned the original Civic Pack `v0.1` state at commit `b24c20634e89fa739c911a101be2c337cc7f5039` with annotated tag `citypack-v0.1`.
- Added root `CHANGELOG.md` for Civic Pack history.
- Standardized the active jurisdiction contract on `prabhagId`; `wardId` remains request-only as a temporary compatibility alias.
- Restricted manual inputs to official `PRABHAG-01` through `PRABHAG-20`; arbitrary IDs such as `PRABHAG-21` return `UNSUPPORTED_ROUTE`.
- Added `resolutionMethod: SELF_REPORTED` to successful manual routes.
- Added and deployed a Prabhag 1-20 manual selector on Firebase Hosting.
- Created `seewik.seewik_civic.prabhags` in `asia-south1` with 20 prabhags, 41 seat identifiers, and no geometry.
- Preserved the old `wards` table temporarily rather than destructively deleting it.
- Final backend result: 7 tests, 0 failures, 0 errors.
- Deployed backend revision: `seewik-api-00004-86q`, serving 100% of traffic.
- Deployed frontend: `https://seewik.web.app`, bundle `index-tLB5j51A.js`.
- Production check: `PRABHAG-01` returned `SUPPORTED_ROUTE` + `SELF_REPORTED`; `PRABHAG-21` returned `UNSUPPORTED_ROUTE`.

The detailed SEC member results contain 41 councillor-seat rows: Prabhags 1-19 have two seats each (38), and Prabhag 20 has three seats, totaling 41. The directly elected president is not the 41st member-results row.
