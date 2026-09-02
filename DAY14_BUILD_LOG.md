# Day 14 build log — local Set 5 and Set 6 evidence

Date prepared: 2026-09-01
Release state: local implementation complete; not pushed or deployed

## Outcome

Day 14 Sets 5 and 6 are complete for local and automated scope. The normal citizen interface no longer exposes demo scaffolding, the cloud/API diagnostic card, raw browser error strings, or filed-report Route/Pack metadata. Citizen copy is simpler in English, Marathi and Hindi, the boundary guide renders immediately, My Actions and Initiative empty states are singular and useful, and a compact translated 112 action remains visible in the header.

The Google-owned fullscreen Places element was replaced with a Seewik-controlled inline Autocomplete Data API list. It uses a short delay, a per-session token, India plus Nandurbar restrictions, active-language requests, minimal selected fields and Google attribution. Loading, no-results and failure states preserve the manual pin. A selected result fills an editable cleaned public label, moves the pin and still requires explicit organiser confirmation.

Initiative creation now sends a stable client request ID. The backend derives a deterministic owner-scoped Initiative ID and writes the Initiative, event, organiser participation and zero-point ledger record in one Firestore transaction. Retrying the same request returns the existing Initiative rather than creating a duplicate or partial record.

## Automated evidence

- Backend: 221 tests passed; zero failures, errors or skips.
- Frontend: 68 tests passed; zero failures or skips.
- TypeScript and Vite production build: passed.
- Production bundle: main JavaScript 1,104.40 kB (303.16 kB gzip). The existing over-500 kB optimization advisory remains non-blocking.
- `npm audit --audit-level=high`: zero vulnerabilities.
- Repository content policy: passed.
- Secret-safe diagnostics policy: passed.
- Whitespace check: passed.
- The browser key remains only in ignored `frontend/.env.local`; the committed `.env.example` contains a placeholder.

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

## Remaining external acceptance

- Physical Android: retry Google connection/persistence, WhatsApp Civic Card image sharing, reward-card review, changed header/search layout, real Google place selection and exact Maps app/web handoff.
- Production: repeat Google-result selection, direct deployed-rule forgery checks, controlled create/join/location flow and cleanup.
- Release: obtain explicit approval, push, wait for green `main`, deploy, record the commit SHA/Cloud Run revision/Hosting evidence and rerun release gates.

These pending checks require the Android phone or an approved deployment. They do not represent incomplete local Set 5 or Set 6 implementation.

## Local routing follow-up

- Local Vite development and preview servers proxy `/api` to the deployed backend, so routing remains available when Vite selects a different local port.
- The backend CORS allowlist also includes the supported `localhost` and `127.0.0.1` development origins for the next deployment.

## Preserved limitations

- Google may not have translated street-level data for every local place even when Marathi or Hindi is requested. The citizen-facing public label remains editable.
- The local movable pin and manual coordinates remain the publishing fallback; Google search is optional.
- The main frontend bundle retains the existing size advisory and can be code-split in a later performance pass.
- Recognition placement and the Civic Awareness expand/collapse affordance remain optional buffer polish, not a safety or release blocker.
