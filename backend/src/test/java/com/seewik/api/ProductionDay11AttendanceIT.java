package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.ImportUserRecord;
import com.google.firebase.auth.UserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionDay11AttendanceIT {
    private static final String PROJECT_ID = "seewik";
    private static final String TEST_SECRET = "day11-production-firestore-attendance-secret-v0.1";

    @Test
    void linkedAccountsExerciseProductionFirestoreAttendanceAndCleanup() throws Exception {
        FirebaseAdminProvider firebase = new FirebaseAdminProvider(PROJECT_ID);
        FirestoreInitiativeGateway gateway = new FirestoreInitiativeGateway(firebase);
        InitiativeService service = new InitiativeService(
                gateway,
                Clock.systemUTC(),
                new AttendanceCodeService(TEST_SECRET));
        String runId = UUID.randomUUID().toString().replace("-", "");
        List<TestAccount> accounts = new ArrayList<>();
        List<String> importedUserIds = new ArrayList<>();
        List<String> initiativeIds = new ArrayList<>();
        try {
            accounts = createLinkedAccounts(firebase, runId, 4, importedUserIds);
            TestAccount organiser = accounts.get(0);
            TestAccount first = accounts.get(1);
            TestAccount second = accounts.get(2);
            TestAccount rateLimited = accounts.get(3);

            String completionFirst = create(service, organiser.uid(), "Day 11 completion-first " + runId);
            initiativeIds.add(completionFirst);
            startNow(firebase, completionFirst);
            assertEquals(0, service.complete(organiser.uid(), completionFirst).pointsAwarded());
            join(service, first.uid(), completionFirst);
            join(service, second.uid(), completionFirst);
            join(service, rateLimited.uid(), completionFirst);

            String code = service.attendanceCode(organiser.uid(), completionFirst).code();
            assertEquals("INITIATIVE_FORBIDDEN", assertThrows(
                    InitiativeService.InitiativeException.class,
                    () -> service.attendanceCode(first.uid(), completionFirst)).code());

            String incorrect = code.equals("000000") ? "000001" : "000000";
            for (int attempt = 1; attempt <= 5; attempt++) {
                assertEquals("ATTENDANCE_CODE_INVALID", assertThrows(
                        InitiativeService.InitiativeException.class,
                        () -> service.codeAttend(
                                rateLimited.uid(),
                                completionFirst,
                                new InitiativeService.AttendanceCodeRequest(incorrect))).code());
            }
            assertEquals("ATTENDANCE_RATE_LIMITED", assertThrows(
                    InitiativeService.InitiativeException.class,
                    () -> service.codeAttend(
                            rateLimited.uid(),
                            completionFirst,
                            new InitiativeService.AttendanceCodeRequest(incorrect))).code());

            InitiativeService.AttendanceResponse firstAttendance = service.codeAttend(
                    first.uid(), completionFirst, new InitiativeService.AttendanceCodeRequest(code));
            assertEquals(20, firstAttendance.participantPointsAwarded());
            assertEquals(0, firstAttendance.organiserPointsAwarded());
            InitiativeService.AttendanceResponse secondAttendance = service.codeAttend(
                    second.uid(), completionFirst, new InitiativeService.AttendanceCodeRequest(code));
            assertEquals(20, secondAttendance.participantPointsAwarded());
            assertEquals(40, secondAttendance.organiserPointsAwarded());
            InitiativeService.AttendanceResponse replay = service.codeAttend(
                    first.uid(), completionFirst, new InitiativeService.AttendanceCodeRequest(code));
            assertTrue(replay.idempotentReplay());
            assertEquals(0, replay.participantPointsAwarded());

            InitiativeService.InitiativeView completionFirstView = findInitiative(
                    service.mine(first.uid()), completionFirst);
            assertEquals(3, completionFirstView.joinerCount());
            assertEquals(2, completionFirstView.codeAttendanceCount());
            assertEquals(0, completionFirstView.selfAttendanceCount());
            assertFalse(completionFirstView.canSelfAttend());
            assertAwardedLedger(firebase, completionFirst, 2, 1);
            assertAttemptStoresNoCode(firebase, completionFirst, rateLimited.uid());

            String attendanceFirst = create(service, organiser.uid(), "Day 11 attendance-first " + runId);
            initiativeIds.add(attendanceFirst);
            join(service, first.uid(), attendanceFirst);
            join(service, second.uid(), attendanceFirst);
            startNow(firebase, attendanceFirst);
            String secondCode = service.attendanceCode(organiser.uid(), attendanceFirst).code();
            assertEquals(0, service.codeAttend(
                    first.uid(), attendanceFirst, new InitiativeService.AttendanceCodeRequest(secondCode))
                    .organiserPointsAwarded());
            assertEquals("INITIATIVE_ATTENDANCE_EXISTS", assertThrows(
                    InitiativeService.InitiativeException.class,
                    () -> service.cancel(
                            organiser.uid(),
                            attendanceFirst,
                            new InitiativeService.CancelRequest("Must be blocked"))).code());
            assertEquals(0, service.codeAttend(
                    second.uid(), attendanceFirst, new InitiativeService.AttendanceCodeRequest(secondCode))
                    .organiserPointsAwarded());
            assertEquals(40, service.complete(organiser.uid(), attendanceFirst).pointsAwarded());
            assertAwardedLedger(firebase, attendanceFirst, 2, 1);

            String selfFallback = create(service, organiser.uid(), "Day 11 self fallback " + runId);
            initiativeIds.add(selfFallback);
            join(service, first.uid(), selfFallback);
            join(service, second.uid(), selfFallback);
            firebase.firestore().collection("initiatives").document(selfFallback)
                    .update("startAt", Instant.now().minus(4, ChronoUnit.HOURS).toString()).get();
            service.complete(organiser.uid(), selfFallback);
            InitiativeService.AttendanceResponse self = service.selfAttend(first.uid(), selfFallback);
            assertEquals("SELF_ATTESTED", self.attendanceBasis());
            assertEquals(0, self.participantPointsAwarded());
            assertTrue(service.selfAttend(first.uid(), selfFallback).idempotentReplay());
            assertEquals("ATTENDANCE_CODE_WINDOW_CLOSED", assertThrows(
                    InitiativeService.InitiativeException.class,
                    () -> service.attendanceCode(organiser.uid(), selfFallback)).code());
            firebase.firestore().collection("initiatives").document(selfFallback)
                    .update("completedAt", Instant.now().minus(8, ChronoUnit.DAYS).toString()).get();
            assertEquals("SELF_ATTENDANCE_WINDOW_CLOSED", assertThrows(
                    InitiativeService.InitiativeException.class,
                    () -> service.selfAttend(second.uid(), selfFallback)).code());
        } finally {
            for (String initiativeId : initiativeIds) cleanupInitiative(firebase, initiativeId);
            for (String uid : importedUserIds) firebase.auth().deleteUser(uid);
            for (FirebaseApp app : FirebaseApp.getApps()) {
                if ("seewik-backend".equals(app.getName())) app.delete();
            }
        }
    }

    private List<TestAccount> createLinkedAccounts(
            FirebaseAdminProvider firebase,
            String runId,
            int count,
            List<String> importedUserIds) throws Exception {
        List<ImportUserRecord> records = new ArrayList<>();
        List<TestAccount> accounts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String uid = "day11" + index + runId.substring(0, 20);
            String email = "day11-" + index + "-" + runId + "@example.invalid";
            records.add(ImportUserRecord.builder()
                    .setUid(uid)
                    .setEmail(email)
                    .setEmailVerified(true)
                    .addUserProvider(UserProvider.builder()
                            .setProviderId("google.com")
                            .setUid("day11-google-" + index + "-" + runId)
                            .setEmail(email)
                            .build())
                    .build());
            accounts.add(new TestAccount(uid));
        }
        assertEquals(0, firebase.auth().importUsers(records).getFailureCount());
        importedUserIds.addAll(accounts.stream().map(TestAccount::uid).toList());
        for (TestAccount account : accounts) {
            assertTrue(Arrays.stream(firebase.auth().getUser(account.uid()).getProviderData())
                    .anyMatch(provider -> "google.com".equals(provider.getProviderId())));
        }
        return accounts;
    }

    private String create(InitiativeService service, String ownerUid, String title) {
        return service.create(ownerUid, new InitiativeService.CreateRequest(
                title,
                "CLEANUP",
                "Temporary Day 11 production verification; removed after testing.",
                Instant.now().plus(1, ChronoUnit.HOURS).toString(),
                "Day 11 production verification fixture",
                21.3700,
                74.2400,
                "None")).initiativeId();
    }

    private void startNow(FirebaseAdminProvider firebase, String initiativeId) throws Exception {
        firebase.firestore().collection("initiatives").document(initiativeId)
                .update("startAt", Instant.now().minus(10, ChronoUnit.MINUTES).toString()).get();
    }

    private void join(InitiativeService service, String ownerUid, String initiativeId) {
        InitiativeService.JoinResponse joined = service.join(ownerUid, initiativeId);
        assertTrue("JOINED".equals(joined.status()) || "ALREADY_JOINED".equals(joined.status()));
    }

    private void assertAwardedLedger(
            FirebaseAdminProvider firebase, String initiativeId, int participantAwards, int organiserAwards)
            throws Exception {
        var entries = firebase.firestore().collection("pointsLedger")
                .whereEqualTo("sourceId", initiativeId).get().get().getDocuments();
        assertEquals((long) participantAwards, entries.stream()
                .filter(entry -> Long.valueOf(20).equals(entry.getLong("awardedPoints"))).count());
        assertEquals((long) organiserAwards, entries.stream()
                .filter(entry -> Long.valueOf(40).equals(entry.getLong("awardedPoints"))).count());
        assertTrue(entries.stream().allMatch(entry -> "points-ledger-v0.3".equals(entry.getString("schemaVersion"))));
    }

    private void assertAttemptStoresNoCode(
            FirebaseAdminProvider firebase, String initiativeId, String ownerUid) throws Exception {
        var attempts = firebase.firestore().collection("initiativeAttendanceAttempts")
                .whereEqualTo("initiativeIdHash", InitiativeService.hash(initiativeId))
                .whereEqualTo("ownerUidHash", InitiativeService.hash(ownerUid))
                .get().get().getDocuments();
        assertEquals(1, attempts.size());
        Map<String, Object> attempt = attempts.getFirst().getData();
        assertEquals(5L, attempt.get("failedAttempts"));
        assertFalse(attempt.containsKey("code"));
        assertFalse(attempt.containsKey("submittedCode"));
    }

    private static InitiativeService.InitiativeView findInitiative(
            InitiativeService.MyInitiativesResponse response, String initiativeId) {
        return response.initiatives().stream()
                .filter(initiative -> initiativeId.equals(initiative.initiativeId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Activity missing from response"));
    }

    private static void cleanupInitiative(FirebaseAdminProvider firebase, String initiativeId) throws Exception {
        var initiative = firebase.firestore().collection("initiatives").document(initiativeId);
        for (var event : initiative.collection("events").get().get().getDocuments()) {
            event.getReference().delete().get();
        }
        for (var participation : firebase.firestore().collection("initiativeParticipations")
                .whereEqualTo("initiativeId", initiativeId).get().get().getDocuments()) {
            participation.getReference().delete().get();
        }
        for (var attempt : firebase.firestore().collection("initiativeAttendanceAttempts")
                .whereEqualTo("initiativeIdHash", InitiativeService.hash(initiativeId))
                .get().get().getDocuments()) {
            attempt.getReference().delete().get();
        }
        for (var entry : firebase.firestore().collection("pointsLedger")
                .whereEqualTo("sourceId", initiativeId).get().get().getDocuments()) {
            entry.getReference().delete().get();
        }
        if (initiative.get().get().exists()) initiative.delete().get();
    }

    private record TestAccount(String uid) {}
}
