package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(result.canManage());
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

    @Test
    void malformedCoordinatesRadiusAndExcessivelyFutureDateAreRejected() {
        var badCoordinates = new InitiativeService.CreateRequest(
                "Clean-up", "CLEANUP", "Clean the public square", "2026-08-28T08:00:00Z",
                "Public square", 91.0, 74.24, "Gloves");
        var tooFarAhead = new InitiativeService.CreateRequest(
                "Clean-up", "CLEANUP", "Clean the public square", "2027-08-29T08:00:00Z",
                "Public square", 21.36, 74.24, "Gloves");

        assertEquals("INVALID_COORDINATES", assertThrows(
                InitiativeService.InitiativeException.class,
                () -> service.create("owner-1", badCoordinates)).code());
        assertEquals("INVALID_START_TIME", assertThrows(
                InitiativeService.InitiativeException.class,
                () -> service.create("owner-1", tooFarAhead)).code());
        assertEquals("INVALID_RADIUS", assertThrows(
                InitiativeService.InitiativeException.class,
                () -> service.discover(new InitiativeService.DiscoveryRequest(21.36, 74.24, 26.0))).code());
    }

    @Test
    void organiserCanCancelOnceWithAReasonAndNoPoints() {
        gateway.byId.put("init-1", ownedActivity("init-1", "owner-1", "2026-08-28T08:00:00Z"));

        InitiativeService.TransitionResponse first = service.cancel(
                "owner-1", "init-1", new InitiativeService.CancelRequest("Heavy rain"));
        InitiativeService.TransitionResponse replay = service.cancel(
                "owner-1", "init-1", new InitiativeService.CancelRequest("Heavy rain"));

        assertEquals("CANCELLED", first.initiativeStatus());
        assertFalse(first.idempotentReplay());
        assertTrue(replay.idempotentReplay());
        assertEquals(0, first.pointsAwarded());
        assertEquals(1, gateway.transitionEvents);
    }

    @Test
    void completionRequiresOrganiserAndScheduledTimeAndIsIdempotent() {
        gateway.byId.put("future", ownedActivity("future", "owner-1", "2026-08-28T08:00:00Z"));
        gateway.byId.put("past", ownedActivity("past", "owner-1", "2026-08-26T08:00:00Z"));

        InitiativeService.InitiativeException early = assertThrows(
                InitiativeService.InitiativeException.class,
                () -> service.complete("owner-1", "future"));
        InitiativeService.InitiativeException unrelated = assertThrows(
                InitiativeService.InitiativeException.class,
                () -> service.complete("owner-2", "past"));
        InitiativeService.TransitionResponse first = service.complete("owner-1", "past");
        InitiativeService.TransitionResponse replay = service.complete("owner-1", "past");

        assertEquals("INITIATIVE_NOT_STARTED", early.code());
        assertEquals("INITIATIVE_FORBIDDEN", unrelated.code());
        assertEquals("COMPLETED", first.initiativeStatus());
        assertTrue(replay.idempotentReplay());
        assertEquals(0, first.pointsAwarded());
        assertEquals(1, gateway.transitionEvents);
    }

    @Test
    void oppositeFinalTransitionAndMissingCancellationReasonAreRejected() {
        gateway.byId.put("init-1", ownedActivity("init-1", "owner-1", "2026-08-26T08:00:00Z"));
        service.complete("owner-1", "init-1");

        InitiativeService.InitiativeException invalid = assertThrows(
                InitiativeService.InitiativeException.class,
                () -> service.cancel("owner-1", "init-1", new InitiativeService.CancelRequest("Changed")));
        InitiativeService.InitiativeException missingReason = assertThrows(
                InitiativeService.InitiativeException.class,
                () -> service.cancel("owner-1", "another", new InitiativeService.CancelRequest(" ")));

        assertEquals("INITIATIVE_INVALID_TRANSITION", invalid.code());
        assertEquals("CANCELLATION_REASON_REQUIRED", missingReason.code());
    }

    @Test
    void myActivitiesIncludesFinalStatusForJoinedCitizensWithoutPrivateLocation() {
        Map<String, Object> cancelled = ownedActivity("init-1", "owner-1", "2026-08-28T08:00:00Z");
        cancelled.put("status", "CANCELLED");
        gateway.byId.put("init-1", cancelled);
        gateway.roles.put("init-1:owner-2", "PARTICIPANT");

        InitiativeService.MyInitiativesResponse result = service.mine("owner-2");

        assertEquals(1, result.count());
        assertEquals("CANCELLED", result.initiatives().getFirst().status());
        assertEquals("PARTICIPANT", result.initiatives().getFirst().role());
        assertFalse(result.initiatives().getFirst().canManage());
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

    private static Map<String, Object> ownedActivity(String id, String ownerUid, String startAt) {
        Map<String, Object> activity = activity(id, 21.36, 74.24, startAt);
        activity.put("ownerUid", ownerUid);
        return activity;
    }

    private static final class FakeGateway implements InitiativeGateway {
        private final List<Map<String, Object>> published = new ArrayList<>();
        private final Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        private final Map<String, Boolean> joined = new LinkedHashMap<>();
        private final Map<String, String> roles = new LinkedHashMap<>();
        private int transitionEvents;
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
            roles.put(initiativeId + ":" + ownerUid, "ORGANISER");
            return initiative;
        }

        @Override
        public List<CitizenInitiative> listPublished(String ownerUid) {
            return published.stream()
                    .map(item -> new CitizenInitiative(
                            item, roles.get(item.get("initiativeId") + ":" + ownerUid)))
                    .toList();
        }

        @Override
        public List<CitizenInitiative> listForCitizen(String ownerUid) {
            return byId.values().stream()
                    .filter(item -> roles.containsKey(item.get("initiativeId") + ":" + ownerUid))
                    .map(item -> new CitizenInitiative(
                            item, roles.get(item.get("initiativeId") + ":" + ownerUid)))
                    .toList();
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
            roles.put(key, "PARTICIPANT");
            return new JoinResult(initiative, false);
        }

        @Override
        public TransitionResult transition(
                String ownerUid,
                String initiativeId,
                String targetStatus,
                String cancellationReason,
                Instant occurredAt) {
            Map<String, Object> initiative = byId.get(initiativeId);
            if (initiative == null) {
                throw new InitiativeService.InitiativeException("INITIATIVE_NOT_FOUND", "Activity was not found");
            }
            if (!ownerUid.equals(initiative.get("ownerUid"))) {
                throw new InitiativeService.InitiativeException("INITIATIVE_FORBIDDEN", "Only organiser");
            }
            if (targetStatus.equals(initiative.get("status"))) return new TransitionResult(initiative, true);
            if (!"PUBLISHED".equals(initiative.get("status"))) {
                throw new InitiativeService.InitiativeException("INITIATIVE_INVALID_TRANSITION", "Final state");
            }
            if ("COMPLETED".equals(targetStatus)
                    && occurredAt.isBefore(Instant.parse(String.valueOf(initiative.get("startAt"))))) {
                throw new InitiativeService.InitiativeException("INITIATIVE_NOT_STARTED", "Not started");
            }
            initiative.put("status", targetStatus);
            transitionEvents++;
            return new TransitionResult(initiative, false);
        }
    }
}
