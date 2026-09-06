# Seewik project handoff - start of Day 17

Date: 2026-09-03

## Read this first

The Day 16 application source is pushed to `main` at commit:

`9b14fe45b91a00dd77367190a44716f14aa34487`

It is **not yet deployed**. The required Quality workflow failed twice only because npm's official advisory endpoint timed out and then returned HTTP 503. The application tests and build passed on both attempts. Do not add a vulnerability suppression, weaken the workflow, or deploy around Quality.

Quality run:

[GitHub Actions run 33822034223](https://github.com/sakshipatil96/seewik/actions/runs/33822034223)

## Product summary

Seewik is a multilingual civic-action platform piloted for Nandurbar, Maharashtra. It helps citizens improve what is wrong, initiate what is good, and build a visible record of contribution.

The interface supports English, Marathi, and Hindi.

The four product pillars are:

1. **Improve** - report civic problems and find the verified official route.
2. **Initiate** - create or join community activities.
3. **My Civic Card** - retain a private civic contribution record, recognition, sharing, and clearly illustrative rewards.
4. **Civic Awareness and Emergency Information** - provide sourced civic information and verified emergency call actions.

## Core architecture

- Frontend: React and TypeScript PWA on Firebase Hosting.
- Backend: Java 21 and Spring Boot on Cloud Run in `asia-south1`.
- Identity: Firebase Authentication with anonymous-first use and Google account linking that preserves the Firebase UID.
- Application data: Firestore with owner-scoped security rules.
- Evidence media: protected Cloud Storage paths.
- Language and image understanding: Vertex AI Gemini with constrained classification and drafting contracts.
- Civic routing: deterministic, versioned Civic Pack data rather than model-selected authorities.
- Geographic suggestion: BigQuery `ST_COVERS` with a timeout, circuit breaker, and checksum-verified snapshot fallback.
- Analysis: privacy-safe BigQuery events that exclude complaint bodies, images, coordinates, and raw citizen identifiers.

The governing rule remains:

> Gemini understands the citizen; the verified Civic Pack decides who is responsible.

## What was already built before Day 16

### Reporting and lifecycle

- Photo or text civic issue intake.
- English, Marathi, Hindi, and mixed-language classification support.
- Eleven supported Civic Pack issue categories plus safe unknown/unsupported behavior.
- Deterministic authority, department, official channel, limitation, SLA, and escalation lookup.
- Marathi or English complaint drafting grounded in route facts.
- Citizen-controlled filing through email, official form, or printable letter; Seewik does not submit on the citizen's behalf.
- Draft saving and owner-scoped report history.
- Filed, overdue/unknown, claimed fixed, verified fixed, rejected repair, and recurrence/reopen lifecycle states.
- Frozen route facts after filing.
- Duplicate-evaluation and append-only points behavior.

### Initiatives and participation

- Initiative templates and custom activity creation.
- Public meeting-point label and validated coordinates.
- Local movable pin and optional restricted Google place search.
- Google Maps handoff for participants.
- Nearby discovery remains optional.
- Join approval, participation modes, capacity handling, cancellation, completion, attendance, and organiser controls.
- Organiser-code and self-attested attendance paths with backend-owned records.

### Identity, recognition, and rewards

- Anonymous-first identity with Google linking under the same UID.
- Owner-private profile synchronization.
- My Actions for reports and organised/joined initiatives.
- Lifetime civic points from versioned backend rules.
- Opt-in monthly top-three recognition with names-only public output.
- Device-local Civic Card image creation and WhatsApp/device sharing without public upload.
- Three `DEMO_ONLY` reward cards with permanent lifetime thresholds and simulated redemption states.
- No real merchant verification, payment, or point-of-sale integration is claimed.

### Awareness and emergency information

- Sourced Fundamental Duties and practical civic-action content.
- Sourced Nandurbar civic responsibility information.
- A separate signed-out Emergency Information page.
- Verified telephone actions including ERSS 112.
- Emergency information remains separate from reporting and does not request location.

### Delivery and security

- Protected `main` release flow through Quality and `Deploy green main` workflows.
- No-traffic Cloud Run candidate, health check, traffic switch, same-commit frontend build, Firebase Hosting/rules deployment, public route checks, and rollback behavior.
- Firestore and Storage emulator security tests.
- Google-linked identity requirements for durable mutations.
- Rate limits and timeouts around paid model endpoints.
- Secret-safe deployment diagnostics.
- Tomcat dependency updated to `10.1.59`; previous backend tests, dependency audit, and high-severity scan passed without suppression.

## Day 16 changes now on main

### Defect fixes

- Google name fallback now checks the linked `google.com` provider when Firebase's top-level display name is empty.
- Synced private profile data prefills the Home greeting, report details, Civic Card, and initiative organiser name.
- Sign-out clears cached profile state.
- Firebase authentication is configured to remain signed in across refreshes, closed tabs, and browser restarts unless the citizen signs out.
- `Anyone can join` initiatives no longer fail when capacity is intentionally empty.

### Responsive navigation

- Bottom navigation remains visible at every supported width, including the previous 904-1079 px gap.
- At 760 px and below, the header keeps compact language (`E`, `म`, `ह`), emergency phone, and profile controls on the Seewik row.
- Wider screens retain full controls without a duplicate circular profile icon.

### Reusable block picker

- New Report Issue category and New Initiative Activity type use a reusable block picker.
- Existing issue and initiative template data remains authoritative.
- Desktop uses a bounded floating panel.
- Mobile uses a bottom sheet with a subtle backdrop.
- The option area scrolls independently on both layouts.
- Search has one input label and a far-right clear control.
- Outside click/tap and Escape close the picker.
- Selecting an option applies it immediately and closes the picker.
- The redundant Done control was removed.
- Report cards retain their normal neutral/selected styling rather than inheriting the orange report action-button style.

### Simplified New Report flow

- The citizen adds a photo and/or short description.
- Gemini can prefill the editable Issue category.
- Seewik tries local JPEG EXIF GPS first.
- If usable photo GPS is absent, it can make one browser-location request.
- If neither source is available, the citizen selects the Prabhag manually.
- Manual changes override late automatic results.
- The compact Location field and Prabhag map remain visible.
- A prefilled or manually selected Prabhag remains highlighted.
- The separate Confirm category button, state, frontend gate, and backend drafting rejection were removed.
- Step 2 is `Location`.
- Step 3 is `Find the right route`.
- `Find official route` uses the category and location currently visible in the form.
- Gemini still does not choose an authority or route.

## Day 16 validation

- Backend: 222 passed, 0 failed, 0 errors, 0 skipped.
- Frontend: 79 passed, 0 failed.
- Frontend production build: passed with the existing non-blocking large-chunk warning.
- Firestore and Storage emulator rules: 3 passed, 0 failed locally.
- Repository content policy: passed in a clean source snapshot.
- Secret-safe diagnostics policy: passed.
- Frozen Prabhag checksum: passed.
- Diff whitespace check: passed.
- Local npm audit: advisory endpoint unavailable/time-out.
- GitHub Quality attempt 1: npm audit endpoint timed out; no vulnerability result was produced.
- GitHub Quality attempt 2: npm audit endpoint returned HTTP 503; no vulnerability result was produced.

Focused tests added:

- Valid north/east JPEG EXIF GPS parsing.
- Correct south/west coordinate signs.
- Safe handling of invalid and truncated JPEG data.
- Picker scroll and dismissal contract.
- Backend complaint drafting without the removed category-confirmation gate.

## Current release state

- Source push: complete.
- Application commit: `9b14fe4`.
- Quality: not green because the npm advisory service did not return a result.
- Deployment: not started successfully.
- Production: still serving the previous successful release.
- Build log: [DAY16_BUILD_LOG.md](DAY16_BUILD_LOG.md).

## Safe deployment retry

### GitHub website

1. Open [Quality run 33822034223](https://github.com/sakshipatil96/seewik/actions/runs/33822034223).
2. Check whether attempt 3 already exists or is running.
3. If the latest attempt is failed and the failure is still only npm HTTP 503/timeout, wait for npm to recover.
4. Choose **Re-run jobs** and then **Re-run failed jobs**.
5. Wait until every Quality step is green, including frontend audit, emulator rules, and high-severity dependency scan.
6. Open the repository's **Actions** page and find the new `Deploy green main` run triggered by the successful Quality completion.
7. Wait for candidate health, backend traffic switch, frontend/Firebase deployment, and public route checks to pass.
8. Open [seewik.web.app](https://seewik.web.app/) only after Deploy reports success.

### GitHub CLI

Run from the repository root:

```bash
gh run view 33822034223
gh run rerun 33822034223 --failed
gh run watch 33822034223 --exit-status
gh run list --workflow "Deploy green main" --limit 5
gh run watch <DEPLOY_RUN_ID> --exit-status
```

Do not run a direct `gcloud run deploy` or `firebase deploy` for this release. That would bypass the tested-commit chain and split backend/frontend release evidence.

## Post-deployment acceptance checks

- Sign in with Google, refresh, close the tab/browser, reopen, and confirm the session remains until explicit sign-out.
- Confirm the Google name appears in Home, report details, Civic Card, and initiative organiser name.
- Publish an `Anyone can join` initiative with no capacity and verify no 503 occurs.
- Check full and compact header controls.
- Check bottom navigation around 904-1079 px.
- Open both block pickers on desktop and mobile; verify scrolling, search clear, immediate close on selection, outside dismissal, and neutral report card colors.
- Test New Report with a GPS photo, a photo without GPS, denied browser location, and manual Prabhag override.
- Verify `Find official route` uses the visible category and Prabhag and still returns only Civic Pack route facts.
- Repeat the report flow in English, Marathi, and Hindi.

## Known honest limitations

- Active automatic Prabhag resolution uses synthetic development geography, not official published GIS geometry.
- The traced official-map polygons remain an orientation aid pending georeference review.
- Citizens can and should edit the suggested Prabhag.
- Nandurbar internal department allocations remain review-pending where official desk-level information is unavailable.
- Photo GPS is often absent after screenshots, editing, messaging, or privacy stripping.
- Rewards remain examples with no live merchant verification or partnership claim.
- Independent Marathi and Hindi language review remains desirable.
- The existing frontend bundle-size warning remains non-blocking technical debt.

## Files that must remain separate

The local untracked files below were not included in commit `9b14fe4`:

- `SEEWIK_PROJECT_HANDOFF_START_DAY12.md`
- `SEEWIK_PROJECT_HANDOFF_START_DAY15.md`

Do not stage, overwrite, or delete them without explicit owner instruction.
