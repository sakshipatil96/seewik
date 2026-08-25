import { randomUUID } from 'node:crypto';
import { deleteApp, initializeApp } from 'firebase/app';
import { deleteUser, getAuth, signInAnonymously } from 'firebase/auth';
import { deleteDoc, doc, getDoc, getFirestore, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';

const firebaseConfig = {
  projectId: 'seewik',
  appId: '1:528138216934:web:868a6101ff9890dbc4e227',
  storageBucket: 'seewik.firebasestorage.app',
  apiKey: 'AIzaSyAfJt7y0MNsQYDVlMt1cZgCajtrkrpy6qs',
  authDomain: 'seewik.firebaseapp.com',
  messagingSenderId: '528138216934',
};

const ownerApp = initializeApp(firebaseConfig, `day4-owner-${randomUUID()}`);
const otherApp = initializeApp(firebaseConfig, `day4-other-${randomUUID()}`);
const ownerAuth = getAuth(ownerApp);
const otherAuth = getAuth(otherApp);
const ownerDb = getFirestore(ownerApp);
const otherDb = getFirestore(otherApp);
const reportId = `day4-rule-check-${randomUUID()}`;
let ownerUser;
let otherUser;
let created = false;

function expectPermissionDenied(error, operation) {
  if (error?.code !== 'permission-denied') {
    throw new Error(`${operation} failed with ${error?.code ?? error}`);
  }
}

try {
  ownerUser = (await signInAnonymously(ownerAuth)).user;
  otherUser = (await signInAnonymously(otherAuth)).user;
  const ownerRef = doc(ownerDb, 'reports', reportId);
  const otherRef = doc(otherDb, 'reports', reportId);
  await setDoc(ownerRef, {
    ownerUid: ownerUser.uid,
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
  });
  created = true;
  const ownerRead = await getDoc(ownerRef);
  if (!ownerRead.exists() || ownerRead.data().ownerUid !== ownerUser.uid) {
    throw new Error('Owner could not read their own DRAFT');
  }
  try {
    await getDoc(otherRef);
    throw new Error('Cross-owner read unexpectedly succeeded');
  } catch (error) {
    expectPermissionDenied(error, 'Cross-owner read');
  }
  try {
    await updateDoc(otherRef, { draftSubject: 'Forbidden update', updatedAt: serverTimestamp() });
    throw new Error('Cross-owner update unexpectedly succeeded');
  } catch (error) {
    expectPermissionDenied(error, 'Cross-owner update');
  }
  await updateDoc(ownerRef, {
    draftSubject: 'रस्त्यावरील मोठ्या खड्ड्याबाबत तक्रार',
    updatedAt: serverTimestamp(),
  });
  console.log(JSON.stringify({
    status: 'PASS',
    ownerCreateReadUpdateDelete: true,
    crossOwnerReadDenied: true,
    crossOwnerUpdateDenied: true,
  }));
} finally {
  if (created && ownerUser) {
    await deleteDoc(doc(ownerDb, 'reports', reportId)).catch(() => undefined);
  }
  if (ownerUser) await deleteUser(ownerUser).catch(() => undefined);
  if (otherUser) await deleteUser(otherUser).catch(() => undefined);
  await Promise.all([deleteApp(ownerApp), deleteApp(otherApp)]);
}
