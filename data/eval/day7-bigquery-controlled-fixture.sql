-- Day 7 controlled analytical fixture.
-- These CTE rows never enter production tables and must not be reported as production evidence.
WITH
  fixture_events AS (
    SELECT * FROM UNNEST([
      STRUCT('fixture-r1' AS report_id_hash, 'PRABHAG-01' AS prabhag_id,
        'POTHOLE_ROAD_DAMAGE' AS issue_type, 'FILED' AS to_status, 'REPORT_FILED' AS event_type,
        TIMESTAMP '2026-08-20 08:00:00+00' AS occurred_at, 5 AS points_awarded, FALSE AS overdue_eligible),
      STRUCT('fixture-r1', 'PRABHAG-01', 'POTHOLE_ROAD_DAMAGE', 'VERIFIED_FIXED',
        'REPAIR_VERIFIED', TIMESTAMP '2026-08-22 08:00:00+00', 40, FALSE),
      STRUCT('fixture-r2', 'PRABHAG-01', 'DRAINAGE_SEWAGE', 'FILED',
        'REPORT_FILED', TIMESTAMP '2026-08-21 08:00:00+00', 5, FALSE),
      STRUCT('fixture-r3', 'PRABHAG-02', 'GARBAGE_SOLID_WASTE', 'FILED',
        'REPORT_FILED', TIMESTAMP '2026-08-23 08:00:00+00', 0, FALSE)
    ])
  ),
  fixture_dedupe AS (
    SELECT * FROM UNNEST([
      STRUCT('POSSIBLE_DUPLICATE' AS disposition, 25.0 AS measured_distance_meters),
      STRUCT('NO_MATCH' AS disposition, 120.0 AS measured_distance_meters),
      STRUCT('DEDUPE_NOT_EVALUATED' AS disposition, CAST(NULL AS FLOAT64) AS measured_distance_meters)
    ])
  ),
  report_times AS (
    SELECT
      report_id_hash,
      MIN(IF(to_status = 'FILED', occurred_at, NULL)) AS filed_at,
      MIN(IF(to_status = 'VERIFIED_FIXED', occurred_at, NULL)) AS verified_at
    FROM fixture_events
    GROUP BY report_id_hash
  ),
  resolution_durations AS (
    SELECT TIMESTAMP_DIFF(verified_at, filed_at, SECOND) AS duration_seconds
    FROM report_times
    WHERE filed_at IS NOT NULL AND verified_at IS NOT NULL AND verified_at >= filed_at
  )
SELECT
  CURRENT_TIMESTAMP() AS queried_at,
  'CONTROLLED_CTE_FIXTURE_NOT_PRODUCTION' AS evidence_class,
  ARRAY(
    SELECT AS STRUCT prabhag_id, issue_type, COUNT(DISTINCT report_id_hash) AS report_count
    FROM fixture_events GROUP BY prabhag_id, issue_type ORDER BY prabhag_id, issue_type
  ) AS reports_by_prabhag_category,
  STRUCT(
    COUNT(DISTINCT IF(to_status = 'FILED', report_id_hash, NULL)) AS filed_reports,
    COUNT(DISTINCT IF(to_status = 'VERIFIED_FIXED', report_id_hash, NULL)) AS verified_fixed_reports,
    SAFE_DIVIDE(
      COUNT(DISTINCT IF(to_status = 'VERIFIED_FIXED', report_id_hash, NULL)),
      COUNT(DISTINCT IF(to_status = 'FILED', report_id_hash, NULL))
    ) AS verified_resolution_rate
  ) AS verified_resolution,
  STRUCT(
    (SELECT COUNT(*) FROM resolution_durations) AS resolved_report_count,
    (SELECT APPROX_QUANTILES(duration_seconds, 100)[SAFE_OFFSET(50)] FROM resolution_durations)
      AS median_filed_to_verified_seconds
  ) AS filed_to_verified_duration,
  STRUCT(
    COUNTIF(overdue_eligible) AS overdue_eligible_events,
    COUNT(DISTINCT IF(overdue_eligible, report_id_hash, NULL)) AS overdue_eligible_reports
  ) AS overdue_eligibility,
  ARRAY(
    SELECT AS STRUCT disposition, COUNT(*) AS evaluation_count,
      COUNT(measured_distance_meters) AS measured_count,
      MIN(measured_distance_meters) AS min_distance_meters,
      APPROX_QUANTILES(measured_distance_meters, 100)[SAFE_OFFSET(50)] AS median_distance_meters,
      MAX(measured_distance_meters) AS max_distance_meters
    FROM fixture_dedupe GROUP BY disposition ORDER BY disposition
  ) AS dedupe_distance_analysis,
  ARRAY(
    SELECT AS STRUCT event_type AS triggering_event, COUNT(*) AS event_count,
      SUM(points_awarded) AS points_awarded
    FROM fixture_events GROUP BY event_type ORDER BY event_type
  ) AS points_by_triggering_event
FROM fixture_events;
