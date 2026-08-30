# Day 9 Build Log

Date: 2026-08-28

Scope in this entry: Sets 1, 2, 3 and 5

Production deployment: Sets 1–3 deployed; Set 4 paused; Set 5 does not change the citizen application

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

Sets 1–3 were subsequently verified, committed and deployed from green `main` at `3490c61c0b6fc7eba35fd610a6d0682ade87edad`. Set 5 is evaluation evidence and tooling only; it does not change the deployed citizen application. Set 4 remains paused by explicit direction.

## Set 5 — Track B private image evaluation

Set 4 remains deliberately paused. Set 5 was completed as a separate Track B evaluation and was not merged with the frozen 60-case Track A text set.

Intake and privacy controls:

- eight previously untested photographs were confirmed as originating in Nandurbar;
- previously viewed pilot candidates remained outside the scored set;
- the user confirmed permission from the family photographers for private Seewik evaluation and privacy-sanitized copies;
- expected labels were confirmed by three to four people, leaving zero unresolved cases;
- raw photographs, source filenames, original checksums, exact source paths, sanitized photographs and raw deployed-endpoint responses remain outside Git;
- all scored inputs use private IDs `TB-IMG-001` through `TB-IMG-008`;
- embedded metadata was removed from every sanitized copy;
- printed coordinate overlays were cropped from `TB-IMG-001` through `TB-IMG-004` without changing the civic evidence;
- no visible face, readable number plate, house number or address required further redaction;
- every sanitized file is a JPEG below the 5 MB ceiling, while the application contract continues to support JPEG, PNG and WebP.

The image list, sanitized-image SHA-256 values, expected labels, prompt `classification-prompt-v0.1`, response schema `classification-v0.1`, Civic Pack `v0.2`, model `gemini-3.7-flash`, policy `evaluation-policy-v0.1` and scoring `image-classification-scoring-v0.1` were frozen before the first scored call. The case-set SHA-256 is:

`6cd468ed84b2bbe28ae643c6b0f566568a39674d6088c670b63beb56dbd71d06`

Scored evaluation:

- two unchanged image-only runs, eight cases per run and 16 calls total;
- no text hint was sent and no failed request was retried;
- category accuracy on valid responses was 12/12 (100%);
- schema validity was 12/16 (75%);
- there were zero misclassifications and no category confusion pairs;
- the failure-inclusive exact category score was 12/16 (75%), but that aggregate is secondary because it combines category correctness with structural validity;
- four calls failed server-side schema validation: two in each run;
- the deployed endpoint returned only its public `SCHEMA_VALIDATION_FAILED` error envelope for those calls, so the rejected provider payload was not exposed to or available from this client-side run;
- the named finding is `IMAGE_PATH_SCHEMA_VALIDATION_INSTABILITY`: the provider call completed, then `ClassificationSchemaValidator` rejected the generated classification before an API success response was built;
- `TB-IMG-006` failed schema validation in both runs, `TB-IMG-002` failed only in run 1 and `TB-IMG-004` failed only in run 2—three affected images with one repeatable case and two run-variable cases;
- Cloud Run revision `seewik-api-00029-yah` independently recorded 12 classification successes and four schema failures during the evaluation window;
- the exact schema violation cannot be recovered retroactively because the deployed service records only an aggregate counter and returns only the public error envelope; it does not retain the rejected provider JSON or validator subcode;
- truncation is not supported by the available evidence: valid image responses used 60–102 candidate tokens against a 512-token output ceiling, but failed-call candidate counts were not retained, so truncation cannot be ruled out absolutely;
- timeout, model-call, transport and other HTTP failure counts were all zero;
- no call requested clarification;
- category and status stability were both 75%; clarification behavior was stable across all eight cases;
- run 1 latency was 3,878 ms minimum, 4,855.5 ms median, 6,025 ms p95 and 6,025 ms maximum;
- run 2 latency was 3,332 ms minimum, 5,845 ms median, 6,260 ms p95 and 6,260 ms maximum;
- across both runs, pothole/road-damage scored 4/4, drainage/sewage 5/6 and garbage/solid-waste 3/6. The reduced failure-inclusive scores are due to schema failures, not wrong-category outputs.

The comparison with frozen Track A is structural, not an accuracy comparison: Track A produced zero schema failures in 120 text-only calls, while Track B produced four in 16 image-only calls. These eight private, purpose-selected photographs remain a small evaluation set. Track B's value is the discovered image-path failure mode, not a broad image-accuracy estimate. Raw endpoint responses are preserved in the private evaluation archive, while the repository contains only sanitized case metadata and analytical results. The temporary evaluation account was deleted after both runs.

Set 5 verification passed: 19 frontend/integrity tests, the production frontend build, 178 backend tests, shell syntax, JSON parsing, repository-content policy and whitespace checks. The existing large initial-chunk warning remains assigned to paused Set 4.

### Diagnostic follow-up instrumentation

After the frozen two-run result was analyzed, the backend gained privacy-safe schema-failure diagnostics for future calls: validator subcode, generated-output character length, provider finish reason and candidate-token count. The public error envelope still excludes generated text, descriptions, image content and private identifiers. A specific operational counter is also recorded per validator subcode.

The diagnostic runner forces `DIAGNOSTIC_UNSCORED`, records `labelMatch` independently and sets every diagnostic call's `scored` field to false. Therefore, a later diagnostic pass cannot alter the frozen 12/12 category-correctness or 12/16 schema-validity findings above.

The diagnostic instrumentation was committed at `34f6f8d03820a19513be947d4ea337eb9eaf1ba6`, passed the protected Quality workflow and was deployed as Cloud Run revision `seewik-api-00032-gag`. One separately labelled eight-image diagnostic pass was then run against that revision with no silent retries:

- all eight rows were marked `DIAGNOSTIC_UNSCORED` and `scored: false`; the frozen scored findings above remain unchanged;
- six responses were schema-valid and all six matched their reviewed category, with zero misclassifications;
- `TB-IMG-006` reproduced as a failure for a third consecutive pass, but this call failed upstream as `MODEL_CALL_FAILED`; because no generated classification reached the validator, the schema-only diagnostic fields were correctly absent and the exact upstream cause remains unresolved;
- `TB-IMG-008` failed local validation with subcode `MALFORMED_JSON`; its provider finish reason was `MAX_TOKENS`, generated-output length was 209 characters and candidate-token count was 60;
- this is direct evidence of truncation for the `TB-IMG-008` diagnostic call despite the 512-token request ceiling: the provider stopped at `MAX_TOKENS` after reporting 60 candidate tokens, and the resulting partial JSON was rejected fail-closed;
- the other six calls completed normally, and the temporary diagnostic account was deleted after the run;
- sanitized diagnostic rows and the summary are committed, while the raw response envelopes remain only in the private evaluation archive.

The follow-up therefore diagnoses one concrete instance of the broader image-path instability—`IMAGE_PATH_MAX_TOKENS_MALFORMED_JSON` for `TB-IMG-008`—without retroactively assigning that cause to the four historical schema failures. It also exposes a narrower remaining observability gap: schema rejections are now diagnosable, while generic upstream model-call failures such as the diagnostic `TB-IMG-006` response still lack a privacy-safe provider error subcode.

## Final production verification and closure

Day 9 Set 7 was completed after the Day 10 identity boundary was deployed:

- production routes `/`, `/report/new`, `/reports`, `/points`, `/initiatives` and `/initiatives/new` returned HTTP 200;
- the production backend `/health` endpoint returned `status: ok`;
- English, Marathi and Hindi rendered at 390 × 844 with no horizontal overflow;
- the production report flow loaded all 20 approximate boundary outlines and retained the visible `official-map-digitized-v0.1` / `REVIEW_PENDING_GEOREFERENCE` warnings;
- manual Prabhag 7 selection synchronized the list and selected outline without changing the automatic resolver contract;
- the deterministic route action remained disabled until the category confirmation was also complete;
- the production browser console contained zero warnings or errors during the multilingual and boundary checks;
- privacy-safe production screenshots were preserved under `evidence/day10-production-{en,mr,hi}.jpg`.

Set 4 performance work remains carried to Day 11 after attendance UI work. Set 6 remains split between Day 11 self-attendance and the later hardened QR/geolocation design; neither is represented as completed Day 9 functionality.
