# Seewik changelog

All notable changes to the versioned Civic Pack and its deterministic routing implementation are recorded here.

## Unreleased

- Standardized the jurisdiction identifier on `prabhagId`; `wardId` remains a temporary request-only compatibility alias.
- Added an official Prabhag 1-20 manual selector with `SELF_REPORTED` resolution.
- Rejects unlisted prabhag identifiers rather than accepting arbitrary values.

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
