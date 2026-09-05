# Day 16 build log - responsive reporting and initiative refinement

Date: 2026-09-03

## Purpose

Day 16 turns the tested civic flows into a cleaner production release. The work fixes the two Android and Safari defects, preserves sign-in across browser restarts, makes navigation available at every supported width, and reduces the New Report and New Initiative forms without weakening Seewik's civic-truth boundaries.

## Product changes

### Identity and initiative reliability

- Google-linked anonymous accounts now fall back to the `google.com` provider name when Firebase's top-level display name is empty.
- The synchronized private profile supplies the Home greeting, report details, Civic Card, and initiative organiser name.
- Sign-out clears cached profile state, while ordinary refreshes and browser restarts preserve Firebase authentication.
- Initiatives with `Anyone can join` can be published with an empty capacity without a null-rejecting record copy aborting the transaction.

### Responsive navigation

- The bottom navigation remains available at every viewport width.
- At widths of 760 px and below, language, emergency, and profile controls become compact icon-led controls in the header.
- Wider layouts retain the full language and text controls without duplicate profile icons.

### Compact block pickers

- New Report Issue category and New Initiative Activity type now use the same reusable block-picker component.
- Existing issue and initiative templates remain authoritative; the component only changes their presentation.
- Desktop uses a bounded floating panel, while mobile uses a scrollable bottom sheet with a subtle backdrop.
- Search, a far-right clear control, selected-card treatment, outside-click dismissal, and Escape dismissal are supported.
- Selecting a card applies it immediately and closes the picker; no redundant Done control remains.

### New Report flow

- A photo and/or description can prefill the editable issue category.
- Location follows a privacy-preserving priority: JPEG EXIF coordinates first, then a one-time browser location request, then manual Prabhag selection.
- The compact Location field and Prabhag map remain visible, and the current Prabhag is highlighted whether suggested or manually selected.
- The separate category-confirmation state, button, frontend gate, and backend drafting rejection were removed.
- The final action is now `Find official route`, which uses the category and location currently visible to the citizen.
- Step headings are now `2 Location` and `3 Find the right route`.

## Civic-truth and privacy boundaries

- Gemini may suggest an issue category, but it does not choose the responsible authority.
- The deterministic router and versioned Civic Pack remain the only sources of authority, department, official channels, and escalation facts.
- Photo-location extraction happens locally in the browser; Seewik does not upload a photo merely to read its metadata.
- Browser location is requested only when usable photo coordinates are unavailable.
- Every suggested category and Prabhag remains editable before routing.
- The Prabhag map remains an orientation aid and does not claim to be official GIS geometry.

## Test coverage added

- JPEG EXIF GPS parsing for north/east coordinates.
- Correct negative signs for south/west coordinates.
- Safe rejection of invalid or truncated JPEG data.
- Picker immediate-selection, scrolling, backdrop, outside-click, Escape, and no-Done behavior.
- Backend complaint drafting now explicitly covers the absence of a separate category-confirmation gate.

## Local validation

- Backend: 222 tests passed, 0 failures, 0 errors, 0 skipped.
- Frontend: 79 tests passed, 0 failures.
- Frontend production build: passed. Vite retained its existing non-blocking large-chunk warning.
- Firestore and Storage emulator rules: 3 tests passed, 0 failures.
- Repository content policy: passed in a clean source snapshot excluding only the two unrelated untracked handoff files.
- Secret-safe diagnostics policy: passed.
- Frozen Prabhag checksum: passed.
- Diff whitespace check: passed.
- Local `npm audit --audit-level=high`: attempted twice, but the npm advisory endpoint timed out from the local execution environment. No suppression or bypass was added. The required GitHub Quality audit remains the release-authoritative dependency result and must pass before deployment can start.

## Release evidence

- Application commit: `9b14fe45b91a00dd77367190a44716f14aa34487`
- Push: successful to `origin/main` (`77bd551..9b14fe4`)
- Quality workflow: [run 33822034223](https://github.com/sakshipatil96/seewik/actions/runs/33822034223)
- Quality attempt 1: application checks passed through 222 backend tests, 79 frontend tests, and the production frontend build; npm's advisory endpoint timed out after five minutes, so the job failed before the emulator and high-severity scan steps.
- Quality attempt 2: application checks passed again; npm's advisory endpoint returned HTTP 503 after five minutes, so the job failed before the emulator and high-severity scan steps.
- Quality attempt 3: retry submission/status could not be confirmed because the GitHub API connection stalled. Check the linked run before submitting another retry.
- Deployment workflow: not started successfully because Quality has not completed successfully.
- Production revision: unchanged from the previous successful release.
- Production image digest: unchanged from the previous successful release.

No vulnerability suppression, audit bypass, direct deployment, or weakened workflow was added. The Day 16 source is pushed but must not be described as deployed until the existing Quality run and its automatic `Deploy green main` successor both complete successfully.

## Files intentionally excluded

The local Day 12 and Day 15 handoff documents remain untracked and are not part of this release.

## Set 1 official-filing release addendum - September 4, 2026

### Citizen flow delivered

- Simplified New Report into editable issue and location steps followed by `Find the right route`.
- Added compact, accessible block pickers for Issue category and Activity type, including desktop floating panels and mobile bottom sheets.
- Added the Civic Responsibility Router result with a clear responsible-authority card.
- Added three explicit filing routes: printable letter, Nagar Palika email, and the official DMA complaint form filing pack.
- Kept every generated subject, body, and citizen detail editable before the citizen takes action.
- Added language-specific drafting for English and Marathi, with the interface language as the default and a manual filing-language override.
- Added dedicated letter-only printing and PDF output instead of printing the complete web page.
- Added structured formal letter, concise email, and DMA grievance drafting contracts without inventing facts.
- Added editable Google-profile-prefilled name and email fields, correspondence details, and Nandurbar's default pincode `425412` for the DMA copy pack.
- Added clear evidence guidance: Seewik discloses when a photo is available but does not claim that a browser email link attached it automatically.
- Added an explicit real-world filing confirmation panel while preserving the existing Review screen as compatibility mode.
- Preserved account-link, duplicate-report, lifecycle, Firestore-save, and points protections.

### Quality and release preparation

- Added backend tests for PRINT, EMAIL, and DMA prompt contracts and compatibility defaults.
- Updated frontend contract tests for the approved filing controls and persistent responsive navigation.
- Restored the reusable Google-link gate around generated-draft persistence after the full suite detected an unguarded save attempt.
- Browser QA covered the three filing panels, dedicated letter print output, desktop/mobile filing-route layouts, and the real-world confirmation panel.
- The unrelated local Day 12, Day 15, and Day 17 handoff documents remain excluded from the release.

## Set 2 follow-up and escalation - September 5, 2026

### Contract and trust boundaries

- Added `report-follow-up-v0.1`, a backend-owned append-only follow-up stream under each private report.
- The backend calculates the initial seven-day wait from backend-owned `filedAt` and calculates unsure reminders from server time; client dates do not control eligibility.
- Rejected repair claims resume the same cycle immediately. Reopening after a verified fix starts a new server-anchored recurrence cycle.
- Internal cycle numbers remain private. A recurrence draft includes only the supported fact that the issue recurred after a previously verified resolution.
- Follow-up and escalation events always award zero points and do not weaken existing claimed-fix, verified-fix, reopen, duplicate or one-time points rules.

### Citizen experience

- Added follow-up-due and escalation-ready indicators to My Actions.
- Added the seven-day resolution question to Report Details with resolved, unresolved and unsure choices.
- Added three recommended escalation routes: Nagar Palika follow-up, District Joint Commissioner Office, and DMA Desk 6.
- Added editable English and Marathi email preparation, with English UI defaulting to English and Marathi/Hindi UI defaulting to Marathi.
- Added copy, email-app opening and explicit sent confirmation. Opening or copying never records an email as sent.
- Bound escalation preparation to the filed route ID, Civic Pack version and route snapshot hash; stale drafts are blocked.
- Added complete English, Marathi and Hindi interface copy and responsive phone layouts.

### Source verification

- District Joint Commissioner Office, Nandurbar email and phone were rechecked on the official DMA regional contacts page on September 5, 2026. The phone remains intentionally excluded from the UI.
- DMA Desk 6 email and municipal-complaint remit were rechecked on the official DMA desk structure page on September 5, 2026.

### Tests prepared

- Added backend tests for server timing, unsure reminders, unresolved zero-point events, rejected claims and verified-fix recurrence cycles.
- Added frontend contract tests for timing ownership, all three channels, route-snapshot binding, explicit sent confirmation and Marathi/Hindi coverage.
- Extended emulator-backed Firestore tests for owner-only reads and direct-write denial on `followUpEvents`.
- Frontend tests: 82 passed, 0 failed.
- Frontend production build: passed. The existing non-blocking large-chunk warning remains.
- Focused backend follow-up tests: 5 passed, 0 failed.
- Firestore emulator rules tests: 3 passed, 0 failed.
- Full backend suite passed 233/233 tests with zero failures and zero runtime errors. Mockito now uses an explicit Surefire Java agent instead of relying on dynamic self-attachment.

### Final Set 2 completion evidence

- Added a production-inaccessible `local-e2e` profile with Firebase emulators and an adjustable server clock. It refuses Cloud Run activation and requires emulator hosts plus a `demo-` project ID.
- Verified that the seven-day prompt is absent before its due date and appears after the server clock reaches day eight. `filedAt` remains server-generated.
- Verified all three escalation routes: Nagar Palika follow-up, District Joint Commissioner Office, and DMA Desk 6.
- Fixed frontend lifecycle-state drift by carrying the backend-returned immutable `routeSnapshotHash` into report state. Genuine route ID, Civic Pack version, and snapshot-hash mismatches still invalidate drafts.
- Aligned the frontend lifecycle response type with the backend wire response.
- Added refresh-safe, report-scoped session persistence for editable filing contact fields. A matching filing-action receipt survives refresh and resume; editing filing content still invalidates it.
- Verified automatic draft language behavior: English UI defaults to English; Marathi and Hindi UI default to Marathi when no manual choice exists.
- Verified manual draft-language choices are not overwritten by interface-language changes, and per-language caching preserves citizen edits when switching away and back.
- Completed the Marathi and Hindi escalation-control translation audit, including `Open email app`.
- Verified Copy/Open is separate from `I sent this follow-up`; sent confirmation remains disabled until an action occurs and awards zero points.
- Final frontend result: 92/92 tests passed. Production build passed; the existing bundle-size advisory remains non-blocking.
- Production spot check: `too many mosquitos in Lokmanya colony` correctly suggested `MOSQUITO_FOGGING`. Local automatic classification is unavailable in this isolated session because the Mac has no Google Cloud ADC or CLI credentials; the safe manual-category fallback remains available.
- No code was pushed or deployed during this completion pass.

## Set 2 final release gate - 5 September 2026

- Escalation route choices use a stable Seewik navy gradient with readable white text in both light and dark themes.
- The three escalation routes are presented as an unnumbered single-column stack at every screen width.
- Desktop behavior was checked in the running local app after the final styling change.
- Backend suite: 233 tests passed, 0 failures, 0 errors.
- Frontend suite: 92 tests passed, 0 failures.
- Firebase emulator security rules: 3 tests passed, 0 failures.
- Production frontend build completed successfully.
- The existing large-bundle advisory remains non-blocking: JavaScript 1,240.85 kB, gzip 336.82 kB.
