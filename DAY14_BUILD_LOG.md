# Day 14 build log — local Set 5 and Set 6 evidence

Date prepared: 2026-09-01
Release state: pushed and deployed; physical Android acceptance remains

## Outcome

Day 14 Sets 5 and 6 are complete for local and automated scope. The normal citizen interface no longer exposes demo scaffolding, the cloud/API diagnostic card, raw browser error strings, or filed-report Route/Pack metadata. Citizen copy is simpler in English, Marathi and Hindi, the boundary guide renders immediately, My Actions and Initiative empty states are singular and useful, and a compact translated 112 action remains visible in the header.

The Google-owned fullscreen Places element was replaced with a Seewik-controlled inline Autocomplete Data API list. It uses a short delay, a per-session token, India plus Nandurbar restrictions, active-language requests, minimal selected fields and Google attribution. Loading, no-results and failure states preserve the manual pin. A selected result fills an editable cleaned public label, moves the pin and still requires explicit organiser confirmation.

Initiative creation now sends a stable client request ID. The backend derives a deterministic owner-scoped Initiative ID and writes the Initiative, event, organiser participation and zero-point ledger record in one Firestore transaction. Retrying the same request returns the existing Initiative rather than creating a duplicate or partial record.

## Automated evidence

- Backend: 221 tests passed; zero failures, errors or skips.
- Frontend: 69 tests passed; zero failures or skips.
- TypeScript and Vite production build: passed.
- Production bundle: main JavaScript 1,104.48 kB (303.17 kB gzip). The existing over-500 kB optimization advisory remains non-blocking.
- `npm audit --audit-level=high`: zero vulnerabilities.
- Repository content policy: passed.
- Secret-safe diagnostics policy: passed.
- Whitespace check: passed.
- The Maps browser-key value is absent from the repository. Local development reads the ignored `frontend/.env.local`; production receives the value from a GitHub Actions secret, and `.env.example` contains only a placeholder. The resulting client key is intentionally browser-visible and is protected by exact referrer and API restrictions.

Coverage includes coordinate boundaries, missing label, non-finite coordinates, legacy text-only records, exact generated Maps links, retry-safe creation, local pin/manual fallback, selected-place municipal bounds, keyboard support, translated copy, emergency call validation, Firestore write denial and the full reports/points/rewards/recognition/Initiative/identity regression suite.

## Live local browser evidence

- Normal Marathi Home showed the new plain headline and persistent 112 action, with no lifecycle demo or API-check card.
- Raw recognition network failure was rendered as a translated citizen-safe message with **Try again**, not `Failed to fetch`.
- A real `Bus stand Nandurbar` search returned an inline Marathi-localized suggestion with required Google attribution.
- Keyboard Enter selected the real result, filled the Marathi public label and placed the meeting-point pin.
- The suggestion list stayed closed after selection; this was explicitly rechecked after fixing a development-mode reopen race.
- A deliberately nonexistent query showed the translated no-results state and retained the visible manual-pin escape.
- English and Hindi switching updated both the meeting-point search and the persistent emergency action.

No public Initiative or production record was created during these checks.

## Production release evidence

- Final deployed application commit: `a17124665976d3dbd09b3e4470d4a8cca84519a2`.
- Cloud Run revision: `seewik-api-00085-wib`, Ready and serving production traffic.
- Container image digest: `sha256:246e559eb1f8ab343199cfd404a63c6a38b93dd3516528d484183f020be73c5a`.
- Quality workflow `33593822198`: passed.
- Deploy workflow `33593898345`: passed, including zero-traffic candidate health, traffic routing, frontend build, Hosting/rules deployment, public-route verification and temporary-tag cleanup.
- The deployed Initiative form returned a live Google result for `Bus stand Nandurbar` in Hindi. Selecting it filled the localized public label, moved the pin and preserved explicit confirmation. No Initiative was published.
- Deployed Firestore forgery verification passed: direct Initiative, Initiative event, participation attendance, attempt, points, reward claim and reward event writes were denied. The temporary anonymous identity was deleted.
- The safe production metadata check confirmed the deployed Git SHA, Ready state and public health endpoint without reading or printing service environment variables.

## Remaining external acceptance

- Physical Android: retry Google connection/persistence, WhatsApp Civic Card image sharing, reward-card review, changed header/search layout, real Google place selection and exact Maps app/web handoff.
- Production: the controlled Google selection and deployed-rule forgery checks are complete; a full signed-in create/join/location temporary-record acceptance remains pending and must include cleanup.

These pending checks require the Android phone or controlled signed-in production identities. They do not represent incomplete local Set 5 or Set 6 implementation.

## Local routing follow-up

- Local Vite development and preview servers proxy `/api` to the deployed backend, so routing remains available when Vite selects a different local port.
- The backend CORS allowlist also includes the supported `localhost` and `127.0.0.1` development origins for the next deployment.
- The production workflow receives the restricted Maps browser key from a GitHub Actions secret and fails before building if that value is absent; no key value is committed or printed.

## Preserved limitations

- Google may not have translated street-level data for every local place even when Marathi or Hindi is requested. The citizen-facing public label remains editable.
- The local movable pin and manual coordinates remain the publishing fallback; Google search is optional.
- The main frontend bundle retains the existing size advisory and can be code-split in a later performance pass.
- Recognition placement and the Civic Awareness expand/collapse affordance remain optional buffer polish, not a safety or release blocker.
