# Seewik Day 6 Build Log

Date: 2026-08-26

## Outcome

Day 6 turns the single-page prototype into an app-like saved-report workspace without weakening the Day 4 or Day 5 correctness rules. Citizens can move between stable screens, resume an owner-protected Firestore draft, open a filed report as an immutable record, review frozen civic-route facts and timeline events, and see points derived from the append-only ledger.

Implementation commit: `cf2a183` (`feat: add saved report workspace`).

## App structure

The interface now has six URL-backed screens:

| Screen | Path | Purpose |
|---|---|---|
| Home | `/` | Product entry points and the explicitly synthetic lifecycle demo |
| New report | `/report/new` | Evidence, citizen-confirmed category/prabhag, and deterministic route lookup |
| Complaint review | `/report/review?report={reportId}` | Edit, save, copy, and file an existing `DRAFT` |
| My reports | `/reports` | Owner-scoped saved drafts and immutable filed reports |
| Report detail | `/reports/{reportId}` | Frozen route facts, timeline, lifecycle actions, and derived points |
| My points | `/points` | Ledger-derived reward explanation and total |

- Desktop primary navigation and a `390 x 844` mobile bottom bar were verified.
- Browser Back, Forward, direct deep links, and refresh all preserve the correct screen.
- Firebase Hosting rewrites every app path to `index.html` so direct links remain refresh-safe.
- Start Over clears the current unsaved workflow. It does not delete or mutate saved Firestore reports.

## Saved reports and ownership

- My Reports authenticates the current anonymous citizen and queries Firestore by the exact `ownerUid`.
- Results are sorted in the client by `updatedAt`, avoiding a new composite-index dependency.
- An empty account gets an explicit empty state rather than synthetic examples.
- A saved `DRAFT` can be hydrated back into the workflow, including issue type, prabhag, complaint language/body, route identity, and pack version.
- Direct review links carry the report ID so a browser refresh can recover the same persisted draft.
- Another anonymous user cannot read the report and does not receive it in their owner-scoped dashboard query.

## Filed-report immutability and frozen facts

- Only `DRAFT` is editable or resumable.
- `FILED`, `OVERDUE`, `CLAIMED_FIXED`, `VERIFIED_FIXED`, and `REOPENED` open in read-only report detail.
- The Firestore rules deny complaint subject/body edits after filing, direct status mutations, and deletion.
- A filed report renders the `routeSnapshot` captured at filing: route, authority, department, source/review status, Civic Pack version, and route-specific known limitations.
- Later Civic Pack changes cannot silently rewrite a filed report.
- The detail view reads owner-protected lifecycle events and points entries, orders the timeline, derives the points total, and exposes only server-guarded actions valid for the current state.

Live two-anonymous-user verification passed:

- owner dashboard listed the same report first as `DRAFT` and then as `FILED`;
- the second user's owner query excluded it;
- filed complaint edits/resume, direct status mutation, and deletion were denied;
- route snapshot remained frozen;
- owner event/dedupe/points reads passed and cross-owner reads failed;
- client writes to server-owned event/dedupe/points records failed;
- first filing awarded 5 points;
- the disposable report, event, dedupe record, points entry, outbox entry, and both anonymous users were removed.

## Stale-state protection

- A saved draft records the exact issue type, prabhag, route ID, and Civic Pack version used to prepare it.
- Filing is blocked when any of those four values differs from the current confirmed workflow.
- Changing issue evidence, category, prabhag, or confirmation clears dependent route/draft state.
- Confirming a different synthetic candidate also clears the prior route.
- A dedicated test changes each fingerprint field independently and confirms that every mismatch is rejected.

## Deployment

- Public frontend: https://seewik.web.app
- Public backend: https://seewik-api-528138216934.asia-south1.run.app
- Backend health: HTTP `200`, `application/json`, `{"status":"ok","service":"seewik-api"}`
- Cloud Run revision: `seewik-api-00012-xvk` at 100% traffic (unchanged; Day 6 introduced no backend runtime change)
- Firebase Hosting release: PASS
- Desktop production browser check: PASS, no console errors, `Seewik systems online`.
- Mobile `390 x 844` production browser check: PASS, mobile navigation visible and desktop navigation hidden.
- Direct production `/report/new` refresh: PASS.
- Production Back/Forward path check: PASS.
- Desktop evidence: `day6-app-home-desktop.png`
- Mobile evidence: `day6-mobile-navigation.png`

## Release gates

- Backend regression suite: `128` passed, `0` failed, `0` errors, `0` skipped.
- Day 6 frontend contract tests: `3` passed, `0` failed.
  - all six static/dynamic paths;
  - only `DRAFT` editable/resumable;
  - category/prabhag/route/pack mismatches make a draft stale.
- Frontend TypeScript and production build: PASS.
- Firestore rules test: PASS.
- Live owner-dashboard, ownership, transition, frozen-route, and cleanup test: PASS.
- Production desktop/mobile/deep-link/history browser checks: PASS, no console errors.

## BigQuery housekeeping

The Day 5 production smoke-test row was deliberately left physically present, as agreed, and remains excluded from every product query.

- exclusions: `1`
- product lifecycle rows: `0`

No Day 6 product record was created by the local synthetic demo or by interface verification.

## What broke and what the test clarified

The first new filed-edit assertion was accidentally executed while the disposable report was still a `DRAFT`. Firestore correctly returned `200` because draft owners are allowed to edit. Moving the assertion to after the server-side `FILED` transition produced the intended `403`, and the corrected live test passed. This distinguishes legitimate draft editing from the immutable filed state instead of weakening both.

The local preview could not complete its cloud health fetch inside the restricted preview environment, but the deployed application reported `Seewik systems online`, the production browser had no console errors, and the direct backend health check returned HTTP `200` JSON.

## Known limitations and next evidence

- Civic Pack remains `v0.2`; all 11 routes remain `OFFICIAL_SOURCE + REVIEW_PENDING`, and department assignments remain `TYPICAL_STRUCTURE_UNVERIFIED` pending Nandurbar Municipal Council/domain review.
- No route has a verified SLA, so the system continues to report `OVERDUE_UNKNOWN` rather than inventing a deadline.
- Synthetic prabhag geometry remains a development-only candidate source that requires citizen confirmation; manual `SELF_REPORTED` selection remains available.
- The exact locked real civic example is still pending. No replacement example was fabricated.
- The frontend bundle is about `793 kB` minified (`237 kB` gzip); Vite's 500 kB warning is advisory, and code splitting remains deferred.
- Anonymous ownership persists only as long as the browser retains the same Firebase anonymous identity; account recovery is not implemented.
- The excluded Day 5 BigQuery smoke row can be physically deleted later; it currently contributes zero product rows.
