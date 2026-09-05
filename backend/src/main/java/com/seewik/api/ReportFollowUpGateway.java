package com.seewik.api;

import java.util.List;
import java.util.Map;

public interface ReportFollowUpGateway {
    ReportBundle load(String reportId, String ownerUid);

    boolean append(
            String reportId,
            String ownerUid,
            String eventId,
            String requestFingerprint,
            Map<String, Object> event);

    record ReportBundle(
            Map<String, Object> report,
            List<Map<String, Object>> followUpEvents,
            List<Map<String, Object>> lifecycleEvents) {}
}
