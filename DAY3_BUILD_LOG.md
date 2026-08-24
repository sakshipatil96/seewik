# Seewik Day 3 Build Log

Date: 2026-08-24

## Civic Pack v0.2 checkpoint

Civic Pack `v0.2` is a new versioned artifact. Civic Pack `v0.1` remains preserved in the repository and through tag `citypack-v0.1`.

### Route catalogue

- Pack version: `v0.2`
- City: `NANDURBAR`
- Supported issue types: 11
- `OFFICIAL_SOURCE`: 11
- `UNSOURCED`: 0
- `DOMAIN_REVIEWED`: 0
- `REVIEW_PENDING`: 11
- Department definitions: 4
- `TYPICAL_STRUCTURE_UNVERIFIED` departments: 4
- Routes carrying citizen-visible limitations: 6
- Official filing/contact channels: 3
- Informational-only links: 1

`PUBLIC_AREA_CLEANLINESS` was added as the eleventh issue type. Garbage overflow, illegal dumping, and general public-area cleanliness now have separate canonical definitions and exclusions.

### Department handling

The Civic Pack continues to use official statutory sources for authority-level responsibility. Likely internal departments are now differentiated, but every department is explicitly labelled `TYPICAL_STRUCTURE_UNVERIFIED` because Nandurbar Municipal Council's internal allocation has not been published or locally confirmed.

The citizen response uses “Likely department,” includes the inference basis, and preserves the independent route fields `sourceStatus` and `reviewStatus`.

### Route limitations

Affected responses now carry machine-actionable and citizen-visible limitations for:

- road ownership;
- electricity-distribution faults;
- water-network operation;
- drainage desk allocation;
- commercial encroachment handling; and
- mosquito-control treatment method.

Facebook is retained only as an informational link and is not returned as a verified filing channel.

### Checkpoint verification

- Civic Pack JSON parse: PASS
- Civic Pack integrity tests: PASS
- Classification schema JSON parse: PASS
- Standalone classification-validator tests at checkpoint: 25 passed, 0 failed
- Full backend tests at checkpoint: 44 passed, 0 failed
- Frontend TypeScript and production build: PASS
- Attribution-language audit for changed files: PASS
- Private-secret pattern audit for changed files: PASS

## Standalone classification contract checkpoint

- Schema version: `classification-v0.1`
- Confidence threshold: `0.80`
- Allowed issue types: the 11 Civic Pack `v0.2` categories plus `UNKNOWN`
- Allowed detected languages: `MR | HI | EN | MIXED | UNKNOWN`
- Additional properties: forbidden
- Authority, department, prabhag, official-channel, SLA, escalation, and route fields: rejected

The validator loads allowed issue types directly from Civic Pack `v0.2`. A regression test also checks that the versioned JSON schema, validator language enum, confidence threshold, and Civic Pack issue catalogue remain aligned.

Cross-field rules are deterministic:

- supported issue with `confidence >= 0.80`: `needsClarification = false` and `clarificationQuestion = null`;
- supported issue with `confidence < 0.80`: clarification and a non-empty question are required; and
- `UNKNOWN`: clarification is required regardless of confidence.

This checkpoint contains no Gemini call, BigQuery call, router call, or frontend wiring. Schema failures are therefore independently attributable and testable.

## Standalone Gemini classifier implementation

The real classifier was implemented and tested as a separate service before any frontend wiring.

- Model: `gemini-3.7-flash`
- Vertex location: `global`
- Response format: `application/json` with `classification-response-schema-vertex-v0.1`
- Strict local validation: `classification-v0.1`
- Prompt catalogue: exact `classificationDefinition` and `excludes` values loaded from Civic Pack `v0.2`
- Citizen text: treated as untrusted evidence and delimiter-escaped
- Model scope: perception only; authority, department, prabhag, channel, SLA, escalation, and route decisions forbidden
- Image types: JPEG, PNG, and WebP
- Image ceiling: 5 MB
- Text ceiling: 2,000 characters
- Evidence persistence: none in the classifier; bytes are sent transiently to Vertex and are not saved by Seewik
- Legacy general-purpose Gemini smoke endpoints: removed after the constrained classifier replaced them

Model-call failures and schema-validation failures have distinct internal codes. Public errors remain controlled and do not expose raw model output or upstream error bodies.

### Standalone real-model exit gate

The exit gate passed before wiring:

1. Marathi text `रस्त्यावर मोठा खड्डा आहे.` returned `POTHOLE_ROAD_DAMAGE`, language `MR`, `needsClarification: false`, and a schema-valid response.
2. The existing harmless application screenshot returned `UNKNOWN`, `needsClarification: true`, and a neutral clarification question.

Neither response contained an authority or routing decision.

## Evaluation evidence

Classification and routing use separate case sets:

- Classification: `data/eval/classification-cases-v0.1.json`
- Routing: `data/eval/routing-cases-v0.1.json`

Classifier cases contain `case_id`, `image_ref`, `input_text`, `expected_issueType`, and `source`. They do not contain expected authority. Routing cases begin with a confirmed issue type and prabhag and carry expected route and authority.

### Real non-voice classification run

- Cases: 12
- Expected issue type matched: 12/12
- `CLASSIFIED`: 11
- `CLARIFICATION_REQUIRED`: 1 harmless non-civic screenshot
- Errors: 0
- Detected-language coverage: 5 MR, 2 HI, 4 EN, 1 MIXED
- Latency: min 2,182 ms; median 2,972 ms; max 41,095 ms
- Slow outliers: 33,211 ms and 41,095 ms
- Raw results: `data/eval/results/classification-results-2026-08-24.ndjson`
- Summary: `data/eval/results/classification-summary-2026-08-24.json`

This is a small, authored smoke/evaluation set with synthetic text fixtures. The 12/12 result verifies the current contract and canonical labels; it is not a general accuracy estimate and does not substitute for the exact real Nandurbar case.

The 41,095 ms maximum belonged to `CLS-HI-DRAINAGE-004`, a short Hindi text-only case. The 33,211 ms outlier belonged to `CLS-IMAGE-UNKNOWN-012`; its screenshot was only about 50 KB. Classification latency is measured around one Vertex model call inside an already-running backend, and the client contains no retry. Application cold start, a large image, and a client retry therefore do not explain these two samples. The current evidence cannot distinguish upstream model queuing from network/service latency.

The backend already rejects images larger than 5 MB. Client-side resizing/downscaling is not implemented. A bounded Gemini-call timeout with a clean manual-category fallback and client-side image downscaling remain Aug 28 hardening tasks; no timeout or broader latency architecture was added today.

### Deterministic routing run

The 12 routing fixtures passed against Civic Pack `v0.2`, including all eleven supported routes and the `UNKNOWN -> UNSUPPORTED_ROUTE` negative control. Authority came only from the router.

## Wired citizen flow

The frontend now enforces three separate steps:

1. Gemini suggests a category from optional image/text evidence.
2. The citizen confirms or corrects the category and confirms/selects Prabhag 1-20.
3. The confirmed `(issueType, prabhagId)` is sent to the deterministic Civic Pack router.

The route button remains disabled until category confirmation. Numeric confidence is not displayed. Low-confidence and `UNKNOWN` results ask for clarification; the citizen can always choose a category manually.

Visual evidence:

- [Classifier and citizen confirmation](day3-classifier-routing-mobile.jpg)
- [Deterministic route result](day3-deterministic-route-mobile.jpg)

The existing BigQuery prabhag resolver remains in `asia-south1`. Synthetic boundary candidates still require citizen confirmation, manual `SELF_REPORTED` selection still overrides them, and the outside-supported-area test remains green.

## Final verification

- Full backend suite: 67 passed, 0 failed, 0 errors, 0 skipped
- Spring production application-context startup: PASS
- Frontend TypeScript and production build: PASS
- Local end-to-end browser flow: PASS
- Production health: PASS
- Production Marathi classification: PASS
- Production deterministic pothole route: PASS
- Legacy general-purpose Gemini endpoint: HTTP 404 as intended
- Public frontend render and API health: PASS

### What broke and how it was resolved

- The first real local run exposed ambiguous Spring constructor injection because a package-private test constructor existed. The production constructor is now explicit, and a full application-context regression test covers startup wiring.
- The globally named Firebase command was unavailable. The same official deployment tool was run through its package runner, and hosting deployment completed successfully.
- Local browser testing initially used a `127.0.0.1` origin not listed in CORS. Testing was rerun on the already-approved `localhost` origin; production origins were unchanged.

## Deployment

- Cloud Run service: `seewik-api`
- Region: `asia-south1`
- Revision: `seewik-api-00008-bnj`
- Traffic: 100%
- Backend URL: `https://seewik-api-528138216934.asia-south1.run.app`
- Classifier endpoint: `https://seewik-api-528138216934.asia-south1.run.app/api/civic/classify`
- Router endpoint: `https://seewik-api-528138216934.asia-south1.run.app/api/civic/route`
- Frontend URL: `https://seewik.web.app`

The prior revision remained healthy while the new container built. Frontend production was published only after the new backend passed health, classification, and routing smoke checks.

## Voice feasibility and repeatability evaluation

Six user-provided short M4A recordings were evaluated directly with `gemini-3.7-flash`. Five contain Marathi civic complaints; the sixth is an intentionally blank negative control. The model received audio bytes but not filenames, and the recordings were not copied into the repository or committed.

The initial experimental transcription-plus-classification run failed the blank control: it invented “There is a dead dog lying on the road,” returned `DEAD_ANIMAL_REMOVAL`, and reported 0.98 confidence. The initial evidence then incorrectly treated that model output as the expected label, creating an invalid circular 6/6 result. The corrected initial result is 5/6 with one severe false positive. That failure remains preserved in `voice-feasibility-results-2026-08-24.ndjson` rather than being overwritten.

All recordings were then run twice using the production `classification-v0.1` output schema and a voice prompt that explicitly allows silence:

- Real Marathi complaint calls correct at confidence >= 0.80: 10/10
- Blank control: `UNKNOWN` + clarification in 2/2 calls
- Stable categories across both runs: 6/6
- Category flips: 0
- Schema/model errors: 0
- Latency: min 2,871 ms; median 3,448.5 ms; max 5,214 ms

The repeatability run passes the supplied-set criterion: correct `issueType`, usable confidence for real complaints, correct blank handling, and no category flips. The initial hallucination shows that silence handling is prompt/contract-sensitive, so this is not a general accuracy claim and does not justify product voice input without additional negative controls.

Evidence:

- Initial corrected failure: `data/eval/results/voice-feasibility-results-2026-08-24.ndjson`
- Repeat raw calls: `data/eval/results/voice-repeatability-results-2026-08-24.ndjson`
- Repeat summary: `data/eval/results/voice-repeatability-summary-2026-08-24.json`

## Remaining voice-dependent and external work

- Run the exact locked real Nandurbar civic example when the user provides its image and location; no substitute was fabricated.
- Obtain Nandurbar Municipal Council/domain review of route and likely-department assignments.
- Replace the separately versioned synthetic prabhag boundaries when official geometry becomes available.
