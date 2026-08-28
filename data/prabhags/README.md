# Prabhag Boundary Datasets

This directory contains Seewik's deterministic development geometry for 20 Nandurbar prabhags.

## Trust boundary

The polygons are **synthetic**. They do not describe official municipal or prabhag boundaries and must never be presented as official civic data.

- `resolutionQuality`: `SYNTHETIC_BOUNDARY`
- `sourceStatus`: `UNSOURCED`
- `reviewStatus`: `REVIEW_PENDING`
- `requiresCitizenConfirmation`: `true`
- manual override: `SELF_REPORTED`

The outer extent is the search extent returned for OpenStreetMap city node [`245694497`](https://www.openstreetmap.org/node/245694497). OpenStreetMap has district and taluka relations for Nandurbar but no city municipal-boundary relation was found. The extent is therefore recorded as `OSM_CITY_SEARCH_EXTENT_NOT_MUNICIPAL_BOUNDARY`.

## Reproducibility

The committed generator uses a fixed seed and a pure-Python clipped Voronoi implementation. It requires no third-party packages.

```text
python3 data/prabhags/generate_synthetic_boundaries.py
python3 data/prabhags/generate_synthetic_boundaries.py --check
```

The second command fails if any committed generated artifact differs from a clean regeneration. The `.sha256` file pins the GeoJSON bytes.

## Replacement

Official geometry should be loaded as a new dataset version. Runtime code queries the active version, so the Civic Pack and route identifiers do not need to be rewritten when verified geometry arrives.

## Approximate digitization from an official map image

`official-map-digitized-boundaries-v0.1.geojson` contains 20 approximate polygons traced from a photographed copy of the Nandurbar Municipal Council General Election 2025 final map received from Nagar Parishad.

The source image is official. The GeoJSON is **not official digital GIS geometry**: the photographed image was blurred, the visible lines were simplified manually, and WGS84 coordinates were assigned with an approximate affine registration. Accuracy is unknown and georeference review remains pending.

Locked status:

- source: `OFFICIAL_SOURCE_IMAGE`
- geometry quality: `APPROXIMATE_DIGITISED_OFFICIAL_MAP_IMAGE`
- review: `REVIEW_PENDING_GEOREFERENCE`
- runtime active: `false`
- citizen confirmation: required
- fitness for use: visual orientation and citizen-assisted confirmation only
- not fit for: legal boundary determination, surveying or silent automatic routing

The Day 9 contract is `data/contracts/day9-language-boundary-contract-v0.1.md`. It keeps the active resolver and its synthetic last-known-good snapshot unchanged until this digitized dataset passes a separately documented topology, visual and georeference review.

The committed SHA-256 file pins the exact GeoJSON bytes:

```text
cd data/prabhags
shasum -a 256 -c official-map-digitized-boundaries-v0.1.sha256
```

The frontend integrity suite additionally checks all 20 locked feature records, closed counter-clockwise rings, finite coordinates, non-zero area, self-intersections, cross-prabhag crossings, shared-edge ownership, expected visible extent and edge-connected coverage:

```text
cd frontend
node --test scripts/boundary-integrity.test.mjs
```

These checks establish that the committed draft is internally usable as a visual aid. They do not establish georeference accuracy and do not activate it for resolver use.
