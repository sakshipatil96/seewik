# Touchpoint 3 — Seewik reward-loop business case

## Decision summary

Seewik's proposed commercial loop is **local-business-funded recognition for verified civic participation**. Citizens do not pay, points are not money, and points are never deducted. A future participating business would pay a modest campaign or placement fee to present an offer to citizens who have crossed a lifetime contribution threshold.

The reward is not the reason to report a problem. Seewik's primary citizen value is making it easier to identify, prepare and retain a record of the appropriate civic action. Rewards sit on top as recognition for sustained, legitimate participation.

This document describes a hypothesis to test. The current Day 13 reward cards and codes are a demonstration only: no offer is redeemable, no merchant verifies a code, no placement is for sale and no partnership is claimed.

## The proposed loop

1. A citizen reports a genuine civic issue or takes part in a qualifying community Initiative.
2. Backend-owned, append-only records award points only for eligible actions. Self-attested attendance receives no points.
3. Lifetime totals permanently unlock the 100, 150 and 250-point thresholds. Claiming never reduces the citizen's total.
4. In a future real pilot, an eligible citizen could claim a time-limited merchant offer and show its code at the participating business.
5. A merchant-side verification process would accept or reject that code and record one use without exposing the citizen's reports, identity or activity history to the business.
6. The business would pay Seewik an agreed fixed campaign or placement fee. Seewik could report privacy-safe aggregate eligibility, claim and verified-use counts.

The model must not depend on selling personal data, charging citizens to access civic services, or charging a municipality per complaint.

## Demand side — why citizens would participate

### Baseline evidence

The supplied Nandurbar survey workbook contains 52 completed responses and 520 scenario answers: ten civic situations per respondent. It was collected from 23 to 29 August 2026.

- **203 of 520 scenario answers (39.0%)** selected **“I don't know”** when asked which department or office the respondent would contact. This is an answer-level result, not “39% of respondents.”
- **36 of 52 respondents (69.2%)** selected “I don't know” for at least one of the ten situations.
- The “I don't know” rate ranged from **25.0%** for household garbage and streetlights to **48.1%** for public toilets, repeated public dumping and unclean public spaces.
- **44 of 52 respondents (84.6%)** currently lived in Nandurbar, and **47 of 52 (90.4%)** had lived there or been closely connected for more than five years. The uncertainty therefore cannot be dismissed as coming only from newcomers.
- **32 of 52 respondents (61.5%)** said they had previously reported a civic problem, while 20 had not.

This supports a narrow demand claim: citizens can benefit from reducing the friction of deciding where and how to act. It does **not** prove that every supplied department name was correct. The workbook's `coded_authority` and `is_correct` columns are blank for all 520 answers, so response correctness has not been independently scored. The sample is small, non-random, English/Marathi only and should not be represented as a population estimate for all of Nandurbar.

### Citizen value before rewards

The citizen-side reasons to return are:

- one guided place to prepare a routeable civic report;
- a durable record of filed reports and joined or organised Initiatives in **My Actions**;
- clearer information about Nagar Parishad responsibilities and escalation;
- discovery of constructive local activities; and
- recognition of verified contribution through **My Civic Card**.

The reward catalogue should remain secondary. A citizen must never be prompted to manufacture, split or exaggerate reports to reach a threshold.

### Is reaching 100 points realistic?

Under the current policy, Tier 1 can be reached through several legitimate combinations:

| Illustrative path | Points | Assessment |
|---|---:|---|
| 20 first accepted report filings | 20 × 5 = 100 | Arithmetically possible but unrealistic for many ordinary citizens and unsafe as the main retention assumption; it could encourage unnecessary reporting. |
| 5 organiser-code attendances at distinct Initiatives | 5 × 20 = 100 | Plausible for a consistently active volunteer, but only if enough genuine local Initiatives are available. |
| 1 eligible completed-Initiative organiser award plus 3 organiser-code attendances | 40 + 60 = 100 | Plausible for a community organiser over several months; not an ordinary low-effort path. |
| 1 first verified civic fix plus 2 organiser-code attendances | 60 + 40 = 100 | Plausible, but depends on a genuine report reaching the verified-fix state and two real attendances. |

The honest conclusion is that **100 points is a pilot hypothesis, not yet a validated retention threshold**. A low-frequency citizen may take six to eighteen months to reach it, while an active volunteer may reach it sooner. The pilot should measure time-to-tier, eligible actions per citizen, invalid or duplicate attempts and drop-off. It should not lower the threshold merely to create a larger commercial audience.

## Supply side — why a local business might participate

A participating business could receive:

- measurable visits from residents who have demonstrated sustained civic participation;
- association with visible, constructive local action;
- a locally targeted placement whose eligibility, claims and verified uses can be counted; and
- a small pilot commitment that can be compared with the business's other local promotional spending.

Seewik should promise only what it can measure. Impressions, eligible citizens, claims and verified uses are defensible future metrics. Increased sales, loyalty or reputation are outcomes to test, not guaranteed benefits.

The Day 13 cards are not evidence of business demand. *Juthalal Store* is a real name used with the owner's permission for the demonstration, but that permission is not a signed campaign, a partnership or acceptance of the displayed offer. *Urja Physiotherapy Clinic* and *Nandurbar Sports Shop* are fictional examples.

## Why a municipality might tolerate or support it

A municipality could see value if the model improves report quality, makes citizen follow-up clearer and recognises constructive participation without using municipal funds for commercial discounts. It could also provide aggregate evidence about where citizens experience routing confusion.

Support cannot be assumed. A real pilot would need to keep these boundaries visible:

- rewards are a Seewik/business programme, not a municipal scheme or endorsement;
- emergency information, reporting and civic information remain available without points;
- no business receives complaint details, location history or citizen identity;
- incentives do not affect routing, prioritisation or the municipality's decision on a report; and
- spam, duplicate reports and unequal access are monitored and addressed.

## Illustrative unit economics

The following is a deliberately simple pilot model, not a forecast. Every number is an assumption to validate with actual merchants and a measured cohort.

### Base-case assumptions for one month

| Input | Illustrative assumption |
|---|---:|
| Active citizens | 500 |
| Citizens eligible at 100 points | 75 (15%) |
| Citizens eligible at 150 points | 40 (8%) |
| Citizens eligible at 250 points | 15 (3%) |
| Claim rate among eligible citizens | 35% |
| Verified-use rate among claimants | 60% |
| Proposed placement fee | ₹1,000 per reward per month |
| Number of participating rewards | 3 |
| Illustrative monthly Seewik revenue | ₹3,000 |

These assumptions yield about 16 uses for the 100-point offer, 8 for the 150-point offer and 3 for the 250-point offer: about **27 uses in total**. Seewik revenue would therefore be approximately **₹110 per verified use** (`₹3,000 ÷ 27.3`).

### Illustrative business cost

| Reward level | Expected uses | Example marginal offer-cost assumption | Fee plus offer cost | Approx. business cost per use |
|---|---:|---:|---:|---:|
| 100 points | 15.75 | ₹10 per use | ₹1,157.50 | ₹74 |
| 150 points | 8.40 | ₹150 per use | ₹2,260.00 | ₹269 |
| 250 points | 3.15 | ₹75 per use | ₹1,236.25 | ₹393 |

The offer-cost assumptions are placeholders, not quotes from the example businesses. They make one commercial risk visible: with only 500 active citizens, a 250-point campaign may be too small to justify the same fixed fee as a 100-point campaign. A pilot may need tier-sensitive pricing, a multi-reward bundle or sponsorship priced partly for community association rather than immediate footfall.

For a real proposal, the maximum sensible placement fee should be calculated as:

`expected verified uses × (business's acceptable acquisition cost − marginal offer cost)`

The model should be rejected or changed if a business cannot see an acceptable cost per verified use without an unaffordable discount, or if Seewik must inflate claims, loosen verification or encourage unnecessary civic actions to make the numbers work.

## What must be true before this becomes real

Before any reward is redeemable, Seewik would need:

- verified business onboarding and authority to accept campaign terms;
- a signed pilot agreement specifying the exact offer, inventory, dates, locations, exclusions, liability, cancellation and customer-support process;
- a merchant or staff verification flow at the point of use, with single-use and expiry enforcement;
- fraud controls for account duplication, code sharing, collusion, false reports and fabricated attendance;
- privacy terms defining the minimum information shared with a business and prohibiting access to a citizen's civic history;
- an agreed fee, invoicing and tax treatment;
- clear ownership of offer costs, refunds and disputes;
- accessibility and non-smartphone handling; and
- at least one signed pilot partner whose willingness to pay and acceptable cost per verified use have been tested.

## Pilot decision gates

A 60-to-90-day pilot should proceed only after the requirements above are met. Continue, change or stop based on:

1. the percentage of active citizens reaching each tier and median time to Tier 1;
2. whether reward introduction changes duplicate, invalid or low-quality report rates;
3. claim-to-verified-use conversion;
4. each business's total cost and cost per verified use;
5. repeat participation after a citizen has claimed a reward;
6. support complaints, privacy incidents and fraud attempts; and
7. explicit municipality and citizen feedback about endorsement clarity and fairness.

## Current-state disclosure

As of Day 13:

- the reward catalogue, claim code and **“Simulate using this reward”** flow are demonstration functionality;
- no business can verify a code;
- no displayed offer can be redeemed;
- no placement fee, billing or settlement system exists;
- no commercial partnership is claimed; and
- points are lifetime, non-deducting contribution thresholds, not currency.

## Survey source and calculation note

Source: user-supplied `Nandurbar Survey for Seewik.xlsx`, `responses!A1:N53` and `answers!A1:M521`. The workbook contains 52 response rows and 520 answer rows. The 39.0% figure is `203 / 520`, based on `answers.dont_know = true`. Respondent-level counts use `responses.dont_know_count`. Only aggregate results are reproduced here; optional names and device information were not used.
