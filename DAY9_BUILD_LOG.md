# Day 9 Build Log

Date: 2026-08-28

Scope in this entry: Sets 1 and 2

Production deployment: deferred to the Day 9 final verification set

## Set 1 — language and boundary requirements

The language and boundary contract was frozen before the Set 2 implementation in `data/contracts/day9-language-boundary-contract-v0.1.md`.

Language decisions:

- interface languages: English, Marathi and Hindi;
- first visit follows a supported device/browser language and otherwise uses English;
- the switcher remains visible and a citizen may change language at any time;
- the choice is stored on the device;
- citizen-entered text and mixed-language evidence remain unchanged;
- complaint drafting remains Marathi/English only;
- authority and route facts remain deterministic Civic Pack values rather than translated guesses.

Boundary decision:

- `official-map-digitized-boundaries-v0.1.geojson` has 20 polygons digitized from a photographed official 2025 Nagar Parishad map;
- the source image is official, while the GeoJSON coordinates are an approximate manual trace from a blurred image;
- the geometry therefore remains `REVIEW_PENDING_GEOREFERENCE` and `isActive: false` for resolver use;
- Day 9's later map may use it only as a prominently labelled visual aid requiring citizen confirmation;
- manual Prabhag 1–20 selection remains complete and independent;
- deterministic resolution continues using the existing active BigQuery dataset and last-known-good snapshot;
- the 40 ward/seat positions are not treated as 40 geographic polygons.

GeoJSON SHA-256:

`a5d1c9870b7f8d335db87ddab4c59c94455f810e97e43eb6d396eac1b604fcc2`

## Set 2 — language and accessibility foundations

Implemented:

- supported-device-language detection with English fallback;
- always-visible English/Marathi/Hindi language selector;
- local persistence and a change-anytime flow;
- document `lang` and localized page title updates;
- versioned `interface-copy-v0.1` catalogue;
- localized navigation, homepage, reporting, complaint review, saved reports, report detail, points and Initiative screens;
- localized issue labels, lifecycle labels and dates while stored values remain unchanged;
- common localized loading/fallback states with safe English fallback for an unrecognized upstream error;
- a clear Marathi/English-only draft-language note in every interface language;
- unchanged citizen complaint and Initiative text;
- unchanged authority, department, route, channel, provenance and limitation facts;
- skip link, visible focus, active navigation semantics, hidden decorative icons, status announcements and approximately 44-pixel minimum targets;
- improved Marathi/Hindi wrapping and spacing.

The Marathi/Hindi catalogue has not been approved by an independent language reviewer. It is implemented and complete as product copy v0.1, but must not be described as independently reviewed civic wording. Civic Pack local wording remains separate.

## Verification

- Frontend tests: 8 passed, 0 failed.
- TypeScript and production build: passed.
- Direct-link and browser navigation contracts: passed.
- Translation catalogue: at least 150 non-empty Marathi/Hindi entries; integrity test passed.
- Source accessibility foundations: passed.
- Bundle after language catalogue: 859.28 kB minified / 251.62 kB gzip.
- Previous baseline: approximately 807.28 kB minified / 239.78 kB gzip.
- Large-chunk warning remains and is assigned to Day 9 Set 4 code splitting.

The local browser visual check could not run because the browser surface denied local-page access when its security policy could not be verified. No visual/browser-audit claim is made here. Production screenshots, 200% zoom, responsive layout and live console verification remain in the final Day 9 verification set.

## Deployment status

Sets 1 and 2 are not deployed by this entry. Day 9 intentionally places deployment after the boundary UI, performance work and final green verification so an incomplete Day 9 interface is not promoted between sets.
