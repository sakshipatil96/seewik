# Day 11 Initiative attendance and reward contract v0.1

Status: frozen before implementation on 2026-08-30.

Contract versions introduced by this work:

- Initiative and participation documents: `initiative-v0.2`
- Attendance events: `initiative-attendance-v0.1`
- Contribution ledger entries: `points-ledger-v0.3`
- Reward policy: `reward-policy-v0.2`

## Frozen contribution values

| Contribution | Points | Idempotency scope |
| --- | ---: | --- |
| First accepted report filing | 5 | report and reason |
| Organiser-code attendance | 20 | participant and Initiative |
| Completed Initiative organiser threshold | 40 | organiser and Initiative |
| First confirmed civic fix | 60 | report and reason, forward-only from policy deployment |
| Self-attested Initiative attendance | 0 | participant and Initiative |

Historical 40-point `FIX_VERIFIED` ledger entries are immutable. They receive no adjustment and are not rewritten.

## Membership and late joining

- Membership and attendance are separate states.
- The organiser's automatic `ORGANISER` membership is not a joined participant.
- A Google-linked citizen may join a `PUBLISHED` Initiative before it starts.
- The confirmed late-join default permits joining from `startAt` through `startAt + 3 hours`, including after the organiser marks the Initiative `COMPLETED` during that window.
- Joining is idempotent and never awards points.

## Self-attendance

- Only a Google-linked owner of a `PARTICIPANT` record may report their own attendance.
- The Initiative must be `COMPLETED`.
- The server-time window is inclusive from `completedAt` through `completedAt + 7 days`.
- The participation record receives `attendanceStatus: I_ATTENDED`, `attendanceBasis: SELF_ATTESTED` and a server reporting time.
- One hashed-actor, append-only event is recorded atomically with the participation update.
- Repeated identical requests are idempotent. An existing attendance record with a different basis is never upgraded or replaced.
- Self-attendance awards zero points and is never called verified.
- The interface prioritises organiser-code attendance while the three-hour code window remains open. It reveals the zero-point self-attendance control after that code window closes.

Citizen wording: “3 of 8 joiners reported attending.”

## Organiser-code attendance

- The server derives a six-digit code from a server-held secret, Initiative identifier and 10-minute UTC time slot.
- The code is displayed only to the Google-linked organiser and is never persisted or logged in plaintext.
- It is accepted only from a Google-linked owner of a `PARTICIPANT` record for that Initiative.
- The overall inclusive window is exactly `startAt` through `startAt + 3 hours`.
- During the first two minutes of a new slot, the preceding slot's code is also accepted. Grace never extends the overall three-hour window.
- Incorrect submissions are limited to five per participant, Initiative and 10-minute slot. Attempt records contain no submitted code.
- The participation record receives `attendanceStatus: I_ATTENDED`, `attendanceBasis: ORGANISER_CODE_ATTESTED` and a server reporting time.
- One append-only attendance event and one 20-point ledger entry are recorded at most once per participant and Initiative.
- Cancellation is forbidden after the first organiser-code attendance.
- This is organiser-mediated attestation, not independent verification.

Citizen wording: “3 of 8 joiners recorded attendance using the organiser's code.”

## Organiser threshold award

- The organiser receives 40 points exactly once when both conditions are true:
  1. the Initiative is `COMPLETED`; and
  2. two distinct non-organiser participants have `ORGANISER_CODE_ATTESTED` attendance.
- The award is evaluated atomically both when completion is recorded and when code attendance is recorded, so event order does not matter.
- The award has its own deterministic event and ledger identifiers.

## Integrity and visibility

- All time checks use server time.
- Attendance, reward, attempt and aggregate decisions are backend-owned transactions.
- Firestore clients cannot create, update or delete Initiative documents, participation records, attendance events, attempt records or ledger entries.
- Counts are derived from `PARTICIPANT` records. The organiser is excluded from numerator and denominator.
- No attendance flow requests geolocation, QR scanning or photo evidence.
- Public responses omit UIDs, raw coordinates, codes and attempt details.
