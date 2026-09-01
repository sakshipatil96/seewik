# Day 13 Checklist — contribution rewards and example coupons

Day 13 adds a clearly labelled demonstration reward loop. Civic points remain a lifetime contribution score: unlocking or claiming a coupon does not deduct points. Every business and offer remains `DEMO_ONLY` until a real partnership and redemption process are separately approved.

## Frozen contribution values

- [x] First accepted report filing: 5 points.
- [x] First `ORGANISER_CODE_ATTESTED` attendance per participant and Initiative: 20 points.
- [x] Completed Initiative organiser award: 40 points, exactly once, only when at least two distinct non-organiser participants have `ORGANISER_CODE_ATTESTED` attendance.
- [x] First verified civic fix: 60 points under the new reward-policy version, applied forward-only from deployment.
- [x] `SELF_ATTESTED` Initiative attendance: zero points.
- [x] Keep every award idempotent and append-only.

## Versioned Day 13 contracts

- [x] Freeze `reward-tiers-v0.1` with permanent lifetime unlocks at 100, 150 and 250 points.
- [x] Freeze `example-business-v0.1` with `businessId`, `displayName`, `category`, optional civic-area context and mandatory `isExample: true` / `DEMO_ONLY` status.
- [x] Freeze `coupon-claim-v0.1` with citizen ownership, coupon/campaign identity, claimed/used timestamps, server-owned 30-day expiry, code status and schema version.
- [x] Use **Unlocked** for permanent tier eligibility, **Claim** for creating the citizen’s example code, and **Used** or **Expired** for code state. Never use **spent**, **balance**, **cash value** or wording that suggests points decrease.

## Coupon unlock contract

- [x] Points are not deducted when a reward is unlocked or claimed.
- [x] Tier 1 unlocks at 100 lifetime contribution points.
- [x] Tier 2 unlocks at 150 lifetime contribution points.
- [x] Tier 3 unlocks at 250 lifetime contribution points.
- [x] Derive eligibility only from backend-owned append-only ledger entries marked `AWARDED`; exclude zero-point, invalid, demo and exact test-allowlist records.
- [x] Keep leaderboard/contribution totals unchanged after a coupon claim.
- [x] Permit one claim per citizen and coupon campaign.
- [x] Make claiming transactional and idempotent.
- [x] Require a Google-linked citizen for every claim and simulated-use mutation.
- [x] Write an append-only `COUPON_CLAIMED` event without mutating or decrementing the points ledger.
- [x] Keep claim creation backend-owned and rate-limit claim attempts per citizen.
- [x] Expire each claim 30 days after it is created, using server time. Thirty days gives a citizen a reasonable demonstration window; a future real campaign may use a different clearly disclosed window based on partner inventory or campaign dates.
- [x] Generate a unique single-use claim code and reject used or expired codes.
- [x] Keep claim codes out of application logging and browser-console logging; the owner-private API response is the only client path that carries a code.
- [x] After deployment, confirm claim codes are absent from Cloud Run and CI logs.
- [x] Do not expose citizen identity or civic activity to an example business.

## Simulated code-use contract

- [x] After claiming, show the owner-private example code and its 30-day expiry on the reward detail screen.
- [x] Let the citizen tap **“Simulate using this reward”** and confirm the action. State plainly: **“This is a demonstration. No shop has verified or accepted this code.”**
- [x] The backend verifies the Google-linked owner, claim ownership, unexpired `CLAIMED` state and unused code, then transactionally records `usedAt` once and appends `COUPON_USE_SIMULATED`.
- [x] No business, merchant account or point-of-sale system verifies the code in Day 13. After simulated use, mark it **Used in simulation**, prevent reuse and leave every contribution total unchanged.

## Honest demonstration boundary

- [x] Mark every seeded business and coupon `DEMO_ONLY`.
- [x] Store the three owner-approved businesses and coupons as committed, versioned fixtures rather than runtime-writable records.
- [x] Require `isExample: true` on every business, coupon and campaign fixture, with integrity tests that fail the build if the marker is missing.
- [x] Display only the visible label **“Example local reward”** on every example card.
- [x] Do not promise a real discount, reimbursement or merchant acceptance.
- [x] Label any reward-use demonstration as simulated.
- [x] Do not build business onboarding or business accounts for this checkpoint.

## Reward interface states

- [x] Show the citizen’s current permanent tier and progress to the next tier inside **My Civic Card**.
- [x] Show locked cards in a clearly disabled style with the exact points still needed; show unlocked cards as claimable without deducting points.
- [x] Show `Unlocked`, `Claimed`, `Used in simulation` and `Expired` states distinctly, including the claim code and 30-day expiry only to its owner.
- [x] Provide an honest below-100-points state with progress rather than an empty or failure screen.
- [x] Preserve the **Example local reward** label through catalogue, claim, code and simulated-use screens.
- [x] Localize all states and actions in English, Marathi and Hindi, with keyboard access, visible focus, status announcements and narrow-screen layouts.

## Migration and integrity

- [x] Version the new reward policy and Initiative points-ledger schema before implementation.
- [x] Do not add retroactive `+20` adjustments and do not rewrite historical 40-point `FIX_VERIFIED` entries as part of the policy migration.
- [x] Audit any pre-launch 40-point production entries separately; the final read-only production audit found zero 40-point entries, so no deletion allowlist or destructive action was required. Preserve the privacy-safe result in `DAY13_BUILD_LOG.md`.
- [x] Test threshold boundaries at 99/100, 149/150 and 249/250 points.
- [x] Reject below-tier and expired claims.
- [x] Reject cross-citizen claim/code access and simulated-use attempts.
- [x] Test duplicate and concurrent claims: one claim record, one code and one `COUPON_CLAIMED` event only.
- [x] Test duplicate simulated-use attempts: one `usedAt` value and one `COUPON_USE_SIMULATED` event only.
- [x] Verify points and recognition totals never decrease or reorder because of claim/use actions.
- [x] Verify locally that Firestore clients cannot forge points, eligibility, claims, codes, use events or expiry.
- [x] After the rules deployment, repeat direct-forgery verification against deployed rules.
- [x] Verify claim codes are absent from backend application logging and frontend-console logging.
- [x] Localize the catalogue, eligibility, claim, expiry and demo disclosures in English, Marathi and Hindi.
- [x] Run report, points, Initiative, identity and production-isolation regressions before deployment.

## Owner-approved example reward cards

- [x] **100 points:** 10% off at *Juthalal Store* — groceries.
- [x] **150 points:** Complimentary physical health checkup — *Urja Physiotherapy Clinic* · one per citizen per year.
- [x] **250 points:** 15% off at *Nandurbar Sports Shop* · example progress copy: **Needs 250 points · 65 to go**.
- [x] Keep the visible demonstration label on each card to exactly **“Example local reward.”**
- [x] Business-name status confirmed by the project owner: *Juthalal Store* is a real business whose owner has given permission for this demonstration use; *Urja Physiotherapy Clinic* and *Nandurbar Sports Shop* are fictional examples. Permission to use a name is not presented as a partnership or acceptance of a real offer; all three rewards remain `DEMO_ONLY` and non-redeemable.

## Business case document — Touchpoint 3

This is a written judging deliverable, not functionality presented as already built.

- [x] Explain the proposed revenue loop: businesses would pay for placement, while citizens would earn access through verified civic action.
- [x] Explain the demand side—why a citizen would participate often enough for a business audience to exist. The baseline survey found that 203 of 520 scenario answers (39.0%) said “I don't know” when asked which department or office to contact; 36 of 52 respondents (69.2%) said this for at least one scenario. Seewik’s primary value is removing that friction, with rewards as recognition on top rather than the reason to report. State and test the retention assumption: Tier 1 requires approximately 20 legitimate accepted report filings at 5 points each, or one qualifying completed-Initiative organiser award at 40 points plus three organiser-code attendances at 20 points each. Assess whether those paths and the expected time to 100 points are realistic for an ordinary citizen without encouraging unnecessary reports or activities.
- [x] Explain what a participating local business could receive: visits from civically active residents, association with visible civic improvement and a measurable alternative to other local advertising.
- [x] Explain why a municipality might tolerate or support the model without presenting Seewik rewards as a municipal programme or endorsement.
- [x] Model the unit economics: proposed business fee, expected citizens reaching each tier, expected claim/use rate, offer cost and estimated cost per use.
- [x] State what must exist before the model becomes real: verified business onboarding, agreed campaign terms, point-of-sale redemption verification, fraud controls, privacy terms and at least one signed pilot partner.
- [x] State plainly that none of the commercial system is currently built, no offer is currently redeemable and no partnership is being claimed.

## Release verification

- [x] Run the backend suite, frontend suite, production build, dependency audit, repository policy, secret-safe diagnostics policy, boundary checksum and whitespace checks.
- [x] Run three-language and narrow-layout acceptance for locked, unlocked, claimed, used and expired states.
- [x] Run production claim, cross-owner and direct-forgery tests with exact temporary accounts/fixtures and mandatory cleanup.
- [x] Push and deploy only from green `main` after explicit owner approval; verify health, hosting routes, rules and temporary-tag cleanup.
- [x] Record the exact application Git SHA, final Cloud Run revision, image digest, workflow runs, fixture cleanup and limitations in `DAY13_BUILD_LOG.md`.

## Explicitly outside Day 13

- Real business onboarding, dashboards, merchant accounts or self-service portals.
- Real point-of-sale or employee verification of a claim code.
- Payment processing, placement billing or settlement.
- Any claim of an actual partnership, accepted discount or delivered health service.
- Transferable, purchasable, deductible or cash-equivalent points.
