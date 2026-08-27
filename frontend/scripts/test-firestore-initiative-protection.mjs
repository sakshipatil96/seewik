import { randomUUID } from 'node:crypto';
import { deleteApp, initializeApp } from 'firebase/app';
import { deleteUser, getAuth, signInAnonymously } from 'firebase/auth';
import { doc, getFirestore, setDoc } from 'firebase/firestore';

const firebaseConfig = {
  projectId: 'seewik',
  appId: '1:528138216934:web:868a6101ff9890dbc4e227',
  storageBucket: 'seewik.firebasestorage.app',
  apiKey: 'AIzaSyAfJt7y0MNsQYDVlMt1cZgCajtrkrpy6qs',
  authDomain: 'seewik.firebaseapp.com',
  messagingSenderId: '528138216934',
};

const app = initializeApp(firebaseConfig, `initiative-rule-check-${randomUUID()}`);
const auth = getAuth(app);
const db = getFirestore(app);
let user;

async function expectDenied(reference, data, operation) {
  try {
    await setDoc(reference, data);
    throw new Error(`${operation} unexpectedly succeeded`);
  } catch (error) {
    if (error?.code !== 'permission-denied') throw error;
  }
}

try {
  user = (await signInAnonymously(auth)).user;
  const initiativeId = `initiative-rule-check-${randomUUID()}`;
  await expectDenied(doc(db, 'initiatives', initiativeId), {
    ownerUid: user.uid,
    status: 'PUBLISHED',
    participantCount: 999,
  }, 'Direct initiative write');
  await expectDenied(doc(db, 'initiatives', initiativeId, 'events', 'forged-event'), {
    eventType: 'INITIATIVE_COMPLETED',
  }, 'Direct Initiative event write');
  await expectDenied(doc(db, 'pointsLedger', `forged-${randomUUID()}`), {
    ownerUid: user.uid,
    pointsAwarded: 999,
  }, 'Direct points write');
  console.log(JSON.stringify({
    status: 'PASS',
    initiativeWritesDenied: true,
    initiativeEventWritesDenied: true,
    pointsWritesDenied: true,
  }));
} finally {
  if (user) await deleteUser(user).catch(() => undefined);
  await deleteApp(app);
}
