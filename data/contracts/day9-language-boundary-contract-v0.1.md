# Day 9 Language and Boundary Contract v0.1

Status: **FROZEN BEFORE DAY 9 SET 2 IMPLEMENTATION**

Frozen: 2026-08-27

Applies to: Day 9 Sets 1–4

## Interface language contract

- The supported interface languages are English (`en`), Marathi (`mr`) and Hindi (`hi`).
- On first use, the interface follows a supported browser/device language. Every other language falls back to English.
- A visible language control is available on every screen.
- A citizen's choice is stored on that device and may be changed at any time.
- Citizen-entered complaint and Initiative text is preserved exactly as entered.
- Mixed-language evidence remains supported.
- Complaint drafts remain limited to Marathi and English. Hindi interface text must state that limitation clearly.
- Authority, route, filing channel, source status, review status, SLA and escalation facts remain the deterministic values supplied by Civic Pack. Interface translation must not rewrite those facts.
- Civic Pack local wording may be displayed only when it is present in the pack. Interface translations are not a source of civic facts.
- The Day 9 translation catalogue is versioned as `interface-copy-v0.1`. Marathi and Hindi interface copy requires product-language review before it can be described as independently reviewed civic wording.

## Prabhag terminology

- Nandurbar has 20 geographic prabhags in this product context.
- The 40 ward/seat positions and directly elected council-president position are not geographic polygons and must not appear as map boundaries.
- The citizen-facing term is `Prabhag 1` through `Prabhag 20`. Internal identifiers remain `PRABHAG-01` through `PRABHAG-20`.

## Boundary dataset decision

The file `data/prabhags/official-map-digitized-boundaries-v0.1.geojson` is derived from a photographed copy of an official Nandurbar Municipal Council 2025 final map. The source image is official; the GeoJSON geometry is an approximate manual digitization from a blurred image and is not official digital GIS geometry.

The three trust levels must remain distinct:

1. `OFFICIAL_SOURCE_IMAGE`: the source map image came from Nagar Parishad.
2. `APPROXIMATE_DIGITISED_OFFICIAL_MAP_IMAGE`: the visible lines were manually traced and approximately georeferenced.
3. Official surveyed/digital boundary geometry: **not available**.

Locked presentation rules:

- Describe the dataset as **approximate boundaries digitized from an official map image**.
- Never describe it as exact, surveyed, legally authoritative or official GIS boundary data.
- Keep `reviewStatus: REVIEW_PENDING_GEOREFERENCE`, `isActive: false` and citizen confirmation requirements until a separately reviewed activation decision.
- Day 9's map may use these polygons as a visual aid, but it must show the limitation and dataset version prominently.
- Show all 20 approximate outlines and highlight only the prabhag returned by the deterministic resolver or selected manually.
- Never guess a nearest polygon when resolution fails.
- A resolved candidate requires explicit citizen confirmation.
- Manual Prabhag 1–20 selection remains a complete equivalent path.
- Outside-Nandurbar coordinates remain rejected.
- If automatic resolution or geometry loading fails, ask the citizen to select manually.
- No third-party map provider receives citizen coordinates. The versioned geometry is rendered locally.
- An approximate location marker may exist only in memory on the report screen, show no numeric coordinates, and be discarded when the report is restarted or the page is left.
- Precise coordinates remain excluded from complaint analytics and public responses.

## Runtime separation

- Existing production prabhag resolution continues to use the active BigQuery dataset and its last-known-good snapshot until the digitized dataset passes a separate georeference review and activation task.
- Displaying the digitized outlines does not change Civic Pack v0.2, route assignment, stored route snapshots or the resolver's source of truth.
- A future verified dataset replaces geometry by a new version. It does not overwrite historical filed-report route snapshots.

## Replacement and review gate

Before the digitized dataset may become active resolver geometry, it needs:

- visual comparison against a clearer official map or boundary description;
- topology and coverage validation;
- georeference accuracy review;
- documented reviewer and review date;
- a new checksum and activation decision;
- a new BigQuery dataset version and rollback plan.

Until then, its fitness for use is visual orientation and citizen-assisted confirmation only.
