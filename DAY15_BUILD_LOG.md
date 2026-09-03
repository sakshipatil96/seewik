# Day 15 build log - unplanned UI changes

Date: 2026-09-02

## Purpose

Day 15 remains focused on Touchpoint 2 and the 90-second demonstration. Before continuing those tasks, this release applies owner-requested interface and initiative-flow improvements so the production experience is clear, consistent and ready to demonstrate.

## Changes completed

- Refined the responsive header with a larger SEEWIK brand, persistent "Civic Action to Community Impact" tagline, accessible language selector and consistent control sizing.
- Simplified desktop navigation while keeping Report and Community available in mobile navigation.
- Added the Home greeting and authenticated Civic Points treatment using the civic-action teal palette.
- Changed Home's Initiate action to open initiative creation directly and redesigned `/initiatives` as the Community discovery feed.
- Added richer activity cards, activity details, joining confirmation, calendar and native sharing actions, plus organiser-only management links.
- Reworked Create Initiative into a five-step review-before-publish flow with activity templates, Google meeting-place map, start/end times, needed-item chips, participation modes, capacity and organiser messaging.
- Added approval-required initiative participation with protected organiser accept/decline controls.
- Refined New Report to use one Add Photo action, clearer category selection, collapsible confirmed steps and citizen-facing route choices.
- Standardised page headings, card spacing, responsive layout and reusable line icons across Home, reporting, initiatives, Civic Card, awareness and emergency information.
- Removed redundant headings, internal diagnostic wording and unnecessary citizen-facing explanatory text.
- Added phone icons to every Emergency Information call button.

## Trust boundaries preserved

- Gemini may suggest report wording and a category, but the citizen confirms them and the verified Civic Pack determines the responsible authority and official channel.
- Seewik prepares complaint routes and drafts; it does not claim a complaint was sent unless the citizen actually sends it.
- Recognition points remain lifetime, non-deducting thresholds rather than money or transferable value.
- No IAM, billing, Cloud Run minimum-instance or production-data changes were made.

## Validation before release

- Backend: 222 tests passed, 0 failures, 0 errors.
- Frontend: 74 tests passed.
- Frontend production build: passed.
- Frontend dependency audit: 0 vulnerabilities.
- Secret-safe diagnostics policy: passed.
- Prabhag boundary checksum: passed.
- Source whitespace validation: passed.

## Release evidence

- Application commit: `a803c7a904ead7930517d7cdd53db9f922dfb414`
- Application Quality workflow: `https://github.com/sakshipatil96/seewik/actions/runs/33696653544` - passed
- Application Deploy workflow: `https://github.com/sakshipatil96/seewik/actions/runs/33696743438` - passed
- Cloud Run revision: `seewik-api-00094-xum`
- Image digest: `sha256:544030af60d02901d9445df0992ad7ca88f396a7ddcb04b44dda5c3fcfa2f823`
- Production health: passed

## Day 15 impact

These changes improve the screens used around the demonstration without changing the Touchpoint 2 evidence, evaluation results, BigQuery role or central trust-boundary message. Touchpoint 2 preparation and the 90-second demo remain the next priorities after production verification.

## Set 3 and README follow-up — local, not yet released

Completed after the application release:

- Replaced last-commit-only deployment detection with a Quality-produced artifact containing the complete pushed commit range. Deployment validates that the tested end SHA matches its candidate and conservatively deploys if the beginning SHA is unavailable.
- Added `data/prabhags/`, both delivery workflows, Civic Awareness and Emergency Information to the applicable production-change and route-verification coverage.
- Added three disposable-emulator Firestore/Storage protection tests. They cover owner reads, Google-linked draft writes, anonymous/cross-owner denial, backend-owned civic-record forgery denial, protected media access and the one-megabyte upload limit without touching production data.
- Added and cross-linked a repository README covering the product, architecture, central trust boundary, local setup, verification, delivery controls and known limitations.

Local verification after these changes:

- Backend: 222 tests passed, 0 failures, 0 errors.
- Frontend: 74 tests passed.
- Firestore/Storage disposable-emulator suite: 3 tests passed.
- Frontend production build: passed; the existing large-chunk advisory remains.
- Frontend dependency audit: 0 vulnerabilities.
- Secret-safe diagnostic policy: passed.
- Prabhag boundary checksum: passed.
- Workflow YAML parsing and source whitespace validation: passed.
- Repository content scan passed for tracked files and the new Set 3 files. The full local workspace scan still identifies the two pre-existing untracked handoff documents because their local absolute paths contain a blocked tooling term; they were not edited, staged or included in this work.

One signed-out simulated-mobile Lighthouse 13.4.1 run against `https://seewik.web.app/` recorded performance 54, accessibility 96, best practices 100 and SEO 82. Its key lab metrics were FCP 6.6 s, LCP 7.4 s, total blocking time 0 ms and CLS 0.148. This is one throttled lab measurement, not an SLA or a field-performance claim; no bundle refactor was started on Day 15.

This follow-up has not been committed, pushed or deployed. The Day 15 Set 2 narration, rehearsals and backup video remain deferred to the owner-approved next work session.
