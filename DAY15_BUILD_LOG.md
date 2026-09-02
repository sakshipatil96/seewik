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
