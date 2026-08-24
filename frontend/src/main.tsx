import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom/client';
import { signInAnonymously } from 'firebase/auth';
import { doc, getDoc, serverTimestamp, setDoc } from 'firebase/firestore';
import { getBytes, ref, uploadBytes } from 'firebase/storage';
import { auth, db, storage } from './firebase';
import './styles.css';

const API_URL = import.meta.env.VITE_API_URL || 'https://seewik-api-528138216934.asia-south1.run.app';
const PRABHAGS = Array.from({ length: 20 }, (_, index) => `PRABHAG-${String(index + 1).padStart(2, '0')}`);
const ISSUE_TYPES = [
  ['GARBAGE_SOLID_WASTE', 'Garbage / solid waste'],
  ['ILLEGAL_DUMPING', 'Illegal dumping'],
  ['PUBLIC_AREA_CLEANLINESS', 'Public-area cleanliness'],
  ['POTHOLE_ROAD_DAMAGE', 'Pothole / road damage'],
  ['STREETLIGHT', 'Streetlight'],
  ['DRAINAGE_SEWAGE', 'Drainage / sewage'],
  ['WATER_SUPPLY', 'Water supply'],
  ['PUBLIC_TOILET_SANITATION', 'Public toilet / sanitation'],
  ['MOSQUITO_FOGGING', 'Mosquito / fogging request'],
  ['DEAD_ANIMAL_REMOVAL', 'Dead animal removal'],
  ['PUBLIC_ROAD_OBSTRUCTION', 'Public-road obstruction'],
] as const;

type DepartmentResult = {
  departmentId: string;
  displayName: string;
  localName: string;
  status: string;
  basis: string;
};

type OfficialChannel = {
  channelId: string;
  type: 'EMAIL' | 'ONLINE_FORM' | 'IN_PERSON';
  value: string;
  label: string;
  scopeNote: string;
};

type InformationalLink = {
  linkId: string;
  type: string;
  value: string;
  label: string;
  status: string;
  scopeNote: string;
};

type KnownLimitation = {
  code: string;
  citizenMessage: string;
  routingImpact: string;
  requiresCitizenAttention: boolean;
};

type RouteResult = {
  status: string;
  routeId?: string;
  prabhagId?: string;
  resolutionMethod?: string;
  authority?: string;
  department?: DepartmentResult;
  officialChannels?: OfficialChannel[];
  informationalLinks?: InformationalLink[];
  knownLimitations?: KnownLimitation[];
  sourceStatus?: string;
  reviewStatus?: string;
  packVersion?: string;
};

type PrabhagResolution = {
  status: string;
  prabhagId?: string;
  prabhagName?: string;
  resolutionQuality?: string;
  requiresCitizenConfirmation: boolean;
  datasetVersion?: string;
  queryLatencyMs?: number;
  message: string;
};

type ClassificationResult = {
  status: 'CLASSIFIED' | 'CLARIFICATION_REQUIRED' | 'CLASSIFICATION_ERROR';
  issueType?: string;
  subcategory?: string | null;
  description?: string;
  confidence?: number;
  detectedLanguage?: 'MR' | 'HI' | 'EN' | 'MIXED' | 'UNKNOWN';
  needsClarification?: boolean;
  clarificationQuestion?: string | null;
  schemaVersion?: string;
  packVersion?: string;
  modelVersion?: string;
  errorCode?: string;
  message?: string;
};

const ISSUE_VALUES = new Set<string>(ISSUE_TYPES.map(([value]) => value));

function issueLabel(value: string) {
  return ISSUE_TYPES.find(([issueType]) => issueType === value)?.[1] ?? value;
}

function App() {
  const [status, setStatus] = useState('Connecting…');
  const [details, setDetails] = useState<string[]>([]);
  const [issueType, setIssueType] = useState(ISSUE_TYPES[0][0]);
  const [prabhagId, setPrabhagId] = useState(PRABHAGS[0]);
  const [routeResult, setRouteResult] = useState<RouteResult | null>(null);
  const [resolution, setResolution] = useState<PrabhagResolution | null>(null);
  const [locationStatus, setLocationStatus] = useState('');
  const [selectionMethod, setSelectionMethod] = useState('SELF_REPORTED');
  const [citizenConfirmed, setCitizenConfirmed] = useState(false);
  const [boundaryDatasetVersion, setBoundaryDatasetVersion] = useState<string | undefined>();
  const [evidenceText, setEvidenceText] = useState('');
  const [evidenceImage, setEvidenceImage] = useState<File | null>(null);
  const [classification, setClassification] = useState<ClassificationResult | null>(null);
  const [classificationStatus, setClassificationStatus] = useState('');
  const [classificationConfirmed, setClassificationConfirmed] = useState(false);
  const [classificationSource, setClassificationSource] = useState('SELF_REPORTED');
  const add = (line: string) => setDetails((old) => [...old, line]);

  useEffect(() => {
    fetch(`${API_URL}/health`).then((response) => response.json()).then((data) => {
      setStatus(data.status === 'ok' ? 'Seewik systems online' : 'Backend returned an unexpected response');
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
    const pixel = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='), (character) => character.charCodeAt(0));
    const objectRef = ref(storage, `day1_checks/${credential.user.uid}/pixel.png`);
    await uploadBytes(objectRef, pixel, { contentType: 'image/png' });
    await getBytes(objectRef, 1024 * 1024);
    add('Storage upload/read: ok');
  }

  async function resolveCoordinates(latitude: number, longitude: number) {
    const response = await fetch(`${API_URL}/api/civic/resolve-prabhag`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ latitude, longitude }),
    });
    if (!response.ok) throw new Error(`Location request failed (${response.status})`);
    const result: PrabhagResolution = await response.json();
    setResolution(result);
    setLocationStatus(result.message);
  }

  function useMyLocation() {
    setRouteResult(null);
    setResolution(null);
    setLocationStatus('Checking your location…');
    if (!navigator.geolocation) {
      setLocationStatus('Location is unavailable in this browser. Select your prabhag manually.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => resolveCoordinates(position.coords.latitude, position.coords.longitude)
        .catch((error) => setLocationStatus(error.message)),
      () => setLocationStatus('Location permission was not provided. Select your prabhag manually.'),
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    );
  }

  function confirmCandidate() {
    if (!resolution?.prabhagId || !resolution.datasetVersion) return;
    setPrabhagId(resolution.prabhagId);
    setSelectionMethod('BIGQUERY_ST_COVERS');
    setCitizenConfirmed(true);
    setBoundaryDatasetVersion(resolution.datasetVersion);
    setLocationStatus(`${resolution.prabhagName} confirmed. You can still choose a different prabhag manually.`);
  }

  function selectManualPrabhag(value: string) {
    setPrabhagId(value);
    setSelectionMethod('SELF_REPORTED');
    setCitizenConfirmed(false);
    setBoundaryDatasetVersion(undefined);
    setResolution(null);
    setLocationStatus('Manual prabhag selection will override any location suggestion.');
  }

  function chooseIssueType(value: string) {
    setIssueType(value as typeof issueType);
    setClassificationConfirmed(false);
    setClassificationSource(classification?.issueType === value ? 'GEMINI_SUGGESTED' : 'CITIZEN_SELECTED');
    setRouteResult(null);
  }

  async function classifyEvidence() {
    setClassification(null);
    setClassificationConfirmed(false);
    setRouteResult(null);
    if (!evidenceImage && !evidenceText.trim()) {
      setClassificationStatus('Add a photo or a short description first.');
      return;
    }
    if (evidenceImage && evidenceImage.size > 5 * 1024 * 1024) {
      setClassificationStatus('Please choose a photo that is 5 MB or smaller.');
      return;
    }
    setClassificationStatus('Checking the issue category…');
    const form = new FormData();
    if (evidenceImage) form.append('image', evidenceImage);
    if (evidenceText.trim()) form.append('text', evidenceText.trim());
    const response = await fetch(`${API_URL}/api/civic/classify`, { method: 'POST', body: form });
    const result: ClassificationResult = await response.json();
    setClassification(result);
    if (!response.ok || result.status === 'CLASSIFICATION_ERROR') {
      setClassificationStatus(result.message ?? 'The category could not be checked. Choose it manually below.');
      setClassificationSource('CITIZEN_SELECTED');
      return;
    }
    if (result.issueType && result.issueType !== 'UNKNOWN' && ISSUE_VALUES.has(result.issueType)) {
      setIssueType(result.issueType as typeof issueType);
      setClassificationSource('GEMINI_SUGGESTED');
    } else {
      setClassificationSource('CITIZEN_SELECTED');
    }
    setClassificationStatus(
      result.status === 'CLASSIFIED' && result.issueType
        ? `Suggested category: ${issueLabel(result.issueType)}. Please confirm or correct it.`
        : result.clarificationQuestion ?? 'The category is unclear. Please choose the best match below.',
    );
  }

  function confirmIssueType() {
    const source = classification?.issueType === issueType && classification.status === 'CLASSIFIED'
      ? 'CITIZEN_CONFIRMED_GEMINI'
      : 'CITIZEN_SELECTED';
    setClassificationSource(source);
    setClassificationConfirmed(true);
    setClassificationStatus(`${issueLabel(issueType)} confirmed. Gemini does not choose the authority or department.`);
  }

  async function findCivicRoute() {
    setRouteResult(null);
    if (!classificationConfirmed) {
      setRouteResult({ status: 'CATEGORY_CONFIRMATION_REQUIRED' });
      return;
    }
    const response = await fetch(`${API_URL}/api/civic/route`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ issueType, prabhagId, resolutionMethod: selectionMethod, citizenConfirmed, boundaryDatasetVersion }),
    });
    if (!response.ok) throw new Error(`Routing request failed (${response.status})`);
    setRouteResult(await response.json());
  }

  return <main>
    <section className="hero"><span className="eyebrow">SEEWIK · CIVIC PACK v0.2</span><h1>See it. Share it.<br />Help fix it.</h1><p>A lightweight foundation for reporting civic issues in your community.</p></section>
    <section className="card">
      <div className="signal" /><h2>Find the civic route</h2>
      <p>Start with a photo or short description. Gemini may suggest an issue category, but you confirm it. Authority and department always come from Civic Pack v0.2.</p>
      <div className="flow-step"><span>1</span><b>Describe the issue</b></div>
      <label>Photo (optional)<input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => {
        setEvidenceImage(event.target.files?.[0] ?? null);
        setClassificationConfirmed(false);
        setRouteResult(null);
      }} /></label>
      <label>Short description (optional)<textarea maxLength={2000} value={evidenceText} placeholder="उदा. रस्त्यावर मोठा खड्डा आहे" onChange={(event) => {
        setEvidenceText(event.target.value);
        setClassificationConfirmed(false);
        setRouteResult(null);
      }} /></label>
      <button className="secondary" onClick={() => classifyEvidence().catch(() => {
        setClassificationStatus('The category could not be checked. Choose it manually below.');
        setClassificationSource('CITIZEN_SELECTED');
      })}>Suggest issue category</button>
      {classificationStatus && <div className={classification?.status === 'CLASSIFICATION_ERROR' ? 'notice' : 'classification-result'}>
        <strong>{classification?.status === 'CLASSIFIED' ? 'Category suggestion ready' : classification?.status === 'CLARIFICATION_REQUIRED' ? 'Please clarify' : 'Category confirmation'}</strong>
        <span>{classificationStatus}</span>
        {classification?.description && <small>{classification.description}</small>}
        {classification?.detectedLanguage && <small>Detected language: {classification.detectedLanguage}</small>}
      </div>}
      <label>Issue category<select value={issueType} onChange={(event) => chooseIssueType(event.target.value)}>{ISSUE_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <button onClick={confirmIssueType}>{classificationConfirmed ? 'Category confirmed' : 'Confirm this category'}</button>
      {classificationConfirmed && <div className="confirmed-line">✓ {issueLabel(issueType)} · {classificationSource}</div>}

      <div className="flow-step"><span>2</span><b>Confirm your prabhag</b></div>
      <p>Location can suggest a prabhag using synthetic development boundaries. The suggestion is never official and must be confirmed. Manual selection always overrides it.</p>
      <button className="secondary" onClick={useMyLocation}>Suggest from my location</button>
      {locationStatus && <div className="notice">{locationStatus}</div>}
      {resolution?.status === 'CANDIDATE_PRABHAG' && resolution.prabhagId && <div className="candidate"><strong>{resolution.prabhagName}</strong><span>{resolution.resolutionQuality} · {resolution.datasetVersion}</span><span>BigQuery lookup: {resolution.queryLatencyMs} ms</span><button onClick={confirmCandidate}>Confirm this suggested prabhag</button></div>}
      <label>Official prabhag number<select value={prabhagId} onChange={(event) => selectManualPrabhag(event.target.value)}>{PRABHAGS.map((value, index) => <option key={value} value={value}>Prabhag {index + 1}</option>)}</select></label>
      <div className="flow-step"><span>3</span><b>Get the deterministic route</b></div>
      <button disabled={!classificationConfirmed} onClick={() => findCivicRoute().catch((error) => setRouteResult({ status: `Routing failed: ${error.message}` }))}>Find official route</button>
      {routeResult && <div className="route-result">
        <strong>{routeResult.status === 'SUPPORTED_ROUTE' ? routeResult.authority : routeResult.status === 'CATEGORY_CONFIRMATION_REQUIRED' ? 'Confirm the issue category first' : routeResult.status}</strong>
        {routeResult.routeId && <>
          {routeResult.department && <div className="department-result">
            <b>{routeResult.department.status === 'TYPICAL_STRUCTURE_UNVERIFIED' ? 'Likely department' : 'Department'}: {routeResult.department.displayName}</b>
            <span>{routeResult.department.localName}</span>
            <span>{routeResult.department.basis}</span>
            <span>{routeResult.department.status}</span>
          </div>}
          <span>{routeResult.routeId}</span>
          <span>{routeResult.prabhagId} · {routeResult.resolutionMethod}</span>
          <span>{routeResult.sourceStatus} · {routeResult.reviewStatus} · {routeResult.packVersion}</span>
          {(routeResult.knownLimitations?.length ?? 0) > 0 && <div className="route-limitations">
            <b>Please check before filing</b>
            <ul>{routeResult.knownLimitations?.map((limitation) => <li key={limitation.code}>{limitation.citizenMessage}</li>)}</ul>
          </div>}
          {(routeResult.officialChannels?.length ?? 0) > 0 && <div className="route-channels">
            <b>Official contact options</b>
            <ul>{routeResult.officialChannels?.map((channel) => <li key={channel.channelId}>
              {channel.type === 'EMAIL' && <a href={`mailto:${channel.value}`}>{channel.label}</a>}
              {channel.type === 'ONLINE_FORM' && <a href={channel.value} target="_blank" rel="noreferrer">{channel.label}</a>}
              {channel.type === 'IN_PERSON' && <span>{channel.label}: {channel.value}</span>}
              <small>{channel.scopeNote}</small>
            </li>)}</ul>
          </div>}
          {(routeResult.informationalLinks?.length ?? 0) > 0 && <div className="informational-links">
            <b>Information only — not a verified filing channel</b>
            <ul>{routeResult.informationalLinks?.map((link) => <li key={link.linkId}><a href={link.value} target="_blank" rel="noreferrer">{link.label}</a><small>{link.scopeNote}</small></li>)}</ul>
          </div>}
        </>}
      </div>}
    </section>
    <section className="card systems"><h2>{status}</h2><p>The secure cloud path remains available for technical validation.</p><button onClick={() => verifyFirebase().catch((error) => add(`Firebase check failed: ${error.message}`))}>Verify Firebase services</button>{details.length > 0 && <ul>{details.map((detail, index) => <li key={`${index}-${detail}`}>{detail}</li>)}</ul>}</section>
    <footer>Built for local civic action</footer>
  </main>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'));
