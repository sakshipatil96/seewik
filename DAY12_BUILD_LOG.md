# Day 12 Build Log

Status: Day 12 is feature-complete and locally release-verified on 2026-08-31. Physical Android Google/WhatsApp acceptance and the separately approved production push/deployment remain pending.

## Completed scope

- Froze `data/contracts/day12-recognition-privacy-content-contract-v0.1.md` before implementation.
- Added backend-owned `citizen-profile-v0.2` migration under the existing Firebase UID. The private Google name and email are used for account recovery/profile display and public-name prefill; the Google photograph is not stored.
- Added separate backend-owned recognition consent, append-only consent events, normalized-name collision monitoring and privacy-safe displayed-name reports.
- Added deterministic monthly selection using `Asia/Kolkata`, exact active 5/20/40/60 awards, duplicate suppression, demo/invalid/test-owner exclusion, opt-in-only candidates, points-first order and alphabetical equal-points order.
- Added idempotent internal month snapshots. Public responses contain ordered names and public explanatory fields only.
- Added the public **Thanks to Our Top Three Citizens of the Month** panel with equal name cards, no visible ranks or points, and honest zero/one/two-person states.
- Added owner-private lifetime/current-month points with contribution breakdown, editable public-name preview, explicit opt-in and withdrawal without changing ledger history.
- Updated Firestore rules so clients cannot write profiles, consent, consent events, collision events, recognition snapshots or abuse reports.
- Added an explicit, client-side Civic Card image generator. It always uses a citizen-chosen display name, includes only high-level contribution categories and totals, creates no public URL, and offers platform sharing with a download fallback.
- Added the approved Civic Awareness / Did You Know topic set: all eleven Article 51A duties with 51A(g), (h), (i) and (j) highlighted; municipal complaint follow-up; official recognition programmes; Nandurbar Who’s Who; and Nagar Parishad responsibilities.
- Connected Article 51A(g) to reporting waste/drainage and to starting a clean-up or plantation. Connected Article 51A(i) to the relevant public-property issue routes.
- Added a separate signed-out Emergency Information page with national 112, locally verified Nandurbar contacts, direct call actions, a non-dispatch disclaimer and stale-source disabling.
- Added a versioned sourced-content schema, official-host validation, last-reviewed/expiry metadata and runtime cache fallback for offline viewing after the app has been loaded online.
- Renamed the citizen work area from **My Reports** to **My Actions** in navigation and on the page while preserving the sentence “Filed reports open as immutable records.”
- Split **My Actions** into **My Reports** and **My Initiatives**. Initiative cards retain organising/joined roles and attendance controls, with completed items shown in green.
- Simplified **Initiate** to two clear choices: create an Initiative or join an Initiative. Personal Initiative history and controls now live only under My Actions.
- Completed English, Marathi and Hindi coverage for recognition, consent, privacy, private points, Civic Card sharing, Civic Awareness and Emergency Information. Month labels are localized on the client while month boundaries remain backend-owned in `Asia/Kolkata`.
- Recorded the owner’s approval of the safety-critical Marathi and Hindi emergency wording on 2026-08-31.

## API surface

- `POST /api/profile/sync` and `GET /api/profile` — Google-linked owner only.
- `GET /api/recognition/current` — public names-only panel.
- `GET /api/recognition/me/points` — authenticated owner-private summary.
- `GET|PUT /api/recognition/me/settings` — Google-linked owner settings.
- `POST /api/recognition/reports` — Google-linked displayed-name concern.

## Verification

- Backend: 207 tests, 0 failures and 0 errors. The local Homebrew JDK required loading the installed Mockito agent explicitly; the first unassisted run failed only at agent attachment, and the established agent-assisted rerun passed without changing repository or application configuration.
- Frontend: 54 tests passed, including Day 12 language coverage, source contracts, privacy boundaries, recognition states, poster generation/sharing fallbacks, Initiative/My Actions information architecture and copy-safety coverage.
- Frontend production build passed. Vite reported only its non-blocking large-chunk advisory.
- `npm audit --audit-level=high`: 0 vulnerabilities.
- Repository content policy, boundary checksum and `git diff --check` passed.
- Local browser checks passed for English, Marathi and Hindi Emergency Information; Hindi and Marathi Civic Awareness; Hindi home recognition; Hindi private Civic Card/points; Initiate; and My Actions.
- The owner human-approved the final Marathi and Hindi emergency wording on 2026-08-31.
- No production data was created or changed. No commit, push or deployment was performed.

## Current stop point

The Day 12 implementation and local verification are complete. WhatsApp poster sharing and Google sign-in still need a physical Android Chrome check when a device is available; that deferred hardware check does not weaken the tested browser fallback or privacy contract.

Production owner-isolation acceptance, commit/push, deployment, health/rules verification, Git SHA and Cloud Run revision evidence remain intentionally unrecorded until the owner explicitly approves production release actions.
