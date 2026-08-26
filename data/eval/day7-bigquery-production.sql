-- Day 7 production analytics evidence.
-- Every lifecycle metric excludes explicitly identified non-product evidence rows.
WITH
  excluded AS (
    SELECT event_id, reason
    FROM `seewik.seewik_civic.analytics_event_exclusions`
  ),
  product_events AS (
    SELECT event.*
    FROM `seewik.seewik_civic.report_lifecycle_events` AS event
    LEFT JOIN excluded USING (event_id)
    WHERE excluded.event_id IS NULL AND NOT event.demo_mode
  ),
  product_dedupe AS (
    SELECT *
    FROM `seewik.seewik_civic.report_dedupe_evaluations`
    WHERE NOT demo_mode
  ),
  report_times AS (
    SELECT
      report_id_hash,
      MIN(IF(to_status = 'FILED', occurred_at, NULL)) AS filed_at,
      MIN(IF(to_status = 'VERIFIED_FIXED', occurred_at, NULL)) AS verified_at
    FROM product_events
    GROUP BY report_id_hash
  ),
  resolution_durations AS (
    SELECT TIMESTAMP_DIFF(verified_at, filed_at, SECOND) AS duration_seconds
    FROM report_times
    WHERE filed_at IS NOT NULL AND verified_at IS NOT NULL AND verified_at >= filed_at
  )
SELECT
  CURRENT_TIMESTAMP() AS queried_at,
  'seewik' AS project_id,
  'seewik_civic' AS dataset_id,
  'asia-south1' AS dataset_region,
  STRUCT(
    (SELECT COUNT(*) FROM `seewik.seewik_civic.report_lifecycle_events`) AS raw_lifecycle_rows,
    (SELECT COUNT(*) FROM excluded) AS excluded_lifecycle_rows,
    (SELECT COUNT(*) FROM product_events) AS product_lifecycle_rows,
    (SELECT COUNT(*) FROM `seewik.seewik_civic.report_dedupe_evaluations`) AS raw_dedupe_rows,
    (SELECT COUNT(*) FROM product_dedupe) AS product_dedupe_rows
  ) AS housekeeping,
  ARRAY(
    SELECT AS STRUCT prabhag_id, issue_type, COUNT(DISTINCT report_id_hash) AS report_count
    FROM product_events
    GROUP BY prabhag_id, issue_type
    ORDER BY prabhag_id, issue_type
  ) AS reports_by_prabhag_category,
  STRUCT(
    (SELECT COUNT(DISTINCT IF(to_status = 'FILED', report_id_hash, NULL)) FROM product_events)
      AS filed_reports,
    (SELECT COUNT(DISTINCT IF(to_status = 'VERIFIED_FIXED', report_id_hash, NULL)) FROM product_events)
      AS verified_fixed_reports,
    SAFE_DIVIDE(
      (SELECT COUNT(DISTINCT IF(to_status = 'VERIFIED_FIXED', report_id_hash, NULL)) FROM product_events),
      (SELECT COUNT(DISTINCT IF(to_status = 'FILED', report_id_hash, NULL)) FROM product_events)
    ) AS verified_resolution_rate
  ) AS verified_resolution,
  STRUCT(
    (SELECT COUNT(*) FROM resolution_durations) AS resolved_report_count,
    (SELECT APPROX_QUANTILES(duration_seconds, 100)[SAFE_OFFSET(50)] FROM resolution_durations)
      AS median_filed_to_verified_seconds
  ) AS filed_to_verified_duration,
  STRUCT(
    (SELECT COUNTIF(overdue_eligible) FROM product_events) AS overdue_eligible_events,
    (SELECT COUNT(DISTINCT IF(overdue_eligible, report_id_hash, NULL)) FROM product_events)
      AS overdue_eligible_reports
  ) AS overdue_eligibility,
  ARRAY(
    SELECT AS STRUCT
      disposition,
      COUNT(*) AS evaluation_count,
      COUNT(measured_distance_meters) AS measured_count,
      MIN(measured_distance_meters) AS min_distance_meters,
      APPROX_QUANTILES(measured_distance_meters, 100)[SAFE_OFFSET(50)] AS median_distance_meters,
      APPROX_QUANTILES(measured_distance_meters, 100)[SAFE_OFFSET(95)] AS p95_distance_meters,
      MAX(measured_distance_meters) AS max_distance_meters
    FROM product_dedupe
    GROUP BY disposition
    ORDER BY disposition
  ) AS dedupe_distance_analysis,
  ARRAY(
    SELECT AS STRUCT
      event_type AS triggering_event,
      COUNT(*) AS event_count,
      SUM(points_awarded) AS points_awarded
    FROM product_events
    GROUP BY event_type
    ORDER BY event_type
  ) AS points_by_triggering_event,
  ARRAY(
    SELECT AS STRUCT event_id, reason
    FROM excluded
    ORDER BY event_id
  ) AS applied_exclusions;
