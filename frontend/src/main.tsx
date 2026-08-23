import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom/client';
import { signInAnonymously } from 'firebase/auth';
import { doc, getDoc, serverTimestamp, setDoc } from 'firebase/firestore';
import { getBytes, ref, uploadBytes } from 'firebase/storage';
import { auth, db, storage } from './firebase';
import './styles.css';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const PRABHAGS = Array.from({ length: 20 }, (_, index) => `PRABHAG-${String(index + 1).padStart(2, '0')}`);
const ISSUE_TYPES = [
  ['GARBAGE_SOLID_WASTE', 'Garbage / solid waste'],
  ['ILLEGAL_DUMPING', 'Illegal dumping'],
  ['POTHOLE_ROAD_DAMAGE', 'Pothole / road damage'],
  ['STREETLIGHT', 'Streetlight'],
  ['DRAINAGE_SEWAGE', 'Drainage / sewage'],
  ['WATER_SUPPLY', 'Water supply'],
  ['PUBLIC_TOILET_SANITATION', 'Public toilet / sanitation'],
  ['MOSQUITO_FOGGING', 'Mosquito / fogging request'],
  ['DEAD_ANIMAL_REMOVAL', 'Dead animal removal'],
  ['PUBLIC_ROAD_OBSTRUCTION', 'Public-road obstruction'],
] as const;

type RouteResult = {
  status: string;
  routeId?: string;
  prabhagId?: string;
  resolutionMethod?: string;
  authority?: string;
  sourceStatus?: string;
  reviewStatus?: string;
};

function App() {
  const [status, setStatus] = useState('Connecting…');
  const [details, setDetails] = useState<string[]>([]);
  const [issueType, setIssueType] = useState(ISSUE_TYPES[0][0]);
  const [prabhagId, setPrabhagId] = useState(PRABHAGS[0]);
  const [routeResult, setRouteResult] = useState<RouteResult | null>(null);
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

  async function findCivicRoute() {
    setRouteResult(null);
    const response = await fetch(`${API_URL}/api/civic/route`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ issueType, prabhagId }),
    });
    if (!response.ok) throw new Error(`Routing request failed (${response.status})`);
    setRouteResult(await response.json());
  }

  return <main><section className="hero"><span className="eyebrow">SEEWIK · CIVIC PACK v0.1</span><h1>See it. Share it.<br/>Help fix it.</h1><p>A lightweight foundation for reporting civic issues in your community.</p></section><section className="card"><div className="signal"/><h2>Find the civic route</h2><p>Select the prabhag you know. This is recorded as <strong>self-reported</strong>; GPS inference is not used.</p><label>Issue type<select value={issueType} onChange={(event) => setIssueType(event.target.value as typeof issueType)}>{ISSUE_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>Official prabhag number<select value={prabhagId} onChange={(event) => setPrabhagId(event.target.value)}>{PRABHAGS.map((value, index) => <option key={value} value={value}>Prabhag {index + 1}</option>)}</select></label><button onClick={() => findCivicRoute().catch((error) => setRouteResult({ status: `Routing failed: ${error.message}` }))}>Find official route</button>{routeResult && <div className="route-result"><strong>{routeResult.status === 'SUPPORTED_ROUTE' ? routeResult.authority : routeResult.status}</strong>{routeResult.routeId && <><span>{routeResult.routeId}</span><span>{routeResult.prabhagId} · {routeResult.resolutionMethod}</span><span>{routeResult.sourceStatus} · {routeResult.reviewStatus}</span></>}</div>}</section><section className="card systems"><h2>{status}</h2><p>The secure cloud path remains available for technical validation.</p><button onClick={() => verifyFirebase().catch((e) => add(`Firebase check failed: ${e.message}`))}>Verify Firebase services</button>{details.length > 0 && <ul>{details.map((d, index) => <li key={`${index}-${d}`}>{d}</li>)}</ul>}</section><footer>Built for local civic action</footer></main>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'));
