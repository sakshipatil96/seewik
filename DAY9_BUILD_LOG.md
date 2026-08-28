# Day 9 Build Log

Date: 2026-08-28

Scope in this entry: Sets 1, 2 and 3

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

## Set 3 — approximate prabhag boundary UI

Implemented:

- a lazy-loaded local SVG boundary guide using `official-map-digitized-boundaries-v0.1.geojson` directly from the versioned repository dataset;
- no third-party map, tile, geocoding or analytics provider;
- all 20 geographic prabhag outlines, each selectable by pointer, Enter or Space;
- a single highlight limited to the current deterministic candidate, confirmed automatic selection or explicit manual selection;
- the exact English label “approximate boundaries digitized from an official map image”;
- visible `official-map-digitized-v0.1` and `REVIEW_PENDING_GEOREFERENCE` provenance;
- an explicit statement that the visual guide is not official digital GIS geometry and never changes automatic routing;
- adjacent manual Prabhag 1–20 selection as a fully equivalent non-map flow;
- no implicit Prabhag 1 default: the route action remains disabled until the citizen explicitly confirms an automatic candidate or makes a manual selection;
- manual selection that overrides an automatic suggestion without guessing a nearest polygon;
- existing outside-Nandurbar rejection and manual degraded fallback unchanged;
- graceful manual fallback when the lazy boundary module or geometry cannot load;
- an optional in-memory location marker quantized to an approximate display position, with no numeric coordinates shown or stored by the guide;
- English, Marathi and Hindi boundary copy in `interface-copy-v0.1`;
- preserved refresh-safe `/report/new` routing and browser Back/Forward behavior.

The approximate digitized dataset remains `isActive: false`. The active BigQuery resolver and packaged last-known-good synthetic snapshot were not changed.

Integrity coverage now verifies:

- the pinned SHA-256;
- locked dataset and feature metadata;
- exactly 20 unique Prabhag 1–20 polygons;
- closed, counter-clockwise, finite, non-zero rings;
- no polygon self-intersections or cross-prabhag crossings;
- the expected visible latitude/longitude extent;
- at most two owners per shared edge;
- one edge-connected visible coverage across all 20 polygons.

## Verification

- Frontend tests after Set 3: 14 passed, 0 failed.
- TypeScript and production build: passed.
- Direct-link and browser navigation contracts: passed.
- Translation catalogue: at least 150 non-empty Marathi/Hindi entries; integrity test passed.
- Source accessibility foundations: passed.
- Boundary integrity, lazy-loading and fallback contracts: passed.
- Local browser: all 20 outlines visible at 1280×720 desktop and 390×844 mobile; no horizontal overflow.
- Keyboard: Enter on an outline selected the matching manual Prabhag and synchronized the list.
- Confirmation gate: the route action remained disabled until both category and prabhag were explicitly confirmed.
- English, Marathi and Hindi map copy rendered without detected text overflow at mobile width.
- The 640-CSS-pixel reflow check, equivalent to 200% zoom from a 1280-pixel viewport, stacked the map and manual selector without horizontal overflow.
- Refresh retained `/report/new` and the selected interface language; browser Back/Forward preserved `/` and `/report/new`.
- Browser console: no errors or warnings during the Set 3 checks.
- Bundle after language catalogue, before Set 3: 859.28 kB minified / 251.62 kB gzip.
- Set 3 build: 870.19 kB / 254.60 kB gzip initial JavaScript plus a lazy 37.60 kB / 4.23 kB gzip boundary chunk.
- Total Set 3 JavaScript: 907.79 kB minified / 258.83 kB gzip; route-level performance work remains assigned to Set 4.
- Previous baseline: approximately 807.28 kB minified / 239.78 kB gzip.
- Large-chunk warning remains and is assigned to Day 9 Set 4 code splitting.

The Set 2 browser limitation was cleared during Set 3, so local desktop, mobile, multilingual, keyboard, reflow, refresh, history and console checks were completed. Production screenshots and production-route verification remain in the final Day 9 verification set.

## Deployment status

Sets 1–3 are not deployed by this entry. Day 9 intentionally places deployment after performance work and final green verification so an incomplete Day 9 interface is not promoted between sets.
