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

### Verification

- Civic Pack JSON parse: PASS
- Civic Pack integrity tests: PASS
- Classification schema JSON parse: PASS
- Standalone classification-validator tests: 25 passed, 0 failed
- Full backend tests: 44 passed, 0 failed
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

### Deployment

This checkpoint was not deployed independently. The existing healthy production revision remains unchanged. Civic Pack `v0.2` will be deployed with the next tested Day 3 application revision.

### Remaining Day 3 work

- Make the real Gemini classification client work and pass standalone tests before wiring.
- Apply the internal `0.80` confidence gate.
- Test five Marathi voice notes as a non-blocking feasibility experiment when recordings are available.
- Connect classification to the existing prabhag-confirmation and deterministic routing flow only after standalone classification is green.

### External verification still pending

- Nandurbar Municipal Council/domain review of route and department assignments.
- Official prabhag boundaries to replace the separately versioned synthetic development dataset.
