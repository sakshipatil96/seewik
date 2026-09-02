# Seewik product follow-ups

## Physical Android acceptance — owner follow-up

- [x] Verify the responsive header, five-button mobile navigation, portrait layout, landscape layout and English/Marathi/Hindi switching on a physical Android phone.
- [x] Verify that the generated Civic Card image is readable and does not expose email, account ID, report text, photographs or location.
- [x] Diagnose the Google connection failure: the account chooser and consent completed, but `POST /api/profile/sync` returned `503 unavailable` because the Cloud Run runtime account lacked Firebase Authentication read access.
- [x] Grant and verify **Firebase Authentication Viewer** (`roles/firebaseauth.viewer`) for the Cloud Run runtime account. This is an IAM-only correction and does not require a deployment.
- [ ] On the same Android Chrome session, without clearing Seewik site data, refresh the production app and retry Google connection. Confirm that My Civic Card loads, then refresh once more and confirm the connection persists.
- [ ] Test Civic Card image sharing through the Android share sheet to a private WhatsApp chat. Confirm that WhatsApp receives a readable image rather than only a web link, and that Seewik remains usable after returning to Chrome.
- [ ] Check all three reward cards on Android: each must say **Example local reward**, show the correct lifetime-points progress and remain clearly locked when the citizen is below its threshold.
- [ ] Record the phone model, Android version, Chrome version, result and any screenshot in the release evidence. Do not create unnecessary civic reports to reach a reward threshold.

## Routing simplicity review — owner follow-up

- [ ] Test the complete production routing experience properly from a citizen's perspective.
- [ ] Evaluate a simpler guided flow: take or upload a photograph → pre-fill a suggested problem → explicitly request and pre-fill a location/prabhag suggestion → show the deterministic civic route → generate the corresponding complaint.
- [ ] Verify the flow on a real Nandurbar case in English, Marathi and Hindi and on a narrow/mobile screen.
- [ ] Preserve the existing trust boundaries during any simplification: no silent location capture, no assumption that a photograph contains usable location, citizen confirmation of category and prabhag, manual overrides, deterministic Civic Pack routing and no invented complaint facts.

This is a later usability review, not a statement that the current application automatically extracts location from an uploaded photograph. Today, photograph classification and the separate **Suggest from my location** action remain explicit citizen actions.

## Preferred Initiative sample content — owner-approved direction

- **Community Yoga** — Every day, 6:30 AM · Shahada Road ground · organised by Aarav Pawar · 24 joining · 1.2 km · bring a mat and water bottle.
- **Birthday Meal Donation** — Sunday, 12:00 PM · Civil Hospital gate · organised by Neha Valvi · 11 joining · 2.4 km · needs 40 food packets and 6 volunteers.
- **Plantation Morning** — Saturday 22 August, 8:00 AM · 2 hours. Treat this as sample wording/layout; choose a real upcoming date before using it as an active Initiative.

## Initiative meeting-point redesign — owner follow-up

The current `Public meeting place` text field is not sufficient. Replace it with a structured meeting-point selection flow.

- [x] Let the organiser choose a meeting point inside Seewik using the approved movable/manual pin, with an explicit confirmation step.
- [x] Add the subsequently approved optional Google place/address search: India plus Nandurbar-bound results, minimal selected fields, no device location, and the local/manual pin retained as fallback.
- [x] Remove **Use my location for discovery** from the Initiative creation form. Creating an Initiative uses the organiser-confirmed meeting-point map/pin, not the organiser’s current device location.
- [x] Save validated latitude/longitude plus a citizen-confirmed display label; do not make a pasted map URL the source of truth.
- [x] Generate the participant-facing Google Maps universal link from the confirmed coordinates, for example `https://www.google.com/maps/search/?api=1&query=<latitude>,<longitude>`.
- [x] Show participants the confirmed label, a small pin/map affordance and an **Open in Google Maps** action for directions.
- [x] Keep location selection explicit: no silent capture, no background tracking and no inference from a photograph.
- [x] Preserve existing Initiatives as legacy text locations; do not rewrite old records without a versioned migration.
- [x] Validate coordinates and backend-owned meeting-point creation, keep raw organiser identity private and test malformed coordinate writes. Published meeting-point editing is not exposed.
- [x] Record the original lower-dependency choice as contract v0.1 and the later owner-approved Google search amendment as v0.2 without changing the deterministic stored meeting-point schema.
- [x] Verify English, Marathi and Hindi labels, keyboard-operated local pin, map fallback and participant link behavior locally.
- [x] Select a real Google result in the local desktop browser and confirm it fills the label, moves the pin and invalidates confirmation after adjustment.
- [x] Repeat Google-result selection once after production deployment; confirm localized label and pin population without publishing a production Initiative.
- [ ] Repeat Google-result selection on physical Android; confirm the exact Maps app/web preview and handoff.
- [x] Before deployment, remove the broad `*seewik*` HTTP referrer and retain only the four exact local/Firebase referrers on the Maps browser key.
- [x] Do not leave **Publish activity** silently disabled when coordinates are missing. The form now shows every unmet requirement, preserves completed fields and provides a manual coordinate fallback without requiring browser geolocation.

This is a functional Initiative improvement, not a Day 12 recognition requirement. Schedule it deliberately rather than restoring a free-text-only place field.

## Minor UI copy and label backlog — owner follow-up

- [x] Execute the owner-approved Day 14 Set 5 sequence: trust cleanup → controlled Google search → boundary/header reliability → auditor-language batch → honest empty states → persistent header 112 → optional polish only after green essential work.
- [x] On a filed report’s citizen-facing **Report details** screen, hide the internal **Route** identifier and **Pack** version. Preserve both values in the immutable record and administrative/debug evidence.
- [x] Collect and prioritise the owner’s 34-finding audit across seven pages; ignore its temporary “should fix” labels in favour of the approved Set 5 ordering recorded in `DAY14_CHECKLIST.md`.
- [x] Separate factual/safety corrections from preference-only polish so comprehension fixes can ship first.
- [x] Apply accepted copy changes consistently in English, Marathi and Hindi rather than patching only the visible English string.
- [x] Run accessibility, narrow-layout, navigation and regression checks after the batch.
- [x] Complete the batch before the Day 14 freeze unless an item is intentionally deferred.

The language default remains remembered/device-language selection with English fallback. A forced Marathi default, custom native-control replacements, Article 51A illustrations and major navigation redesign are deliberately deferred.
