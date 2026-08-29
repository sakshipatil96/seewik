# Day 10 identity and recovery contract v0.1

Status: frozen on 2026-08-29. Live UID-preservation, collision and recovery acceptance passed. The owner approved activation of the Set 3 boundary with existing anonymous reads preserved and the next durable write paused for Google linking without clearing unfinished work.

## Identity states

- `ANONYMOUS_SESSION`: Firebase Auth is still restoring or creating the first-load anonymous session. No durable civic write may be initiated from this state.
- `GOOGLE_LINK_REQUIRED`: an anonymous Firebase user exists and may use public, read-only and transient assistance, but must link Google before the first durable civic write.
- `GOOGLE_LINKED`: the Firebase user has a `google.com` provider. A successful link must preserve the pre-link Firebase UID and refresh the ID token before the queued write resumes.
- `SIGNED_OUT`: the citizen deliberately signed out. Seewik must not silently create another anonymous working account. The citizen can use public features or sign back in with Google.

## Existing UID ownership inventory

The following records remain keyed to the existing Firebase UID. Day 10 does not migrate, re-key, copy, merge or delete them.

| Record or effect | Current ownership key | Write path | Day 10 treatment |
| --- | --- | --- | --- |
| Complaint drafts and reports | `reports.ownerUid` | Direct Firestore create/update/delete | Preserve UID; frontend link gate now, rules enforcement in Set 3 |
| Report lifecycle events | Parent report ownership | Backend transition endpoint | Preserve report and UID; backend enforcement in Set 3 |
| Dedupe evaluations | Parent report ownership | Backend-only | Preserve report and UID |
| Points ledger | `pointsLedger.ownerUid` | Backend-only append | Preserve UID; never rewrite ledger identifiers |
| Initiatives | `initiatives.organiserUid` | Backend create/cancel/complete | Preserve UID; backend enforcement in Set 3 |
| Initiative participation | participant UID | Backend join | Preserve UID; backend enforcement in Set 3 |
| Initiative events | actor/organiser UID via backend | Backend-only append | Preserve UID and event identifiers |
| Technical Day 1 checks | Document/object path UID | Direct Firestore/Storage | Treat as a write and require linking in the normal UI |
| Citizen profile | Document ID and `ownerUid` | Direct Firestore after link | New minimal `citizen-profile-v0.1`; no civic content |
| Account collision audit | Winning `ownerUid` | Direct append after confirmed switch | New privacy-safe `account-audit-v0.1`; no losing UID or civic content |

## Pre-link capabilities

The following remain available without linking because they are public, read-only or transient and do not create a citizen-owned civic record:

- health and route lookups;
- language selection and navigation;
- approximate boundary guidance and manual prabhag selection;
- temporary location-based prabhag resolution and nearby-Initiative discovery;
- issue classification and complaint wording generation;
- local form editing, review and copying;
- the synthetic lifecycle walkthrough;
- reading records owned by the current restored UID.

The link gate is triggered immediately before the first durable write: saving or editing a draft, recording a lifecycle transition, publishing/joining/managing an Initiative, or performing the technical Firestore/Storage write check. Cancelled, blocked or failed Google popups must leave the form in memory.

While the session remains `GOOGLE_LINK_REQUIRED`, Seewik must visibly warn that access to records owned by that temporary UID is device-only: clearing browser data before linking Google can permanently remove the citizen's ability to recover existing reports, drafts, points and Initiative activity.

## Minimal profile contract

Profile path: `profiles/{existingFirebaseUid}`.

Stored fields are limited to:

- `ownerUid` — the unchanged Firebase UID;
- `authProvider` — fixed to `GOOGLE`;
- `recoverable` — fixed to `true`;
- `schemaVersion` — fixed to `citizen-profile-v0.1`;
- `updatedAt` — server timestamp.

Seewik does not copy Google email, display name or profile image into this Firestore profile. Firebase Authentication may retain provider account data required for authentication. Civic evidence and public Initiative content remain outside the profile document.

## Collision policy

If Google reports that the credential already belongs to another Firebase user:

1. The current anonymous user remains active while a warning is displayed.
2. Cancel leaves that user and all of its data untouched.
3. Continue requires explicit acknowledgement that the accounts will not be merged.
4. The existing Google-linked Firebase account wins.
5. The queued mutation from the losing anonymous session is discarded, not retried under the winning account.
6. Stale reports, points, Initiative memberships and organiser state are cleared before the winning account's data is shown.
7. No records are copied, combined, deduplicated, re-keyed or deleted.
8. A privacy-safe outcome event records only the winning UID, fixed event/outcome codes, schema version and server timestamp. It contains no losing UID and no civic content.

## Sign-out and recovery

Signing out never deletes civic data. A deliberate local marker suppresses automatic anonymous-account creation after sign-out. Signing in with the same Google account restores the same Firebase UID and therefore the same owner-protected records. Cross-browser recovery and the real collision path passed live OAuth acceptance testing. Physical Android Chrome popup-versus-redirect behavior remains a documented device-only follow-up.

## Enforcement boundary

Set 3 rejects anonymous durable writes at each independent boundary: backend report/Initiative mutations, direct Firestore report and technical-check writes, and technical Storage writes. The backend and rules inspect the linked identities map rather than only the provider used for the latest sign-in. Owner-scoped reads and transient classification, complaint wording, route and discovery behavior remain available. The release deploys backend, frontend, Firestore rules and Storage rules through green main with the previously healthy revision recorded for rollback.
