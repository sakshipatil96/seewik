import { randomUUID } from 'node:crypto';
import { deleteApp, initializeApp } from 'firebase/app';
import { deleteUser, getAuth, signInAnonymously } from 'firebase/auth';
import { collection, doc, getDocs, getFirestore, query, serverTimestamp, setDoc, where } from 'firebase/firestore';

const firebaseConfig = {
  projectId: 'seewik',
  appId: '1:528138216934:web:868a6101ff9890dbc4e227',
  storageBucket: 'seewik.firebasestorage.app',
  apiKey: 'AIzaSyAfJt7y0MNsQYDVlMt1cZgCajtrkrpy6qs',
  authDomain: 'seewik.firebaseapp.com',
  messagingSenderId: '528138216934',
};

const app = initializeApp(firebaseConfig, `day10-anonymous-rule-${randomUUID()}`);
const auth = getAuth(app);
const db = getFirestore(app);
let user;

try {
  user = (await signInAnonymously(auth)).user;
  await getDocs(query(collection(db, 'reports'), where('ownerUid', '==', user.uid)));
  const reportRef = doc(db, 'reports', `day10-anonymous-denial-${randomUUID()}`);
  try {
    await setDoc(reportRef, {
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
    });
    throw new Error('Anonymous DRAFT create unexpectedly succeeded');
  } catch (error) {
    if (error?.code !== 'permission-denied') throw error;
  }
  console.log(JSON.stringify({
    status: 'PASS',
    anonymousOwnerReadsPreserved: true,
    anonymousDraftCreateDenied: true,
  }));
} finally {
  if (user) await deleteUser(user).catch(() => undefined);
  await deleteApp(app);
}
