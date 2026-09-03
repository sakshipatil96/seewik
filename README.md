# Seewik

Seewik is a multilingual civic-action platform for Nandurbar. It helps a citizen describe a local problem, confirm the relevant civic facts, find the verified complaint route, organise or join a community initiative, and keep a private record of civic contributions.

**Live application:** [seewik.web.app](https://seewik.web.app/)

Seewik is an independent prototype. It is not a government service, emergency-response provider, legal adviser, or proof that a complaint has been submitted. The dedicated Emergency Information page keeps verified call actions separate from normal civic reporting.

## Product pillars

1. **Improve** — add a photograph or description, receive a Gemini category suggestion, confirm the issue and Prabhag, and use the deterministic Civic Pack route and complaint draft.
2. **Initiate** — create or join local activities with an organiser-confirmed meeting point, participant directions, approval controls, attendance and completion states.
3. **My Civic Card** — view private contribution history, lifetime non-deducting points, opt-in recognition, a locally generated sharing image and clearly illustrative rewards.
4. **Civic Awareness and Emergency Information** — read sourced civic information and reach verified emergency numbers through direct call actions.

The interface is available in English, Marathi and Hindi.

## Core design rule

> Gemini understands the citizen; the verified Civic Pack decides who is responsible.

Gemini may suggest an issue category and help draft citizen wording. It does not select the authority, department, complaint channel, SLA, escalation path or Prabhag. Those fields come from versioned civic data and deterministic code, with explicit citizen confirmation where required.

## Architecture

```text
React + TypeScript PWA on Firebase Hosting
        |
        | Firebase Authentication identity
        v
Java 21 + Spring Boot API on Cloud Run
        |
        +-- Vertex AI Gemini: bounded classification and drafting
        +-- Civic Pack: deterministic authority and channel routing
        +-- BigQuery ST_COVERS: approximate Prabhag suggestion
        +-- Firestore: owner-scoped reports, initiatives and ledgers
        +-- Cloud Storage: protected citizen-owned media path
        +-- Google Maps + Places: optional meeting-point selection
```

Firebase Security Rules deny client forgery of backend-owned lifecycle events, Initiative records, attendance, points, recognition and reward claims. Sensitive write paths also verify Firebase identity in the backend. BigQuery analytics exclude complaint text, photographs, coordinates and raw citizen identifiers.

## Current status

The four product pillars are deployed. The latest recorded Day 15 application release passed:

- 222 backend tests;
- 74 frontend tests;
- the frontend production build;
- the high-severity dependency audit and vulnerability scan;
- repository, secret-safe diagnostic and Prabhag checksum gates;
- candidate health, traffic switch, Firebase Hosting/rules deployment and route checks.

The release evidence is recorded in [DAY15_BUILD_LOG.md](DAY15_BUILD_LOG.md). Physical Android follow-ups and known limitations remain tracked in [PROJECT_TODOS.md](PROJECT_TODOS.md).

## Local frontend

Prerequisites: Node.js 24 and npm.

```bash
cd frontend
npm ci
npm run dev
```

Vite prints the exact localhost URL. The local development server proxies `/api` to the deployed Cloud Run API, so signed-in mutations can affect production records. Use harmless test data and do not create unnecessary reports or initiatives.

Google meeting-place search is optional. To enable it locally, copy `frontend/.env.example` to `frontend/.env.local` and insert a browser key restricted to the exact localhost/Firebase referrers and only the Maps JavaScript API and Places API (New). Never commit `.env.local` or a secret value.

## Verification

Backend tests require Java 21 and Maven:

```bash
cd backend
mvn -B test
```

Frontend tests and build:

```bash
cd frontend
npm ci
npm test
npm run build
npm audit --audit-level=high
```

Firestore and Storage rules are tested against disposable local emulators, never the production project:

```bash
cd frontend
npm run test:rules:emulator
```

Repository-wide local gates:

```bash
bash scripts/check_repository_content.sh
bash scripts/check_secret_safe_diagnostics.sh
cd data/prabhags
shasum -a 256 -c official-map-digitized-boundaries-v0.1.sha256
```

## Delivery

`.github/workflows/quality.yml` runs the required repository, boundary, backend, frontend, dependency and local security-rule checks. A successful push to `main` records the complete pushed commit range. `.github/workflows/deploy.yml` deploys only when that tested range contains application, civic-boundary, Firebase-rule or delivery-workflow changes.

Production deployment uses a no-traffic Cloud Run candidate, verifies its health, moves traffic, builds the frontend from the same tested commit, deploys Hosting plus Firestore and Storage rules, checks all public routes and retains rollback behavior. Production pushes and deployments require explicit owner approval.

Release diagnostics must use allow-listed projections. Do not print Cloud Run environment variables, Secret Manager payloads, identity tokens, attendance secrets or reward claim codes.

## Evidence and project records

- [Project file and service map](PROJECT_FILE_MAP.md)
- [Day 12 build log](DAY12_BUILD_LOG.md)
- [Day 13 build log](DAY13_BUILD_LOG.md)
- [Day 14 build log](DAY14_BUILD_LOG.md)
- [Day 15 build log](DAY15_BUILD_LOG.md)
- [Touchpoint 3 business case](TOUCHPOINT3_BUSINESS_CASE.md)
- [Security findings](SECURITY_FINDINGS.md)
- [Changelog](CHANGELOG.md)

Earlier build logs (`DAY1_BUILD_LOG.md` through `DAY11_BUILD_LOG.md`) preserve the foundation, routing, evaluation, lifecycle, identity, Initiative and attendance evidence. Versioned product contracts live in `data/contracts/`; civic data and evaluation fixtures live under `data/`.

## Known boundaries

- Civic Pack routes remain review-pending where Nandurbar-specific desk assignments or service commitments are not officially published.
- Automatic Prabhag suggestion uses an explicitly synthetic development boundary dataset; the citizen confirms or manually selects the Prabhag.
- Google place search is optional, may return some English address fragments, and does not replace the organiser-confirmed public label and coordinates.
- Rewards are labelled **Example local reward**. Points are lifetime, non-deducting recognition thresholds—not money—and simulated use is not merchant verification.
- No real merchant onboarding, payment, point-of-sale redemption, live municipal campaign feed or legal guidance is implemented.
