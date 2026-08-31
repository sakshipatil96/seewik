# Day 12 recognition, privacy and sourced-content contract v0.1

Status: frozen before Day 12 implementation on 2026-08-31.

Contract versions introduced by Sets 1-3:

- Private citizen profile: `citizen-profile-v0.2`
- Public-recognition consent: `recognition-consent-v0.1`
- Monthly recognition selection: `monthly-recognition-v0.1`
- Recognition abuse report: `recognition-abuse-report-v0.1`
- Active contribution ledger: `points-ledger-v0.3`
- Active reward policy: `reward-policy-v0.2`

## Private account profile migration

Profile path: `profiles/{existingFirebaseUid}`.

The backend migrates the existing minimal profile in place after verifying a Google-linked Firebase token. The document ID and `ownerUid` remain the existing Firebase UID. Reports, drafts, points-ledger entries, Initiative roles and attendance records are not copied, re-keyed, merged, rewritten or deleted.

The private profile stores:

- `ownerUid`;
- `authProvider: GOOGLE`;
- `recoverable: true`;
- `privateGoogleName` from Firebase Authentication, or an empty value when Google supplies none;
- `privateGoogleEmail` from Firebase Authentication;
- `schemaVersion: citizen-profile-v0.2`;
- backend timestamps.

The profile never stores a Google photograph. Only the owner may read the profile through Firestore or the authenticated profile API. Clients cannot create, update or delete profile documents. Email and private Google name never enter a public response, recognition snapshot, name-collision event, analytics event or poster payload.

## Public display name and consent

Recognition consent is independent of Google authentication, points participation and poster sharing. Its backend-owned document is `recognitionConsents/{uid}` and stores the editable `publicDisplayName` separately from the private Google account name.

The first recognition settings view prefills the public display-name editor from the Google-provided name. Prefill is not consent. The citizen must explicitly activate recognition after previewing the exact public name. The states are:

- `PRIVATE`: a display-name draft may exist, but it has never been public;
- `OPTED_IN`: the citizen is eligible for the live monthly candidate set;
- `WITHDRAWN`: public recognition was withdrawn without changing points.

Withdrawal removes the citizen from the live panel on its next safe calculation. Public endpoints recalculate from current consent and never fall back to a stale cached panel after a calculation failure. Re-opting in requires another explicit action. Consent changes are append-only audit events; the current consent document is a projection, not the audit history.

Public display names are 2-60 Unicode characters after trimming and whitespace normalization. Validation blocks URLs, email addresses, phone/contact details and reserved or misleading official-sounding titles. Names need not be unique. A normalized-name collision creates a backend-only monitoring event containing internal account references or hashes, never email addresses, and does not automatically block either citizen.

Suspected impersonation can be reported from a displayed name. The public response exposes no account identifier; the backend resolves the selected panel position to the current internal selection and stores a privacy-safe report.

## Monthly recognition boundary

The live panel is titled **Thanks to Our Top Three Citizens of the Month**. It is a Seewik thank-you, not government certification or proof of independently verified impact.

Each month uses `Asia/Kolkata` boundaries: start inclusive at 00:00 on the first day and end exclusive at 00:00 on the first day of the next month. Technical timezone details are not displayed.

Eligibility is derived only from backend-owned `pointsLedger` entries that satisfy all of these conditions:

- `policyStatus: AWARDED`;
- `schemaVersion: points-ledger-v0.3`;
- `rewardPolicyVersion: reward-policy-v0.2`;
- a strictly positive awarded value matching the reason: `REPORT_FILED` 5, `INITIATIVE_ATTENDANCE_ORGANISER_CODE_ATTESTED` 20, `INITIATIVE_ORGANISER_COMPLETED_REWARDED` 40 or `FIX_VERIFIED` 60;
- `occurredAt` falls inside the IST month;
- `demoMode` is not true;
- the owner is not in the exact configured production-test exclusion list.

Duplicate award documents with the same owner, source and reward reason count once. Self-attendance, zero-point records, recorded-not-rewarded events, malformed events, historical reward-policy versions and invalid values do not qualify.

Only citizens whose current status is `OPTED_IN` enter the candidate set. Candidates are ordered by internal monthly points descending, then normalized public display name alphabetically. An internal stable account tie-break is allowed only when both the points and display names are identical; it has no visible effect because identical names remain visually indistinguishable.

At most three names are displayed. If zero, one or two people qualify, only that honest partial state is returned. Private citizens are never used as substitutes.

## Deterministic rebuild and public response

The backend computes a content hash from the month boundary, valid deduplicated ledger entries, active consents and ordered selection. `recognitionMonths/{yyyy-MM}` is rewritten only when that content hash changes. Repeating a rebuild with identical inputs is idempotent.

The internal snapshot may retain private provenance required to audit the result, including selected internal account references, candidate count and aggregate totals. Firestore clients cannot read or write it.

The public API returns only:

- the month key/label;
- panel status;
- the ordered public-name array;
- public explanatory copy and schema version.

It never returns public points, UIDs, email, private Google names, report or Initiative identifiers, coordinates, complaint text, attendance details or raw ledger provenance.

## Private points

An authenticated citizen may read only their own points summary. Lifetime points include their legitimate positive `AWARDED` history without rewriting older entries. Current-month points use the active Day 12 eligibility rules so the private monthly number agrees with recognition calculation. Contribution-type explanations remain private.

## Client and Firestore boundary

- Profile, consent, consent audit, collision monitoring, abuse reports and monthly selection writes are backend-only.
- `pointsLedger` remains backend-only and append-only.
- Public recognition is read through the sanitized backend API, never direct Firestore access.
- Profile and consent owner reads may contain private data and therefore require matching Firebase ownership.
- Public-panel failures fail closed: no cached private or withdrawn name is served as a fallback.

## Sourced-content boundary reserved for Sets 5-6

Civic Awareness and Emergency Information are separate signed-out-accessible pages. Content facts require a versioned authoritative source, jurisdiction, reviewed date and status. Gemini must not invent or interpret legal, municipal or emergency guidance. Emergency sources may be visually quiet at the bottom of the page, but remain readable and accessible. The Nagar Parishad campaign tracker is future work and is not part of Day 12.
