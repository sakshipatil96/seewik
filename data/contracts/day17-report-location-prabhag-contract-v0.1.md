# Seewik Report Location and Prabhag Contract v0.1

Status: Set 3 implementation contract

## Purpose

Seewik uses a citizen's report location to suggest a Nandurbar prabhag. The suggestion reduces filing friction but never becomes an official, legal or silent boundary determination. The citizen confirms the suggested prabhag by continuing to the route or overrides it manually.

## Location priority

The New Report flow attempts location sources in this order:

1. `PHOTO`: GPS coordinates embedded in the selected photo.
2. `DEVICE`: the citizen's available browser location.
3. `GOOGLE`: an address or landmark deliberately selected from Google Places.
4. `MANUAL`: a prabhag deliberately selected without usable coordinates.

A deliberate Google selection or manual prabhag choice overrides an earlier automatic suggestion. Adding a new photo may replace a photo/device suggestion, but it must not silently replace a citizen's manual prabhag choice.

## Address behavior

- Photo and device coordinates may be reverse-geocoded into a readable address.
- A reverse-geocoding failure does not change the coordinate-based prabhag result.
- The address field remains editable.
- Google suggestions are restricted to India and checked against the Nandurbar map extent before acceptance.
- Selecting a Google result stores one stable display value and must not trigger another autocomplete request.
- An edited address invalidates route-dependent drafts and receipts because complaint wording may contain the old location.
- Editing address wording alone does not silently move coordinates or select another prabhag.

## Boundary dataset

Runtime dataset: `seewik-map-trace-v0.2`

Normalized trust fields:

```yaml
resolutionQuality: APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE
sourceStatus: MUNICIPAL_OFFICE_WALL_MAP_PHOTO
reviewStatus: NOT_AUTHORITY_VERIFIED
requiresCitizenConfirmation: true
```

The immutable source was traced from a photograph of a map displayed at the Nagar Parishad office. It was not supplied or published by the authority as machine-readable GIS geometry.

The source metadata's roughly +/-100 m built-up-core value describes the digitization method's expected error. It is not measured accuracy, authority verification or a service guarantee. `sharpSourceCoverage` describes source-image legibility and must never be presented as geographic accuracy.

No internal visual-alignment percentage is published.

## Resolver behavior

- The backend performs deterministic point-in-polygon resolution.
- BigQuery `ST_COVERS` remains the primary resolver.
- The checksum-pinned v0.2 snapshot is the bounded last-known-good fallback.
- The resolver returns at most one candidate prabhag.
- It must never select the nearest polygon when the point is uncovered.
- An uncovered point returns `OUTSIDE_SUPPORTED_AREA` or requires manual selection rather than a guess.
- BigQuery rows with a different dataset version or weaker trust fields are rejected.
- The runtime dataset version accompanies every coordinate-derived route request.

## Citizen confirmation and override

- A coordinate-derived result is a candidate until the citizen presses Find official route.
- Pressing Find official route records acceptance only when the displayed candidate still matches the latest resolver response.
- Selecting a prabhag from the list or map is an explicit manual override.
- Manual override retains the readable address and marker when available.
- The permanently visible 20-prabhag map highlights the suggested or selected prabhag.
- The non-map list remains a complete, keyboard-accessible alternative.

## Invalidation rules

Changing any of the following clears the existing civic route and all dependent filing drafts, filing-action receipts and unsent route-dependent state:

- coordinates;
- Google place selection;
- readable address text;
- prabhag;
- issue category;
- evidence used to derive the issue or location.

For already filed reports, immutable filing evidence is not rewritten. Any future escalation draft must be regenerated against the filed route snapshot and current lifecycle contract rather than reusing a draft from another route or cycle.

## Privacy

- Exact coordinates and readable addresses remain owner-private.
- Public contribution, recognition and community views do not expose report coordinates, evidence or private address text.
- The map may show the current position only within the owner's report flow.
- No location is used for advertising, rewards or public ranking.

## Language and accessibility

- All labels, guidance, errors and boundary caveats are available in English, Marathi and Hindi.
- Place names and addresses returned by Google may remain in the form supplied by Google.
- The interface must never translate a source label in a way that changes its factual address.
- Map outlines are keyboard-selectable and expose selection state to assistive technology.
- The manual prabhag list remains usable when Google Maps, geolocation, reverse geocoding or the visual map is unavailable.

## Activation rule

Production activation is coordinated:

1. Prepare and validate the v0.2 BigQuery rows.
2. Deactivate previous boundary rows and activate exactly the 20 v0.2 rows.
3. Confirm the production query returns only `seewik-map-trace-v0.2` with the normalized trust fields.
4. Deploy the backend that requires v0.2.
5. Deploy the frontend that displays v0.2.
6. Verify candidate, manual override, uncovered-point and fallback behavior.

The backend must not be deployed while BigQuery still serves active synthetic rows. Snapshot fallback is for bounded dependency failure, not routine dataset mismatch.

## Required evidence before push

- Frozen source and source-image checksums.
- 20 unique valid polygons with closed finite rings.
- Counter-clockwise exterior winding.
- No self-intersections.
- Frozen double-trace baseline across all 190 pairs: 25 crossing pairs and 78 proper crossings.
- Deterministic overlap estimate below the 1,000 m2 engineering ceiling; supplied independent measurement: 607 m2.
- Multi-match resolution always chooses the lowest prabhag ID and increments an operational counter.
- Gap/sliver review without claiming more than the check establishes.
- Backend resolver, fallback, stale-version and confirmation tests.
- Frontend photo/device/Google/manual priority tests.
- Route and receipt invalidation tests.
- English, Marathi and Hindi UI checks.
- Keyboard and narrow/wide-screen walkthroughs.

Structural and sampled checks do not establish legal or geographic accuracy. Citizen confirmation remains mandatory even when every engineering check passes.
