# Seewik Evaluation Policy

Version: `evaluation-policy-v0.1`

## Frozen-contract rule

An evaluation case set, its expected labels, the classifier prompt version, the Civic Pack version, the response schema version, and the scoring rules must be frozen before the first scored model call.

After any scored result has been inspected, changing a prompt, expected label, case, scoring rule, or validator requires a new case-set or prompt version and a completely new run. Results produced before and after that change must never be combined or presented as one evaluation.

Raw responses and failures are preserved. A failed request may not be silently retried and substituted into the same scored run. Model-call failures and local schema-validation failures are reported separately.

## Measurement boundaries

- Classification accuracy measures Gemini's `issueType` output only. Classifier cases never contain an expected authority, department, route, SLA, or escalation.
- Routing accuracy is measured separately against the deterministic Civic Pack router.
- End-to-end workflow completion is reported separately from both classification and routing accuracy.
- Authored Track A complaints are labelled `SYNTHETIC_TEST_TEXT`. They are not described as real Nandurbar citizen reports.
- Track B image evaluation remains a separate case set and is not merged into Track A.
- Human-baseline scoring requires an answer key frozen before survey responses are opened.

## Reproducibility

Every run records the deployed endpoint and region, Git SHA, case-set version and SHA-256, prompt version, schema version, Civic Pack version, model version, timestamps, raw responses, exact-match score, language-label score, clarification behaviour, run stability, and latency distribution.
