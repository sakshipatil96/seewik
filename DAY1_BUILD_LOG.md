# Seewik Day 1 build log

Date: 2026-08-21 (America/Los_Angeles)

## Scope

Minimal Java 21/Spring Boot API, React/TypeScript/Vite mobile-first PWA, Firebase foundation, Cloud Run deployment, and a real Vertex AI Gemini smoke call. Later-product features are intentionally excluded.

## Verified starting state

- Google Cloud project: `seewik`
- Active gcloud account: `patilsakshi.siya@gmail.com`
- ADC: valid; quota project set to `seewik`
- Java: 21 arm64; Node: arm64; Maven available
- Firebase web app: `seewik-web` (`1:528138216934:web:868a6101ff9890dbc4e227`)

## Decisions

- Backend package: `com.seewik.api`
- Cloud Run service/region: `seewik-api` / `asia-south1`
- Gemini: `gemini-3.7-flash`, Vertex AI location `global`, authenticated with ADC/service identity
- Firebase test data is isolated under `day1_checks/{uid}` and guarded by per-user anonymous-auth rules.
- No application secrets or credential files are committed. Firebase web configuration is a public client identifier, not a server credential.

## Build and verification results

- Backend Maven tests/package: PASS (`Tests run: 1, Failures: 0, Errors: 0`; `BUILD SUCCESS`)
- Local `GET /healthz`: `{"status":"ok","service":"seewik-api"}`
- Real text Gemini call through Spring Boot + ADC: `Seewik Gemini smoke OK`
- Real multimodal Gemini call through Spring Boot + ADC using the harmless frontend screenshot: correctly described the Seewik UI and its cream/mint/forest-green palette.
- Frontend TypeScript/Vite production build: PASS
- Firebase anonymous auth: PASS; test UID created
- Firestore Standard `(default)` database: created in `asia-south1`; authenticated test write/read returned `true`
- Cloud Storage for Firebase default bucket: `seewik.firebasestorage.app`, `ASIA-SOUTH1`; authenticated SVG upload/read returned HTTP `200/200`
- Firestore and Storage rules: compiled and deployed successfully
- Browser smoke: backend health, anonymous auth, Firestore write/read, and Storage upload/read all displayed `ok`
- Cloud Run deployed revision: `seewik-api-00002-c84`, serving 100% of traffic
- Public `GET /health`: `{"status":"ok","service":"seewik-api"}`
- Public deployed Gemini smoke: `Deployed Seewik Gemini OK`
- Firebase Hosting deploy: PASS
- Deployed frontend → Cloud Run → Firebase browser smoke: PASS; all four displayed checks succeeded

## Problems encountered

- Initial ADC refresh was blocked by the workspace network sandbox, not by missing credentials. After scoped network/config permission, ADC refreshed successfully.
- npm's default cache contains root-owned files. Used a disposable cache under `/private/tmp` rather than modifying user-owned cache permissions.
- Maven's default cache was outside the writable workspace. Used `backend/.m2`, which is ignored by Git.
- First Cloud Run source upload failed because the default compute/build identity lacked read access to the source bucket. A bucket-scoped `roles/storage.objectViewer` binding fixed source retrieval.
- The next Cloud Run build exposed that the same identity lacked Google’s standard `roles/run.builder` permission. The user explicitly approved the persistent project-wide grant; deployment then succeeded.
- The first browser Storage read failed because one rule applied `request.resource.size` to reads, where `request.resource` is absent. Split read/write rules and configured the bucket CORS origins; browser verification then passed.
- The first deployed browser auth check failed because the initial service worker intercepted cross-origin Firebase requests. Restricted offline interception to same-origin GET requests, replaced the cache, redeployed, and verified all checks.
- Cloud Run reserves some paths ending in `z`; public `/healthz` is intercepted by Google and returns 404 before Spring Boot. The backend retains local `/healthz` and exposes `/health` as its verified public equivalent.

## URLs

- Firebase Console: `https://console.firebase.google.com/project/seewik/overview`
- Cloud Run: `https://seewik-api-528138216934.asia-south1.run.app`
- Public health: `https://seewik-api-528138216934.asia-south1.run.app/health`
- Firebase Hosting: `https://seewik.web.app`

## Screenshot

- Deployed browser-verified frontend: `day1-deployed-verification.jpg`

## Unresolved

- The exact locked civic example image was not identifiable. Multimodal wiring is proven with a harmless UI screenshot; the locked civic-image proof remains a follow-up.
- A bare public Cloud Run `/healthz` is impossible because it is a reserved path; use the verified `/health` endpoint. Local `/healthz` remains implemented and tested.
