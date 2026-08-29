# Day 11 Checklist — self-attested Initiative attendance and performance

Day 11 adds a deliberately limited attendance report after recoverable Google-linked profiles are live. It is self-attestation, not independent verification, and awards zero points.

Core attendance-path target: approximately 45 minutes. Security, regression, production verification and evidence time are additional.

## Set 1 — Freeze the self-attendance contract

- [ ] Require a Google-linked citizen who owns an Initiative `PARTICIPANT` record; allow an older anonymous participation after its unchanged UID is successfully linked.
- [ ] Keep membership status and attendance status separate.
- [ ] Permit attendance reporting only after the organiser has changed the Initiative to `COMPLETED`.
- [ ] Add `attendanceStatus: I_ATTENDED`, `attendanceBasis: SELF_ATTESTED` and `attendanceReportedAt` to the citizen's participation record.
- [ ] Record an append-only `INITIATIVE_ATTENDANCE_SELF_ATTESTED` event with a hashed actor UID.
- [ ] Make the operation idempotent so repeated taps cannot create duplicate events or counts.
- [ ] Allow only the participation owner to report their own attendance.
- [ ] Do not allow organisers to mark other citizens attended in bulk.
- [ ] Reject attendance reports for non-participants and for every Initiative state other than `COMPLETED`.
- [ ] Award zero points and keep the ledger policy `RECORDED_NOT_REWARDED`.
- [ ] Never label self-attendance as verified, confirmed by Seewik or reward-eligible.
- [ ] Count joiners using `PARTICIPANT` records; do not treat the organiser's automatic membership as a joined citizen.
- [ ] Freeze the display wording: “3 of 8 joiners reported attending,” using the actual derived counts.

## Set 2 — Implement the participant flow

- [ ] Add an authenticated, owner-scoped attendance endpoint.
- [ ] Update the participation and append-only Initiative event atomically.
- [ ] Derive the reported-attendance numerator and joined-participant denominator from participation records.
- [ ] Add an “I attended” control to completed joined activities under `My activities`.
- [ ] Hide or disable the control before completion and explain why.
- [ ] Replace the control with an honest self-reported status after success.
- [ ] Show “reported attending,” never “verified attendance.”
- [ ] Localize the flow in English, Marathi and Hindi.
- [ ] Preserve keyboard access, status announcements and narrow-screen layout.
- [ ] Keep QR scanning, geofence checks, photo uploads and non-zero rewards out of this self-attested version.

## Set 3 — Attendance integrity and production checks

- [ ] Test participant-only authorization and Google-linked identity enforcement.
- [ ] Test that the organiser cannot report attendance for another UID.
- [ ] Test pre-completion, cancelled, non-participant and duplicate requests.
- [ ] Test the append-only event and zero-point ledger behavior.
- [ ] Test the denominator excludes the organiser and does not double-count repeated joins.
- [ ] Test the exact “reported attending” wording and prohibit “verified” claims.
- [ ] Verify Firestore rules prevent direct client forgery.
- [ ] Run report, points and Initiative regression suites.
- [ ] Verify the flow with separate organiser and participant accounts in production.
- [ ] Preserve privacy-safe evidence with no raw location or civic text.

## Set 4 — Carried performance work

This carries forward the former Day 9 Set 4 after the attendance UI is stable, avoiding a second round of route-splitting changes.

- [ ] Preserve the approximately 807.28 kB minified / 239.78 kB gzip pre-Day-9 baseline in the comparison record.
- [ ] Add route-level lazy loading.
- [ ] Split reporting and Initiative features, including the attendance UI.
- [ ] Avoid loading Firebase-heavy screens on the homepage where practical.
- [ ] Keep boundary code limited to the report flow.
- [ ] Prevent overlapping Initiative refresh requests.
- [ ] Stop Initiative polling immediately after leaving the screen.
- [ ] Preserve direct-link refresh and browser history.
- [ ] Measure minified, gzip and transferred JavaScript.
- [ ] Measure desktop/mobile LCP, INP and CLS with comparable conditions.
- [ ] Document remaining warnings and deploy only after green verification.

## Later hardened attendance phase

- [ ] Design server-signed, short-lived or rotating Initiative QR tokens.
- [ ] Define scan time windows, replay protection and token invalidation.
- [ ] Define a consent-based proximity check using a documented geofence radius.
- [ ] Avoid continuous tracking and avoid retaining raw participant coordinates unless a separately reviewed need requires it.
- [ ] Address QR screenshots, token sharing, GPS spoofing and multiple-account abuse.
- [ ] Keep `SELF_ATTESTED` distinct from any future `PROXIMITY_ATTESTED` or independently verified basis.
- [ ] Do not enable non-zero rewards until the verification and anti-fraud standard is separately approved and tested.
