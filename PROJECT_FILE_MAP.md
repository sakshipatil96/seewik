# Seewik / Patchamomma project file and service map

This map is for handoffs and repository study. Generated directories such as `frontend/node_modules`, `frontend/dist`, `backend/target`, `.m2`, font caches and temporary PDF page renders are intentionally excluded. Never copy credentials, access tokens or OAuth client secrets into a handoff, issue, log or commit.

## First-read order

1. Current handoff: `SEEWIK_PROJECT_HANDOFF_START_DAY12.md`
2. New work: `DAY12_CHECKLIST.md`
3. Day 12 progress: `DAY12_BUILD_LOG.md`
4. Day 12 contract: `data/contracts/day12-recognition-privacy-content-contract-v0.1.md`
5. Product follow-ups: `PROJECT_TODOS.md`
6. Day 13 rewards: `DAY13_CHECKLIST.md`
7. Touchpoint 3 business case: `TOUCHPOINT3_BUSINESS_CASE.md`
8. Parent project guide: `../Seewik_what_we_built till now.md`
9. Version history: `CHANGELOG.md`
10. Identity contract: `data/contracts/day10-identity-migration-contract-v0.1.md`
11. Language/boundary contract: `data/contracts/day9-language-boundary-contract-v0.1.md`
12. Main frontend: `frontend/src/main.tsx`
13. Initiative backend: `backend/src/main/java/com/seewik/api/InitiativeController.java`, `InitiativeService.java`, `InitiativeGateway.java`, `FirestoreInitiativeGateway.java` and `AttendanceCodeService.java`
14. Security rules: `firestore.rules` and `storage.rules`
15. Required gates: `.github/workflows/quality.yml` and `.github/workflows/deploy.yml`
16. Credential-handling record: `SECURITY_FINDINGS.md`

## Project roots and handling rules

- Git repository: this directory (`seewik/`).
- Patchamomma project root: the parent directory (`../`).
- Parent guide: `../Seewik_what_we_built till now.md`.
- Project instructions: `../AGENTS.md`
- Synced `../sources/`: currently empty. If files appear there later, they are read-only reference material and must not be edited, renamed, moved or deleted.
- Private Track B archive: `../private-evaluation/track-b-v0.1`. Never commit its raw response envelopes or private images.
- Official-source research: `../research/nandurbar_ward_maps_2026-08-21`.
- Generated overview PDFs and map outputs: `../output`. Treat as derived artifacts, not runtime inputs.
- `../tmp` contains generated/intermediate files and is not authoritative project state.
- Original pasted project handoff: task attachment only. Treat it as historical context; current repository contracts/build logs take precedence where the project has since changed.

## Build logs, checklists and design records

- `DAY1_BUILD_LOG.md` — cloud/PWA/Firebase/Vertex foundation.
- `DAY2_BUILD_LOG.md` — Civic Pack v0.1, prabhags, BigQuery and synthetic boundary safeguards.
- `DAY3_BUILD_LOG.md` — Civic Pack v0.2, constrained classification and voice feasibility.
- `DAY4_BUILD_LOG.md` — English/Marathi complaint drafting and Firestore drafts.
- `DAY5_BUILD_LOG.md` — report lifecycle, dedupe, points and BigQuery analytics.
- `DAY6_BUILD_LOG.md` — saved-report workspace, URL-backed screens and points UI.
- `DAY7_BUILD_LOG.md` — Initiate MVP, protected model endpoints and text evaluation.
- `DAY7_BIGQUERY_EVIDENCE.md` — Day 7 production/controlled-fixture analytical evidence.
- `DAY8_SET1_BUILD_LOG.md` — rate limits, timeouts, circuit breaker, metrics and capacity evidence.
- `DAY8_SET2_BUILD_LOG.md` — Initiative completion/cancellation and green-main deployment.
- `DAY9_CHECKLIST.md` / `DAY9_BUILD_LOG.md` — languages, approximate map, Track B image evaluation and closure.
- `DAY10_CHECKLIST.md` / `DAY10_BUILD_LOG.md` — recoverable Google profiles, write enforcement and production closeout.
- `DAY11_CHECKLIST.md` — completed attendance implementation checklist and remaining device QA boundary.
- `DAY11_BUILD_LOG.md` — attendance/reward implementation and release evidence.
- `SEEWIK_PROJECT_HANDOFF_START_DAY12.md` — self-contained Day 12 kickoff with current production state and frozen recognition decisions.
- `DAY12_CHECKLIST.md` — planned opt-in top-three recognition, private points, client-side sharing and sourced-awareness work.
- `DAY12_BUILD_LOG.md` — Sets 1-6 profile, recognition, sharing, sourced-awareness, emergency and local verification evidence.
- `SECURITY_FINDINGS.md` — named credential-diagnostic findings, persistence scope, remediation and prevention rules; never contains credential values.
- `DAY13_CHECKLIST.md` — contribution/reward/coupon contract, completed local checks and remaining release gates.
- `TOUCHPOINT3_BUSINESS_CASE.md` — survey-grounded demand case, proposed revenue loop, illustrative unit economics, pilot requirements and explicit demonstration boundaries.
- `PROJECT_TODOS.md` — routing simplification, Initiative meeting-point redesign and UI-polish follow-ups.
- `DESIGN_REFERENCE.md` — visual direction.
- `CHANGELOG.md` — versioned implementation history.

## Frontend application

Runtime and configuration:

- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/tsconfig.json`
- `frontend/index.html`
- `frontend/public/manifest.webmanifest`
- `frontend/public/sw.js`

Application code:

- `frontend/src/main.tsx` — app screens, Improve flow, reports, points and Initiatives.
- `frontend/src/styles.css` — main responsive styling.
- `frontend/src/firebase.ts` — public Firebase web configuration.
- `frontend/src/i18n.ts` — English/Marathi/Hindi catalogue.
- `frontend/src/AccountControl.tsx` — profile/sign-in/sign-out UI.
- `frontend/src/accountIdentity.ts` — identity state model.
- `frontend/src/accountService.ts` — anonymous session, Google link, collision and recovery behavior.
- `frontend/src/apiConfig.ts` — shared backend API origin.
- `frontend/src/recognitionClient.ts` — public recognition and owner-private recognition/points API contracts.
- `frontend/src/RewardCatalogue.tsx` — Day 13 example reward tiers, claim-code states and simulated-use interface.
- `frontend/src/RecognitionPanel.tsx` — public names-only monthly thank-you and displayed-name report UI.
- `frontend/src/RecognitionSettings.tsx` — explicit public-name preview, opt-in, edit and withdrawal UI.
- `frontend/src/ContributionPoster.tsx` / `civicCardImage.ts` — explicit local Civic Card image generation, platform share and download fallback.
- `frontend/src/CivicAwarenessPage.tsx` — approved sourced awareness topics and related Seewik actions.
- `frontend/src/EmergencyInformationPage.tsx` — signed-out national and Nandurbar emergency call interface.
- `frontend/src/sourcedContent.ts` — source freshness and emergency-call validation.
- `frontend/src/content/*.json` — versioned English awareness and emergency source records.
- `frontend/src/reportNavigation.ts` — refresh-safe route parsing.
- `frontend/src/PrabhagBoundaryMap.tsx` / `PrabhagBoundaryMap.css` — lazy-loaded approximate boundary guide.
- `frontend/src/vite-env.d.ts`

Frontend tests and production probes:

- `frontend/scripts/account-identity.test.mjs`
- `frontend/scripts/boundary-integrity.test.mjs`
- `frontend/scripts/boundary-ui.test.mjs`
- `frontend/scripts/contribution-poster.test.mjs`
- `frontend/scripts/copy-safety.test.mjs`
- `frontend/scripts/evidence-reset.test.mjs`
- `frontend/scripts/i18n-accessibility.test.mjs`
- `frontend/scripts/image-evaluation-integrity.test.mjs`
- `frontend/scripts/report-navigation.test.mjs`
- `frontend/scripts/recognition.test.mjs`
- `frontend/scripts/rewards.test.mjs`
- `frontend/scripts/sourced-content.test.mjs`
- `frontend/scripts/test-firestore-draft-ownership.mjs`
- `frontend/scripts/test-firestore-initiative-protection.mjs`
- `frontend/scripts/test-google-write-enforcement.mjs`
- `frontend/scripts/test-production-ownership.mjs`

## Backend application

Build/configuration:

- `backend/pom.xml`
- `backend/.gcloudignore`
- `backend/src/main/resources/application.properties`
- `backend/src/main/java/com/seewik/api/SeewikApiApplication.java`
- `backend/src/main/java/com/seewik/api/WebConfig.java`

Classification and complaint drafting:

- `ClassificationController.java`
- `ClassificationControllerAdvice.java`
- `CivicClassificationService.java`
- `ClassificationPromptFactory.java`
- `ClassificationSchemaValidator.java`
- `ComplaintDraftController.java`
- `ComplaintDraftService.java`
- `ComplaintDraftValidator.java`
- `ComplaintPromptFactory.java`
- `GeminiGateway.java`
- `GeminiService.java`
- `ModelCallExecutor.java`

Routing and prabhags:

- `ApiController.java`
- `CivicRouterService.java`
- `BigQueryConfig.java`
- `GoogleBigQueryPrabhagGateway.java`
- `PrabhagBoundaryGateway.java`
- `PrabhagResolverService.java`
- `PrabhagCircuitBreaker.java`
- `LastKnownGoodPrabhagSnapshot.java`

Identity, rate limiting and metrics:

- `CitizenIdentityVerifier.java`
- `FirebaseCitizenIdentityVerifier.java`
- `FirebaseAdminProvider.java`
- `PaidEndpointRateLimiter.java`
- `FirestorePaidEndpointRateLimiter.java`
- `RateLimitPolicy.java`
- `OperationalMetrics.java`

Private profiles and Day 12 recognition:

- `CitizenAccountDirectory.java`
- `FirebaseCitizenAccountDirectory.java`
- `CitizenProfileController.java`
- `CitizenProfileService.java`
- `CitizenProfileGateway.java`
- `FirestoreCitizenProfileGateway.java`
- `RecognitionController.java`
- `RecognitionService.java`
- `RecognitionGateway.java`
- `FirestoreRecognitionGateway.java`

Reports, lifecycle, points and analytics:

- `ReportLifecycleController.java`
- `ReportLifecycleService.java`
- `ReportLifecycleContract.java`
- `ReportLifecycleGateway.java`
- `FirestoreReportLifecycleGateway.java`
- `ReportDedupeEvaluator.java`
- `LifecycleAnalyticsPublisher.java`
- `BigQueryLifecycleAnalyticsPublisher.java`

Initiatives and Day 11 attendance extension points:

- `InitiativeController.java`
- `InitiativeService.java`
- `InitiativeGateway.java`
- `FirestoreInitiativeGateway.java`
- `AttendanceCodeService.java`

Backend tests live under `backend/src/test/java/com/seewik/api`. The most relevant Day 11 regression files are:

- `InitiativeControllerTest.java`
- `InitiativeServiceTest.java`
- `InitiativeAttendanceServiceTest.java`
- `AttendanceCodeServiceTest.java`
- `ProductionDay11AttendanceIT.java`
- `ProductionDay13RewardsIT.java`
- `ProductionDay13RewardsHttpIT.java`
- `ProductionDay13Legacy40PointAuditIT.java`
- `ProductionDay7ReleaseIT.java`
- `ProductionDay8Set2ReleaseIT.java`
- `FirebaseCitizenIdentityVerifierTest.java`
- `ReportLifecycleServiceTest.java`
- `ReportLifecycleControllerTest.java`
- `FirestoreReportLifecycleGatewayTest.java`
- `CivicPackIntegrityTest.java`
- `EvaluationCaseSetTest.java`

The complete backend suite currently contains 215 passing tests. The frontend suite contains 58 passing tests.

## Runtime schemas and packaged civic data

- `backend/src/main/resources/civic-pack-v0.1.json`
- `backend/src/main/resources/civic-pack-v0.2.json`
- `backend/src/main/resources/classification-schema-v0.1.json`
- `backend/src/main/resources/classification-response-schema-vertex-v0.1.json`
- `backend/src/main/resources/complaint-draft-schema-v0.1.json`
- `backend/src/main/resources/complaint-draft-response-schema-vertex-v0.1.json`
- `backend/src/main/resources/report-lifecycle-schema-v0.1.json`
- `backend/src/main/resources/dedupe-evaluation-schema-v0.1.json`
- `backend/src/main/resources/points-ledger-schema-v0.1.json`
- `backend/src/main/resources/points-ledger-schema-v0.3.json`
- `backend/src/main/resources/initiative-attendance-schema-v0.1.json`
- `backend/src/main/resources/prabhag-snapshot-synthetic-v0.1.geojson`

Day 11 adds versioned attendance and Initiative points contracts without rewriting the older schemas.

## Firebase and delivery configuration

- `.firebaserc`
- `firebase.json`
- `firestore.rules`
- `storage.rules`
- `storage-cors.json`
- `.github/workflows/quality.yml`
- `.github/workflows/deploy.yml`
- `.github/dependabot.yml`
- `scripts/check_repository_content.sh`
- `scripts/check_secret_safe_diagnostics.sh`
- `scripts/describe_production_release.sh`

Quality runs repository policy, secret-safe diagnostic policy, whitespace, boundary checksum, Java tests, frontend tests/build/audit and a high/critical vulnerability scan. Deployment starts only from a successful `main` Quality run, creates a no-traffic backend candidate, verifies health, moves traffic, deploys Hosting/Firestore/Storage rules, verifies routes and retains rollback behavior. Production release evidence must use `scripts/describe_production_release.sh`, which exposes only allow-listed non-secret metadata.

## Contracts

- `data/contracts/day8-set1-protection-contract-v0.1.md`
- `data/contracts/day8-set1-cleanup-contract-v0.2.md`
- `data/contracts/day9-language-boundary-contract-v0.1.md`
- `data/contracts/day10-identity-migration-contract-v0.1.md`
- `data/contracts/day11-attendance-reward-contract-v0.1.md`
- `data/contracts/day12-recognition-privacy-content-contract-v0.1.md`
- `data/content/sourced-content-schema-v0.1.json`

The Day 11 contract freezes attendance windows, integrity boundaries, exact 5/20/40/60 reward values and the forward-only reward-policy migration. The Day 12 contract freezes private profile migration, consent, names-only monthly recognition and sourced-content boundaries.

## Civic/ward/prabhag data

Prabhag data:

- `data/prabhags/README.md`
- `data/prabhags/bigquery-schema.json`
- `data/prabhags/synthetic-boundaries-v0.1.geojson`
- `data/prabhags/synthetic-boundaries-v0.1.ndjson`
- `data/prabhags/synthetic-boundaries-v0.1.sha256`
- `data/prabhags/official-map-digitized-boundaries-v0.1.geojson`
- `data/prabhags/official-map-digitized-boundaries-v0.1.sha256`
- `data/prabhags/generate_synthetic_boundaries.py`
- `data/prabhags/generate_official_map_digitized_boundaries_v0_1.py`

Ward/source data:

- `data/wards/WARD_SOURCE_NOTES.md`
- `data/wards/nandurbar-ward-source-v0.1.json`
- `data/wards/wards.ndjson`
- `data/wards/bigquery-schema.json`

The synthetic dataset remains active for automatic suggestions. The official-map-derived trace is an inactive approximate visual guide pending georeference review.

## BigQuery analytics

- `data/bigquery/report-lifecycle-events-schema.json`
- `data/bigquery/report-dedupe-evaluations-schema.json`
- `data/bigquery/analytics-event-exclusions-schema.json`
- `data/bigquery/day5-lifecycle-analytics.sql`
- `data/eval/day7-bigquery-production.sql`
- `data/eval/day7-bigquery-controlled-fixture.sql`

BigQuery dataset: `seewik.seewik_civic`, location `asia-south1`.

## Evaluation sources, tooling and results

Policy and case sets:

- `data/eval/EVALUATION_POLICY.md`
- `data/eval/classification-cases-v0.1.json`
- `data/eval/classification-cases-v0.2.json`
- `data/eval/routing-cases-v0.1.json`
- `data/eval/voice-cases-v0.1.json`
- `data/eval/human-baseline-answer-key-v0.1.json`
- `data/eval/classification-image-cases-v0.1-draft.json`

Evaluation scripts:

- `scripts/run_classification_eval.sh`
- `scripts/run_day7_deployed_evaluation.sh`
- `scripts/run_image_classification_eval.sh`
- `scripts/run_track_b_deployed_evaluation.sh`
- `scripts/run_track_b_deployed_diagnostic.sh`
- `scripts/summarize_image_classification_eval.sh`

Committed results are under `data/eval/results/` and include:

- Day 3 initial classification and voice results.
- Day 7 two-run Track A raw rows, summaries and repeatability report.
- Day 7 BigQuery production and controlled-fixture evidence.
- Day 8 protection/latency/forced-failure and release verification.
- Day 9 Track B run 1, run 2, diagnostic and repeatability summaries.

Private Track B material is under `../private-evaluation/track-b-v0.1`:

- `private-manifest.json`
- `sanitized/TB-IMG-001.jpg` through `TB-IMG-008.jpg`
- private raw-response envelopes for run 1, run 2 and diagnostic 1

The user confirmed full family-photographer permission for private evaluation and privacy-sanitized copies. Never commit the private archive or raw source photographs.

## Official-source research

Start with:

- `../research/nandurbar_ward_maps_2026-08-21/SOURCE_LOG.md`

Saved official/reference downloads are in:

- `../research/nandurbar_ward_maps_2026-08-21/official_downloads/`

They include Maharashtra SEC ward-formation/reservation/election documents, Nandurbar district/taluka material, DMA municipal contact/register material and the 2025/2026 member-results sources. Some Shahada documents are retained as research context and must not be misrepresented as Nandurbar city boundaries.

## Evidence and overview artifacts

Committed product screenshots:

- `day1-deployed-verification.jpg`
- `day1-frontend-verification.jpg`
- `day1-mobile-verification.jpg`
- `day2-bigquery-runtime-verification.png`
- `day3-classifier-routing-mobile.jpg`
- `day3-deterministic-route-mobile.jpg`
- `day4-complaint-draft-panel.png`
- `day4-complaint-draft-verification.png`
- `day5-lifecycle-demo-desktop.png`
- `day5-lifecycle-demo-mobile.png`
- `day6-app-home-desktop.png`
- `day6-mobile-navigation.png`
- `day7-initiatives-production-desktop.png`
- `evidence/day10-production-en.jpg`
- `evidence/day10-production-mr.jpg`
- `evidence/day10-production-hi.jpg`

Derived overview PDFs:

- `../output/pdf/Seewik_Community_Overview.pdf`
- `../output/pdf/Seewik_Developer_Overview.pdf`

Derived map-review outputs:

- `../output/nandurbar_ward_map_enhanced/`

## Source-control references

- GitHub repository: `https://github.com/sakshipatil96/seewik`
- GitHub Actions: `https://github.com/sakshipatil96/seewik/actions`
- Tags: `citypack-v0.1`, `citypack-v0.2`, `boundaries-synthetic-v0.1`

## Production and cloud links

Citizen application:

- `https://seewik.web.app`
- `https://seewik.web.app/report/new`
- `https://seewik.web.app/reports`
- `https://seewik.web.app/points`
- `https://seewik.web.app/initiatives`
- `https://seewik.web.app/initiatives/new`

Backend:

- `https://seewik-api-528138216934.asia-south1.run.app/health`

Firebase:

- Project overview: `https://console.firebase.google.com/project/seewik/overview`
- Authentication: `https://console.firebase.google.com/project/seewik/authentication/users`
- Sign-in providers: `https://console.firebase.google.com/project/seewik/authentication/providers`
- Firestore: `https://console.firebase.google.com/project/seewik/firestore/databases/-default-/data`
- Storage: `https://console.firebase.google.com/project/seewik/storage`
- Hosting: `https://console.firebase.google.com/project/seewik/hosting/sites`
- Project settings: `https://console.firebase.google.com/project/seewik/settings/general`

Google Cloud:

- Dashboard: `https://console.cloud.google.com/home/dashboard?project=seewik`
- Cloud Run: `https://console.cloud.google.com/run/detail/asia-south1/seewik-api/metrics?project=seewik`
- Logs Explorer: `https://console.cloud.google.com/logs/query?project=seewik`
- BigQuery: `https://console.cloud.google.com/bigquery?project=seewik`
- Vertex AI: `https://console.cloud.google.com/vertex-ai?project=seewik`
- IAM: `https://console.cloud.google.com/iam-admin/iam?project=seewik`

## Current privacy and safety boundaries

- Do not commit raw civic photographs, raw Track B responses, credentials or tokens.
- Do not copy Google email, display name or photo into profile documents.
- Do not silently capture report or attendance location.
- Do not describe synthetic/approximately digitized boundaries as official GIS geometry.
- Do not allow Gemini to decide civic authority or route facts.
- Do not expose owner UIDs or raw Initiative coordinates publicly.
- Do not rewrite append-only lifecycle, attendance or points history.
- Delete production data only through an exact reviewed allowlist after read-only inspection.
- Keep example businesses/coupons visibly `DEMO_ONLY` until a real partnership exists.
