# Day 12 Build Log

Status: Day 12 is implemented, pushed and deployed on 2026-08-31. Physical Android Google/WhatsApp acceptance remains pending because a device is not yet available.

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
- The public recognition response exposed only status, month, names, message and schema fields; it contained no points, UIDs, emails or activity identifiers.
- Production owner-isolation passed with exact temporary fixtures: owner draft/points reads succeeded, cross-owner reads were denied, organiser and participant roles remained isolated, and unrelated Initiatives were excluded. Temporary records and users were deleted in mandatory cleanup.
- Deployed Firestore draft protection, Initiative protection and Google-write enforcement probes passed. Expected permission-denied results confirmed that direct technical writes remain blocked.

## Production release evidence

- Application commit: `efb78250d134527f22c8e91d42dca6ac99cce656`.
- Quality run `33444731488`: passed in 1 minute 18 seconds, including repository policy, whitespace, boundary integrity, 207 backend tests, 54 frontend tests, production build, dependency audit and high/critical scan.
- Deployment run `33444841237`: passed in 5 minutes 3 seconds. The candidate passed health before traffic, the verified frontend and Firestore/Storage rules were published, production routes passed and the temporary candidate tag was removed.
- Day 12 application revision `seewik-api-00068-til` was healthy and served the verified application commit. Attendance-secret rotation created healthy intermediate revision `seewik-api-00037-czd` and moved 100% of traffic to the rotated configuration.
- Security-remediation quality run `33448168389` passed in 1 minute 15 seconds, including the new secret-safe diagnostics gate and the complete existing quality suite.
- Security-remediation deployment run `33448272448` passed in 5 minutes 27 seconds. Its corrected traffic-only candidate lookup passed in the real deployment path, followed by candidate health, traffic, hosting/rules, route and tag-cleanup gates.
- Final live revision `seewik-api-00073-fav` serves 100% of traffic, is Ready, carries remediation commit `4e2d1441cb2b34e1a1b651da1b73b07414da398c` and image digest `sha256:37532de37d973f5840cc0cf294e1c1c86f86053813ae6a08b100faecebd88bb2`. An in-memory comparison confirmed it retained the rotated secret and differs from the retired exposed value; neither value was printed.
- Backend `/health` passed. Hosting routes `/`, `/report/new`, `/reports`, `/points`, `/initiatives`, `/initiatives/new`, `/awareness` and `/emergency` returned HTTP 200.
- The live web app is `https://seewik.web.app` and the backend is `https://seewik-api-528138216934.asia-south1.run.app`.

## Security finding and remediation

- Named finding: `attendance_code_secret_exposed_in_diagnostic_output_rotated`.
- Source: a raw Cloud Run revision JSON diagnostic printed runtime environment values into the private task-tool output. It did not originate from the deployment workflow.
- The owner approved immediate rotation. The replacement revision is healthy and active at 100%; there were no live Initiative codes to invalidate.
- The previous value was not found in the working tree, Git history, local shell history, Quality logs or deployment logs. Its remaining copies are the private task transcript and access-controlled retired Cloud Run revision configuration; it is inactive.
- The deployment workflow now requests traffic metadata only, a safe release-evidence script exposes allow-listed non-secret fields only, and the required Quality gate rejects raw Cloud Run JSON/YAML dumps and Secret Manager payload access in workflows/scripts.
- The finding is recorded beside the earlier OAuth diagnostic finding in `SECURITY_FINDINGS.md` so the repeated pattern remains visible.

## Current stop point

Day 12 implementation, release and non-device production verification are complete. WhatsApp poster sharing and Google sign-in still need a physical Android Chrome check when a device is available; that deferred hardware check does not weaken the tested browser fallback or privacy contract.
