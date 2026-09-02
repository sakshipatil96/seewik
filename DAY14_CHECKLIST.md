# Day 14 Checklist — Initiative meeting points and creation clarity

Day 14 makes Initiative creation understandable and usable without silently treating a device's current location as the meeting point. It also closes the remaining physical Android acceptance checks and removes the last citizen-facing internal report metadata. It does not redesign report routing or begin survey analysis.

## Set 0 — Freeze the location and publishing contract

- [x] Initially freeze Day 14 to the lower-dependency version: a movable/manual pin, citizen-confirmed place label and generated external Google Maps link. This selection-method decision was later superseded by the approved amendment below.
- [x] Preserve the original no-Places decision as contract v0.1. The later v0.2 amendment adds optional Places search but does not make it required for validated meeting-point coordinates.
- [x] Freeze the rule that the organiser explicitly selects and confirms the meeting point. Device location, if supported later, may only help centre a map after permission; it is never silently saved as the activity location.
- [x] Remove **Use my location for discovery** from Initiative creation.
- [x] Freeze the participant action as **Open in Google Maps**, generated from the confirmed coordinates.
- [x] Preserve existing Initiatives as legacy text-location records; no automatic geocoding or destructive migration.
- [x] Version the contract as `initiative-meeting-point-v0.1`.

### Approved scope amendment — Google place search

- [x] Owner approved a restricted Google Places Autocomplete convenience layer after the initial lower-dependency freeze; record the amended decision as `initiative-meeting-point-v0.2` without changing the stored meeting-point schema.
- [x] Keep the local movable pin and manual coordinates as the functional fallback; Google search cannot become a publishing dependency.
- [x] Restrict predictions to India and the Nandurbar municipal map bounds, request only name/address/location after selection, and never use device location for creation.
- [x] Keep the final citizen-confirmed label and coordinates as the source of truth; do not store a search query, browser key or complete Google Place response.

## Set 1 — Close the remaining physical Android checks

- [ ] On the same Android Chrome session, without clearing site data, refresh production and retry Google connection after the Firebase Authentication Viewer permission change.
- [ ] Confirm My Civic Card loads after connection and remains connected after another refresh.
- [ ] Share the generated Civic Card image through the Android share sheet to a private WhatsApp chat; confirm an image arrives and Seewik remains usable after returning.
- [ ] Check all three reward cards: visible **Example local reward** labels, correct lifetime progress and clear locked states below each threshold.
- [ ] Open an Initiative's **Open in Google Maps** action on Android and confirm it hands the generated meeting point to Google Maps or its web fallback.
- [ ] Record device/OS/Chrome details and results in the appropriate release evidence. Treat the earlier layout, image-privacy and three-language checks as passed rather than repeating them unnecessarily.

## Set 2 — Meeting-point data and backend integrity

- [x] Define a structured, versioned meeting point containing a citizen-confirmed display label, latitude, longitude and schema version.
- [x] Validate latitude, longitude, label length and required fields on the backend; reject malformed, missing and non-finite coordinates.
- [x] Keep Initiative creation and location writes backend-owned and restricted to the signed-in organiser.
- [x] Derive the Google Maps universal link from validated coordinates; do not accept a pasted map link as the source of truth.
- [x] Keep raw account identity private and exclude precise coordinates from routine logs and analytics.
- [x] Read legacy Initiatives safely when only the existing text place is present.
- [x] Add contract and persistence tests for valid, boundary, malformed and legacy meeting points.

## Set 3 — Clear organiser creation flow

- [x] Replace the free-text-only meeting-place step with the selected map/pin flow and a required, editable public label.
- [x] Let the organiser move the pin, review the label and explicitly confirm the meeting point before publishing.
- [x] Remove **Use my location for discovery** and do not require geolocation permission to create an Initiative.
- [x] Keep **Publish activity** enabled only when the contract is satisfied, but show every unmet requirement beside the action instead of leaving a silently disabled button.
- [x] Preserve all completed form fields when validation, map loading, network access or publishing fails.
- [x] Give map failures an accessible manual fallback consistent with the Set 0 choice.
- [x] Use **Create an Initiative** and **Join an Initiative** consistently in English, Marathi and Hindi.

## Set 4 — Participant and My Actions experience

- [x] Show the confirmed meeting-point label on nearby Initiative cards and My Actions Initiative records.
- [x] Add a small location/pin affordance and an **Open in Google Maps** action that uses the generated universal link.
- [x] Verify the generated link in the desktop browser without exposing the organiser's current or home location. Physical Android handoff remains in Set 1.
- [x] Keep **My Actions** organised into reports and Initiatives, with clear **Organising** and **Joined** tags and completed items shown consistently.
- [x] Keep the approved Community Yoga, Birthday Meal Donation and Plantation Morning wording restricted to labelled demo/test fixtures; no invented live attendance, organisers or dates were added to production.
- [x] Confirm through the existing identity and Initiative regressions that joined and organised records continue loading after refresh and Google account recovery.

## Set 5 — Citizen-facing cleanup and wording batch

Owner-approved execution order on 2026-09-01:

### 5.1 — Trust cleanup

- [x] Put the homepage lifecycle demonstration and all synthetic controls behind explicit `?debug=1`; normal citizens must not see demo scaffolding.
- [x] Replace raw network strings such as **Failed to fetch** with shared citizen-safe, translated messages and appropriate retry actions.
- [x] Remove the normal-mode cloud/API diagnostic card and **Verify cloud services** action; retain technical validation only in debug/test surfaces.
- [x] On filed report details, hide the internal **Route** identifier and **Pack** version while preserving both in the immutable record and diagnostic evidence.

### 5.2 — Controlled Google meeting-place search

- [x] Replace the Google-owned `PlaceAutocompleteElement` interface with a Seewik-controlled inline suggestion list using the current Autocomplete Data API.
- [x] Keep a fresh session token per search session, a short input delay, minimal selected fields, India/Nandurbar restrictions and required Google attribution.
- [x] Add explicit loading, no-results and failure states with an always-visible escape to the manual pin.
- [x] Keep the search inline at 375px; do not open an unbranded fullscreen Google panel.
- [x] Send the active Seewik language and region `IN` with each request. Do not promise that Google has translated data for every local place.
- [x] Auto-fill only a cleaned primary place name; remove a leading Plus Code from the public label, keep the label editable and require the existing confirmation.

### 5.3 — Boundary and header reliability

- [x] Remove the indefinite approximate-boundary loading state by eagerly loading the small local guide or providing a bounded failure path to manual selection.
- [x] Make the sticky header visually opaque so scrolled headings cannot remain legible behind its controls.

### 5.4 — Plain-language batch

- [x] Remove the non-sequential `03` and `04` labels from Home.
- [x] Change the Home headline to **Report a problem. Get it to the right office.**
- [x] Replace **without rewriting their frozen facts** with **Filed reports can’t be edited.**
- [x] Replace **Get the deterministic route** with **Find the right office.**
- [x] Remove **Civic Pack v0.2** from citizen-facing instructions while preserving the version internally.
- [x] Reduce the Prabhag explanation to one plain sentence plus a small-print boundary caveat.
- [x] Replace **owner-protected draft** / **immutable records** with **Only you can see your drafts.** / **Filed reports can’t be changed.**
- [x] Replace the backend-ledger explanation with **Points are earned only from verified actions.**
- [x] Shorten the device-only warning while keeping complete recovery details in the account dialog.
- [x] Apply every accepted wording change consistently in English, Marathi and Hindi.

### 5.5 — Honest empty states

- [x] Remove the duplicate thin **No saved reports yet** line and keep the useful My Actions empty-state card.
- [x] Before location is shared, show an Initiative prompt explaining that the citizen can find nearby initiatives or create one; retain the existing post-search empty state.

### 5.6 — Persistent emergency access

- [x] Add a compact, translated, accessible orange **112** action to the sticky header instead of crowding the five-button mobile navigation with a sixth item.
- [x] Re-verify that every current callable emergency contact retains its explicit `tel:` action; do not make an entire card place an accidental call.

### 5.7 — Optional polish only after green essential work

- [x] After the essential batch was green, improve the low-risk subset: targeted Marathi/Hindi line-height, report-placeholder contrast and duplicate Initiative eyebrows. Recognition placement and the Civic Awareness affordance remain optional buffer work.
- [x] Re-run copy-safety checks for vulgar, pun-like, joking, ambiguous or accidentally inappropriate wording.
- [x] Keep factual and safety wording separate from optional visual polish so essential corrections are not blocked.

### Explicitly deferred from Set 5

- Segmented language control or a forced Marathi default. Keep remembered/device-language selection with English fallback.
- Custom photo and date controls.
- Article 51A illustration/icon system.
- Civic Card zero-state redesign and zero-point explanation restructuring.
- Automatic refresh-on-focus and major navigation redesign.

## Set 6 — Integrity, accessibility and regression tests

- [x] Load the real Google Places widget locally through the restricted browser key without exposing the key in repository changes or routine diagnostics.
- [x] Verify English, Marathi and Hindi search instructions and preserve the keyboard/manual pin flow when Google search is optional or unavailable.
- [x] Cover accepted, missing-coordinate, missing-label and outside-Nandurbar Google selections with deterministic tests.
- [x] Select a real Google result through the live desktop suggestion list; confirm label/pin population and adjustment invalidation.
- [ ] Repeat the live Google-result selection on physical Android and after production deployment; confirm the exact Maps preview/handoff.
- [x] Coordinate boundaries, missing label, missing pin, non-numeric values and forged/cross-organiser writes are rejected by backend validation and local rule/ownership coverage.
- [x] A retry with the same client request ID creates one Initiative only; the transactional write prevents partial public records, and frontend failures preserve the form.
- [x] Legacy text-only Initiative records still render and remain joinable where otherwise valid.
- [x] Google Maps links encode the exact validated coordinates and open correctly in local desktop verification.
- [x] Direct Firestore Initiative creation and meeting-point forgery remain rejected by the current local rule suite.
- [ ] Repeat direct Initiative and meeting-point forgery checks against the deployed rules after release.
- [x] Keyboard selection, visible focus, screen-reader labels, status announcements and non-map/manual fallback work in automated and live local checks.
- [x] English, Marathi and Hindi automated responsive coverage plus the earlier physical Android portrait/landscape/intermediate-width pass remain green. Repeat the changed Google search and header on Android in Set 1.
- [x] Full regression: reports, lifecycle, points, My Civic Card, recognition, rewards, Initiative join/organise, attendance, identity recovery and emergency information.
- [x] Repository and diagnostic policy scans found no committed browser key, account token, attendance secret, claim code or routine precise-coordinate logging.

## Set 7 — Release

- [ ] Run backend tests, frontend tests, production build, dependency audit, repository checks and secret scan.
- [ ] Test the production create/join/location flow using controlled temporary records and remove them afterward without touching citizen records.
- [ ] Complete three-language, keyboard and narrow-layout browser checks.
- [ ] Obtain explicit owner approval before pushing or deploying.
- [ ] Deploy only from green `main`; record the commit SHA, frontend release and Cloud Run revision when applicable.
- [ ] Write `DAY14_BUILD_LOG.md` with test counts, physical-device results, production evidence, cleanup and limitations.

## Explicitly outside Day 14

- Replacing the local pin fallback with a Google-only map
- Unrestricted Google search, background geocoding or Places data storage
- Automatic or background location tracking
- Inferring an Initiative meeting point from a photograph or the organiser's device location
- Automatic geocoding or rewriting of old Initiative records
- Live Nagar Parishad campaign ingestion
- The broader report-routing simplicity redesign
- Survey CSV reshaping, BigQuery scoring or baseline analysis
- Real business onboarding, payment or point-of-sale reward verification

## Owner inputs needed

- Provide the remaining Android results when the phone is available: Google persistence, WhatsApp image share and reward-card check.
- Send any final minor wording/UI changes before Set 5 begins.
- Approve push and production deployment only after the Day 14 release evidence is green.
