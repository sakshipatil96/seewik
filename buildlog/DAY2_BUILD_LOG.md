# Seewik Day 2 build log

Date: 2026-08-22–2026-08-23 (America/Los_Angeles)

## Outcome

Built and deployed Civic Pack `v0.1`, a deterministic Spring Boot civic router, the manual prabhag flow, and a real BigQuery jurisdiction lookup path. Because official Nandurbar city geometry was unavailable, the lookup uses an explicitly synthetic, reproducible development dataset rather than presenting guessed geometry as official data.

The final trust rule is:

```text
synthetic geometry proposes -> citizen confirms
manual SELF_REPORTED selection overrides the proposal
```

No Gemini call is made by the civic router and no model decides civic authority.

## Prior ward research checked

- Inspected `research/nandurbar_ward_maps_2026-08-21/SOURCE_LOG.md` and the saved official downloads before implementation.
- Confirmed that no downloadable official Nandurbar Municipal Council boundary map, boundary annexure, polygon dataset, or credible locality-per-prabhag spatial reference was available.
- Kept the Nandurbar taluka map as context only and excluded Zilla Parishad/Panchayat Samiti material.
- Checked OpenStreetMap/Nominatim as a final development-data source. A named Nandurbar city node and search extent were available, but no municipal city-boundary relation was found. District and taluka relations were not used as city boundaries.

## Prabhag identifier resolution

- The SEC 2025 summary displays `40` total wards and `41` total seats for Nandurbar.
- The official 2025 member-results PDF enumerates Prabhags `1` through `20`, with seat identifiers `1A`–`19B` and `20A`–`20C`, totalling 41 councillor seats.
- Seewik therefore exposes the 20 directly enumerated prabhags and preserves all 41 seat identifiers. It does not fabricate Prabhags 21–40.
- The citizen-facing and runtime identifier is `prabhagId`. `wardId` remains a temporary request compatibility alias.

## Official-source prabhag list

- Version: `v0.1`
- Records: 20 result-enumerated prabhags
- Referenced seat identifiers: 41
- Manual method: `SELF_REPORTED`
- Original geometry: none
- Files:
  - `data/wards/nandurbar-ward-source-v0.1.json`
  - `data/wards/wards.ndjson`
  - `data/wards/bigquery-schema.json`
  - `data/wards/WARD_SOURCE_NOTES.md`

## Synthetic boundary dataset

- Version: `synthetic-v0.1`
- Method: fixed-seed clipped Voronoi tessellation
- Generator: `data/prabhags/generate_synthetic_boundaries.py`
- Generator dependencies: Python standard library only
- Generator seed: `seewik-nandurbar-synthetic-boundaries-v0.1`
- Polygons: 20
- Outer extent: Nominatim search extent for OpenStreetMap city node `245694497`
- Extent quality: `OSM_CITY_SEARCH_EXTENT_NOT_MUNICIPAL_BOUNDARY`
- Resolution quality: `SYNTHETIC_BOUNDARY`
- `sourceStatus`: `UNSOURCED`
- `reviewStatus`: `REVIEW_PENDING`
- `requiresCitizenConfirmation`: `true`
- GeoJSON checksum: `059533c8988334e7a268482c83bac9693e74783081c5b3a8cb51061bda4e100a`
- Reproducibility check: PASS; regeneration produced byte-identical committed artifacts.

Generated artifacts:

- `data/prabhags/synthetic-boundaries-v0.1.geojson`
- `data/prabhags/synthetic-boundaries-v0.1.ndjson`
- `data/prabhags/synthetic-boundaries-v0.1.sha256`
- `data/prabhags/bigquery-schema.json`
- `data/prabhags/README.md`

Official geometry can later be loaded as a new dataset version. The active-version query boundary keeps the Civic Pack and route identifiers stable when the synthetic dataset is replaced.

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

Civic Pack evidence and review counts:

- `OFFICIAL_SOURCE`: 10
- `UNSOURCED`: 0
- `DOMAIN_REVIEWED`: 0
- `REVIEW_PENDING`: 10

The boundary dataset has a separate status count of 20 `UNSOURCED` and 20 `REVIEW_PENDING` records. Boundary status does not weaken or overwrite the source status of a civic route.

Each route uses Nandurbar Municipal Council as the authority based on the Maharashtra Municipal Councils, Nagar Panchayats and Industrial Townships Act, 1965. No contact channel, phone number, SLA, or escalation was invented. Unverified internal departments remain `UNVERIFIED_INTERNAL_DESK`; SLA and escalation remain `NOT_VERIFIED`.

## Runtime resolver and deterministic router

- Resolver endpoint: `POST /api/civic/resolve-prabhag`
- Route endpoint: `POST /api/civic/route`
- BigQuery lookup: parameterized `ST_COVERS(geometry, ST_GEOGPOINT(longitude, latitude))`
- Query cache: disabled for the recorded timings
- In-extent result: `CANDIDATE_PRABHAG`
- No covering polygon: `OUTSIDE_SUPPORTED_AREA`
- BigQuery failure: `RESOLUTION_UNAVAILABLE` with manual fallback
- Invalid coordinates: `INVALID_COORDINATES`
- A synthetic candidate is rejected by the route endpoint with `CONFIRMATION_REQUIRED` until `citizenConfirmed=true` and the dataset version matches.
- A confirmed candidate is recorded as `CITIZEN_CONFIRMED_SYNTHETIC_BOUNDARY`.
- Manual `SELF_REPORTED` selection always remains available and overrides a suggestion.
- Unknown issues, unlisted prabhags, and missing mappings return `UNSUPPORTED_ROUTE`.
- The router only indexes Civic Pack data; it contains no Gemini call and makes no model-based authority decision.

## Firestore and BigQuery

- Firestore `(default)` location verified before dataset work: `asia-south1`
- Firestore type: `FIRESTORE_NATIVE`
- BigQuery dataset: `seewik.seewik_civic`
- BigQuery location: `asia-south1`
- Existing source tables preserved: `wards`, `prabhags`
- Runtime table: `seewik.seewik_civic.prabhag_boundaries`
- Runtime rows: 20
- Valid `GEOGRAPHY` polygons: 20
- Active dataset version: `synthetic-v0.1`
- Cloud Run runtime identity has project-level `bigquery.jobUser` and `bigquery.dataViewer` roles. Dataset-level IAM scoping was attempted but the current interface reported that operation as allowlist-only, so the working permission was not removed or replaced with an unverified configuration.

Validation query:

```text
polygonCount=20
correctlyLabelled=20
distinctPrabhags=20
emptyGeometry=0
```

## Five production BigQuery lookup samples

These requests were sent to the deployed Cloud Run service, not executed through a local backend. The real BigQuery job path ran in production and the query cache was disabled. `queryLatencyMs` was measured inside the Cloud Run backend around the BigQuery resolver call, so it excludes client-to-service network time.

| # | Sample | Coordinates | Result | Prabhag | `queryLatencyMs` |
|---|---|---|---|---|---:|
| 1 | city centre, cold sample | `21.363778, 74.2411418` | `CANDIDATE_PRABHAG` | `PRABHAG-11` | 6014 |
| 2 | southwest, inside extent | `21.22, 74.10` | `CANDIDATE_PRABHAG` | `PRABHAG-01` | 687 |
| 3 | northeast, inside extent | `21.50, 74.38` | `CANDIDATE_PRABHAG` | `PRABHAG-20` | 534 |
| 4 | northwest, inside extent | `21.50, 74.10` | `CANDIDATE_PRABHAG` | `PRABHAG-17` | 540 |
| 5 | Dhule, outside extent | `20.9042, 74.7749` | `OUTSIDE_SUPPORTED_AREA` | none | 675 |

Sorted timings: `534, 540, 675, 687, 6014` ms.

- Five-sample p50 / median: **675 ms**
- Observed cold path: **6014 ms (6.014 s)**
- Subsequent observed range: **534–687 ms**

The 6014 ms cold sample is retained and reported separately rather than being hidden by the median. It is 2.4 times the proposed 2.5-second circuit-breaker threshold.

Measured cold-path latency exceeds the proposed application timeout, which is precisely why the planned memory fallback exists in the architecture. No circuit breaker was added on Day 2.

Before finalising the threshold on Aug 28, take a second production round: 10 warm samples and 3 genuine cold samples from freshly started revisions. Report warm p50 and cold-path behavior separately.

## Tests and live verification

Final backend result:

```text
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Final frontend result:

```text
vite build: PASS
```

Coverage includes:

- supported deterministic route
- unknown issue -> `UNSUPPORTED_ROUTE`
- missing or unlisted prabhag -> `UNSUPPORTED_ROUTE`
- simultaneous `OFFICIAL_SOURCE` + `REVIEW_PENDING`
- manual `SELF_REPORTED` route
- unconfirmed synthetic suggestion -> `CONFIRMATION_REQUIRED`
- stale boundary dataset version -> `CONFIRMATION_REQUIRED`
- confirmed synthetic suggestion -> supported route with confirmation recorded
- parameterized BigQuery query and cache-disabled configuration
- covering boundary -> `CANDIDATE_PRABHAG`
- no covering boundary -> `OUTSIDE_SUPPORTED_AREA`
- invalid coordinates and resolver failure fallbacks
- deterministic boundary regeneration

Production smokes:

```text
unconfirmed synthetic candidate -> CONFIRMATION_REQUIRED
confirmed synthetic candidate -> SUPPORTED_ROUTE
confirmed resolution method -> CITIZEN_CONFIRMED_SYNTHETIC_BOUNDARY
manual PRABHAG-02 override -> SUPPORTED_ROUTE + SELF_REPORTED
Dhule coordinates -> OUTSIDE_SUPPORTED_AREA
```

Browser verification screenshot: `day2-bigquery-runtime-verification.png`.

## Deployment

- Tests were green before deployment.
- Cloud Run service: `seewik-api`
- Region: `asia-south1`
- Revision: `seewik-api-00005-6l6`
- Traffic: 100%
- API URL: `https://seewik-api-528138216934.asia-south1.run.app`
- Health URL: `https://seewik-api-528138216934.asia-south1.run.app/health`
- Resolver URL: `https://seewik-api-528138216934.asia-south1.run.app/api/civic/resolve-prabhag`
- Router URL: `https://seewik-api-528138216934.asia-south1.run.app/api/civic/route`
- Frontend URL: `https://seewik.web.app`
- Public health after deployment: PASS
- Live resolver, confirmation, manual override, supported route, and outside-area checks: PASS

## Problems encountered

- The official SEC summary/result terminology conflict was preserved rather than silently normalized; the directly enumerated 20 prabhags are used.
- OpenStreetMap provided a city search extent but not an official municipal boundary. The extent and all derived polygons are labelled accordingly.
- The first hosted frontend build retained a local development API fallback. The fallback was corrected to the production API, rebuilt, redeployed, and verified live.
- The cold production lookup took 6014 ms; it is recorded in the raw evidence rather than hidden.

## Reproducibility

- Git branch: `main`
- Civic Pack `v0.1` tag: `citypack-v0.1`
- Tagged commit: `71d5aae`
- Synthetic boundary tag: `boundaries-synthetic-v0.1`
- Synthetic boundary commit: `2ec87c9`
- Prabhag/manual-routing baseline before this runtime addition: `70549ef`
- Civic Pack remains `v0.1`; synthetic boundary data has its own `synthetic-v0.1` version.
- The committed generator, fixed seed, fixed timestamp, generated artifacts, and SHA-256 checksum allow byte-identical regeneration.

## Not built (locked out of Day 2)

No full Gemini structured classification/confidence flow, Marathi complaint generation, voice, lifecycle, points, Initiate, leaderboard, BigQuery memory circuit breaker, MCP, ADK, or heavy UI polish was added.

## Unresolved blockers

1. Official Nandurbar prabhag geometry or boundary descriptions remain unavailable. `synthetic-v0.1` must be replaced, not relabelled, when authoritative geometry arrives.
2. Exact locked real civic example image/location is not present; the worked-example test was not substituted with fake Nandurbar evidence.
3. Domain review is pending for all ten civic routes, including internal departments, verified SLA, and escalation paths.
4. Baseline survey and Nagar Parishad/domain-review outreach remain human tasks.
