package com.seewik.api;

import java.util.Map;
import java.util.function.BiFunction;

public interface ReportLifecycleGateway {
    ReportLifecycleService.TransitionResponse transact(
            String reportId,
            String ownerUid,
            String eventId,
            String requestFingerprint,
            ReportLifecycleService.TransitionAttempt attempt,
            BiFunction<Map<String, Object>, ReportDedupeEvaluator.DedupeResult,
                    ReportLifecycleService.TransitionPlan> planner);
}
