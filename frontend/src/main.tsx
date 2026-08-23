import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom/client';
import { signInAnonymously } from 'firebase/auth';
import { doc, getDoc, serverTimestamp, setDoc } from 'firebase/firestore';
import { getBytes, ref, uploadBytes } from 'firebase/storage';
import { auth, db, storage } from './firebase';
import './styles.css';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

function App() {
  const [status, setStatus] = useState('Connecting…');
  const [details, setDetails] = useState<string[]>([]);
  const add = (line: string) => setDetails((old) => [...old, line]);

  useEffect(() => {
    fetch(`${API_URL}/health`).then((r) => r.json()).then((data) => {
      setStatus(data.status === 'ok' ? 'Day 1 systems online' : 'Backend returned an unexpected response');
      add(`Cloud API: ${data.status}`);
    }).catch((error) => setStatus(`API check failed: ${error.message}`));
  }, []);

  async function verifyFirebase() {
    setDetails([]);
    const credential = await signInAnonymously(auth);
    add(`Anonymous auth: ${credential.user.uid.slice(0, 8)}…`);

    const testRef = doc(db, 'day1_checks', credential.user.uid);
    await setDoc(testRef, { ok: true, checkedAt: serverTimestamp() });
    const snapshot = await getDoc(testRef);
    add(`Firestore write/read: ${snapshot.data()?.ok === true ? 'ok' : 'failed'}`);

    const pixel = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='), c => c.charCodeAt(0));
    const objectRef = ref(storage, `day1_checks/${credential.user.uid}/pixel.png`);
    await uploadBytes(objectRef, pixel, { contentType: 'image/png' });
    await getBytes(objectRef, 1024 * 1024);
    add('Storage upload/read: ok');
  }

  return <main><section className="hero"><span className="eyebrow">SEEWIK · DAY 1</span><h1>See it. Share it.<br/>Help fix it.</h1><p>A lightweight foundation for reporting civic issues in your community.</p></section><section className="card"><div className="signal"/><h2>{status}</h2><p>The first secure cloud path is ready for validation.</p><button onClick={() => verifyFirebase().catch((e) => add(`Firebase check failed: ${e.message}`))}>Verify Firebase services</button>{details.length > 0 && <ul>{details.map((d, index) => <li key={`${index}-${d}`}>{d}</li>)}</ul>}</section><footer>Built for local civic action</footer></main>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'));
