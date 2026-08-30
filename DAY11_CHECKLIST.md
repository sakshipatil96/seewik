# Day 11 Checklist — self-attested and organiser-code Initiative attendance

Day 11 first adds a deliberately limited self-attendance report after recoverable Google-linked profiles are live. It then adds an organiser-controlled rotating six-digit attendance code without QR scanning or geolocation. Self-attendance awards zero points; organiser-code attendance awards 20 civic points and completed organising awards 40 civic points. These awards contribute to the non-deducting 100/150/200 example-coupon tiers planned in `DAY13_CHECKLIST.md`.

Core self-attendance-path target: approximately 45 minutes. The code path, security, regression, production verification and evidence time are additional.

## Set 1 — Freeze the self-attendance contract

- [x] Freeze the confirmed late-join default: allow joining through the valid three-hour organiser-code window, including after an early completion.
- [x] Freeze the confirmed display default: prioritise code attendance during its window and reveal zero-point self-attendance after it closes.
- [x] Freeze `data/contracts/day11-attendance-reward-contract-v0.1.md` with the corrected 5/20/40/60 values and version identifiers.
- [x] Require a Google-linked citizen who owns an Initiative `PARTICIPANT` record; allow an older anonymous participation after its unchanged UID is successfully linked.
- [x] Keep membership status and attendance status separate.
- [x] Permit attendance reporting only after the organiser has changed the Initiative to `COMPLETED`.
- [x] Permit self-attendance from `completedAt` through `completedAt + 7 days`, enforced using server time.
- [x] Add `attendanceStatus: I_ATTENDED`, `attendanceBasis: SELF_ATTESTED` and `attendanceReportedAt` to the citizen's participation record.
- [x] Record an append-only `INITIATIVE_ATTENDANCE_SELF_ATTESTED` event with a hashed actor UID.
- [x] Make the operation idempotent so repeated taps cannot create duplicate events or counts.
- [x] Allow only the participation owner to report their own attendance.
- [x] Do not allow organisers to mark other citizens attended in bulk.
- [x] Reject attendance reports for non-participants and for every Initiative state other than `COMPLETED`.
- [x] Award zero points and keep the ledger policy `RECORDED_NOT_REWARDED`.
- [x] Never label self-attendance as verified, confirmed by Seewik or reward-eligible.
- [x] Count joiners using `PARTICIPANT` records; do not treat the organiser's automatic membership as a joined citizen.
- [x] Freeze the display wording: “3 of 8 joiners reported attending,” using the actual derived counts.

## Set 2 — Implement the participant flow

- [x] Add an authenticated, owner-scoped attendance endpoint.
- [x] Update the participation and append-only Initiative event atomically.
- [x] Derive the reported-attendance numerator and joined-participant denominator from participation records.
- [x] Add an “I attended” control to completed joined activities under `My activities`.
- [x] Hide or disable the control before completion and explain why.
- [x] Replace the control with an honest self-reported status after success.
- [x] Show “reported attending,” never “verified attendance.”
- [x] Localize the flow in English, Marathi and Hindi.
- [x] Preserve keyboard access, status announcements and narrow-screen layout.
- [x] Keep QR scanning, geofence checks, photo uploads and non-zero rewards out of this self-attested version.

## Set 3 — Attendance integrity and production checks

- [x] Test participant-only authorization and Google-linked identity enforcement.
- [x] Test that the organiser cannot report attendance for another UID.
- [x] Test pre-completion, cancelled, non-participant and duplicate requests.
- [x] Test the append-only event and zero-point ledger behavior.
- [x] Test the denominator excludes the organiser and does not double-count repeated joins.
- [x] Test the exact “reported attending” wording and prohibit “verified” claims.
- [x] Verify Firestore rules prevent direct client forgery locally; repeat against deployed production rules before closeout.
- [x] Run report, points and Initiative regression suites.
- [x] Verify the production datastore flow with four separate Google-linked organiser/participant test accounts and combine it with controller identity tests and deployed rules checks.
- [x] Preserve privacy-safe evidence with no raw location or civic text.

## Set 4 — Organiser-code attendance and civic points

This is an organiser-mediated attendance basis, not independent proof of physical presence. It uses no QR scanner and requests no participant geolocation.

- [x] Generate the active six-digit code on the server; do not derive it from a client-held secret.
- [x] Rotate the code every 10 minutes and bind it to one Initiative and time slot.
- [x] Accept organiser codes exactly from `startAt` through `startAt + 3 hours`, enforced using server time.
- [x] Show the current code only to the Google-linked organiser on the owned Initiative screen.
- [x] Accept a code only from a Google-linked citizen who owns a `PARTICIPANT` record for that Initiative.
- [x] Add `attendanceStatus: I_ATTENDED` and `attendanceBasis: ORGANISER_CODE_ATTESTED` without calling it verified attendance.
- [x] Record one append-only `INITIATIVE_ATTENDANCE_ORGANISER_CODE_ATTESTED` event per participant.
- [x] Award exactly 20 points once per participant and Initiative for organiser-code attendance.
- [x] Award exactly 40 points once to the organiser only after the Initiative is `COMPLETED` and at least two distinct `PARTICIPANT` records have `ORGANISER_CODE_ATTESTED` attendance; award when the second condition becomes true regardless of event order.
- [x] Keep self-attendance available as the zero-point fallback and never upgrade it silently to code attendance.
- [x] Limit incorrect entries to five attempts per participant per 10-minute slot.
- [x] Add a two-minute rotation-boundary grace period without extending the overall event window.
- [x] Never store or log the entered plaintext code.
- [x] Make attendance events, participant points and organiser points idempotent and append-only.
- [x] Forbid cancellation after the first code-attested attendance record and test that boundary directly.
- [x] Localize organiser display, participant entry, expiry, rate-limit and success states in English, Marathi and Hindi.
- [x] Verify keyboard/numeric-input usability and narrow-screen layout.
- [x] Test wrong, expired, cross-Initiative, isolated per-user attempt budgets, duplicate and post-window code submissions.
- [x] Verify Firestore clients cannot forge the attendance basis or either points entry locally; repeat against deployed production rules before closeout.
- [x] Production-test with separate organiser and participant accounts and remove every temporary record.

## Remaining human device verification

- [ ] Verify the Google link/sign-in popup and redirect return on a physical Android Chrome device; this requires the user's phone and is the only remaining Day 11 device check.

## Deferred performance work

- [ ] Preserve the approximately 807.28 kB minified / 239.78 kB gzip pre-Day-9 baseline and the current approximately 902 kB minified / 262 kB gzip measurement in the comparison record.
- [ ] Measure production transfer and Core Web Vitals under comparable desktop/mobile conditions.
- [ ] Keep route-level lazy loading and feature splitting deferred until after the checkpoint unless measurement identifies a release-blocking problem.
- [ ] Preserve refresh-safe routes, browser history and the report-only boundary load when performance work resumes.

## Optional later hardened attendance phase

- [ ] Reconsider QR or consent-based proximity only if the organiser-code evidence is insufficient for a later product requirement.
- [ ] Treat code sharing and multiple Google accounts as known limitations before attaching externally redeemable value to attendance points.
