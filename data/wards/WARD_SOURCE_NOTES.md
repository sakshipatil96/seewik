# Nandurbar ward source notes - v0.1

The dataset does not contain polygons or centroids. `lat` and `lng` are intentionally null and `geometryType` is `NONE`. GPS auto-resolution is unavailable; no outside-city coordinate result is produced because there is no spatial resolver to test.

## Identifier discrepancy

Two official 2025 SEC sources conflict in terminology:

- The election summary PDF displays `40` under `TOTAL WARDS` and `41` under `TOTAL SEATS` for Nandurbar.
- The member-results PDF (PDF pages 369-372) enumerates prabhag numbers `1` through `20`; seat IDs are `1A`-`19B` and `20A`-`20C`, totaling 41 seats.

The v0.1 records therefore preserve the 20 directly enumerated `prabhagId` values and their 41 seat IDs, while flagging the summary discrepancy. It does not invent `PRABHAG-21` through `PRABHAG-40`. Manual selection uses Prabhags 1-20 and records the resolution method as `SELF_REPORTED`.

## Official sources

- SEC election summary: https://mahasec.maharashtra.gov.in/Upload/PDF/DUE%20ELECTION%20DATA%202025.pdf#page=6
- SEC member results: https://mahasec.maharashtra.gov.in/Upload/PDF/Member%20Winning%20Candidate%20Data.pdf#page=369
- Prior boundary research: `research/nandurbar_ward_maps_2026-08-21/SOURCE_LOG.md`
