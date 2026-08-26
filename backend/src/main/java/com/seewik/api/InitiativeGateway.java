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

    List<Map<String, Object>> listPublished();

    JoinResult join(String ownerUid, String initiativeId, Instant occurredAt);

    record JoinResult(Map<String, Object> initiative, boolean alreadyJoined) {}
}
