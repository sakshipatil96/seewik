import { createHash, randomUUID } from 'node:crypto';
import { deleteApp, initializeApp } from 'firebase/app';
import { deleteUser, getAuth, signInAnonymously } from 'firebase/auth';
import { collection, doc, getDoc, getDocs, getFirestore, query, where } from 'firebase/firestore';

const adminAccessToken = process.env.SEEWIK_ADMIN_ACCESS_TOKEN;
if (!adminAccessToken) throw new Error('SEEWIK_ADMIN_ACCESS_TOKEN is required');

const firebaseConfig = {
  projectId: 'seewik',
  appId: '1:528138216934:web:868a6101ff9890dbc4e227',
  storageBucket: 'seewik.firebasestorage.app',
  apiKey: 'AIzaSyAfJt7y0MNsQYDVlMt1cZgCajtrkrpy6qs',
  authDomain: 'seewik.firebaseapp.com',
  messagingSenderId: '528138216934',
};
const apiUrl = 'https://seewik-api-528138216934.asia-south1.run.app';
const firestoreBase = 'https://firestore.googleapis.com/v1/projects/seewik/databases/(default)/documents';
const runId = randomUUID().replaceAll('-', '');
const ownerApp = initializeApp(firebaseConfig, `ownership-owner-${runId}`);
const otherApp = initializeApp(firebaseConfig, `ownership-other-${runId}`);
const ownerAuth = getAuth(ownerApp);
const otherAuth = getAuth(otherApp);
const ownerDb = getFirestore(ownerApp);
const otherDb = getFirestore(otherApp);
const createdPaths = [];
let ownerUser;
let otherUser;

function hash(value) {
  return createHash('sha256').update(value).digest('hex');
}

function fieldValue(value) {
  if (value === null) return { nullValue: null };
  if (typeof value === 'string') return { stringValue: value };
  if (typeof value === 'boolean') return { booleanValue: value };
  if (typeof value === 'number' && Number.isInteger(value)) return { integerValue: String(value) };
  if (typeof value === 'number') return { doubleValue: value };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(fieldValue) } };
  return { mapValue: { fields: fields(value) } };
}

function fields(data) {
  return Object.fromEntries(Object.entries(data).map(([key, value]) => [key, fieldValue(value)]));
}

async function adminRequest(path, init = {}) {
  const response = await fetch(`${firestoreBase}/${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${adminAccessToken}`,
      'X-Goog-User-Project': 'seewik',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
    },
  });
  if (!response.ok && !(init.method === 'DELETE' && response.status === 404)) {
    throw new Error(`Admin Firestore ${init.method ?? 'GET'} ${path} returned ${response.status}`);
  }
}

async function seed(path, data) {
  await adminRequest(path, { method: 'PATCH', body: JSON.stringify({ fields: fields(data) }) });
  createdPaths.push(path);
}

async function expectPermissionDenied(operation, label) {
  try {
    await operation();
    throw new Error(`${label} unexpectedly succeeded`);
  } catch (error) {
    if (error?.code !== 'permission-denied') throw error;
  }
}

async function mine(user) {
  const token = await user.getIdToken(true);
  const response = await fetch(`${apiUrl}/api/initiatives/mine`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw new Error(`My Initiatives returned ${response.status}`);
  return response.json();
}

try {
  ownerUser = (await signInAnonymously(ownerAuth)).user;
  otherUser = (await signInAnonymously(otherAuth)).user;
  const reportId = `day10-ownership-report-${runId}`;
  const ledgerId = `day10-ownership-points-${runId}`;
  const sharedInitiativeId = `day10-ownership-shared-${runId}`;
  const ownerOnlyInitiativeId = `day10-ownership-owner-only-${runId}`;
  const now = new Date().toISOString();
  const future = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();

  await seed(`reports/${reportId}`, {
    ownerUid: ownerUser.uid,
    status: 'DRAFT',
    confirmedIssueType: 'POTHOLE_ROAD_DAMAGE',
    prabhagId: 'PRABHAG-03',
    routeId: 'NMC-PW-POTHOLE-v0.2',
    authority: 'Nandurbar Municipal Council',
    draftLanguage: 'EN',
    draftSubject: 'Privacy-safe ownership verification',
    draftBody: 'Temporary labelled production record used only to verify owner-scoped access.',
    packVersion: 'v0.2',
    schemaVersion: 'complaint-draft-v0.1',
    createdAt: now,
    updatedAt: now,
  });
  await seed(`pointsLedger/${ledgerId}`, {
    ownerUid: ownerUser.uid,
    sourceType: 'REPORT',
    sourceId: reportId,
    awardedPoints: 5,
    schemaVersion: 'points-ledger-v0.1',
    demoMode: true,
  });

  const initiative = (initiativeId, participantCount) => ({
    initiativeId,
    ownerUid: ownerUser.uid,
    title: 'Privacy-safe ownership verification',
    category: 'CLEANUP',
    description: 'Temporary labelled production record used only for access verification.',
    startAt: future,
    placeName: 'Production verification fixture',
    latitude: 21.36,
    longitude: 74.24,
    needs: [],
    status: 'PUBLISHED',
    participantCount,
    createdAt: now,
    updatedAt: now,
    schemaVersion: 'initiative-v0.1',
  });
  await seed(`initiatives/${sharedInitiativeId}`, initiative(sharedInitiativeId, 2));
  await seed(`initiatives/${ownerOnlyInitiativeId}`, initiative(ownerOnlyInitiativeId, 1));

  const participation = (initiativeId, uid, role) => ({
    participationId: `part_${hash(`${initiativeId}:${uid}`)}`,
    initiativeId,
    ownerUid: uid,
    role,
    status: 'JOINED',
    joinedAt: now,
    schemaVersion: 'initiative-v0.1',
  });
  const ownerSharedParticipation = participation(sharedInitiativeId, ownerUser.uid, 'ORGANISER');
  const ownerOnlyParticipation = participation(ownerOnlyInitiativeId, ownerUser.uid, 'ORGANISER');
  const otherParticipation = participation(sharedInitiativeId, otherUser.uid, 'PARTICIPANT');
  await seed(`initiativeParticipations/${ownerSharedParticipation.participationId}`, ownerSharedParticipation);
  await seed(`initiativeParticipations/${ownerOnlyParticipation.participationId}`, ownerOnlyParticipation);
  await seed(`initiativeParticipations/${otherParticipation.participationId}`, otherParticipation);

  const ownerReports = await getDocs(query(collection(ownerDb, 'reports'), where('ownerUid', '==', ownerUser.uid)));
  if (!ownerReports.docs.some((item) => item.id === reportId)) throw new Error('Owner DRAFT was not readable');
  const ownerPoints = await getDocs(query(collection(ownerDb, 'pointsLedger'), where('ownerUid', '==', ownerUser.uid)));
  if (!ownerPoints.docs.some((item) => item.id === ledgerId)) throw new Error('Owner points record was not readable');

  await expectPermissionDenied(() => getDoc(doc(otherDb, 'reports', reportId)), 'Cross-owner DRAFT read');
  await expectPermissionDenied(() => getDoc(doc(otherDb, 'pointsLedger', ledgerId)), 'Cross-owner points read');

  const ownerMine = await mine(ownerUser);
  const otherMine = await mine(otherUser);
  const ownerRoles = new Map(ownerMine.initiatives.map((item) => [item.initiativeId, item.role]));
  const otherRoles = new Map(otherMine.initiatives.map((item) => [item.initiativeId, item.role]));
  if (ownerRoles.get(sharedInitiativeId) !== 'ORGANISER'
      || ownerRoles.get(ownerOnlyInitiativeId) !== 'ORGANISER') {
    throw new Error('Organiser ownership was not preserved');
  }
  if (otherRoles.get(sharedInitiativeId) !== 'PARTICIPANT'
      || otherRoles.has(ownerOnlyInitiativeId)) {
    throw new Error('Participant ownership was not isolated');
  }

  console.log(JSON.stringify({
    status: 'PASS',
    ownerDraftReadable: true,
    crossOwnerDraftDenied: true,
    ownerPointsReadable: true,
    crossOwnerPointsDenied: true,
    organiserRolePreserved: true,
    participantRolePreserved: true,
    unrelatedInitiativeExcluded: true,
  }));
} finally {
  for (const path of createdPaths.reverse()) await adminRequest(path, { method: 'DELETE' }).catch(() => undefined);
  if (ownerUser) await deleteUser(ownerUser).catch(() => undefined);
  if (otherUser) await deleteUser(otherUser).catch(() => undefined);
  await Promise.all([deleteApp(ownerApp), deleteApp(otherApp)]);
}
