# Day 17 Build Log - 8-9 September 2026

## Day objective

Day 17 was a closure day before the September 9 checkpoint. Work stayed on known report-flow defects, missing evaluation and business-case deliverables, architecture communication, and rehearsal findings. No new civic workflow was added.

## Frozen report provenance

- Added `boundaryDatasetVersion` to report drafts and frozen route snapshots.
- Freeze validation now requires the boundary version, and `routeSnapshotHash` includes it with the canonical English authority and route data.
- Updated Firestore rules so legitimate draft saves containing the new provenance field are accepted while protected report fields remain immutable after filing.
- Kept legacy reports readable and allowed old drafts to gain the new provenance only through the permitted draft update path.

## Critical report and filing corrections

- Moved the durable-account gate to the real-world filing confirmation boundary so a guest can describe an issue, obtain a verified route, and prepare complaint wording before Google sign-in is required.
- Preserved the guest complaint preview across the account-linking handoff with owner, route, method, language, and format version checks.
- Added a 25-second complaint-draft deadline, deterministic editable fallback wording, and a non-blocking shorter retry that never overwrites citizen edits.
- Corrected the service worker cache path so responses are cloned before consumption, the full cache-write chain remains inside `event.waitUntil()`, and old workers are invalidated by a cache-version change.
- Switched the default email handoff to synchronous mobile-safe `mailto:` navigation and added the existing 1,900-character copy fallback to avoid silent long-link failures.
- Centralized plain-text clipboard handling across modern and fallback paths. A follow-up correction prevents iOS Notes from interpreting DMA complaint content as a URL before it is pasted into the government form.
- Localized responsible-authority and department display names without changing the canonical English values stored in reports or included in route hashes.
- Replaced the citizen-visible resumed-draft Firestore identifier with the safe message `Draft resumed.`
- Added the explicit device-location recovery message while preserving Google address search and manual Prabhag selection.

## Guided autofill usability correction

- Added independent category and location states for waiting, active analysis, slow work, success, failure, timeout, and manual selection.
- Photo uploads immediately explain that category and location suggestions are being prepared; description-only classification retains the existing 650 ms typing pause.
- Only the field being populated automatically is temporarily locked. Photo and description editing remain available.
- After five seconds, the citizen can choose manually. Manual choices invalidate pending requests so late automatic results cannot overwrite them.
- Added a 25-second ceiling with automatic return to manual controls, accessible live-region announcements, reduced-motion behavior, and English, Marathi, and Hindi copy.
- Distinguished a prior device-location refusal from a new photo signal so the application does not repeatedly prompt after refusal or leave a new location attempt waiting without a valid resolver path.

## Four-pillar translation triage

- Audited citizen-visible literal translation calls across Initiate/reporting, Inspire/community activities, Information/emergency and civic-awareness content, and Impact/My Actions and Civic Card.
- Added Marathi and Hindi copy for 23 high-visibility gaps, including filing identity fields, photo and PDF actions, community meeting-point map states, account privacy explanations, and Civic Card points privacy.
- Information screens already had complete literal-key coverage, so sourced emergency and awareness content was left unchanged.
- This was the scoped Day 17 triage rather than an attempt to translate every low-visibility string in the application.

## Survey baseline

- Reconfirmed the frozen Q1 answer-key SHA-256 checksum: `548ab65f2ef49ee8310e5a86af77d3d47c06bc5aa9730fdc8dad0be1a3eb662a`.
- Preserved the raw workbook and CSV outside Git and created sanitized respondent, answer, scoring, and Civic Pack defect datasets without names or email columns.
- Confirmed 52 complete respondents, 520 scenario answers, no duplicate respondent/scenario pairs, and zero exclusions or test entries.
- Mechanically scored Q1 authority identification at 183/520, or 35.2%.
- Froze the post-collection, pre-scoring Q2 filing-channel rubric and scored verified-channel identification at 178/520, or 34.2%. Q2 is disclosed as not pre-registered.
- Recorded 203/520 `don't know` answers, or 39.0%.
- Separated eight possible Civic Pack defect signals for review without allowing them to influence scoring.
- Loaded four sanitized audit tables into the existing `seewik.seewik_civic` BigQuery dataset and verified the row-level Q1/Q2 totals against the frozen report.

## Cost per request

- Used exported Cloud Billing and Cloud Logging data for 18 August through 7 September rather than estimating from public price lists.
- Measured gross report-path cost at approximately $0.0169 per report started and projected approximately $8.43 per month for 500 active citizens filing one report each.
- Estimated a complaint-draft request at approximately $0.0123 gross using the recorded model-backed request count, with the allocation limitation stated explicitly.
- Recorded $0.00 billed Places Autocomplete cost for 169 session-usage units during the measured period.
- Preserved gross and post-credit figures separately because temporary credits reduced the displayed net bill to $0.00.

## Architecture artifact

- Completed the runtime architecture diagram covering Citizen, React/Firebase, Cloud Run, Gemini, Civic Pack, BigQuery, and Firestore.
- Made the AI/deterministic boundary explicit, placed `ST_COVERS` on the request path, and showed the 1.5-second boundary timeout with snapshot fallback.
- Preserved interactive HTML and editable JSON source with image and video export options for the submission and demo.

## Demo rehearsal findings

- The first mobile rehearsal surfaced five user-facing issues: iPhone Safari redirect authentication state, untranslated authority details, mobile email/Gmail handoff behavior, an internal resumed-draft identifier, and DMA clipboard encoding behavior.
- Default email handoff now works on iPhone Chrome, iPhone Safari, Mac Chrome, and Mac Safari.
- Plain-text DMA copying was confirmed on the same device/browser set after preventing URL recognition.
- Authority and department localization and the safe resumed-draft message were confirmed on production.
- Gmail's mobile website still does not reliably preserve compose parameters; the working default-mail action and explicit copy paths remain the reliable mobile choices.

## Validation

- Frontend test suite: 126 passed, 0 failed.
- Production TypeScript and Vite build completed successfully.
- The existing large JavaScript bundle warning remains a known non-blocking optimization item and is not part of the checkpoint scope.
- Guided autofill was exercised locally for multilingual visible states and location outcomes. Final category success, manual-override, stale-request, and timeout checks remain part of the production release walkthrough.

## Release boundary

- Application changes, tests, this Day 17 log, the pending Day 16 verification entry, and the Day 17 cost report are intended for the next commit.
- Local project handoff documents are operational references only and must remain untracked and excluded from every commit.
- Deployment follows the green pushed `main` workflow; production checks must cover the frozen report path in English, Marathi, and Hindi without submitting a false government complaint.
