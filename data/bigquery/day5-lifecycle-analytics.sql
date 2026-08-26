-- All queries exclude visible synthetic demo data. Identifiers are irreversible SHA-256 hashes.

-- Resolution rate by prabhag: share of first filings that later reached VERIFIED_FIXED.
WITH reports AS (
  SELECT
    report_id_hash,
    ANY_VALUE(prabhag_id HAVING MIN occurred_at) AS prabhag_id,
    COUNTIF(event_type = 'REPORT_FILED') > 0 AS filed,
    COUNTIF(event_type = 'FIX_VERIFIED') > 0 AS verified
  FROM `seewik.seewik_civic.report_lifecycle_events`
  WHERE demo_mode = FALSE
    AND event_id NOT IN (SELECT event_id FROM `seewik.seewik_civic.analytics_event_exclusions`)
  GROUP BY report_id_hash
)
SELECT prabhag_id, COUNTIF(filed) AS filed_reports, COUNTIF(verified) AS verified_reports,
  SAFE_DIVIDE(COUNTIF(verified), COUNTIF(filed)) AS resolution_rate
FROM reports
GROUP BY prabhag_id
ORDER BY prabhag_id;

-- Median elapsed time from first FILED to first VERIFIED_FIXED.
WITH durations AS (
  SELECT report_id_hash,
    TIMESTAMP_DIFF(
      MIN(IF(event_type = 'FIX_VERIFIED', occurred_at, NULL)),
      MIN(IF(event_type = 'REPORT_FILED', occurred_at, NULL)),
      HOUR) AS hours_to_verified
  FROM `seewik.seewik_civic.report_lifecycle_events`
  WHERE demo_mode = FALSE
    AND event_id NOT IN (SELECT event_id FROM `seewik.seewik_civic.analytics_event_exclusions`)
  GROUP BY report_id_hash
)
SELECT APPROX_QUANTILES(hours_to_verified, 2)[OFFSET(1)] AS median_hours_filed_to_verified
FROM durations
WHERE hours_to_verified IS NOT NULL;

-- Open-report age by category.
WITH latest AS (
  SELECT * EXCEPT(row_number) FROM (
    SELECT issue_type, report_id_hash, to_status, occurred_at,
      ROW_NUMBER() OVER (PARTITION BY report_id_hash ORDER BY occurred_at DESC) AS row_number
    FROM `seewik.seewik_civic.report_lifecycle_events`
    WHERE demo_mode = FALSE
      AND event_id NOT IN (SELECT event_id FROM `seewik.seewik_civic.analytics_event_exclusions`)
  ) WHERE row_number = 1
)
SELECT issue_type, COUNT(*) AS open_reports,
  APPROX_QUANTILES(TIMESTAMP_DIFF(CURRENT_TIMESTAMP(), occurred_at, DAY), 2)[OFFSET(1)] AS median_open_days
FROM latest
WHERE to_status NOT IN ('VERIFIED_FIXED')
GROUP BY issue_type
ORDER BY open_reports DESC;

-- Honest overdue metric: zero eligible rows when no route has a verified dueAt.
SELECT COUNTIF(event_type = 'OVERDUE_REACHED' AND overdue_eligible) AS verified_overdue_events
FROM `seewik.seewik_civic.report_lifecycle_events`
WHERE demo_mode = FALSE
  AND event_id NOT IN (SELECT event_id FROM `seewik.seewik_civic.analytics_event_exclusions`);

-- Points are summed from immutable triggering events; no stored user total exists.
SELECT owner_id_hash, SUM(points_awarded) AS derived_points
FROM `seewik.seewik_civic.report_lifecycle_events`
WHERE demo_mode = FALSE
  AND event_id NOT IN (SELECT event_id FROM `seewik.seewik_civic.analytics_event_exclusions`)
GROUP BY owner_id_hash
ORDER BY derived_points DESC;

-- Every filing dedupe evaluation: successful transitions plus blocked attempts.
WITH evaluations AS (
  SELECT dedupe_disposition AS disposition, dedupe_distance_meters AS measured_distance_meters
  FROM `seewik.seewik_civic.report_lifecycle_events`
  WHERE demo_mode = FALSE AND event_type = 'REPORT_FILED'
    AND event_id NOT IN (SELECT event_id FROM `seewik.seewik_civic.analytics_event_exclusions`)
  UNION ALL
  SELECT disposition, measured_distance_meters
  FROM `seewik.seewik_civic.report_dedupe_evaluations`
  WHERE demo_mode = FALSE
)
SELECT disposition, COUNT(*) AS evaluations,
  APPROX_QUANTILES(measured_distance_meters, 2)[OFFSET(1)] AS median_distance_meters
FROM evaluations
GROUP BY disposition
ORDER BY evaluations DESC;
