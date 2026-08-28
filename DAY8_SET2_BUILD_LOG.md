# Day 8 Set 2 build log

Date: 2026-08-27

Application implementation: `2df586c`, `1116d00`

Release hardening: `4c67342`, `4581295`, `4f477ea`

Deployed revision: `seewik-api-00026-yen`

Deployed application SHA: `4f477eae7e23724c7b197d74f178e9af650e6751`

Rollback revision: `seewik-api-00021-rat`

## Outcome

The application portion of Set 2 is complete in production. Initiative organisers can cancel a published activity or mark it completed, joined citizens can see the final status, invalid or cross-user transitions are rejected, and the event history remains append-only. Initiative rewards remain zero.

The release path now requires a green `main`: repository policy, whitespace, 177 backend tests, evaluation checksum integrity, frontend tests and build, the frontend dependency audit, and the fixed high/critical dependency gate must all pass before deployment can start. Deployment uses a short-lived repository identity, tests a zero-traffic backend candidate, shifts traffic only after health succeeds, publishes the frontend afterward, verifies production routes, and retains a rollback path.

Survey scoring was deliberately deferred while response collection continues. The frozen answer key was not changed or opened for revision. This does not block the application lifecycle, security, or deployment work completed here.

## Initiative minimum lifecycle

Only these transitions were added:

- `PUBLISHED → CANCELLED`;
- `PUBLISHED → COMPLETED`.

Rules enforced by the backend:

- only the organiser can perform either transition;
- cancellation requires a non-blank reason;
- completion is rejected before the scheduled start time;
- cancellation after completion and completion after cancellation are rejected;
- repeating the same successful transition is idempotent;
- a repeated transition does not add another event;
- the originally published activity facts remain unchanged;
- cancelled and completed activities are excluded from upcoming nearby discovery;
- joined citizens can see their final-status activities through `My activities`;
- public responses still omit organiser UID and raw coordinates;
- neither transition creates a reward entry or changes points.

Creation and joining retain their existing append-only zero-point ledger entries. Cancellation and completion add only Initiative transition events.

## Citizen interface

The Initiate screen now includes `My activities`. Organisers see the allowed action for a published activity. Joined citizens see cancelled or completed status without receiving organiser controls. Success and failure messages use an announced status region, buttons have visible focus treatment, and the relevant controls meet the existing minimum touch-target treatment.

The existing report flow also received an evidence-freshness correction. Changing the photograph or complaint text clears the earlier classification, confirmed facts, route and draft. A new classification replaces the facts with evidence from the current submission. This prevents a prior snack/gift photograph description from surviving after the citizen selects a drainage photograph.

## Focused security verification

The security work was limited to paths affected by Set 2 and reused existing authentication and Set 1 protection coverage rather than repeating unrelated tests.

Verified locally or in the complete regression suite:

- organiser-only cancellation and completion;
- cancellation reason and scheduled-time checks;
- invalid and opposite final transitions;
- idempotent transition events;
- malformed coordinates, unsupported radius, past date and more-than-one-year future date;
- missing authentication rejection and verified Firebase identity ownership;
- concurrent rate-limit protection and model-timeout cleanup from Set 1;
- direct client writes to Initiative documents, Initiative events and the points ledger are denied by production Firestore rules;
- no tracked cloud private key or raw survey export pattern was found.

The live two-user test confirmed:

1. an attempted client participant count of `999` was ignored and creation began at one;
2. two simultaneous joins by the same participant produced one `JOINED`, one `ALREADY_JOINED`, and a stored count of two;
3. the participant could not cancel or complete the organiser's activity;
4. organiser cancellation was idempotent and created one transition event;
5. a participant could see the cancellation status and reason without organiser controls;
6. organiser completion after the scheduled time was idempotent;
7. opposite final transitions returned conflict responses;
8. all creation and join ledger entries awarded zero points;
9. the two temporary activities, their events, participations, ledger entries and both temporary users were removed.

## Green-main release path

`Quality` runs on each `main` push and pull request. It performs:

- repository content policy check;
- Git whitespace check;
- complete backend tests, including frozen evaluation integrity;
- frontend tests and production build;
- frontend high-severity dependency audit;
- fixed high/critical repository dependency scan.

`Deploy green main` starts only after a successful `Quality` push on `main`. It authenticates through a repository- and branch-restricted workload identity; no long-lived cloud key is stored. It records the current healthy revision, deploys the backend with no traffic, checks the candidate directly, moves traffic, builds and publishes the same frontend commit, verifies the backend plus six refresh-safe frontend routes, and removes the temporary candidate URL. Documentation-only changes do not deploy the application again.

The deployed SHA is recorded in both the Cloud Run `commit-sha` label and `SEEWIK_GIT_SHA` environment value. Local `HEAD`, `origin/main`, the successful quality run, the deployment run and the deployed revision all agreed on `4f477eae7e23724c7b197d74f178e9af650e6751` before this documentation-only commit.

## Dependency gate finding

The first new quality run correctly stopped deployment because the older backend dependency set contained fixed high/critical findings. The gate was not weakened. The supported backend baseline was updated to Spring Boot `3.5.14`, Spring Framework `6.2.19`, Tomcat `10.1.57`, Jackson `2.21.5`, Netty `4.1.136.Final`, and HttpCore `5.4.3`.

The complete backend suite passed after both dependency updates. The final high/critical scan passed. The frontend audit also reported no high-severity vulnerability.

## Deployment and rollback evidence

The first protected deployment built ready revision `seewik-api-00024-zuq` with zero traffic, but the script's traffic-tag query returned an empty URL. Candidate verification therefore stopped the release before traffic changed, restored `seewik-api-00021-rat` to 100%, did not publish the frontend, and left production healthy. The temporary failed-candidate tag was then removed.

The query was replaced with an exact JSON selection, and failure cleanup now also removes the candidate tag. The second protected deployment succeeded:

- quality run `33144593657`: success;
- deployment run `33144645239`: success;
- revision `seewik-api-00026-yen`: latest created and latest ready;
- production traffic: 100%;
- backend health: HTTP 200 JSON;
- frontend: published at `https://seewik.web.app`;
- refresh-safe route verification: six of six passed;
- previous healthy revision: `seewik-api-00021-rat` remains ready for rollback.

Rollback procedure: route 100% traffic to the recorded previous healthy revision, verify `/health`, and investigate the candidate without publishing its frontend. The automated workflow performs the backend traffic restoration if any release step fails after the rollback revision is recorded.

## Final gates

- backend: 177 tests passed, 0 failures, 0 errors, 0 skipped;
- frontend: 5 tests passed;
- frontend production build: passed;
- frontend dependency audit: passed;
- fixed high/critical dependency gate: passed;
- evaluation checksum integrity: passed;
- repository content policy: passed;
- Git whitespace: passed;
- live lifecycle integration: 1 test passed;
- production direct-write rules check: passed;
- temporary test data and users: removed.

The current production bundle remains approximately 807.28 kB minified and 239.78 kB gzip. The existing large-chunk advisory is reserved for the ordered Day 9 performance work.

## Post-release privacy audit

Only match counts were retained; raw production log text was not copied into evidence. Revision `seewik-api-00026-yen` had zero matches for bearer content, ID-token structures, the known lifecycle test titles/descriptions/reason, encoded-image markers, multipart metadata and the Firebase web key. It also had zero error-severity entries during the verification window.

## Remaining boundaries

- Survey scoring remains deferred until the product owner closes or pauses response collection. The frozen key must remain unchanged.
- Independent ambiguity review is still unavailable with one reviewer.
- Initiative verification and non-zero reward weights are not implemented; all Initiative points remain zero.
- Accessibility, language, boundary/map work, performance/code splitting, Track B and verification design move to the ordered Day 9 work.
- Synthetic prabhag boundaries remain non-official development data and require citizen confirmation; manual Prabhag 1-20 selection remains available.
- No alert policies were created.

Sanitized machine-readable release evidence: [data/eval/results/day8-set2-production-verification-2026-08-27.json](data/eval/results/day8-set2-production-verification-2026-08-27.json).
