# Seewik changelog

All notable changes to the versioned Civic Pack and its deterministic routing implementation are recorded here.

## Unreleased

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
