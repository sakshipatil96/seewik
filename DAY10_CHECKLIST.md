# Day 10 Checklist — recoverable Google-linked profiles

Day 10 changes the anonymous-first assumption without re-keying existing citizen data. Firebase Anonymous Authentication remains the first-load identity mechanism. Before the first state-changing write, the citizen must link that anonymous user to Google. A successful link preserves the Firebase UID, so existing ownership keys remain unchanged.

Google is the only profile provider for this build. Provider-neutral boundaries should still be used so another provider can be added later without rewriting civic ownership logic.

## Set 1 — Freeze the identity and migration contract

- [x] Inventory every anonymous-UID-owned record and every state-changing path: reports, drafts, lifecycle events, points, Initiative creation, joining, cancellation, completion and future attendance.
- [x] Define `ANONYMOUS_SESSION`, `GOOGLE_LINK_REQUIRED` and `GOOGLE_LINKED` states.
- [x] Keep the existing anonymous UID when Google linking succeeds.
- [x] Require a server-verifiable linked Google identity before any state-changing write; do not rely only on a hidden or disabled frontend control.
- [x] Decide explicitly which read-only and transient operations remain available before linking.
- [x] Preserve all existing ownership IDs, document paths, append-only events and ledger identifiers.
- [x] Add a minimal versioned profile contract keyed by the existing Firebase UID.
- [x] Minimize stored profile data; document whether display name, profile image or email is necessary before storing it.
- [x] Keep profile data separate from civic evidence and public Initiative content.
- [x] Freeze the collision policy: when a Google credential already belongs to another Firebase user, the existing Google-linked account wins and no automatic data merge occurs.
- [x] Require a clear confirmation before abandoning the current anonymous session during a collision.
- [x] Explain that reports, drafts, points, organiser rights and Initiative memberships attached to the current anonymous UID will not transfer automatically.
- [x] Never delete or silently rewrite anonymous records during collision handling.
- [x] Record only a privacy-safe collision audit event with no civic content.

Frozen contract: `data/contracts/day10-identity-migration-contract-v0.1.md`.

## Set 2 — Add the Google-link gate

- [x] Continue creating or restoring an anonymous Firebase user on first load.
- [x] Add one reusable provider-neutral account-link service and configure Google as the only enabled provider.
- [x] Trigger Google linking before the first state-changing write.
- [x] Preserve the same Firebase UID after a successful `link` operation.
- [x] Refresh the Firebase ID token after linking before retrying the original action.
- [x] Resume the intended action only after the backend accepts the linked identity.
- [x] Handle popup blocked, popup closed, network failure, expired credential and provider-disabled states without losing form input.
- [x] Do not create an email/password, password-reset, email-link or verification-email flow in this build.
- [x] Add a visible account/profile state and a clear sign-in explanation in English, Marathi and Hindi.
- [x] Preserve accessibility, keyboard operation, narrow-screen layout and safe browser history.

Local implementation complete. Real Google popup verification remains a pre-release Set 7 acceptance check.

Emulated mobile acceptance complete at 390 × 844 in English, Marathi and Hindi. The account prompt, recovery state, focus and actions fit correctly. A physical Android Chrome popup-versus-redirect check remains explicitly deferred until a device is available; it does not weaken the server-side write boundary.

## Set 3 — Enforce linked ownership on every write

- [x] Extend the authenticated-citizen contract to distinguish anonymous sessions from Google-linked identities while preserving the UID.
- [x] Enforce Google-linked identity on backend Initiative mutations.
- [x] Enforce Google-linked identity on backend report lifecycle mutations and any server-created ledger effects.
- [x] Enforce the same rule in Firestore rules for direct report and draft writes.
- [x] Keep lifecycle events, Initiative events and points-ledger entries server-only and append-only.
- [x] Ensure a linked citizen can still read records owned by the unchanged UID.
- [x] Verify organiser-only Initiative transitions remain organiser-only after linking.
- [x] Confirm that anonymous users cannot bypass the gate with direct API, Firestore or Storage calls.
- [x] Keep read-only public/nearby discovery behavior aligned with the frozen Set 1 decision.

Set 3 release candidate complete. Anonymous ID tokens retain owner-scoped reads and transient assistance, while durable report, Initiative and technical-check writes require a non-empty `firebase.identities["google.com"]` claim. Production acceptance runs only after green-main deployment.

## Set 4 — Handle existing-account collisions safely

- [x] Detect the Firebase credential-already-in-use collision separately from ordinary popup failures.
- [x] Offer an explicit cancel path that leaves the current anonymous session and its data untouched.
- [x] If the citizen continues, sign into the existing Google-linked Firebase account; that existing account wins.
- [x] Do not copy, combine, deduplicate or delete data automatically.
- [x] Show a plain warning before switching that current-device anonymous data will not transfer.
- [x] Clear stale in-memory civic state after the account switch so one UID's data is never displayed under another UID.
- [x] Reload reports, points and Initiative memberships for the winning UID.
- [x] Write a privacy-safe collision outcome event suitable for operational diagnosis.
- [x] Test both directions: unused Google credential preserves the anonymous UID; existing Google credential switches to the established UID.

Production acceptance complete: first-time linking preserved the anonymous UID, and the collision path switched cleanly to the established Google-linked UID without merging or deleting civic data.

## Set 5 — Profile and recovery experience

- [x] Create the minimal profile record only after successful Google linking.
- [x] Show whether the current account is linked and recoverable.
- [x] Warn an unlinked anonymous session that access to its existing data remains device-only and can be lost if browser data is cleared.
- [x] Confirm that the same Google account restores the same Firebase UID and data on another browser or device.
- [x] Define sign-out behavior without deleting civic data.
- [x] Prevent accidental creation of a second anonymous working state immediately after sign-out.
- [x] Preserve unsent form input across a cancelled login attempt where safe.
- [x] Add privacy copy explaining what account information is stored and why.
- [x] Keep account deletion and cross-account data transfer out of scope until their retention and audit rules are designed.
- [x] Give deliberate sign-out its own My Reports recovery state instead of showing a false zero-report result.
- [x] Explain that signing out does not delete saved work and that signing back in restores it.
- [x] Distinguish signed-out, linked-empty and anonymous-empty report states.
- [x] Remove Refresh, Create Report and Start Over controls when they would be misleading on the signed-out report screen.
- [x] Prevent the internal `ACCOUNT_SIGNED_OUT` code from reaching citizen-facing copy.

Production recovery acceptance complete. The signed-out/report-state correction is implemented and locally verified in English, Marathi and Hindi at desktop and phone-sized layouts.

## Set 6 — Identity regression and security gates

- [x] Update tests that currently assume anonymous writes while preserving their ownership assertions.
- [x] Add tests for UID preservation, token refresh and retried writes after successful linking.
- [x] Add tests for collision cancellation and existing-account-wins behavior.
- [x] Add tests proving no automatic merge, overwrite or cross-account data exposure.
- [x] Add Firestore-rule tests for anonymous denial; linked-owner acceptance was confirmed in production during UID-preservation testing.
- [x] Add backend tests for anonymous mutation denial and linked-Google mutation acceptance.
- [x] Re-run report, draft, lifecycle, points and Initiative regression suites.
- [x] Verify existing anonymous records are not migrated, re-keyed or deleted.
- [x] Verify profile and audit records contain no complaint or Initiative evidence.

## Set 7 — Final verification, documentation and deployment

This carries forward the former Day 9 Set 7 after the identity change is complete.

- [ ] Update `DAY10_BUILD_LOG.md`, `CHANGELOG.md`, the Day 9 closure note and the parent project guide.
- [ ] Run affected backend and frontend gates.
- [ ] Run repository-content, dataset-integrity and whitespace checks.
- [ ] Deploy only from green `main` and preserve the previous healthy revision for rollback.
- [ ] Verify a new anonymous session is prompted to link Google before its first write.
- [x] Verify successful linking preserves the UID and existing records.
- [x] Verify the collision flow does not crash, merge data or expose the losing UID's records.
- [x] Verify same-account recovery on a separate clean browser session.
- [x] Verify an unlinked anonymous session shows the device-only access warning before Google is connected.
- [ ] Verify reports, drafts, points and Initiative organiser/participant ownership in production.
- [ ] Verify production routes, all three languages, boundary fallbacks, narrow layouts and browser console.
- [ ] Preserve representative privacy-safe production screenshots in English, Marathi and Hindi.
- [ ] Record app, local, remote, checked and deployed commit identifiers separately.

## Explicitly deferred

- Initiative attendance confirmation is Day 11 work.
- QR plus geolocation attendance verification is a later hardened phase, not part of the Day 10 login release.
- Automatic cross-account data merging is not planned for this build.
- Non-zero Initiative rewards remain disabled.
