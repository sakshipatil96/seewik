# Prabhag Boundary Datasets

This directory contains the versioned geometry artifacts used by Seewik to suggest one of Nandurbar's 20 prabhags. A suggested prabhag is never a legal boundary determination and always requires citizen confirmation or correction.

## Active runtime trust boundary

Set 3 uses `official-map-digitized-boundaries-v0.2.geojson` as the best available approximate geometry for citizen-confirmed suggestions. It was traced from a clearer photograph of a 2025 prabhag map displayed at the Nagar Parishad office. The council did not send or publish this file as digital GIS data.

Runtime normalization deliberately uses these values:

- `datasetVersion`: `seewik-map-trace-v0.2`
- `resolutionQuality`: `APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE`
- `sourceStatus`: `MUNICIPAL_OFFICE_WALL_MAP_PHOTO`
- `reviewStatus`: `NOT_AUTHORITY_VERIFIED`
- `requiresCitizenConfirmation`: `true`
- manual override: `SELF_REPORTED`

`isActive: true` in the generated BigQuery rows means only that the dataset is eligible to make an approximate suggestion. It does not make the geometry official, authority-verified, surveyed or suitable for silent routing. The immutable source GeoJSON retains its original `isActive: false` field; normalization, rather than mutation, creates the runtime record.

The user-facing map must say that it is an approximate orientation aid created from a wall-map photograph and not official digital ward geometry. Exact coordinates remain private. A citizen can always override the suggestion by selecting a prabhag manually.

## Version 0.2 artifacts

- `official-map-digitized-boundaries-v0.2.geojson`: immutable 20-prabhag source trace
- `source/ward-map-full-v0.2.png`: source wall-map photograph
- `official-map-digitized-boundaries-v0.2.sha256`: pinned source checksums
- `prepare_map_trace_v0_2.mjs`: deterministic runtime normalizer
- `official-map-digitized-boundaries-v0.2.ndjson`: generated BigQuery-ready rows
- `backend/src/main/resources/prabhag-snapshot-map-trace-v0.2.geojson`: checksum-pinned last-known-good fallback

The source metadata records the digitization method, scale fitting to the published 32.41 km2 council area, rotation from the visible railway centreline, and positioning from Nandurbar station. Its roughly +/-100 m built-up-core figure is a method estimate, not measured geographic accuracy or authority verification.

The `sharpSourceCoverage` fields describe source-image legibility. They must not be presented as boundary accuracy, alignment confidence or an authority endorsement. No internal visual-alignment percentage is published.

## Runtime preparation

Generate the normalized BigQuery rows from the immutable source:

```text
node data/prabhags/prepare_map_trace_v0_2.mjs
```

Production activation must replace the previously active dataset as one coordinated release. Do not deploy backend constants that require v0.2 while BigQuery still serves active synthetic rows. The checksum-verified fallback is a safety mechanism, not a substitute for loading the matching runtime rows.

## Integrity boundary

The v0.2 integrity suite checks the frozen checksum, feature count, unique identifiers, finite closed rings, counter-clockwise winding and self-intersections. It also scans all 190 prabhag pairs and freezes the known double-trace baseline: 25 crossing pairs, 78 proper crossings and a deterministic estimated-overlap ceiling of 1,000 m2.

```text
cd frontend
node --test scripts/boundary-v0.2-integrity.test.mjs
```

The independently supplied measurement for this frozen file estimated 607 m2 of total overlap. The automated ceiling is deliberately higher to accommodate estimator resolution without normalizing away the source uncertainty. These checks establish internal structural fitness only. They do not prove geographic accuracy, authority verification, legal validity or the absence of every possible sub-resolution gap. Citizen confirmation remains required.

## Historical datasets

The synthetic v0.1 dataset and generator remain committed as historical, reproducible development evidence. They use:

- `resolutionQuality`: `SYNTHETIC_BOUNDARY`
- `sourceStatus`: `UNSOURCED`
- `reviewStatus`: `REVIEW_PENDING`
- `requiresCitizenConfirmation`: `true`

`official-map-digitized-boundaries-v0.1.geojson` is an earlier approximate trace from a less clear photographed map copy. It remains inactive and must not be presented as official digital GIS geometry.

The Day 9 historical contract is `data/contracts/day9-language-boundary-contract-v0.1.md`.
