package com.seewik.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ReportDedupeEvaluator {
    public static final double THRESHOLD_METERS = 75.0;
    public static final String HEURISTIC_VERSION = "same-category-75m-v0.1";
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final Set<String> ACTIVE_STATUSES = Set.of("FILED", "OVERDUE", "CLAIMED_FIXED", "REOPENED");

    public DedupeResult evaluate(
            String reportId,
            Map<String, Object> report,
            Collection<Map<String, Object>> candidates,
            Double latitude,
            Double longitude,
            boolean overrideRequested) {
        if (latitude == null || longitude == null) {
            return new DedupeResult("DEDUPE_NOT_EVALUATED", null, null, THRESHOLD_METERS, HEURISTIC_VERSION, false);
        }

        String issueType = string(report, "confirmedIssueType");
        String prabhagId = string(report, "prabhagId");
        String nearestReportId = null;
        Double nearestDistance = null;
        for (Map<String, Object> candidate : candidates) {
            String candidateId = string(candidate, "_documentId");
            if (reportId.equals(candidateId)
                    || !issueType.equals(string(candidate, "confirmedIssueType"))
                    || !prabhagId.equals(string(candidate, "prabhagId"))
                    || !ACTIVE_STATUSES.contains(string(candidate, "status"))) {
                continue;
            }
            Double candidateLatitude = number(candidate.get("dedupeLatitude"));
            Double candidateLongitude = number(candidate.get("dedupeLongitude"));
            if (candidateLatitude == null || candidateLongitude == null) continue;
            double distance = distanceMeters(latitude, longitude, candidateLatitude, candidateLongitude);
            if (nearestDistance == null || distance < nearestDistance) {
                nearestDistance = distance;
                nearestReportId = candidateId;
            }
        }

        if (nearestDistance == null) {
            return new DedupeResult("NO_CANDIDATE", null, null, THRESHOLD_METERS, HEURISTIC_VERSION, false);
        }
        boolean possibleDuplicate = nearestDistance <= THRESHOLD_METERS;
        String disposition = possibleDuplicate
                ? (overrideRequested ? "OVERRIDDEN_POSSIBLE_DUPLICATE" : "POSSIBLE_DUPLICATE")
                : "NOT_A_MATCH";
        return new DedupeResult(
                disposition,
                nearestReportId,
                roundCentimeters(nearestDistance),
                THRESHOLD_METERS,
                HEURISTIC_VERSION,
                possibleDuplicate);
    }

    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double latitudeDelta = Math.toRadians(lat2 - lat1);
        double longitudeDelta = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double roundCentimeters(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static String string(Map<String, Object> value, String key) {
        Object result = value.get(key);
        return result == null ? "" : result.toString();
    }

    public record DedupeResult(
            String disposition,
            String candidateReportId,
            Double measuredDistanceMeters,
            double thresholdMeters,
            String heuristicVersion,
            boolean possibleDuplicate) {}
}
