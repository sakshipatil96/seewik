# Day 12 Checklist — recognition and civic awareness

Day 12 adds privacy-safe recognition, a shareable contribution record and sourced civic-awareness/emergency information. It must not turn civic reports into public profiles, expose precise locations or imply that contribution points independently prove impact.

## Decisions to freeze before public recognition

- [ ] Confirm the recommended visibility default: every citizen remains private unless they explicitly opt in to a public recognition alias.
- [ ] Confirm the recommended leaderboard shape: monthly Prabhag-level aggregate totals, not a public list of individual citizens.
- [ ] Confirm that a leaderboard Prabhag means the explicitly confirmed place of the civic contribution, not the citizen's home or inferred background location.
- [ ] Confirm the recommended migration rule: report rewards may use the report's frozen confirmed Prabhag; Initiative rewards enter geographic totals only prospectively after Initiatives gain an explicit confirmed Prabhag. Older/unassigned Initiative rewards remain citywide only.
- [ ] Confirm the recommended small-group privacy threshold: suppress a Prabhag row until at least 10 distinct contributors qualify in that month.
- [ ] Confirm that `Citizen of the Month` uses an optional citizen-chosen alias only; never copy a Google name, email or photograph.
- [ ] Confirm that ties share the recognition position instead of being broken by hidden personal or location data.
- [ ] Freeze the recognition month in `Asia/Kolkata` and document exact inclusive start/end timestamps.
- [ ] Freeze a versioned Day 12 recognition/privacy/content contract before implementation.

## Set 1 — trustworthy recognition data

- [ ] Derive recognition only from the append-only points ledger and the active 5/20/40/60 reward policy.
- [ ] Exclude zero-point records, demo fixtures, deleted test allowlists and any event not marked `AWARDED`.
- [ ] Do not infer a citizen's Prabhag from a background location request or the approximate boundary guide.
- [ ] Use only an explicitly confirmed, purpose-limited Prabhag value for geographic aggregation.
- [ ] Keep raw UIDs, report IDs, Initiative IDs, coordinates, complaint text and attendance details out of public recognition responses.
- [ ] Hash internal grouping identifiers where raw ownership is unnecessary.
- [ ] Define deterministic monthly aggregation, tie behavior and idempotent rebuilds.
- [ ] Version the recognition aggregate schema and retain enough internal provenance to audit totals without exposing it publicly.

## Set 2 — privacy-safe Prabhag leaderboard

- [ ] Add a backend-owned monthly aggregation path; Firestore clients must not write leaderboard totals.
- [ ] Show aggregate contribution points and contributor count only for rows meeting the privacy threshold.
- [ ] Label suppressed rows honestly rather than displaying zero.
- [ ] Clearly state that points represent recorded Seewik contributions, not an official municipal performance score.
- [ ] Do not rank Prabhags using the inactive approximate polygon dataset.
- [ ] Provide citywide context without exposing a small Prabhag through subtraction or drill-down.
- [ ] Add month selection with bounded history and stable sorting.
- [ ] Test threshold boundaries, ties, late ledger events, duplicate events and rebuild idempotency.

## Set 3 — Citizen of the Month and sharing

- [ ] Keep Citizen of the Month disabled until the alias/consent decision is frozen.
- [ ] Store recognition consent separately from Google authentication and civic evidence.
- [ ] Allow a citizen to preview, opt in, change their alias and withdraw public display without deleting or rewriting their ledger.
- [ ] Moderate alias length and characters without collecting a legal name.
- [ ] Prevent aliases from containing contact details, URLs or misleading official titles.
- [ ] Create a private-by-default contribution summary showing total points and counts by contribution type.
- [ ] Add an explicit user-triggered share/export card with no UID, report text, Initiative location or precise activity history.
- [ ] Mark the card as a Seewik contribution record, not government certification or independent verification.
- [ ] Add accessible copy/download behavior and a safe failure state when browser sharing is unavailable.
- [ ] Test revoked consent, alias changes, ties, duplicate awards and private-account defaults.

## Set 4 — sourced civic-awareness content

- [ ] Define a versioned content schema with topic, language, jurisdiction, source title, source URL, source date, reviewed date and status.
- [ ] Start with a small explicitly selected topic set instead of an open-ended feed.
- [ ] Use authoritative primary sources; do not use Gemini to invent legal, municipal or emergency guidance.
- [ ] Keep source facts independent from translated interface explanations.
- [ ] Display source and last-reviewed information beside each item.
- [ ] Reject unsourced, stale, contradictory or out-of-jurisdiction content at build/test time.
- [ ] Preserve a visible limitation when Nandurbar-specific guidance is unavailable.
- [ ] Add a review/expiry interval so old awareness content cannot remain silently current.

## Set 5 — sourced emergency information

- [ ] Verify every emergency number and instruction against current authoritative sources immediately before release.
- [ ] Separate national emergency contacts from locally verified Nandurbar contacts.
- [ ] Use direct `tel:` actions only for verified telephone numbers.
- [ ] Display that Seewik is not an emergency response service and does not dispatch help.
- [ ] Keep emergency information usable without sign-in and without requesting location.
- [ ] Do not personalize emergency advice from civic reports, points or Initiative history.
- [ ] Add stale-source handling that removes or clearly disables unverified local contact details.
- [ ] Test number formatting, keyboard/screen-reader behavior and offline/error states.

## Set 6 — language, mobile and release verification

- [ ] Localize all recognition, consent, privacy, sharing, awareness and emergency states in English, Marathi and Hindi.
- [ ] Obtain human review for safety-critical Marathi and Hindi emergency wording before treating it as final.
- [ ] Verify narrow Android layouts, text zoom, long translations, visible focus and status announcements.
- [ ] Verify signed-out, anonymous, Google-linked, opted-in, opted-out and consent-withdrawn states.
- [ ] Extend Firestore rules tests to block forged aggregates, aliases, consent, public recognition and content writes.
- [ ] Run report, lifecycle, points, Initiative, attendance, identity and navigation regressions.
- [ ] Run production owner-isolation and public-response privacy checks with temporary allowlisted fixtures and complete cleanup.
- [ ] Deploy through green main, verify health/routes/rules and record the final Git SHA and Cloud Run revision.
- [ ] Write `DAY12_BUILD_LOG.md` with sources, limitations, test counts and privacy-safe evidence.

## Explicitly outside Day 12

- Coupon claims and example businesses remain Day 13 work.
- Initiative meeting-point redesign is tracked in `PROJECT_TODOS.md` and should not delay the recognition/privacy contract.
- Minor label and copy changes are collected for a deliberate UI-polish pass unless one blocks comprehension or safety.
- No public legal names, Google profile photographs, public civic histories, precise locations or raw activity evidence.
