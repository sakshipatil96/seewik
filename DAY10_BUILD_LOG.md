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

## 2026-08-29 — Next 2 mobile emulation and Set 3 enforcement candidate

### Approved existing-anonymous policy

- Existing anonymous users retain owner-scoped read access to reports, drafts, points and Initiative membership state.
- Their next durable write opens the Google-link explanation and preserves unfinished in-memory work while authentication is cancelled or completed.
- No anonymous record is migrated, re-keyed, merged or deleted by enforcement activation.

### Next 2 — mobile acceptance

- Verified the account prompt and signed-out recovery state at 390 × 844 in English, Marathi and Hindi.
- Headings, privacy copy and Google/Not now actions wrap without overflow; focusable controls remain reachable.
- No Google credential action was started during emulation.
- Physical Android Chrome popup-versus-redirect behavior is explicitly deferred until a device is available.

### Next 3 — independent write enforcement

- Added a verified-token identity flag derived from the non-empty Firebase `firebase.identities["google.com"]` list.
- Backend Initiative create/join/cancel/complete and report lifecycle transitions return HTTP 403 with `GOOGLE_LINK_REQUIRED` before reaching persistence for an anonymous token.
- Anonymous nearby discovery, My Initiatives reads, classification, complaint wording and owner-scoped record reads remain available.
- Firestore report create/update/delete and technical-check writes now require the linked Google identity; report and points reads remain owner-scoped and available.
- Storage technical-check writes now require the same identity claim.
- The protected deployment now publishes Hosting, Firestore rules and Storage rules together.
- Added a privacy-safe production bypass script that creates one temporary anonymous test account, performs no successful write, verifies direct API/Firestore/Storage denial plus preserved reads, and removes the temporary account in cleanup.

### Pre-release verification and rollback

- Rollback app commit: `665dabcd193455fbdf0ebb7f9d450b6fe6b7c975`.
- Rollback Cloud Run revision: `seewik-api-00048-jut`, healthy at 100% before this candidate.
- Backend suite: 182 passed.
- Frontend suite: 33 passed; production build passed with the existing deferred initial-bundle warning.
- Firebase Rules API syntax and semantic tests returned no issues for `firestore.rules` or `storage.rules`; neither test published a ruleset.
- Repository content policy and whitespace checks passed before commit.

### Production release and direct-bypass acceptance

- Checked and deployed app commit: `47b2a74d7d47d31ce7020b7bd77d0c7ab792feba`.
- Quality workflow: `33281783161`, passed.
- Deployment workflow: `33281831787`. Its first attempt failed while resolving the default Firebase Storage bucket because the deployer lacked `firebasestorage.defaultBucket.get`; the workflow restored `seewik-api-00048-jut` to 100% and removed the failed candidate tag.
- Named deployment finding: `STORAGE_RULES_DEPLOYER_BUCKET_LOOKUP_PERMISSION_GAP`.
- Remediation: added only the read-only `roles/firebasestorage.viewer` role to `seewik-deployer@seewik.iam.gserviceaccount.com`, then reran the unchanged green commit.
- The deployment retry passed backend candidate verification, traffic routing, Hosting, Firestore rules, Storage rules and production route verification.
- Active backend revision: `seewik-api-00055-juy`, 100% traffic, labelled with the deployed app commit.
- Production bypass result: anonymous owner-scoped report, points and Initiative reads remained available; Initiative API mutations, report lifecycle mutations, direct report writes, technical Firestore writes and technical Storage writes were denied.
- The acceptance script created one temporary anonymous account, made no successful civic write and removed the account during cleanup.
- Commit identifiers at acceptance close: app/deployed/checked `47b2a74d7d47d31ce7020b7bd77d0c7ab792feba`; local and remote documentation head are recorded by the following closeout commit.

## 2026-08-30 — No-Android production closeout

### Owner isolation

- Added a repeatable privacy-safe production ownership probe using two temporary anonymous identities and explicitly labelled fixtures.
- The owner read its DRAFT and points record; the other identity was denied direct access to both.
- `My activities` returned `ORGANISER` for the owner's two activities and `PARTICIPANT` for the joined citizen's shared activity.
- The participant did not receive the owner's unrelated activity.
- Every temporary report, points record, Initiative, participation and authentication user was removed during cleanup.

### Production visual and route acceptance

- `/`, `/report/new`, `/reports`, `/points`, `/initiatives` and `/initiatives/new`: HTTP 200.
- Backend `/health`: `status: ok`.
- English, Marathi and Hindi home/account states fit at 390 × 844 with `scrollWidth == clientWidth`.
- The production report flow loaded all 20 approximate boundary outlines; manual Prabhag 7 selection synchronized with one pressed outline and the route action stayed gated pending category confirmation.
- Browser console warnings/errors: zero.
- Screenshots contain only public interface text and an empty temporary device-only account state:
  - `evidence/day10-production-en.jpg` — SHA-256 `feffb85b7f3f654d810b16adcbcabfbe8b540f420af08e4f8cc813577fe4e3d3`;
  - `evidence/day10-production-mr.jpg` — SHA-256 `07b345d5b4be1e80de7bd4dc998e9b39bd3054946e383e3964421b73e747b876`;
  - `evidence/day10-production-hi.jpg` — SHA-256 `aeb2e070186d50d96a8393de29253831a007284c62c363e76c459a180a9ca148`.

### Disposition

- All Day 10 work that does not require a physical Android device is complete.
- Physical Android Chrome Google popup-versus-redirect behavior remains the only device-dependent acceptance item.
- Attendance and the carried performance work begin under `DAY11_CHECKLIST.md`.

### Closeout release

- Closeout commit: `5b67ce97b08ec92ef12dd9bbdcc56ee7484b3e66`.
- Quality workflow `33298520210`: passed.
- Deployment workflow `33298564024`: passed, including candidate health, traffic routing, Hosting, Firestore rules, Storage rules and production route verification.
- Active backend revision: `seewik-api-00058-viy`, 100% production traffic.
