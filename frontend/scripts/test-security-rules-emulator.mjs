import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
} from 'firebase/firestore';
import { getBytes, ref, uploadBytes } from 'firebase/storage';

const projectId = 'demo-seewik-rules';
const storageBucket = `${projectId}.appspot.com`;
const firestoreRules = readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8');
const storageRules = readFileSync(new URL('../../storage.rules', import.meta.url), 'utf8');

const googleClaims = {
  firebase: {
    identities: { 'google.com': ['test-google-subject'] },
    sign_in_provider: 'google.com',
  },
};

let testEnvironment;

function validDraft(ownerUid) {
  return {
    ownerUid,
    status: 'DRAFT',
    confirmedIssueType: 'POTHOLE_ROAD_DAMAGE',
    prabhagId: 'PRABHAG-03',
    routeId: 'NMC-PW-POTHOLE-v0.2',
    authority: 'Nandurbar Municipal Council',
    draftLanguage: 'MR',
    draftSubject: 'रस्त्यावरील खड्ड्याबाबत तक्रार',
    draftBody: 'आमच्या परिसरातील रस्त्यावर मोठा खड्डा आहे. कृपया आवश्यक कार्यवाही करावी.',
    packVersion: 'v0.2',
    schemaVersion: 'complaint-draft-v0.1',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

before(async () => {
  testEnvironment = await initializeTestEnvironment({
    projectId,
    firestore: { rules: firestoreRules },
    storage: { rules: storageRules },
  });
});

beforeEach(async () => {
  await testEnvironment.clearFirestore();
});

after(async () => {
  await testEnvironment.cleanup();
});

test('Firestore preserves owner reads and requires a Google-linked owner for draft writes', async () => {
  const ownerUid = `owner-${randomUUID()}`;
  const outsiderUid = `outsider-${randomUUID()}`;
  const reportId = `seeded-${randomUUID()}`;

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), 'reports', reportId), validDraft(ownerUid));
  });

  const anonymousOwner = testEnvironment.authenticatedContext(ownerUid).firestore();
  const linkedOwner = testEnvironment.authenticatedContext(ownerUid, googleClaims).firestore();
  const outsider = testEnvironment.authenticatedContext(outsiderUid, googleClaims).firestore();

  await assertSucceeds(getDoc(doc(anonymousOwner, 'reports', reportId)));
  await assertFails(getDoc(doc(outsider, 'reports', reportId)));
  await assertFails(setDoc(doc(anonymousOwner, 'reports', `anonymous-${randomUUID()}`), validDraft(ownerUid)));
  await assertSucceeds(setDoc(doc(linkedOwner, 'reports', `linked-${randomUUID()}`), validDraft(ownerUid)));
  await assertFails(setDoc(doc(linkedOwner, 'reports', `wrong-owner-${randomUUID()}`), validDraft(outsiderUid)));
});

test('Firestore rejects direct writes to backend-owned civic records', async () => {
  const uid = `linked-${randomUUID()}`;
  const database = testEnvironment.authenticatedContext(uid, googleClaims).firestore();
  const protectedWrites = [
    ['initiatives', `initiative-${randomUUID()}`, { ownerUid: uid, status: 'PUBLISHED' }],
    ['pointsLedger', `points-${randomUUID()}`, { ownerUid: uid, pointsAwarded: 999 }],
    ['recognitionConsents', uid, { ownerUid: uid, optedIn: true }],
    ['recognitionRewardClaims', `claim-${randomUUID()}`, { ownerUid: uid, code: 'FORGED-CODE' }],
    ['recognitionRewardEvents', `event-${randomUUID()}`, { ownerUid: uid, eventType: 'COUPON_USE_SIMULATED' }],
    ['initiativeParticipations', `participation-${randomUUID()}`, { ownerUid: uid, attendanceStatus: 'I_ATTENDED' }],
  ];

  for (const [collectionName, recordId, data] of protectedWrites) {
    await assertFails(setDoc(doc(database, collectionName, recordId), data));
  }

  await assertFails(setDoc(
    doc(database, 'initiatives', `initiative-${randomUUID()}`, 'events', `event-${randomUUID()}`),
    { eventType: 'INITIATIVE_ATTENDANCE_ORGANISER_CODE_ATTESTED' },
  ));
});

test('Storage permits a small owner object but blocks anonymous, cross-owner and oversized writes', async () => {
  const ownerUid = `storage-owner-${randomUUID()}`;
  const outsiderUid = `storage-outsider-${randomUUID()}`;
  const objectName = `day1_checks/${ownerUid}/rules-check-${randomUUID()}.png`;
  const linkedStorage = testEnvironment.authenticatedContext(ownerUid, googleClaims).storage(storageBucket);
  const anonymousStorage = testEnvironment.authenticatedContext(ownerUid).storage(storageBucket);
  const outsiderStorage = testEnvironment.authenticatedContext(outsiderUid, googleClaims).storage(storageBucket);

  await assertSucceeds(uploadBytes(ref(linkedStorage, objectName), new Uint8Array([1]), { contentType: 'image/png' }));
  await assertSucceeds(getBytes(ref(anonymousStorage, objectName)));
  await assertFails(getBytes(ref(outsiderStorage, objectName)));
  await assertFails(uploadBytes(
    ref(anonymousStorage, `day1_checks/${ownerUid}/anonymous-${randomUUID()}.png`),
    new Uint8Array([1]),
    { contentType: 'image/png' },
  ));
  await assertFails(uploadBytes(
    ref(linkedStorage, `day1_checks/${outsiderUid}/cross-owner-${randomUUID()}.png`),
    new Uint8Array([1]),
    { contentType: 'image/png' },
  ));
  await assertFails(uploadBytes(
    ref(linkedStorage, `day1_checks/${ownerUid}/oversized-${randomUUID()}.png`),
    new Uint8Array(1024 * 1024),
    { contentType: 'image/png' },
  ));
});
