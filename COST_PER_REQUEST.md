# Cost per Request

## Result

Measured production baseline for August 18 through September 7, 2026, in USD.

| Measure | Gross cost before credits | Net cost after credits |
| --- | ---: | ---: |
| Cost per report started | $0.0169 | $0.0000 |
| Estimated cost per complaint-draft request | $0.0123 | $0.0000 |
| Projected monthly cost at 500 active citizens and one report each | $8.43 | $0.00 at the current credit level |
| Places Autocomplete cost per recorded session | $0.0000 | $0.0000 |

The business-case planning figure is **about $0.017 per report**, or **about $8.43 per month for 500 reports**, before credits.

## Cost per report

The report denominator is 118 authenticated classification requests recorded by the production operational metrics. A classification request is used as the closest available proxy for a report started. Failed and retried attempts are retained because they can still consume infrastructure and model resources.

| Component | Actual gross cost | Cost per report started |
| --- | ---: | ---: |
| Gemini through Vertex AI | $1.87 | $0.0158 |
| Cloud Run | $0.12 | $0.0010 |
| BigQuery | $0.00 | $0.0000 |
| Firestore | $0.00 | $0.0000 |
| Cloud Storage | $0.00 | $0.0000 |
| **Report-path total** | **$1.99** | **$0.0169** |

Artifact Registry added $0.04 of deployment storage cost. It is excluded from the request unit cost because it does not increase with each citizen report. Including it would make total project gross cost $2.03 for the period.

## Complaint-draft estimate

Production metrics recorded 118 classification requests and 34 complaint-draft requests, for 152 model-backed request attempts. The billing export combines all Gemini usage rather than identifying which endpoint produced each token charge.

The draft estimate therefore allocates the $1.87 Gemini charge equally across the 152 recorded model-backed attempts:

`$1.87 / 152 = $0.01230 per model-backed request`

The average Cloud Run charge was approximately $0.00004 per billed Cloud Run request:

`$0.12 / 3,043 = $0.000039 per Cloud Run request`

This gives an estimated complaint-draft request cost of approximately **$0.0123 gross**. This is an allocation estimate, not direct endpoint-level billing. Classification requests can include image input and may cost more than text-only drafting, so the true draft-only cost may be lower.

## Places Autocomplete

The billing export recorded:

- 169 Autocomplete Session Usage units
- 86 Autocomplete Requests
- 45 Place Details Enterprise and Atmosphere requests
- $0.00 gross cost and $0.00 net cost

The measured cost per recorded Autocomplete session was therefore **$0.00** during this period. This reports actual billing only and does not assume a future price-list charge after free usage or credits change.

## Method

1. Exported Cloud Billing grouped by SKU for August 18 through September 7, 2026.
2. Used list cost as gross cost and the post-savings subtotal as net cost.
3. Included Gemini, BigQuery, Firestore, Cloud Run and Cloud Storage in report-path cost.
4. Excluded Artifact Registry, Cloud Build, Firebase Hosting, Identity Platform, Maps and Logging from the request unit cost because they are deployment, hosting, account, map-display or observability overhead rather than the requested report-path components.
5. Exported Cloud Run request logs and operational metric snapshots. The available logs begin August 22, so the denominator covers fewer days than the billing export. This makes the calculated gross unit cost conservative rather than artificially low.
6. Deduplicated the cumulative operational snapshots by taking each Cloud Run revision's maximum counter once, then summing those maxima across revisions.
7. Used request attempts rather than successes for costing because failed and retried requests can consume billable resources.
8. Projected 500 monthly reports by multiplying the measured gross cost per report by 500.

## Source controls and limitations

- Billing source: `seewik-billing-sku-2026-08-18_to_2026-09-07.csv`
- Logging source: `seewik-cloud-logs-2026-08-18_to_2026-09-07.csv`
- Billing recorded 3,043 Cloud Run requests; the logging export contained 2,976 request records and 3,442 cumulative metric snapshots.
- The source period contains development, smoke-test and production activity. The report-path total is therefore a conservative early-stage operating baseline, not a clean mature-production cohort.
- Current credits reduced the displayed bill to $0.00. Gross cost is the safer figure for the Touchpoint 3 unit-economics case because credits may change.
- Recalculate after meaningful citizen volume exists, ideally with endpoint-level token and cost attribution for classification and drafting.
