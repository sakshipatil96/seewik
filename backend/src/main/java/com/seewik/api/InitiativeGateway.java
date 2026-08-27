package com.seewik.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface InitiativeGateway {
    Map<String, Object> create(
            String ownerUid,
            String initiativeId,
            Map<String, Object> initiative,
            Map<String, Object> event,
            Map<String, Object> participation,
            Map<String, Object> ledgerEntry);

    List<CitizenInitiative> listPublished(String ownerUid);

    List<CitizenInitiative> listForCitizen(String ownerUid);

    JoinResult join(String ownerUid, String initiativeId, Instant occurredAt);

    TransitionResult transition(
            String ownerUid,
            String initiativeId,
            String targetStatus,
            String cancellationReason,
            Instant occurredAt);

    record JoinResult(Map<String, Object> initiative, boolean alreadyJoined) {}

    record CitizenInitiative(Map<String, Object> initiative, String role) {}

    record TransitionResult(Map<String, Object> initiative, boolean idempotentReplay) {}
}
