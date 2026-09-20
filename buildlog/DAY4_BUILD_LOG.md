# Seewik Day 4 Build Log

Date: 2026-08-24

## Outcome

Day 4 is complete. Seewik can now create English or Marathi complaint drafts after the citizen has confirmed an issue category and a deterministic Civic Pack route. Gemini is limited to wording `subject` and `body`; it cannot return or change the authority, department, route, prabhag, channel, SLA, escalation, contact details, or pack metadata.

The citizen can edit the generated subject and body, must explicitly review them before copying, and is told that nothing is submitted automatically. Each generated draft is persisted as a Firestore `DRAFT` owned by the current anonymous Firebase user.

## Revised Day 4 gate

- English complaint drafting: PASS
- Marathi complaint drafting: PASS
- Draft grounded in confirmed citizen facts and server-retrieved Civic Pack route: PASS
- Gemini unable to change authority, department, route, SLA, or escalation: PASS
- Missing facts remain explicit rather than invented: PASS
- Citizen edit, review, and copy flow: PASS
- Firestore `DRAFT` persistence: PASS
- Required report fields and version timestamps: PASS
- Anonymous-user ownership enforced by Firestore rules: PASS
- Unsupported/unconfirmed route tests: PASS
- Prompt-injection test: PASS
- Missing-facts tests: PASS
- Backend tests before deployment: PASS
- Frontend production build before deployment: PASS
- Backend and frontend deployment: PASS

## Drafting architecture

The request path is deliberately separated:

1. The citizen confirms an issue category and prabhag.
2. The Spring Boot service calls the deterministic Civic Pack router on the server.
3. Unsupported or unconfirmed routes stop before any Gemini call.
4. The prompt receives the route as trusted, immutable context and citizen text as escaped, untrusted evidence.
5. Gemini receives a response schema that permits only `subject` and `body`.
6. A strict local validator rejects malformed output, wrong-language output, unexpected routing fields, URLs, email addresses, or phone numbers.
7. The backend combines the validated wording with authority, route, prabhag, and version fields from deterministic server state.
8. The frontend saves the resulting report as an anonymous-owned Firestore `DRAFT` and requires citizen review before copy.

No complaint is sent automatically.

## Contracts and versions

- Civic Pack: `v0.2`
- Complaint draft version: `complaint-draft-v0.1`
- Complaint schema version: `complaint-draft-v0.1`
- Draft languages: `MR | EN`
- Gemini model: `gemini-3.7-flash`
- Vertex location: `global`
- Maximum model output: 2,048 tokens
- Firestore status: `DRAFT`

The Gemini response schema has exactly two required fields: `subject` and `body`. The final API response restores only deterministic route facts and application-controlled metadata.

## Firestore DRAFT model

Each report stores:

- `ownerUid`
- `status`
- `confirmedIssueType`
- `prabhagId`
- `routeId`
- `authority`
- `draftLanguage`
- `draftSubject`
- `draftBody`
- `packVersion`
- `schemaVersion`
- `createdAt`
- `updatedAt`

Firestore rules require authenticated anonymous ownership on create, read, update, and delete. After creation, only `draftSubject`, `draftBody`, and `updatedAt` may change. Route, authority, classification, ownership, versions, status, and creation time remain immutable.

### Live ownership probe

The deployed rules were tested using two temporary anonymous users:

- owner create: allowed
- owner read: allowed
- owner edit: allowed
- cross-owner read: denied
- cross-owner edit: denied
- owner delete: allowed
- temporary report and both temporary users: cleaned up

## Safety and failure behavior

The tests cover:

- supported confirmed route;
- English and Marathi output;
- category not confirmed;
- unsupported issue type;
- unconfirmed synthetic prabhag candidate;
- missing citizen facts;
- missing location carried as `LOCATION_OR_LANDMARK`;
- oversized citizen facts;
- citizen prompt injection escaped and separated from immutable route context;
- forbidden authority, department, route, channel, SLA, and escalation fields;
- invented URL, email, and phone details;
- malformed model JSON;
- schema failure separated from model-call failure; and
- controlled errors that do not expose raw model output.

The exact real Nandurbar civic example was not available. Browser and production smoke checks therefore use an explicitly synthetic pothole fixture and do not claim to be a real citizen report.

## Model evidence

### Local real-model checks

Marathi pothole draft:

- status: `DRAFT_READY`
- route: `NMC-PW-POTHOLE-v0.2`
- authority: `Nandurbar Municipal Council`
- latency: 8,507 ms
- prompt tokens: 622
- candidate tokens: 81
- total tokens: 1,364

English streetlight draft:

- status: `DRAFT_READY`
- route: `NMC-PW-STREETLIGHT-v0.2`
- authority: `Nandurbar Municipal Council`
- latency: 2,577 ms
- prompt tokens: 600
- candidate tokens: 61
- total tokens: 772

The first local complaint-draft model check used the classifier-sized 512-token output ceiling. Gemini returned a truncated response that failed schema validation, and the service failed closed. Complaint drafting now uses a separate 2,048-token ceiling; classification remains unchanged at 512.

### Production smoke checks

Marathi pothole draft:

- HTTP 200
- route: `NMC-PW-POTHOLE-v0.2`
- prabhag: `PRABHAG-03`
- authority: `Nandurbar Municipal Council`
- pack/schema: `v0.2` / `complaint-draft-v0.1`
- citizen review required: `true`
- latency: 6,136 ms
- prompt/candidate/total tokens: 622 / 82 / 1,162

English streetlight draft:

- HTTP 200
- route: `NMC-PW-STREETLIGHT-v0.2`
- prabhag: `PRABHAG-04`
- authority: `Nandurbar Municipal Council`
- pack/schema: `v0.2` / `complaint-draft-v0.1`
- citizen review required: `true`
- latency: 3,782 ms
- prompt/candidate/total tokens: 605 / 60 / 773

These are functional smoke checks, not a complaint-quality evaluation.

## Test and browser evidence

- Full Spring Boot suite: 100 passed, 0 failed, 0 errors, 0 skipped
- Focused controller regression after the final message correction: 4 passed
- Frontend TypeScript and production build: PASS
- Firestore live ownership test: PASS
- Local browser workflow: PASS
- Production browser workflow: PASS
- Production `/health`: HTTP 200
- Production Marathi complaint draft: HTTP 200
- Production English complaint draft: HTTP 200
- Firestore production DRAFT save: PASS
- Citizen edit/review/copy on local build: PASS
- Citizen review/copy on deployed build: PASS

Visual evidence:

- [Deployed bilingual complaint draft panel](day4-complaint-draft-panel.png)
- [Reviewed/copy control and Firestore DRAFT notice](day4-complaint-draft-verification.png)

The production browser fixture created a DRAFT with a generated ID beginning `dsV6cyha`; it contains only synthetic test text and remains owned by the browser's anonymous user.

## What broke and how it was resolved

- The first real complaint draft exceeded the inherited classifier output allowance and was truncated. Drafting now has a separate 2,048-token model ceiling.
- The first deployed Day 4 revision used “Marathi complaint draft” in its controlled model-error text even for English requests. The text was made language-neutral, covered by a controller regression test, and replaced by the final revision.
- Cloud Run returned a platform-level 404 for `/healthz`; the equivalent application endpoint `/health` returned HTTP 200 and is the recorded production health check.
- The frontend bundle remains about 759 KB minified and triggers Vite's advisory chunk-size warning. It does not fail the build; code splitting remains a later performance task.

## Deployment

- Google Cloud project: `seewik`
- Cloud Run service: `seewik-api`
- Cloud Run region: `asia-south1`
- Final revision: `seewik-api-00010-rns`
- Traffic: 100%
- Backend URL: `https://seewik-api-528138216934.asia-south1.run.app`
- Complaint endpoint: `https://seewik-api-528138216934.asia-south1.run.app/api/civic/draft-complaint`
- Frontend URL: `https://seewik.web.app`
- Firebase project console: `https://console.firebase.google.com/project/seewik/overview`

The existing revision stayed available during each Cloud Run build. Firestore rules were deployed and verified before the frontend release.

## Not built on Day 4

- automatic complaint submission;
- lifecycle/status tracking;
- verified municipal SLA or escalation;
- points, Initiate, or leaderboard;
- voice product input;
- full production voice workflow;
- the exact real civic worked example; and
- heavy UI polish or bundle optimization.

## Remaining external work

- Run the exact locked real Nandurbar example after its image and location are supplied.
- Obtain Nandurbar Municipal Council/domain review of route and likely-department assignments.
- Replace synthetic prabhag boundaries when official geometry is received.

