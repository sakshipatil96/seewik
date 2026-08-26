# Seewik Day 7 BigQuery Evidence

Date: 2026-08-26

## Scope

This evidence completes Day 7 checklist section 8. All queries ran in project `seewik`, dataset `seewik_civic`, region `asia-south1`. Production results and controlled fixture results are deliberately separate.

## Production results

Production SQL: `data/eval/day7-bigquery-production.sql`

Raw output: `data/eval/results/day7-bigquery-production-2026-08-26.json`

Query time: `2026-08-26 23:02:56 UTC`

| Measurement | Production result |
|---|---:|
| Raw lifecycle rows | 1 |
| Applied smoke-test exclusions | 1 |
| Product lifecycle rows | 0 |
| Raw dedupe rows | 0 |
| Product dedupe rows | 0 |
| Reports by prabhag/category | No product rows |
| Filed reports | 0 |
| Verified-fixed reports | 0 |
| Verified resolution rate | Not calculable; denominator is zero |
| Resolved reports with a duration | 0 |
| Median `FILED → VERIFIED_FIXED` duration | Not calculable; no resolved product reports |
| Overdue-eligible events | 0 |
| Overdue-eligible reports | 0 |
| Dedupe-distance distribution | No product evaluations |
| Points by triggering event | No product events |

The excluded row is event `evt_ac7b1f01b8f5d06f5dd8fd0514e3255cabc83af11d942f2cfe696a6f313d7e7d`, labelled `PRODUCTION_SMOKE_TEST`. It is physically present but contributes to none of the product metrics.

Civic Pack `v0.2` contains 11 routes, all with `sla: null`; there are zero verified route-specific SLAs. The production query independently found zero overdue-eligible events and reports. This is the expected honest result: Seewik does not manufacture overdue status without a verified SLA.

Empty arrays and null rates/durations are preserved rather than reported as 0% resolution or zero-second duration. A zero denominator is absence of production evidence, not a measured performance outcome.

## Controlled fixture results

Fixture SQL: `data/eval/day7-bigquery-controlled-fixture.sql`

Raw output: `data/eval/results/day7-bigquery-controlled-fixture-2026-08-26.json`

Query time: `2026-08-26 23:03:10 UTC`

The fixture consists only of in-query CTE values. It does not insert into or modify production tables.

| Measurement | Controlled fixture result |
|---|---:|
| Filed reports | 3 |
| Verified-fixed reports | 1 |
| Verified resolution rate | 33.33% |
| Median `FILED → VERIFIED_FIXED` duration | 172,800 seconds / 2 days |
| Overdue-eligible reports | 0 |
| `POSSIBLE_DUPLICATE` measured distance | 25 m |
| `NO_MATCH` measured distance | 120 m |
| `DEDUPE_NOT_EVALUATED` measured distance | null, intentionally unmeasured |
| `REPORT_FILED` fixture points | 10 across 3 events |
| `REPAIR_VERIFIED` fixture points | 40 across 1 event |

These fixture values verify the grouping, safe-rate, duration, distance and point calculations. They are not Seewik production performance or citizen activity.

## Checklist disposition

- Smoke exclusion: PASS — one exclusion, zero product lifecycle rows.
- Reports by prabhag/category: PASS — query ran; no product rows.
- Verified-resolution rate: PASS — query ran; null with zero denominator.
- Median filed-to-verified duration: PASS — query ran; null with no resolved product report.
- Verified-SLA overdue eligibility: PASS — zero verified SLA routes and zero overdue-eligible product reports.
- Dedupe-distance analysis: PASS — query ran; no production evaluations; controlled result kept separate.
- Points by triggering event: PASS — query ran; no production events; controlled result kept separate.
- Production versus fixture separation: PASS.
