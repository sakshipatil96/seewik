# Seewik product follow-ups

## Routing simplicity review — owner follow-up

- [ ] Test the complete production routing experience properly from a citizen's perspective.
- [ ] Evaluate a simpler guided flow: take or upload a photograph → pre-fill a suggested problem → explicitly request and pre-fill a location/prabhag suggestion → show the deterministic civic route → generate the corresponding complaint.
- [ ] Verify the flow on a real Nandurbar case in English, Marathi and Hindi and on a narrow/mobile screen.
- [ ] Preserve the existing trust boundaries during any simplification: no silent location capture, no assumption that a photograph contains usable location, citizen confirmation of category and prabhag, manual overrides, deterministic Civic Pack routing and no invented complaint facts.

This is a later usability review, not a statement that the current application automatically extracts location from an uploaded photograph. Today, photograph classification and the separate **Suggest from my location** action remain explicit citizen actions.

## Initiative meeting-point redesign — owner follow-up

The current `Public meeting place` text field is not sufficient. Replace it with a structured meeting-point selection flow.

- [ ] Let the organiser choose a meeting point inside Seewik using a searchable map and movable pin, with an explicit confirmation step.
- [ ] Save validated latitude/longitude plus a citizen-confirmed display label; do not make a pasted map URL the source of truth.
- [ ] Generate the participant-facing Google Maps universal link from the confirmed coordinates, for example `https://www.google.com/maps/search/?api=1&query=<latitude>,<longitude>`.
- [ ] Show participants the confirmed label, a small pin/map affordance and an **Open in Google Maps** action for directions.
- [ ] Keep location selection explicit: no silent capture, no background tracking and no inference from a photograph.
- [ ] Preserve existing Initiatives as legacy text locations; do not rewrite old records without a versioned migration.
- [ ] Validate coordinates and meeting-point ownership on the backend, keep raw organiser identity private and test malformed/cross-Initiative location updates.
- [ ] Decide the map provider before implementation. Recommended full experience: Google Maps JavaScript API plus Places Autocomplete, using a browser-restricted key and billing controls. Lower-dependency fallback: explicit current-location/manual pin selection plus the generated external Google Maps link, without place search.
- [ ] Verify English, Marathi and Hindi labels, keyboard use, narrow screens, map fallback and participant link behavior.

This is a functional Initiative improvement, not a Day 12 recognition requirement. Schedule it deliberately rather than restoring a free-text-only place field.

## Minor UI copy and label backlog — owner follow-up

- [ ] Collect the user's minor label, wording, spacing and control changes in one annotated list, preferably with the screen name and a screenshot for each item.
- [ ] Separate factual/safety corrections from preference-only polish so comprehension fixes can ship first.
- [ ] Apply accepted copy changes consistently in English, Marathi and Hindi rather than patching only the visible English string.
- [ ] Run accessibility, narrow-layout, navigation and regression checks after the batch.
- [ ] Complete the batch before the Day 14 freeze unless an item is intentionally deferred.
