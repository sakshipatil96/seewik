package com.seewik.api;

import java.util.Map;
import java.util.Set;

public final class ReportLifecycleContract {
    public static final String SCHEMA_VERSION = "report-lifecycle-v0.1";
    public static final String ROUTE_SNAPSHOT_SCHEMA_VERSION = "route-snapshot-v0.1";

    private ReportLifecycleContract() {}

    public enum ReportStatus {
        DRAFT,
        FILED,
        OVERDUE,
        CLAIMED_FIXED,
        VERIFIED_FIXED,
        REOPENED
    }

    public enum EventType {
        REPORT_FILED,
        OVERDUE_REACHED,
        REPAIR_CLAIM_RECORDED,
        FIX_VERIFIED,
        REPAIR_CLAIM_REJECTED,
        ISSUE_RECURRED
    }

    public enum VerificationBasis {
        CITIZEN_ATTESTATION,
        CITIZEN_PHOTO,
        MUNICIPAL_ACKNOWLEDGEMENT,
        NONE
    }

    public static final Map<ReportStatus, Set<ReportStatus>> ALLOWED_TRANSITIONS = Map.of(
            ReportStatus.DRAFT, Set.of(ReportStatus.FILED),
            ReportStatus.FILED, Set.of(ReportStatus.OVERDUE, ReportStatus.CLAIMED_FIXED),
            ReportStatus.OVERDUE, Set.of(ReportStatus.CLAIMED_FIXED),
            ReportStatus.CLAIMED_FIXED, Set.of(ReportStatus.VERIFIED_FIXED, ReportStatus.REOPENED),
            ReportStatus.VERIFIED_FIXED, Set.of(ReportStatus.REOPENED),
            ReportStatus.REOPENED, Set.of(ReportStatus.OVERDUE, ReportStatus.CLAIMED_FIXED));

    public static boolean allows(ReportStatus from, ReportStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static EventType eventType(ReportStatus from, ReportStatus to) {
        if (from == ReportStatus.DRAFT && to == ReportStatus.FILED) return EventType.REPORT_FILED;
        if ((from == ReportStatus.FILED || from == ReportStatus.REOPENED) && to == ReportStatus.OVERDUE) {
            return EventType.OVERDUE_REACHED;
        }
        if ((from == ReportStatus.FILED || from == ReportStatus.OVERDUE || from == ReportStatus.REOPENED)
                && to == ReportStatus.CLAIMED_FIXED) {
            return EventType.REPAIR_CLAIM_RECORDED;
        }
        if (from == ReportStatus.CLAIMED_FIXED && to == ReportStatus.VERIFIED_FIXED) {
            return EventType.FIX_VERIFIED;
        }
        if (from == ReportStatus.CLAIMED_FIXED && to == ReportStatus.REOPENED) {
            return EventType.REPAIR_CLAIM_REJECTED;
        }
        if (from == ReportStatus.VERIFIED_FIXED && to == ReportStatus.REOPENED) {
            return EventType.ISSUE_RECURRED;
        }
        throw new IllegalArgumentException("Unsupported lifecycle transition: " + from + " -> " + to);
    }
}
