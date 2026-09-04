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
