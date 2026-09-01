package com.seewik.api;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;

@Component
public class FirestoreRecognitionGateway implements RecognitionGateway {
    private static final int PAGE_SIZE = 500;
    private final FirebaseAdminProvider firebase;

    public FirestoreRecognitionGateway(FirebaseAdminProvider firebase) {
        this.firebase = firebase;
    }

    @Override
    public Consent findConsent(String ownerUid) {
        try {
            DocumentSnapshot document = firebase.firestore()
                    .collection("recognitionConsents").document(ownerUid).get().get();
            return document.exists() ? consent(document) : null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Recognition settings could not be loaded", exception);
        } catch (ExecutionException exception) {
            throw failure("Recognition settings could not be loaded", exception.getCause());
        }
    }

    @Override
    public List<Consent> activeConsents() {
        List<Consent> result = new ArrayList<>();
        for (QueryDocumentSnapshot document : paged(
                firebase.firestore().collection("recognitionConsents").whereEqualTo("status", "OPTED_IN"),
                "Recognition candidates could not be loaded")) {
            result.add(consent(document));
        }
        return List.copyOf(result);
    }

    @Override
    public Consent saveConsent(Consent consent, ConsentEvent event) {
        Firestore store = firebase.firestore();
        var consentRef = store.collection("recognitionConsents").document(consent.ownerUid());
        var eventRef = store.collection("recognitionConsentEvents").document(event.eventId());
        try {
            store.runTransaction(transaction -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("ownerUid", consent.ownerUid());
                data.put("publicDisplayName", consent.publicDisplayName());
                data.put("normalizedDisplayName", consent.normalizedDisplayName());
                data.put("status", consent.status());
                data.put("consentedAt", date(consent.consentedAt()));
                data.put("withdrawnAt", date(consent.withdrawnAt()));
                data.put("updatedAt", FieldValue.serverTimestamp());
                data.put("schemaVersion", consent.schemaVersion());
                transaction.set(consentRef, data, SetOptions.merge());

                Map<String, Object> eventData = new LinkedHashMap<>();
                eventData.put("eventId", event.eventId());
                eventData.put("ownerUid", event.ownerUid());
                eventData.put("eventType", event.eventType());
                eventData.put("publicDisplayNameHash", event.publicDisplayNameHash());
                eventData.put("occurredAt", Date.from(event.occurredAt()));
                eventData.put("schemaVersion", event.schemaVersion());
                transaction.create(eventRef, eventData);
                return null;
            }).get();
            return consent;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Recognition settings could not be saved", exception);
        } catch (ExecutionException exception) {
            throw failure("Recognition settings could not be saved", exception.getCause());
        }
    }

    @Override
    public List<String> collidingOwnerUids(String normalizedDisplayName, String excludingOwnerUid) {
        try {
            return firebase.firestore().collection("recognitionConsents")
                    .whereEqualTo("normalizedDisplayName", normalizedDisplayName)
                    .limit(25)
                    .get().get().getDocuments().stream()
                    .map(document -> document.getString("ownerUid"))
                    .filter(uid -> uid != null && !uid.equals(excludingOwnerUid))
                    .sorted()
                    .toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Display-name collision monitoring could not run", exception);
        } catch (ExecutionException exception) {
            throw failure("Display-name collision monitoring could not run", exception.getCause());
        }
    }

    @Override
    public void recordNameCollision(NameCollisionEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", event.eventId());
        data.put("ownerUidHash", event.ownerUidHash());
        data.put("collidingOwnerUidHashes", event.collidingOwnerUidHashes());
        data.put("normalizedDisplayNameHash", event.normalizedDisplayNameHash());
        data.put("occurredAt", Date.from(event.occurredAt()));
        data.put("schemaVersion", event.schemaVersion());
        create("recognitionNameCollisions", event.eventId(), data, "Name-collision monitoring could not be saved");
    }

    @Override
    public List<Map<String, Object>> awardedLedgerEntries() {
        return paged(
                firebase.firestore().collection("pointsLedger").whereEqualTo("policyStatus", "AWARDED"),
                "Contribution ledger could not be loaded").stream()
                .map(FirestoreRecognitionGateway::ledger)
                .toList();
    }

    @Override
    public List<Map<String, Object>> ownerLedgerEntries(String ownerUid) {
        return paged(
                firebase.firestore().collection("pointsLedger").whereEqualTo("ownerUid", ownerUid),
                "Your contribution ledger could not be loaded").stream()
                .map(FirestoreRecognitionGateway::ledger)
                .toList();
    }

    @Override
    public List<RewardClaim> ownerRewardClaims(String ownerUid) {
        return paged(
                firebase.firestore().collection("recognitionRewardClaims")
                        .whereEqualTo("ownerUid", ownerUid),
                "Reward claim records could not be loaded").stream()
                .map(FirestoreRecognitionGateway::rewardClaim)
                .toList();
    }

    @Override
    public RewardClaim findRewardClaim(String claimId) {
        try {
            DocumentSnapshot snapshot = firebase.firestore()
                    .collection("recognitionRewardClaims").document(claimId).get().get();
            return snapshot.exists() ? rewardClaim(snapshot) : null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Reward claims could not be loaded", exception);
        } catch (ExecutionException exception) {
            throw failure("Reward claims could not be loaded", exception.getCause());
        }
    }

    @Override
    public RewardClaim createRewardClaim(RewardClaim claim, RewardClaimEvent event) {
        Firestore store = firebase.firestore();
        var claimRef = store.collection("recognitionRewardClaims").document(claim.claimId());
        var eventRef = store.collection("recognitionRewardEvents").document(event.eventId());
        try {
            return store.runTransaction(transaction -> {
                DocumentSnapshot existing = transaction.get(claimRef).get();
                if (existing.exists()) return rewardClaim(existing);
                transaction.create(claimRef, rewardClaimToDocument(claim));
                transaction.create(eventRef, rewardEventToDocument(event));
                return claim;
            }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Reward claim could not be saved", exception);
        } catch (ExecutionException exception) {
            throw failure("Reward claim could not be saved", exception.getCause());
        }
    }

    @Override
    public RewardClaim transitionRewardClaim(
            String claimId,
            String ownerUid,
            String expectedStatus,
            RewardClaim updated,
            RewardClaimEvent event) {
        Firestore store = firebase.firestore();
        var claimRef = store.collection("recognitionRewardClaims").document(claimId);
        var eventRef = store.collection("recognitionRewardEvents").document(event.eventId());
        try {
            return store.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(claimRef).get();
                if (!snapshot.exists()) return null;
                RewardClaim existing = rewardClaim(snapshot);
                if (!ownerUid.equals(existing.ownerUid()) || !expectedStatus.equals(existing.claimStatus())) {
                    return existing;
                }
                transaction.set(claimRef, rewardClaimToDocument(updated), SetOptions.merge());
                transaction.create(eventRef, rewardEventToDocument(event));
                return updated;
            }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Reward claim could not be updated", exception);
        } catch (ExecutionException exception) {
            throw failure("Reward claim could not be updated", exception.getCause());
        }
    }

    @Override
    public MonthSnapshot findMonthSnapshot(String monthKey) {
        try {
            DocumentSnapshot document = firebase.firestore()
                    .collection("recognitionMonths").document(monthKey).get().get();
            if (!document.exists()) return null;
            List<SelectedCitizen> selected = new ArrayList<>();
            Object rawSelected = document.get("selectedCitizens");
            if (rawSelected instanceof List<?> values) {
                for (Object value : values) {
                    if (!(value instanceof Map<?, ?> item)) continue;
                    selected.add(new SelectedCitizen(
                            string(item.get("ownerUid")),
                            string(item.get("publicDisplayName")),
                            integer(item.get("monthlyPoints"))));
                }
            }
            return new MonthSnapshot(
                    monthKey,
                    instant(document.get("monthStart")),
                    instant(document.get("monthEndExclusive")),
                    List.copyOf(selected),
                    integer(document.get("candidateCount")),
                    string(document.get("contentHash")),
                    instant(document.get("generatedAt")),
                    string(document.get("schemaVersion")),
                    string(document.get("rewardPolicyVersion")));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Recognition snapshot could not be loaded", exception);
        } catch (ExecutionException exception) {
            throw failure("Recognition snapshot could not be loaded", exception.getCause());
        }
    }

    @Override
    public boolean saveMonthSnapshotIfChanged(MonthSnapshot snapshot) {
        Firestore store = firebase.firestore();
        var reference = store.collection("recognitionMonths").document(snapshot.monthKey());
        try {
            return store.runTransaction(transaction -> {
                DocumentSnapshot existing = transaction.get(reference).get();
                if (existing.exists() && snapshot.contentHash().equals(existing.getString("contentHash"))) {
                    return false;
                }
                List<Map<String, Object>> selected = snapshot.selectedCitizens().stream().map(item -> Map.<String, Object>of(
                        "ownerUid", item.ownerUid(),
                        "ownerUidHash", RecognitionService.hash(item.ownerUid()),
                        "publicDisplayName", item.publicDisplayName(),
                        "monthlyPoints", item.monthlyPoints())).toList();
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("monthKey", snapshot.monthKey());
                data.put("timezone", RecognitionService.RECOGNITION_ZONE.getId());
                data.put("monthStart", Date.from(snapshot.monthStart()));
                data.put("monthEndExclusive", Date.from(snapshot.monthEndExclusive()));
                data.put("selectedCitizens", selected);
                data.put("publicNames", snapshot.selectedCitizens().stream()
                        .map(SelectedCitizen::publicDisplayName).toList());
                data.put("candidateCount", snapshot.candidateCount());
                data.put("contentHash", snapshot.contentHash());
                data.put("generatedAt", FieldValue.serverTimestamp());
                data.put("schemaVersion", snapshot.schemaVersion());
                data.put("rewardPolicyVersion", snapshot.rewardPolicyVersion());
                transaction.set(reference, data);
                return true;
            }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Recognition snapshot could not be saved", exception);
        } catch (ExecutionException exception) {
            throw failure("Recognition snapshot could not be saved", exception.getCause());
        }
    }

    @Override
    public void recordAbuseReport(AbuseReport report) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportId", report.reportId());
        data.put("reporterUidHash", report.reporterUidHash());
        data.put("targetOwnerUidHash", report.targetOwnerUidHash());
        data.put("targetDisplayNameHash", report.targetDisplayNameHash());
        data.put("monthKey", report.monthKey());
        data.put("targetPosition", report.targetPosition());
        data.put("reason", report.reason());
        data.put("details", report.details());
        data.put("occurredAt", Date.from(report.occurredAt()));
        data.put("schemaVersion", report.schemaVersion());
        create("recognitionAbuseReports", report.reportId(), data, "The recognition report could not be saved");
    }

    private void create(String collection, String id, Map<String, Object> data, String message) {
        try {
            firebase.firestore().collection(collection).document(id).create(data).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(message, exception);
        } catch (ExecutionException exception) {
            throw failure(message, exception.getCause());
        }
    }

    private List<QueryDocumentSnapshot> paged(Query base, String message) {
        List<QueryDocumentSnapshot> result = new ArrayList<>();
        Query page = base.orderBy(FieldPath.documentId()).limit(PAGE_SIZE);
        try {
            while (true) {
                List<QueryDocumentSnapshot> documents = page.get().get().getDocuments();
                result.addAll(documents);
                if (documents.size() < PAGE_SIZE) return List.copyOf(result);
                page = base.orderBy(FieldPath.documentId())
                        .startAfter(documents.getLast())
                        .limit(PAGE_SIZE);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(message, exception);
        } catch (ExecutionException exception) {
            throw failure(message, exception.getCause());
        }
    }

    private static Consent consent(DocumentSnapshot document) {
        return new Consent(
                string(document.get("ownerUid")),
                string(document.get("publicDisplayName")),
                string(document.get("normalizedDisplayName")),
                string(document.get("status")),
                instant(document.get("consentedAt")),
                instant(document.get("withdrawnAt")),
                instant(document.get("updatedAt")),
                string(document.get("schemaVersion")));
    }

    private static Map<String, Object> ledger(QueryDocumentSnapshot document) {
        Map<String, Object> result = new LinkedHashMap<>(document.getData());
        result.putIfAbsent("ledgerEntryId", document.getId());
        return result;
    }

    private static Map<String, Object> rewardClaimToDocument(RewardClaim claim) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("claimId", claim.claimId());
        result.put("ownerUid", claim.ownerUid());
        result.put("couponId", claim.couponId());
        result.put("businessId", claim.businessId());
        result.put("tierRequired", claim.tierRequired());
        result.put("code", claim.code());
        result.put("claimedAt", Date.from(claim.claimedAt()));
        result.put("expiresAt", Date.from(claim.expiresAt()));
        result.put("usedAt", claim.usedAt() == null ? null : Date.from(claim.usedAt()));
        result.put("claimStatus", claim.claimStatus());
        result.put("schemaVersion", claim.schemaVersion());
        result.put("contractVersion", claim.contractVersion());
        result.put("updatedAt", FieldValue.serverTimestamp());
        return result;
    }

    private static Map<String, Object> rewardEventToDocument(RewardClaimEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", event.eventId());
        data.put("claimId", event.claimId());
        data.put("ownerUid", event.ownerUid());
        data.put("eventType", event.eventType());
        data.put("couponId", event.couponId());
        data.put("occurredAt", Date.from(event.occurredAt()));
        data.put("schemaVersion", event.schemaVersion());
        return data;
    }

    private static RewardClaim rewardClaim(DocumentSnapshot document) {
        return new RewardClaim(
                string(document.get("claimId")),
                string(document.get("ownerUid")),
                string(document.get("couponId")),
                string(document.get("businessId")),
                integer(document.get("tierRequired")),
                string(document.get("code")),
                instant(document.get("claimedAt")),
                instant(document.get("expiresAt")),
                instant(document.get("usedAt")),
                string(document.get("claimStatus")),
                string(document.get("schemaVersion")),
                string(document.get("contractVersion")));
    }

    private static Date date(Instant value) {
        return value == null ? null : Date.from(value);
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toDate().toInstant();
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value == null) return null;
        try {
            return Instant.parse(value.toString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static RecognitionService.RecognitionException failure(String message, Throwable cause) {
        return new RecognitionService.RecognitionException("RECOGNITION_STORE_FAILED", message, cause);
    }
}
