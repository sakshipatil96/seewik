# Day 13 Checklist — contribution rewards and example coupons

Day 13 adds a clearly labelled demonstration reward loop. Civic points remain a lifetime contribution score: unlocking or claiming a coupon does not deduct points. Every business and offer remains `DEMO_ONLY` until a real partnership and redemption process are separately approved.

## Frozen contribution values

- [x] First accepted report filing: 5 points.
- [x] First `ORGANISER_CODE_ATTESTED` attendance per participant and Initiative: 20 points.
- [x] Completed Initiative organiser award: 40 points, exactly once, only when at least two distinct non-organiser participants have `ORGANISER_CODE_ATTESTED` attendance.
- [x] First verified civic fix: 60 points under the new reward-policy version, applied forward-only from deployment.
- [x] `SELF_ATTESTED` Initiative attendance: zero points.
- [x] Keep every award idempotent and append-only.

## Coupon unlock contract

- [ ] Points are not deducted when a reward is unlocked or claimed.
- [ ] Tier 1 unlocks at 100 lifetime contribution points.
- [ ] Tier 2 unlocks at 150 lifetime contribution points.
- [ ] Tier 3 unlocks at 200 lifetime contribution points.
- [ ] Derive eligibility from the owner-protected append-only points ledger.
- [ ] Keep leaderboard/contribution totals unchanged after a coupon claim.
- [ ] Permit one claim per citizen and coupon campaign.
- [ ] Make claiming transactional and idempotent.
- [ ] Expire each claim seven days after it is created, using server time.
- [ ] Generate a unique single-use claim code and reject used or expired codes.
- [ ] Do not expose citizen identity or civic activity to an example business.

## Honest demonstration boundary

- [ ] Mark every seeded business and coupon `DEMO_ONLY`.
- [ ] Display “Example local reward — no current business partnership.”
- [ ] Do not promise a real discount, reimbursement or merchant acceptance.
- [ ] Label any redemption demonstration as simulated.
- [ ] Do not build business onboarding or business accounts for this checkpoint.

## Migration and integrity

- [x] Version the new reward policy and Initiative points-ledger schema before implementation.
- [x] Do not add retroactive `+20` adjustments and do not rewrite historical 40-point `FIX_VERIFIED` entries as part of the policy migration.
- [ ] Audit any pre-launch 40-point production entries separately; delete only records proven to be test/demo fixtures, using an explicit document allowlist and preserving a privacy-safe cleanup record.
- [ ] Test threshold boundaries at 99/100, 149/150 and 199/200 points.
- [ ] Test duplicate claims, concurrent claims, expiry and single-use behavior.
- [ ] Verify Firestore clients cannot forge points, eligibility, claims, redemptions or expiry.
- [ ] Localize the catalogue, eligibility, claim, expiry and demo disclosures in English, Marathi and Hindi.
- [ ] Run report, points, Initiative, identity and production-isolation regressions before deployment.
