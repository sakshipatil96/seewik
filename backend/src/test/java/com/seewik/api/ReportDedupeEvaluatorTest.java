package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportDedupeEvaluatorTest {
    private final ReportDedupeEvaluator evaluator = new ReportDedupeEvaluator();

    @Test
    void fourNudgedPotholesYieldOneCanonicalAndThreePossibleDuplicates() {
        Map<String, Object> draft = report("new", "DRAFT", 0, 0);
        List<Map<String, Object>> filed = new ArrayList<>();
        double[][] points = {
            {21.370000, 74.240000},
            {21.370100, 74.240000},
            {21.370300, 74.240000},
            {21.370540, 74.240000}
        };
        List<String> dispositions = new ArrayList<>();
        for (int index = 0; index < points.length; index++) {
            var result = evaluator.evaluate(
                    "attempt-" + index, draft, filed, points[index][0], points[index][1], false);
            dispositions.add(result.disposition());
            if (index == 0) filed.add(report("attempt-0", "FILED", points[index][0], points[index][1]));
        }
        assertEquals(List.of("NO_CANDIDATE", "POSSIBLE_DUPLICATE", "POSSIBLE_DUPLICATE", "POSSIBLE_DUPLICATE"),
                dispositions);
    }

    @Test
    void nonMatchStillRecordsTheNearestMeasuredDistance() {
        var result = evaluator.evaluate(
                "new",
                report("new", "DRAFT", 0, 0),
                List.of(report("existing", "FILED", 21.370000, 74.240000)),
                21.371000,
                74.240000,
                false);
        assertEquals("NOT_A_MATCH", result.disposition());
        assertNotNull(result.measuredDistanceMeters());
        assertTrue(result.measuredDistanceMeters() > 75.0);
    }

    @Test
    void missingCoordinatesIsExplicitlyNotEvaluated() {
        var result = evaluator.evaluate("new", report("new", "DRAFT", 0, 0), List.of(), null, null, false);
        assertEquals("DEDUPE_NOT_EVALUATED", result.disposition());
        assertEquals(null, result.measuredDistanceMeters());
    }

    private static Map<String, Object> report(String id, String status, double latitude, double longitude) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("_documentId", id);
        report.put("status", status);
        report.put("confirmedIssueType", "POTHOLE_ROAD_DAMAGE");
        report.put("prabhagId", "PRABHAG-03");
        if (latitude != 0 || longitude != 0) {
            report.put("dedupeLatitude", latitude);
            report.put("dedupeLongitude", longitude);
        }
        return report;
    }
}
