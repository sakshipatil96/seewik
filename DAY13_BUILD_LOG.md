# Day 13 build log — contribution rewards and example coupons

Date completed: 2026-09-01

## Outcome

Day 13 is deployed. My Civic Card now contains a clearly labelled example reward catalogue with permanent lifetime unlocks at 100, 150 and 250 points. Claims expire after 30 days, simulated use is owner-only and single-use, and neither claiming nor simulated use deducts civic points. Every offer remains `DEMO_ONLY`; no merchant verifies a code and no partnership or redeemable offer is claimed.

The Touchpoint 3 business case is recorded in `TOUCHPOINT3_BUSINESS_CASE.md`, including survey-grounded citizen demand, honest time-to-tier assumptions, illustrative unit economics and the prerequisites for a real pilot.

## Release identity

- Day 13 application commit: `9974b71562a8b288d907f14b350f683833213d09`
- Production-evidence hardening commit: `3b808924dd4d1c2657b6eab38fdcf82fc8b020c9`
- Final Cloud Run revision: `seewik-api-00079-naq`
- Production traffic: 100% to `seewik-api-00079-naq`
- Container image digest: `sha256:f09a1077ec0b2d1b7b9a11e52ce90705d33795e1054cbfe09911216c073d4969`
- Hosting: `https://seewik.web.app/`
- API: `https://seewik-api-528138216934.asia-south1.run.app`

The temporary candidate traffic tag was removed by the deployment workflow. The pre-existing named Day 8 rollback tags remain deliberately available and carry no untagged production percentage.

## Automated release evidence

Final green-main workflows:

- Quality run `33489109526`: passed in 59 seconds for commit `3b808924dd4d1c2657b6eab38fdcf82fc8b020c9`.
- Deploy green main run `33489202601`: passed in 4 minutes 56 seconds for the same commit.

The initial application release also passed Quality run `33475223932` and Deploy run `33475295690` before the evidence-only test hardening.

Local and CI gates:

- Backend: 215 tests passed; zero failures, errors or skips.
- Frontend: 58 tests passed; zero failures or skips.
- TypeScript and Vite production build: passed.
- Production bundle: main JavaScript 1,037.86 kB (294.73 kB gzip); the existing over-500 kB optimization advisory remains non-blocking.
- `npm audit --audit-level=high`: zero vulnerabilities.
- CI high/critical filesystem dependency scan: passed.
- Repository content policy, secret-safe diagnostics policy, boundary checksum and whitespace checks: passed.

## Production acceptance

- Cloud Run candidate health, traffic switch, Firebase Hosting, Firestore rules, Storage rules, production routes and candidate-tag cleanup passed in the deployment workflow.
- Independent route checks returned HTTP 200 for `/`, `/points`, `/reports`, `/initiatives`, `/emergency` and `/awareness` after the application release.
- A production Firestore transaction test used two imported, temporary Google-provider identities and exact 60 + 40-point fixtures. It verified 100-point eligibility, one claim and code, retry idempotency, 30-day expiry, cross-owner rejection, one simulated use, two append-only events without a code field, and an unchanged 100-point total. Cleanup ran in `finally`.
- Deployed Firestore rules rejected direct writes to points, reward claims and reward events, as well as the existing Initiative-owned collections.
- The deployed API preserved anonymous reward reads and returned `GOOGLE_LINK_REQUIRED` for an anonymous claim mutation.
- A 390 × 844 production browser pass found three reward cards, three visible example labels, no horizontal overflow, responsive mobile navigation and no browser warnings or errors. English, Marathi and Hindi reward copy was present.
- Automated interface coverage exercised locked, unlocked, claimed, used and expired states, keyboard/focus behavior, status announcements and narrow layouts.
- A narrow, privacy-safe Cloud Run log search and searches of both Day 13 CI runs found no value matching the claim-code format.

## Fixture cleanup and legacy 40-point audit

The final read-only production audit returned:

- 40-point awarded ledger records: 0
- candidate 40-point test/demo records: 0
- Day 13 temporary `pointsLedger` records: 0
- Day 13 temporary `recognitionRewardClaims` records: 0
- Day 13 temporary `recognitionRewardEvents` records: 0
- Day 13 temporary Firebase users: 0

There is therefore no deletion allowlist, no legacy 40-point deletion to perform and no destructive-action approval required.

## Limitations preserved

- No real reward is redeemable, no business verifies a code and no business partnership is claimed. Juthalal Store's owner permitted use of the name for this demonstration; that permission is not presented as acceptance of the displayed offer. The other two businesses are fictional examples.
- A successful claim was exercised against production Firestore with the same service/gateway code used by the deployed application. The laptop's user credentials lacked the service-account token-signing setup needed to mint a temporary Google-linked ID token for a successful end-to-end HTTP claim; the deployed HTTP read/Google-link boundary was verified separately. No identity safeguard was weakened to bypass this limitation.
- Production browser acceptance used the locked, below-tier state. Unlocked, claimed, used and expired visual states are covered by automated local interface tests; they were not created in the user's browser.
- Physical Android Google sign-in and native sharing remain untested because an Android device was not available.
- The main frontend bundle retains the existing size advisory and can be code-split in a later performance pass.
