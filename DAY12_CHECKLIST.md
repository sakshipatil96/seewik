# Day 12 Checklist — recognition, sharing and civic awareness

Day 12 adds an opt-in monthly recognition panel, a private contribution summary, client-side shareable posters and sourced civic-awareness/emergency information. It must not expose private citizens, email addresses, Google photographs, precise activity history or sensitive civic evidence.

## Frozen recognition and privacy model

- [x] Store the Google-linked citizen's name and email for account use; both are private by default.
- [x] Never display or include email in public recognition or posters.
- [x] Keep Google profile photographs out of the first recognition version.
- [x] Store public-recognition consent separately from Google sign-in.
- [x] Rank only citizens who have opted in; citizens who remain private never enter the public candidate set.
- [x] Use the append-only points ledger and the active 5/20/40/60 reward policy to calculate monthly eligibility internally.
- [x] Use `Asia/Kolkata` month boundaries internally without displaying technical timezone details.
- [x] Select up to three opted-in citizens each month and display higher contributors first.
- [x] Present them as **Thanks to Our Top Three Citizens of the Month**, with equal visual weight.
- [x] Show names only: no public points, point totals, rank numbers, email addresses or profile photographs.
- [x] Citizens can always see their own points privately.
- [x] Use alphabetical display-name order only when internal monthly points are equal.
- [x] If fewer than three opted-in citizens qualify, show only those available; never substitute or expose a private citizen.
- [x] Do not enforce display-name uniqueness. Detect normalized-name collisions for monitoring without blocking legitimate shared names.
- [x] Use Prabhag only for civic location context and filtering, never for recognition scoring or competition.
- [x] Do not build a Prabhag leaderboard, contributor threshold, Prabhag tie system or historical Initiative-to-Prabhag migration.
- [x] A poster always contains the citizen's chosen display name and can be shared whether or not the citizen opted into public recognition.
- [x] Sharing a poster is a deliberate one-time disclosure and does not change public-recognition consent.
- [x] Generate posters on the citizen's device; do not upload them to a public, enumerable or guessable URL.

## Set 1 — freeze the Day 12 contract and profile migration

- [x] Write and version the Day 12 recognition/privacy/content contract before implementation.
- [x] Define the monthly selection boundary, qualifying ledger statuses and deterministic rebuild behavior.
- [x] Define the exact opt-in, withdrawal and public-panel replacement behavior.
- [x] Extend the minimal profile schema to store the Google-provided display name and email privately.
- [x] Document why the data is stored, where it is used and how withdrawal from recognition differs from account data deletion.
- [x] Preserve the existing Google-linked UID and all reports, drafts, points and Initiative roles during profile migration.
- [x] Never expose profile email or photograph through public APIs, Firestore reads, logs, analytics or poster data.
- [x] Prefill the public display name from the Google name, allow the citizen to edit it before opting in and store it separately from the private Google account name.
- [x] Apply a display-name length cap and block URLs, phone numbers, contact details and reserved official-sounding titles such as `Nagar Parishad Officer`.
- [x] Define a normalized-name collision event containing internal account references only, never email addresses.
- [x] Add an abuse/report path for impersonation; a collision alone must not block two genuine people with the same name.

## Set 2 — trustworthy monthly selection

- [x] Derive eligibility only from backend-owned, append-only ledger entries marked `AWARDED`.
- [x] Use the frozen reward values: report filing 5, organiser-code attendance 20, eligible completed organiser 40 and first verified civic fix 60; self-attendance remains 0.
- [x] Exclude zero-point records, demo fixtures, exact test allowlists and invalid/non-awarded events.
- [x] Build the candidate set from citizens whose recognition consent is active at the defined monthly selection point.
- [x] Keep non-opted-in citizens entirely outside recognition ranking and public selection responses.
- [x] Select at most three candidates by monthly points, with alphabetical name ordering for equal totals.
- [x] Return public names and display order only; keep points, UIDs, emails, report IDs, Initiative IDs, coordinates and activity details private.
- [x] Make monthly calculation/rebuild idempotent and auditable without exposing its private provenance.
- [x] Test month boundaries in IST, late ledger events, duplicate awards, equal totals, fewer than three candidates, consent withdrawal and deterministic rebuilds.
- [x] Ensure Firestore clients cannot forge consent, recognition selection or aggregate results.

## Set 3 — recognition and private points interfaces

- [x] Add an explicit recognition opt-in with plain-language preview of exactly what becomes public.
- [x] Do not bundle recognition consent into Google sign-in, points participation or poster sharing.
- [x] Allow withdrawal without deleting or rewriting the citizen's points ledger.
- [x] Show each citizen their own lifetime/monthly points privately, including contribution-type explanations where useful.
- [x] Build the public **Thanks to Our Top Three Citizens of the Month** panel.
- [x] Give all three names equal card size, typography and visual treatment while preserving the accepted points-based display order.
- [x] Do not show `1st`, `2nd`, `3rd`, public point totals, score comparisons or Google photographs.
- [x] Provide honest empty and partial states when zero, one or two opted-in citizens qualify.
- [x] Clearly describe recognition as a Seewik thank-you, not government certification or independently verified civic impact.
- [x] Test private-by-default, opted-in, opted-out, withdrawn, edited-name, collision and partial-panel states.

## Set 4 — client-side shareable contribution poster

- [x] Create a poster only after a citizen explicitly requests it.
- [x] Always include the citizen's chosen display name on the poster.
- [x] Keep email, UID, precise locations, complaint text, report evidence and detailed activity history off the poster.
- [x] Use safe high-level contribution wording and avoid claims of government certification or independent verification.
- [x] Generate the image locally in the browser/device and share it through the platform share flow when supported.
- [x] Do not upload the generated poster or create a public poster URL.
- [x] Provide a local download fallback when direct sharing is unavailable.
- [x] Keep poster sharing independent from recognition consent in both directions.
- [x] Test local image generation, preview, platform-share detection and download/error fallbacks in the browser.
- [ ] Test the physical Android Chrome → WhatsApp share handoff when a device is available.

## Set 5 — sourced civic-awareness content

- [x] Define a versioned content schema with topic, language, jurisdiction, source title, source URL, source date, reviewed date and status.
- [x] Start with a small explicitly selected topic set instead of an open-ended feed.
- [x] Use authoritative primary sources; do not use Gemini to invent legal, municipal or emergency guidance.
- [x] Keep source facts independent from translated interface explanations.
- [x] Display source and last-reviewed information beside each item.
- [x] Reject unsourced, stale, contradictory or out-of-jurisdiction content at build/test time.
- [x] Preserve a visible limitation when Nandurbar-specific guidance is unavailable.
- [x] Add a review/expiry interval so old awareness content cannot remain silently current.

## Set 6 — sourced emergency information

- [x] Verify every emergency number and instruction against current authoritative sources for the 2026-08-31 local build; repeat this check immediately before release.
- [x] Separate national emergency contacts from locally verified Nandurbar contacts.
- [x] Use direct `tel:` actions only for verified telephone numbers.
- [x] Display that Seewik is not an emergency response service and does not dispatch help.
- [x] Keep emergency information usable without sign-in and without requesting location.
- [x] Do not personalize emergency advice from reports, points or Initiative history.
- [x] Add stale-source handling that removes or clearly disables unverified local contact details.
- [x] Test number formatting, keyboard/screen-reader behavior and offline/error states.

## Set 7 — language, mobile, security and release verification

- [x] Localize recognition, consent, privacy, private-points, poster, awareness and emergency states in English, Marathi and Hindi.
- [x] Obtain human review for safety-critical Marathi and Hindi emergency wording before treating it as final. Owner approved on 2026-08-31.
- [x] Verify browser-simulated narrow Android layouts, text zoom resilience, long translations, visible focus and status announcements. Physical Android share/sign-in remains separately deferred below.
- [x] Verify signed-out, anonymous, Google-linked, opted-in, opted-out and consent-withdrawn states through regression coverage and local browser states.
- [x] Extend rules/security tests to block forged profiles, consent, recognition results and content writes.
- [x] Run report, lifecycle, points, Initiative, attendance, identity and navigation regressions.
- [ ] Run public-response privacy checks and production owner-isolation checks with exact temporary fixtures and complete cleanup.
- [x] Run backend tests, frontend tests, production build, dependency audit and repository checks.
- [ ] Commit and push only after green checks and explicit approval.
- [ ] Deploy from green `main`, verify health/routes/rules and record the exact Git SHA and Cloud Run revision.
- [x] Write `DAY12_BUILD_LOG.md` with sources, limitations, test counts and privacy-safe verification evidence; add deployment evidence only after an approved production release.

## Inputs needed from the owner

- [x] Provide or approve the first small set of civic-awareness topics.
- [x] Human-review the final Marathi and Hindi emergency wording before release. Approved by the owner on 2026-08-31.
- [ ] When available, test Google sign-in and WhatsApp poster sharing on physical Android Chrome.
- [x] Send minor UI label/text changes as a batch with the screen name and current → desired wording or an annotated screenshot.

## Explicitly outside Day 12

- Coupon claims and example businesses remain Day 13 work.
- The Initiative meeting-point map redesign is tracked in `PROJECT_TODOS.md`; do not restore a free-text-only place field.
- Minor label/copy changes are collected for a deliberate UI-polish pass unless one blocks comprehension or safety.
- No Prabhag leaderboard, public points, public rank labels, Google profile photographs, public civic histories, precise locations or raw activity evidence.
