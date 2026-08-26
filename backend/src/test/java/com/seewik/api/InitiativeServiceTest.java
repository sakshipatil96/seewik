package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InitiativeServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");
    private FakeGateway gateway;
    private InitiativeService service;

    @BeforeEach
    void setUp() {
        gateway = new FakeGateway();
        service = new InitiativeService(gateway, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void creatingAnActivityPublishesItWithTheOrganiserAndZeroUnverifiedPoints() {
        InitiativeService.InitiativeView result = service.create("owner-1", request());

        assertEquals("PUBLISHED", result.status());
        assertEquals(1, result.participantCount());
        assertEquals("INITIATIVE_CREATED", gateway.event.get("eventType"));
        assertEquals("ORGANISER", gateway.participation.get("role"));
        assertEquals(0, gateway.ledger.get("awardedPoints"));
        assertEquals(0, gateway.ledger.get("pointsAwarded"));
        assertEquals("RECORDED_NOT_REWARDED", gateway.ledger.get("policyStatus"));
        assertEquals("points-ledger-v0.2", gateway.ledger.get("schemaVersion"));
    }

    @Test
    void nearbyDiscoveryFiltersByDistanceAndPastDateThenSortsNearestFirst() {
        gateway.published.add(activity("near", 21.3700, 74.2400, "2026-08-28T08:00:00Z"));
        gateway.published.add(activity("far", 21.4300, 74.2400, "2026-08-28T08:00:00Z"));
        gateway.published.add(activity("past", 21.3600, 74.2400, "2026-08-26T08:00:00Z"));

        InitiativeService.DiscoveryResponse result = service.discover(
                new InitiativeService.DiscoveryRequest(21.3600, 74.2400, 10.0));

        assertEquals(2, result.count());
        assertEquals("near", result.initiatives().get(0).initiativeId());
        assertEquals("far", result.initiatives().get(1).initiativeId());
    }

    @Test
    void joiningIsIdempotentAndLiveCountChangesOnlyOnce() {
        Map<String, Object> activity = activity("init-1", 21.36, 74.24, "2026-08-28T08:00:00Z");
        gateway.byId.put("init-1", activity);

        InitiativeService.JoinResponse first = service.join("owner-2", "init-1");
        InitiativeService.JoinResponse replay = service.join("owner-2", "init-1");

        assertEquals("JOINED", first.status());
        assertEquals(2, first.participantCount());
        assertEquals("ALREADY_JOINED", replay.status());
        assertEquals(2, replay.participantCount());
    }

    @Test
    void invalidOrPastActivitiesAreRejected() {
        var invalid = new InitiativeService.CreateRequest(
                "Clean-up", "CLEANUP", "Clean the public square", "2026-08-26T08:00:00Z",
                "Public square", 21.36, 74.24, "Gloves");
        InitiativeService.InitiativeException exception = assertThrows(
                InitiativeService.InitiativeException.class,
                () -> service.create("owner-1", invalid));
        assertEquals("INVALID_START_TIME", exception.code());
    }

    private static InitiativeService.CreateRequest request() {
        return new InitiativeService.CreateRequest(
                "Neighbourhood clean-up",
                "CLEANUP",
                "Clean the public square together.",
                "2026-08-28T08:00:00Z",
                "Nehru Chowk",
                21.36,
                74.24,
                "Bring gloves and reusable water bottles");
    }

    private static Map<String, Object> activity(
            String id, double latitude, double longitude, String startAt) {
        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("initiativeId", id);
        activity.put("title", "Community activity");
        activity.put("category", "CLEANUP");
        activity.put("description", "A useful local activity");
        activity.put("startAt", startAt);
        activity.put("placeName", "Public place");
        activity.put("latitude", latitude);
        activity.put("longitude", longitude);
        activity.put("needs", "");
        activity.put("status", "PUBLISHED");
        activity.put("participantCount", 1);
        activity.put("schemaVersion", "initiative-v0.1");
        return activity;
    }

    private static final class FakeGateway implements InitiativeGateway {
        private final List<Map<String, Object>> published = new ArrayList<>();
        private final Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        private final Map<String, Boolean> joined = new LinkedHashMap<>();
        private Map<String, Object> event;
        private Map<String, Object> participation;
        private Map<String, Object> ledger;

        @Override
        public Map<String, Object> create(
                String ownerUid,
                String initiativeId,
                Map<String, Object> initiative,
                Map<String, Object> event,
                Map<String, Object> participation,
                Map<String, Object> ledgerEntry) {
            this.event = event;
            this.participation = participation;
            this.ledger = ledgerEntry;
            byId.put(initiativeId, initiative);
            return initiative;
        }

        @Override
        public List<Map<String, Object>> listPublished() {
            return List.copyOf(published);
        }

        @Override
        public JoinResult join(String ownerUid, String initiativeId, Instant occurredAt) {
            Map<String, Object> initiative = byId.get(initiativeId);
            if (initiative == null) {
                throw new InitiativeService.InitiativeException("INITIATIVE_NOT_FOUND", "Activity was not found");
            }
            String key = initiativeId + ":" + ownerUid;
            if (joined.putIfAbsent(key, true) != null) return new JoinResult(initiative, true);
            initiative.put("participantCount", ((Number) initiative.get("participantCount")).intValue() + 1);
            return new JoinResult(initiative, false);
        }
    }
}
