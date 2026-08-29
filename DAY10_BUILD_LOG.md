# Day 10 build log

## 2026-08-29 — Sets 1, 2, 4 and 5 local implementation

Release status: local only. Not committed, pushed or deployed. Set 3 anonymous-write rejection is intentionally unchanged.

### Set 1 — frozen identity contract

- Added `data/contracts/day10-identity-migration-contract-v0.1.md`.
- Inventoried report, draft, lifecycle, points, Initiative, participation, event and technical-check ownership paths.
- Froze the four runtime states, including deliberate `SIGNED_OUT` handling.
- Kept transient classification, routing, complaint wording, nearby discovery and local form work available before linking.
- Froze existing-account-wins with explicit confirmation, no automatic merge and no losing-account mutation retry.
- Froze a profile with no copied Google email, display name or photo.

### Set 2 — local Google-link gate

- Added a reusable account provider service with Google as the only configured provider.
- Restores/creates the anonymous first-load Firebase user.
- Queues durable mutations at the frontend gate, links from a direct citizen click, verifies UID preservation, refreshes the ID token and then retries the queued mutation.
- Leaves classification, route lookup, complaint wording and read-only discovery available before linking.
- Covers draft persistence/editing, report lifecycle, Initiative create/join/manage and the technical Firestore/Storage check.
- Added English, Marathi and Hindi account copy plus a keyboard-contained, narrow-screen dialog.
- Maps blocked/closed popup, network, expired credential, provider-disabled and unauthorized-domain failures without clearing form state.

### Set 4 — local collision handling

- Separates credential collisions from ordinary popup errors.
- Defaults to cancel and requires an acknowledgement before switching accounts.
- Existing Google-linked account wins; the losing anonymous account is not merged, copied, re-keyed or deleted.
- Discards the losing account's queued mutation, clears account-bound memory, then reloads reports, points and Initiative memberships for the winner.
- Adds a privacy-safe audit record with no losing UID and no civic content.

### Set 5 — local profile and recovery experience

- Adds versioned minimal profile writes after Google linking.
- Displays linked/recoverable state while keeping Google email transient to Firebase Auth/UI.
- Displays an explicit device-only access warning for an unlinked anonymous session, including the risk of clearing browser data before linking.
- Signs out without deleting civic data and suppresses silent creation of a second anonymous working account.
- Provides a same-Google-account sign-in path for recovery.
- Adds profile and account-audit Firestore rules without changing existing anonymous report-write permissions.
- Checks the token's linked Google identity rather than only the provider used for the latest sign-in, so an anonymous-to-Google link is recognized after token refresh.

### Local verification

- `npm test`: 28 passed, including the explicit unlinked device-only warning.
- `npm run build`: passed.
- Backend regression suite: 178 passed with the local Byte Buddy test agent enabled.
- Dependency audit: zero vulnerabilities.
- Repository content policy, boundary integrity and whitespace checks: passed.
- Added identity, collision, profile-minimization, mutation-gate and Firestore-rule integrity tests.
- Local visual acceptance passed on My Reports at desktop and 390 px phone width: the unlinked device-only warning stays above the report content, and English, Marathi and Hindi sign-in prompts fit without button overflow.
- The single anonymous Firebase Auth user created for the visual check made no civic writes and was deleted after the check; a separately identified earlier anonymous account was not changed.

### Deliberately pending

- Real Google popup and UID-preservation acceptance test.
- Real existing-account collision in both directions.
- Same-account recovery in a clean browser/device.
- Set 3 backend and Firestore anonymous-write rejection.
- Commit, push and production deployment.

## 2026-08-29 — OAuth credential exposure decision

- Event code: `oauth_client_secret_exposed_privately_rotation_deferred`.
- Scope: the existing Google OAuth client secret appeared in a private task-tool output. It was not added to the repository or frontend bundle, and no secret value is recorded here.
- Risk decision: treat the limited private exposure as low severity and do not disrupt the enabled Google provider during Day 10 authentication acceptance testing.
- Deferred remediation: before public launch, rotate the credential. If the console still offers no reset control, create a replacement client while the existing client remains active, update the Firebase provider configuration, test immediately, and retire the old client only after successful verification.

## 2026-08-29 — First production Google-link attempt

- Result: Google linking did not complete; UID preservation remains inconclusive and no civic mutation was resumed.
- Observability finding: the application caught the Firebase exception and preserved the form, but its generic fallback hid the privacy-safe `auth/...` error code and left the browser console empty.
- Named issue: `GOOGLE_SIGN_IN_ERROR_CODE_NOT_OBSERVABLE`.
- Remediation: display only a sanitized Firebase Auth diagnostic code alongside the citizen-facing message. Do not display provider messages, tokens, email addresses or OAuth configuration values.
- Configuration note: the project support email is contact information shown to users; it does not need to match the Google account selected for sign-in. If the OAuth audience remains in Testing, the selected account must separately appear in the configured test-user list.
- Root-cause candidate found before the retry: the production workflow deployed Hosting but omitted the new Firestore profile rules. A successful Google link could therefore be followed by a `permission-denied` profile write, which the generic fallback previously hid.
- Deployment correction: deploy `hosting,firestore:rules` together and lock that requirement in an integrity test. The retry remains necessary to confirm this was the observed production failure.
- Fix verification before push: 30 frontend tests, frontend production build, 178 backend tests, dependency audit, repository content policy, boundary checksum and whitespace checks passed. The first plain local Maven attempt hit the known macOS test-agent attachment limitation; the complete suite passed with the Byte Buddy agent supplied explicitly, matching the established local procedure.

## 2026-08-29 — Production identity acceptance and signed-out report-state correction

### Production identity acceptance

- First-time Google linking preserved the anonymous Firebase UID and refreshed the identity to a Google-linked, recoverable profile.
- The profile document remained keyed by the preserved UID and contained only `ownerUid`, `authProvider`, `recoverable`, `schemaVersion` and `updatedAt`; no Google email, display name or photo was copied into Firestore.
- A deliberate sign-out followed by sign-in restored the same UID and profile document.
- A clean-browser credential collision displayed the explicit existing-account warning, switched to the established Google-linked UID and did not merge, delete or expose the losing anonymous session's civic data.
- The unlinked anonymous state displayed the device-only access warning before Google was connected.
- One no-write anonymous session created during collision acceptance remains pending separately confirmed cleanup; this change does not delete authentication records.

### Named UX defects

- `SIGNED_OUT_MY_REPORTS_FALSE_EMPTY_STATE`: deliberate sign-out displayed an empty-profile result even though saved work remained attached to the Google account.
- `ACCOUNT_SIGNED_OUT_RAW_CODE_LEAK`: an internal state code appeared directly in citizen-facing copy.
- `REPORT_START_OVER_VISUAL_AMBIGUITY`: the report workflow's Start Over control remained visible behind My Reports and could be mistaken for a report-state action.
- Recurring lesson: `INTERNAL_STATE_PRESENTATION_BOUNDARY` — internal codes and hidden states need an explicit, tested citizen-facing presentation contract.

### Correction

- Added explicit `SIGNED_OUT`, `LINKED_EMPTY`, `ANONYMOUS_EMPTY`, `LOADING` and `HAS_REPORTS` report-view states.
- The signed-out My Reports and report-detail screens now explain that signing out deletes nothing, saved work remains attached to Google and signing in restores access.
- Signed-out report screens no longer request report data or show Refresh, Create Report, false empty-profile content or Start Over.
- Successful sign-in while My Reports is open now reloads account-owned report state automatically.
- Raw `ACCOUNT_SIGNED_OUT` messages are mapped to localized recovery copy in English, Marathi and Hindi.

### Verification

- Frontend tests: 32 passed, including report-state and citizen-copy integrity coverage.
- Frontend production build: passed; the existing initial-bundle size warning remains deferred performance work.
- Backend regression suite: 178 passed with the established inherited Byte Buddy test agent setting.
- Dependency audit: zero vulnerabilities.
- Repository content policy, boundary integrity and whitespace checks: passed.
- Local visual acceptance passed on My Reports at the default desktop viewport and 390 × 844 phone viewport in English, Marathi and Hindi. Recovery copy wrapped correctly, the primary action remained visible and no misleading report controls appeared.
