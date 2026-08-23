# Synthetic Prabhag Boundaries

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
