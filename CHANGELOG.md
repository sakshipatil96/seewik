# Seewik changelog

All notable changes to the versioned Civic Pack and its deterministic routing implementation are recorded here.

## Unreleased

- Enforced Google-linked identity for every durable report, Initiative and technical-check write across backend, Firestore and Storage while preserving anonymous owner-scoped reads and transient civic assistance; added explicit `GOOGLE_LINK_REQUIRED` API responses, atomic rules deployment and a production direct-bypass acceptance script.
- Replaced the signed-out My Reports false-empty state and raw `ACCOUNT_SIGNED_OUT` code with a localized recovery panel that explains saved work remains attached to Google; distinguished linked-empty and anonymous-empty states, reloads reports after sign-in, and removed misleading Refresh, Create Report and Start Over controls from signed-out report screens.
- Froze the Day 9 language/boundary contract: English, Marathi and Hindi interface support; supported-device first-use language; persistent change-anytime selector; Marathi/English-only complaint drafts; and unchanged deterministic civic facts.
- Classified the 20-polygon `official-map-digitized-v0.1` dataset as an approximate digitization from an official Nagar Parishad map image, not official GIS geometry; pinned its checksum and kept it inactive for resolver use pending georeference review.
- Added the versioned `interface-copy-v0.1` catalogue, localized primary citizen screens and dates, and accessibility foundations including a skip link, active navigation semantics, visible focus, status announcements and approximately 44-pixel controls.
- Preserved citizen-entered and mixed-language text exactly, kept Civic Pack authority/route facts unchanged, and recorded that independent Marathi/Hindi product-language review is still pending.
- Added organiser-only `PUBLISHED → CANCELLED` and `PUBLISHED → COMPLETED` Initiative transitions with required cancellation reason, scheduled-time enforcement, idempotent append-only events, final-state discovery exclusion, joined-citizen status visibility and zero reward changes.
- Added `My activities` controls and corrected report evidence freshness so replacing complaint text or a photograph clears the previous classification, confirmed facts, route and draft before new evidence is classified.
- Added green-main quality and deployment workflows covering backend/frontend gates, frozen evaluation integrity, repository policy, dependency checks, an isolated zero-traffic backend candidate, health-before-traffic, frontend route verification and rollback.
- Restricted deployment identity to the production repository and `main` branch, recorded the deployed Git SHA, and avoided long-lived cloud deployment keys.
- Updated the supported backend dependency baseline until the fixed high/critical scan passed; the gate was not bypassed or weakened.
- Deployed Day 8 Set 2 revision `seewik-api-00026-yen` at 100% traffic and removed all temporary test activity, participation, event, ledger and user records.
- Deferred survey scoring while response collection remains open; the frozen answer key is unchanged and the deferral does not block the application release.
- Replaced snapshot fail-fast startup with an explicit degraded manual-selection mode when packaged prabhag data is missing, corrupt or checksum-invalid; no automatic prabhag is guessed, and degraded requests are counted.
- Added `unsupported_route_total` and `low_confidence_clarification_total`, and documented the precise mapping between write-up concepts and existing prabhag, fallback, breaker, failure and latency metrics.
- Preserved the 1,500 ms BigQuery deadline as a pre-measurement conservative choice, then verified ten explicit warm samples and three autoscaling-confirmed cold starts against the deployed image.
- Deployed cleanup revision `seewik-api-00021-rat` at 100% traffic, published the manual-selection frontend behavior, and removed all temporary cold-sample URLs.
- Added transactional per-UID and project-wide rolling limits for paid classification and drafting, controlled HTTP 429/503 responses, bounded `Retry-After`, and active TTL cleanup for expired hashed limiter records.
- Added 15-second classification and 20-second drafting deadlines with cancellation, no automatic retry, distinct timeout/model/schema failures, manual category fallback, and manual complaint writing/copying while deterministic route facts remain visible.
- Added a 1,500 ms BigQuery deadline, closed/open/half-open circuit breaker, and checksum-verified synthetic snapshot fallback that never guesses a prabhag and always preserves citizen confirmation plus manual selection.
- Added revision-tagged privacy-safe counters and latency summaries without citizen identifiers, content, categories, coordinates or response IDs; no alert policies were created.
- Deployed backend revision `seewik-api-00015-hir` at 100% traffic, retained `seewik-api-00014-txh` for rollback, published the frontend fallbacks, and preserved Day 8 Set 1 production evidence.
- Protected classification and complaint drafting with server-verified Firebase identity tokens, rejecting unauthenticated calls before paid model work begins.
- Added Initiate activity creation, nearby Haversine discovery, idempotent joining, live participant counts, and append-only zero-point contribution records pending verified participation.
- Added refresh-safe `/initiatives` and `/initiatives/new` screens to desktop navigation, mobile navigation, and the homepage.
- Froze the 60-case multilingual Track A contract and evaluation policy, then preserved two deployed raw runs with 120/120 category matches, 120/120 language matches, 100% two-run category stability, correct clarification on every unknown control, and no call/schema/transport/HTTP failure.
- Froze the ten-scenario human-baseline answer key before response inspection, including municipal-authority synonyms, exact-authority secondary scoring, ambiguity rules, and representative-versus-institution rules.
- Added Day 7 BigQuery evidence with production smoke exclusion and controlled aggregation fixtures kept strictly separate.
- Deployed backend revision `seewik-api-00014-txh` at 100% traffic in `asia-south1` and published the Initiate frontend to Firebase Hosting.
- Added URL-backed Home, New Report, Complaint Review, My Reports, Report Detail, and My Points screens with desktop navigation, a mobile bottom bar, refresh-safe deep links, and browser Back/Forward support.
- Added an owner-scoped saved-report workspace: Firestore `DRAFT` reports can be reopened and edited, while filed and later reports open as immutable records with their frozen route facts, timeline, lifecycle actions, and derived points.
- Added explicit stale-draft checks across issue type, prabhag, route, and Civic Pack version before filing; changing evidence or route inputs clears dependent results instead of reusing stale facts.
- Added a Start Over action that clears only the current unsaved workflow and explicitly preserves saved Firestore reports.
- Extended the live two-user Firebase verification to cover owner-scoped report listing and to prove that a `FILED` report cannot be edited, resumed as a draft, directly status-mutated, or deleted.
- Added authenticated, server-guarded report lifecycle transitions with immutable events: `DRAFT -> FILED -> CLAIMED_FIXED -> VERIFIED_FIXED`, honest `OVERDUE_UNKNOWN`, and distinct repair-rejection and recurrence reopen events.
- Frozen Civic Pack route facts on first filing so later pack changes cannot rewrite previously filed recipients, departments, channels, limitations, SLA, escalation, or provenance.
- Added append-only `points-ledger-v0.1`: +5 for the first filing and +40 for the first verified fix, with totals derived from ledger entries and no repeat award after reopening or re-verification.
- Added the `same-category-75m-v0.1` duplicate heuristic with measured distance recorded on every evaluation, explicit `DEDUPE_NOT_EVALUATED`, and zero filing points for citizen overrides.
- Added privacy-safe BigQuery lifecycle and dedupe event exports in `asia-south1`; only hashed report/user identifiers and analytical dimensions leave Firestore, while bodies, photos, tracking IDs, and raw identifiers remain excluded.
- Added owner-only Firestore reads for lifecycle events, dedupe evaluations, and points; all corresponding client writes and all analytics-outbox client access are denied.
- Added a citizen timeline/action panel and an explicitly synthetic, browser-local lifecycle demo excluded from Firestore, analytics, and real rewards.
- Added the standalone Gemini `gemini-3.7-flash` civic classifier with Vertex structured output, Civic Pack v0.2 prompt definitions, strict local schema validation, and explicit model-call versus schema-failure reporting.
- Added transient JPEG/PNG/WebP image input and optional text input with a 5 MB image ceiling; report evidence is not persisted by the classifier.
- Added citizen confirmation/correction between Gemini category suggestion and deterministic routing. Numeric confidence remains an internal control signal and is not rendered in the citizen UI.
- Removed the legacy general-purpose Gemini smoke endpoints after the constrained classifier replaced them.
- Added separate 12-case classification and 12-case routing evaluation sets. The initial authored non-voice classification smoke set matched 12/12 expected categories; this small synthetic fixture result is not treated as a general accuracy estimate.
- Deployed Civic Pack v0.2 and the classifier in Cloud Run revision `seewik-api-00008-bnj`, then published the three-step flow to Firebase Hosting.
- Added strict `classification-schema-v0.1` with the 11 Civic Pack issue types plus `UNKNOWN`, language enum `MR | HI | EN | MIXED | UNKNOWN`, and `additionalProperties: false`.
- Added a standalone classification validator that reads allowed issue types from Civic Pack `v0.2`, enforces the internal `0.80` confidence gate, and rejects authority, department, prabhag, channel, SLA, escalation, and route fields before any wiring exists.
- Standardized the jurisdiction identifier on `prabhagId`; `wardId` remains a temporary request-only compatibility alias.
- Added an official Prabhag 1-20 manual selector with `SELF_REPORTED` resolution.
- Rejects unlisted prabhag identifiers rather than accepting arbitrary values.
- Added deterministic `synthetic-v0.1` development boundaries for all 20 prabhags, generated from a fixed seed and a committed pure-Python Voronoi script.
- Added the BigQuery `ST_COVERS` runtime resolver with `OUTSIDE_SUPPORTED_AREA` rejection.
- Requires explicit citizen confirmation before a synthetic candidate can be used; manual `SELF_REPORTED` selection remains the override.
- Records boundary provenance, quality, dataset version, and five uncached production lookup timings.
- Keeps the synthetic geometry independently labelled `UNSOURCED` and `REVIEW_PENDING`; boundary dataset version `synthetic-v0.1` remains independent from Civic Pack versions.

## Civic Pack v0.2 - 2026-08-24

Evidence-traceable department differentiation and canonical classification definitions for Nandurbar.

### Added

- Added `PUBLIC_AREA_CLEANLINESS`, bringing the supported Civic Pack catalogue to eleven deterministic issue types.
- Added canonical `classificationDefinition` and `excludes` rules to every issue type so the future Gemini prompt and evaluation labels can share one source of truth.
- Added structured likely-department metadata with the independent status `TYPICAL_STRUCTURE_UNVERIFIED`; no Nandurbar desk is presented as confirmed.
- Added route-level, citizen-visible `knownLimitations` for road ownership, electricity-network faults, water-network operation, drainage desk allocation, encroachment handling, and mosquito-control treatment.
- Added full source title, URL, and section reference on every route.
- Separated the observed municipal Facebook page into `informationalLinks`; only verified contact methods remain in `officialChannels`.

### Preserved

- Exact independent route fields `sourceStatus` and `reviewStatus`.
- Initial counts: 11 `OFFICIAL_SOURCE`, 0 `UNSOURCED`, 0 `DOMAIN_REVIEWED`, and 11 `REVIEW_PENDING`.
- Null SLA and escalation values where no citable commitment exists.
- Deterministic routing with no Gemini authority or department decision.
- Synthetic BigQuery prabhag candidates require citizen confirmation, and manual `SELF_REPORTED` selection remains available.

### Data limitations

- All department assignments are likely internal handlers inferred from typical municipal structure and remain `TYPICAL_STRUCTURE_UNVERIFIED` pending Nandurbar confirmation.
- Authority-level limitations travel with affected route responses instead of living only in documentation.

## Civic Pack v0.1 - 2026-08-22

Initial evidence-traceable Civic Pack for Nandurbar Municipal Council.

### Added

- Ten deterministic civic routes covering garbage, illegal dumping, potholes, streetlights, drainage/sewage, water supply, public toilets/sanitation, mosquito/fogging requests, dead-animal removal, and public-road obstruction.
- Independent verification fields: `sourceStatus` and `reviewStatus`.
- Initial counts: 10 `OFFICIAL_SOURCE`, 0 `UNSOURCED`, 0 `DOMAIN_REVIEWED`, and 10 `REVIEW_PENDING`.
- Official council, DMA complaint-form, and in-person municipal-office channels.
- Explicit `NOT_VERIFIED` SLA/escalation and `UNVERIFIED_INTERNAL_DESK` department values where Nandurbar-specific information was not published.
- Deterministic Spring Boot `(issueType, wardId) -> route` endpoint with `UNSUPPORTED_ROUTE` handling and no Gemini authority decision.
- Evidence-traceable prabhag source data with no fabricated geometry or coordinates.

### Data limitations

- GPS jurisdiction resolution is unavailable because no defensible official boundary, centroid, or locality-anchor dataset has been acquired yet.
- The SEC summary displays 40 wards and 41 seats, while the official member results enumerate Prabhags 1-20 and 41 A/B/C seat identifiers. The directly enumerated prabhag identifiers are preserved without inventing Prabhags 21-40.
- All routes await municipal/domain review.

### Deployment

- Cloud Run revision: `seewik-api-00003-zp5`
- Region: `asia-south1`
- BigQuery dataset/table: `seewik.seewik_civic.wards`
