import { randomUUID } from 'node:crypto';
import { deleteApp, initializeApp } from 'firebase/app';
import { deleteUser, getAuth, signInAnonymously } from 'firebase/auth';
import { collection, doc, getDocs, getFirestore, query, serverTimestamp, setDoc, where } from 'firebase/firestore';
import { getStorage, ref, uploadBytes } from 'firebase/storage';

const firebaseConfig = {
  projectId: 'seewik',
  appId: '1:528138216934:web:868a6101ff9890dbc4e227',
  storageBucket: 'seewik.firebasestorage.app',
  apiKey: 'AIzaSyAfJt7y0MNsQYDVlMt1cZgCajtrkrpy6qs',
  authDomain: 'seewik.firebaseapp.com',
  messagingSenderId: '528138216934',
};
const apiUrl = 'https://seewik-api-528138216934.asia-south1.run.app';
const runId = randomUUID();
const app = initializeApp(firebaseConfig, `google-write-gate-${runId}`);
const auth = getAuth(app);
const db = getFirestore(app);
const storage = getStorage(app);
let user;

async function expectGoogleLinkRequired(path, init) {
  const response = await fetch(`${apiUrl}${path}`, init);
  const body = await response.json();
  if (response.status !== 403 || body.errorCode !== 'GOOGLE_LINK_REQUIRED') {
    throw new Error(`${path} returned ${response.status}/${body.errorCode ?? 'NO_CODE'}`);
  }
}

async function expectFirestoreDenied(reference, data, operation) {
  try {
    await setDoc(reference, data);
    throw new Error(`${operation} unexpectedly succeeded`);
  } catch (error) {
    if (error?.code !== 'permission-denied') throw error;
  }
}

try {
  user = (await signInAnonymously(auth)).user;
  const token = await user.getIdToken(true);
  const authorization = { Authorization: `Bearer ${token}` };

  const mine = await fetch(`${apiUrl}/api/initiatives/mine`, { headers: authorization });
  if (!mine.ok) throw new Error(`Anonymous read path returned ${mine.status}`);
  const rewards = await fetch(`${apiUrl}/api/recognition/me/rewards`, { headers: authorization });
  if (!rewards.ok) throw new Error(`Anonymous reward read path returned ${rewards.status}`);
  await getDocs(query(collection(db, 'reports'), where('ownerUid', '==', user.uid)));
  await getDocs(query(collection(db, 'pointsLedger'), where('ownerUid', '==', user.uid)));

  await expectGoogleLinkRequired('/api/initiatives', {
    method: 'POST',
    headers: { ...authorization, 'Content-Type': 'application/json' },
    body: '{}',
  });
  await expectGoogleLinkRequired('/api/initiatives/direct-bypass/join', {
    method: 'POST',
    headers: authorization,
  });
  await expectGoogleLinkRequired('/api/reports/direct-bypass/transitions', {
    method: 'POST',
    headers: { ...authorization, 'Content-Type': 'application/json' },
    body: JSON.stringify({ toStatus: 'FILED', idempotencyKey: `gate-${runId}` }),
  });
  await expectGoogleLinkRequired('/api/recognition/me/rewards/claims', {
    method: 'POST',
    headers: { ...authorization, 'Content-Type': 'application/json' },
    body: JSON.stringify({ couponId: 'coupon-juthalal-100' }),
  });

  await expectFirestoreDenied(doc(db, 'reports', `anonymous-gate-${runId}`), {
    ownerUid: user.uid,
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
  }, 'Direct anonymous report write');
  await expectFirestoreDenied(doc(db, 'day1_checks', user.uid), {
    ok: true,
    checkedAt: serverTimestamp(),
  }, 'Direct anonymous technical Firestore write');

  try {
    await uploadBytes(
      ref(storage, `day1_checks/${user.uid}/anonymous-gate.png`),
      new Uint8Array([0]),
      { contentType: 'image/png' },
    );
    throw new Error('Direct anonymous technical Storage write unexpectedly succeeded');
  } catch (error) {
    if (error?.code !== 'storage/unauthorized') throw error;
  }

  console.log(JSON.stringify({
    status: 'PASS',
    anonymousReadsPreserved: true,
    anonymousRewardReadPreserved: true,
    initiativeApiWritesDenied: true,
    lifecycleApiWritesDenied: true,
    rewardApiWritesDenied: true,
    reportWritesDenied: true,
    technicalFirestoreWritesDenied: true,
    technicalStorageWritesDenied: true,
  }));
} finally {
  if (user) await deleteUser(user).catch(() => undefined);
  await deleteApp(app);
}
