import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom/client';
import { signInAnonymously } from 'firebase/auth';
import { collection, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc, where } from 'firebase/firestore';
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

type ComplaintDraftResult = {
  status: 'DRAFT_READY' | 'DRAFT_ERROR';
  draftVersion?: string;
  schemaVersion?: string;
  packVersion?: string;
  language?: 'MR' | 'EN';
  routeId?: string;
  prabhagId?: string;
  authority?: string;
  authorityLocalName?: string;
  subject?: string;
  body?: string;
  missingDetails?: string[];
  citizenReviewRequired?: boolean;
  modelVersion?: string;
  latencyMs?: number;
  errorCode?: string;
  message?: string;
};

type LifecycleResponse = {
  status: 'TRANSITION_RECORDED' | 'POSSIBLE_DUPLICATE';
  eventId?: string;
  reportId: string;
  fromStatus: string;
  toStatus: string;
  eventType?: string;
  verificationBasis: string;
  occurredAt: string;
  dedupeDisposition?: string;
  measuredDistanceMeters?: number;
  pointsAwarded: number;
  pointsWeight: number;
  errorCode?: string;
  message?: string;
};

type TimelineItem = {
  eventType: string;
  toStatus: string;
  occurredAt: string;
  verificationBasis: string;
  pointsAwarded: number;
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
  const [complaintFacts, setComplaintFacts] = useState('');
  const [locationDetails, setLocationDetails] = useState('');
  const [draftLanguage, setDraftLanguage] = useState<'MR' | 'EN'>('MR');
  const [complaintDraft, setComplaintDraft] = useState<ComplaintDraftResult | null>(null);
  const [draftSubject, setDraftSubject] = useState('');
  const [draftBody, setDraftBody] = useState('');
  const [draftStatus, setDraftStatus] = useState('');
  const [draftDocumentId, setDraftDocumentId] = useState<string | null>(null);
  const [draftReviewed, setDraftReviewed] = useState(false);
  const [currentCoordinates, setCurrentCoordinates] = useState<{ latitude: number; longitude: number } | null>(null);
  const [reportStatus, setReportStatus] = useState('DRAFT');
  const [timeline, setTimeline] = useState<TimelineItem[]>([]);
  const [lifecycleStatus, setLifecycleStatus] = useState('');
  const [duplicateWarning, setDuplicateWarning] = useState<LifecycleResponse | null>(null);
  const [filingChannelId, setFilingChannelId] = useState('');
  const [acknowledgementId, setAcknowledgementId] = useState('');
  const [pointsTotal, setPointsTotal] = useState(0);
  const [demoStep, setDemoStep] = useState(0);
  const add = (line: string) => setDetails((old) => [...old, line]);

  function resetDraft() {
    setComplaintDraft(null);
    setDraftSubject('');
    setDraftBody('');
    setDraftStatus('');
    setDraftDocumentId(null);
    setDraftReviewed(false);
    setReportStatus('DRAFT');
    setTimeline([]);
    setLifecycleStatus('');
    setDuplicateWarning(null);
    setFilingChannelId('');
    setAcknowledgementId('');
  }

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
      (position) => {
        setCurrentCoordinates({ latitude: position.coords.latitude, longitude: position.coords.longitude });
        resolveCoordinates(position.coords.latitude, position.coords.longitude)
          .catch((error) => setLocationStatus(error.message));
      },
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
    resetDraft();
  }

  function selectManualPrabhag(value: string) {
    setPrabhagId(value);
    setSelectionMethod('SELF_REPORTED');
    setCitizenConfirmed(false);
    setBoundaryDatasetVersion(undefined);
    setResolution(null);
    setLocationStatus('Manual prabhag selection will override any location suggestion.');
    setRouteResult(null);
    resetDraft();
  }

  function chooseIssueType(value: string) {
    setIssueType(value as typeof issueType);
    setClassificationConfirmed(false);
    setClassificationSource(classification?.issueType === value ? 'GEMINI_SUGGESTED' : 'CITIZEN_SELECTED');
    setRouteResult(null);
    resetDraft();
  }

  async function classifyEvidence() {
    setClassification(null);
    setClassificationConfirmed(false);
    setRouteResult(null);
    resetDraft();
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
    if (!complaintFacts.trim()) setComplaintFacts(evidenceText.trim() || result.description || '');
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
    resetDraft();
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
    const result: RouteResult = await response.json();
    setRouteResult(result);
    setFilingChannelId(result.officialChannels?.[0]?.channelId ?? '');
    if (result.status === 'SUPPORTED_ROUTE' && !complaintFacts.trim()) {
      setComplaintFacts(evidenceText.trim() || classification?.description || '');
    }
  }

  async function persistNewDraft(result: ComplaintDraftResult) {
    if (!result.routeId || !result.prabhagId || !result.authority || !result.language || !result.subject || !result.body || !result.packVersion || !result.schemaVersion) {
      throw new Error('Draft metadata is incomplete and was not saved.');
    }
    const credential = auth.currentUser ? { user: auth.currentUser } : await signInAnonymously(auth);
    const reportRef = doc(collection(db, 'reports'));
    await setDoc(reportRef, {
      ownerUid: credential.user.uid,
      status: 'DRAFT',
      confirmedIssueType: issueType,
      prabhagId: result.prabhagId,
      routeId: result.routeId,
      authority: result.authority,
      draftLanguage: result.language,
      draftSubject: result.subject,
      draftBody: result.body,
      packVersion: result.packVersion,
      schemaVersion: result.schemaVersion,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    setDraftDocumentId(reportRef.id);
    setReportStatus('DRAFT');
    setTimeline([{ eventType: 'DRAFT_SAVED', toStatus: 'DRAFT', occurredAt: new Date().toISOString(), verificationBasis: 'NONE', pointsAwarded: 0 }]);
    return reportRef.id;
  }

  async function createComplaintDraft() {
    if (routeResult?.status !== 'SUPPORTED_ROUTE') {
      setDraftStatus('Get a supported civic route before drafting.');
      return;
    }
    if (!complaintFacts.trim()) {
      setDraftStatus('Add the factual issue details before drafting.');
      return;
    }
    resetDraft();
    setDraftStatus(`Creating ${draftLanguage === 'MR' ? 'Marathi' : 'English'} complaint draft…`);
    const response = await fetch(`${API_URL}/api/civic/draft-complaint`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        issueType,
        prabhagId,
        resolutionMethod: selectionMethod,
        citizenConfirmed,
        boundaryDatasetVersion,
        classificationConfirmed,
        citizenDescription: complaintFacts.trim(),
        locationDetails: locationDetails.trim() || null,
        draftLanguage,
      }),
    });
    const result: ComplaintDraftResult = await response.json();
    setComplaintDraft(result);
    if (!response.ok || result.status !== 'DRAFT_READY' || !result.subject || !result.body) {
      setDraftStatus(result.message ?? 'The complaint draft could not be created. Your confirmed route is unchanged.');
      return;
    }
    setDraftSubject(result.subject);
    setDraftBody(result.body);
    setDraftReviewed(false);
    try {
      const reportId = await persistNewDraft(result);
      setDraftStatus(`Saved as Firestore DRAFT · ${reportId.slice(0, 8)}…`);
    } catch (error) {
      setDraftStatus(`Draft created but could not be saved: ${(error as Error).message}`);
    }
  }

  async function saveDraftEdits() {
    if (!draftDocumentId || !draftSubject.trim() || !draftBody.trim()) {
      setDraftStatus('No saved draft is available to update.');
      return false;
    }
    await updateDoc(doc(db, 'reports', draftDocumentId), {
      draftSubject: draftSubject.trim(),
      draftBody: draftBody.trim(),
      updatedAt: serverTimestamp(),
    });
    setDraftStatus(`Draft changes saved · ${draftDocumentId.slice(0, 8)}…`);
    return true;
  }

  async function copyReviewedDraft() {
    if (!draftReviewed) {
      setDraftStatus('Review the draft and confirm before copying.');
      return;
    }
    const saved = await saveDraftEdits();
    if (!saved) return;
    const recipient = complaintDraft?.authorityLocalName || complaintDraft?.authority || '';
    await navigator.clipboard.writeText(`${recipient}\n\n${draftSubject.trim()}\n\n${draftBody.trim()}`);
    setDraftStatus('Reviewed complaint copied. No complaint was submitted automatically.');
  }

  async function refreshDerivedPoints() {
    const credential = auth.currentUser ? { user: auth.currentUser } : await signInAnonymously(auth);
    const snapshot = await getDocs(query(
      collection(db, 'pointsLedger'),
      where('ownerUid', '==', credential.user.uid),
    ));
    setPointsTotal(snapshot.docs.reduce((total, item) => total + Number(item.data().awardedPoints ?? 0), 0));
  }

  async function transitionReport(toStatus: string, dedupeOverride = false) {
    if (!draftDocumentId) {
      setLifecycleStatus('Save a draft before changing its lifecycle.');
      return;
    }
    const credential = auth.currentUser ? { user: auth.currentUser } : await signInAnonymously(auth);
    const idToken = await credential.user.getIdToken();
    setLifecycleStatus(`Recording ${toStatus.replaceAll('_', ' ').toLowerCase()}…`);
    const isFiling = toStatus === 'FILED';
    const response = await fetch(`${API_URL}/api/reports/${draftDocumentId}/transitions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${idToken}` },
      body: JSON.stringify({
        toStatus,
        idempotencyKey: crypto.randomUUID(),
        verificationBasis: toStatus === 'OVERDUE' ? 'NONE' : 'CITIZEN_ATTESTATION',
        filingChannelId: isFiling && filingChannelId ? filingChannelId : null,
        acknowledgementId: isFiling && acknowledgementId.trim() ? acknowledgementId.trim() : null,
        evidenceReference: null,
        note: null,
        latitude: isFiling ? currentCoordinates?.latitude ?? null : null,
        longitude: isFiling ? currentCoordinates?.longitude ?? null : null,
        dedupeOverride: isFiling && dedupeOverride,
      }),
    });
    const result: LifecycleResponse = await response.json();
    if (response.status === 409 && result.status === 'POSSIBLE_DUPLICATE') {
      setDuplicateWarning(result);
      setLifecycleStatus(`Possible duplicate found ${result.measuredDistanceMeters?.toFixed(1)} m away. Review before overriding.`);
      return;
    }
    if (!response.ok || result.status !== 'TRANSITION_RECORDED') {
      setLifecycleStatus(result.message ?? `Lifecycle update failed (${response.status}).`);
      return;
    }
    setDuplicateWarning(null);
    setReportStatus(result.toStatus);
    setTimeline((items) => [...items, {
      eventType: result.eventType ?? result.toStatus,
      toStatus: result.toStatus,
      occurredAt: result.occurredAt,
      verificationBasis: result.verificationBasis,
      pointsAwarded: result.pointsAwarded,
    }]);
    const dedupeNote = isFiling
      ? ` · ${result.dedupeDisposition}${result.measuredDistanceMeters != null ? ` at ${result.measuredDistanceMeters.toFixed(1)} m` : ''}`
      : '';
    setLifecycleStatus(`${result.toStatus.replaceAll('_', ' ')} recorded${result.pointsAwarded ? ` · +${result.pointsAwarded} points` : ''}${dedupeNote}`);
    await refreshDerivedPoints();
  }

  const demoStates = [
    ['DRAFT', 'Draft saved; nothing submitted'],
    ['FILED', 'Citizen confirms manual filing · +5 demo points'],
    ['OVERDUE', 'Synthetic verified dueAt passes on the simulated clock'],
    ['CLAIMED_FIXED', 'Repair claim recorded'],
    ['VERIFIED_FIXED', 'Citizen attestation recorded · +40 demo points'],
    ['REOPENED', 'Issue recurred; no points awarded'],
  ];

  return <main>
    <section className="hero"><span className="eyebrow">SEEWIK</span><h1>A Civic Intelligence Platform</h1></section>
    <section className="card">
      <div className="signal" /><h2>Find the civic route</h2>
      <p>Start with a photo or short description. Gemini may suggest an issue category, but you confirm it. Authority and department always come from Civic Pack v0.2.</p>
      <div className="flow-step"><span>1</span><b>Describe the issue</b></div>
      <label>Photo (optional)<input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => {
        setEvidenceImage(event.target.files?.[0] ?? null);
        setClassificationConfirmed(false);
        setRouteResult(null);
        resetDraft();
      }} /></label>
      <label>Short description (optional)<textarea maxLength={2000} value={evidenceText} placeholder="उदा. रस्त्यावर मोठा खड्डा आहे" onChange={(event) => {
        setEvidenceText(event.target.value);
        setClassificationConfirmed(false);
        setRouteResult(null);
        resetDraft();
      }} /></label>
      <button className="secondary" onClick={() => classifyEvidence().catch(() => {
        setClassificationStatus('The category could not be checked. Choose it manually below.');
        setClassificationSource('CITIZEN_SELECTED');
      })}>Suggest issue category</button>
      {classificationStatus && <div aria-live="polite" className={`status-panel ${classification?.status === 'CLASSIFICATION_ERROR' ? 'state-error' : classification?.status === 'CLASSIFIED' ? 'state-success' : 'state-warning'}`}>
        <strong>{classification?.status === 'CLASSIFIED' ? 'Category suggestion ready' : classification?.status === 'CLARIFICATION_REQUIRED' ? 'Please clarify' : 'Category confirmation'}</strong>
        <span>{classificationStatus}</span>
        {classification?.description && <small>{classification.description}</small>}
        {classification?.detectedLanguage && <small>Detected language: {classification.detectedLanguage}</small>}
      </div>}
      <label>Issue category<select value={issueType} onChange={(event) => chooseIssueType(event.target.value)}>{ISSUE_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <button onClick={confirmIssueType}>{classificationConfirmed ? 'Category confirmed' : 'Confirm this category'}</button>
      {classificationConfirmed && <div className="confirmed-line" aria-live="polite">✓ {issueLabel(issueType)} · {classificationSource}</div>}

      <div className="flow-step"><span>2</span><b>Confirm your prabhag</b></div>
      <p>Location can suggest a prabhag using synthetic development boundaries. The suggestion is never official and must be confirmed. Manual selection always overrides it.</p>
      <button className="secondary" onClick={useMyLocation}>Suggest from my location</button>
      {locationStatus && <div aria-live="polite" className={`status-panel ${resolution?.status === 'CANDIDATE_PRABHAG' ? 'state-success' : resolution?.status === 'OUTSIDE_SUPPORTED_AREA' || resolution?.status === 'RESOLUTION_UNAVAILABLE' || resolution?.status === 'INVALID_COORDINATES' ? 'state-error' : 'state-warning'}`}>{locationStatus}</div>}
      {resolution?.status === 'CANDIDATE_PRABHAG' && resolution.prabhagId && <div className="candidate"><strong>{resolution.prabhagName}</strong><span>{resolution.resolutionQuality} · {resolution.datasetVersion}</span><span>BigQuery lookup: {resolution.queryLatencyMs} ms</span><button onClick={confirmCandidate}>Confirm this suggested prabhag</button></div>}
      <label>Official prabhag number<select value={prabhagId} onChange={(event) => selectManualPrabhag(event.target.value)}>{PRABHAGS.map((value, index) => <option key={value} value={value}>Prabhag {index + 1}</option>)}</select></label>
      <div className="flow-step"><span>3</span><b>Get the deterministic route</b></div>
      <button disabled={!classificationConfirmed} onClick={() => findCivicRoute().catch((error) => setRouteResult({ status: `Routing failed: ${error.message}` }))}>Find official route</button>
      {routeResult && <div aria-live="polite" className={`route-result ${routeResult.status === 'SUPPORTED_ROUTE' ? 'state-success' : 'state-error'}`}>
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
      {routeResult?.status === 'SUPPORTED_ROUTE' && <>
        <div className="flow-step"><span>4</span><b>Create and review the complaint draft</b></div>
        <p>The recipient and route stay fixed from Civic Pack. Gemini only drafts the wording from the facts you confirm below.</p>
        <div className="locked-recipient">
          <small>Locked recipient</small>
          <strong>{routeResult.authority}</strong>
          <span>{routeResult.routeId} · {routeResult.prabhagId}</span>
        </div>
        <label>Confirmed complaint facts<textarea maxLength={2000} value={complaintFacts} placeholder="Describe only what happened. Do not add guesses." onChange={(event) => {
          setComplaintFacts(event.target.value);
          resetDraft();
        }} /></label>
        <label>Location or landmark (optional)<input type="text" maxLength={500} value={locationDetails} placeholder="उदा. बस स्थानकाजवळ" onChange={(event) => {
          setLocationDetails(event.target.value);
          resetDraft();
        }} /></label>
        <label>Draft language<select value={draftLanguage} onChange={(event) => {
          setDraftLanguage(event.target.value as 'MR' | 'EN');
          resetDraft();
        }}><option value="MR">Marathi</option><option value="EN">English</option></select></label>
        <button onClick={() => createComplaintDraft().catch((error) => setDraftStatus(`Drafting failed: ${error.message}`))}>Create complaint draft</button>
        {draftStatus && <div aria-live="polite" className={`status-panel ${complaintDraft?.status === 'DRAFT_ERROR' || draftStatus.includes('could not') || draftStatus.includes('failed') ? 'state-error' : 'state-warning'}`}>{draftStatus}</div>}
        {complaintDraft?.status === 'DRAFT_READY' && <div className="draft-panel">
          <div className="draft-heading"><div><small>Recipient</small><strong>{complaintDraft.authorityLocalName || complaintDraft.authority}</strong></div><span>{complaintDraft.language} · {complaintDraft.draftVersion}</span></div>
          {(complaintDraft.missingDetails?.length ?? 0) > 0 && <div className="missing-facts"><b>Missing fact</b><span>Add a location or landmark before submitting if one is available. It was not invented in this draft.</span></div>}
          <label>Subject<input type="text" maxLength={160} value={draftSubject} onChange={(event) => {
            setDraftSubject(event.target.value);
            setDraftReviewed(false);
          }} /></label>
          <label>Complaint body<textarea className="draft-body" maxLength={2500} value={draftBody} onChange={(event) => {
            setDraftBody(event.target.value);
            setDraftReviewed(false);
          }} /></label>
          <label className="review-check"><input type="checkbox" checked={draftReviewed} onChange={(event) => setDraftReviewed(event.target.checked)} /><span>I reviewed the facts, recipient and wording.</span></label>
          <div className="draft-actions">
            <button className="secondary" disabled={!draftDocumentId} onClick={() => saveDraftEdits().catch((error) => setDraftStatus(`Draft save failed: ${error.message}`))}>Save changes</button>
            <button disabled={!draftReviewed || !draftDocumentId} onClick={() => copyReviewedDraft().catch((error) => setDraftStatus(`Copy failed: ${error.message}`))}>Copy reviewed complaint</button>
          </div>
          <small>No complaint is submitted automatically. The saved Firestore record remains a DRAFT owned by your anonymous account.</small>
        </div>}
        {draftDocumentId && <div className="lifecycle-panel">
          <div className="lifecycle-heading">
            <div><small>Report lifecycle</small><strong>{reportStatus.replaceAll('_', ' ')}</strong></div>
            <div className="points-pill"><span>Derived points</span><b>{pointsTotal}</b></div>
          </div>
          <p>Confirm real-world actions here. Seewik records them but never files a complaint for you.</p>
          {reportStatus === 'DRAFT' && <>
            {(routeResult.officialChannels?.length ?? 0) > 0 && <label>Channel you used<select value={filingChannelId} onChange={(event) => setFilingChannelId(event.target.value)}>
              <option value="">Not recorded</option>
              {routeResult.officialChannels?.map((channel) => <option key={channel.channelId} value={channel.channelId}>{channel.label}</option>)}
            </select></label>}
            <label>Acknowledgement / tracking ID (optional)<input maxLength={200} value={acknowledgementId} onChange={(event) => setAcknowledgementId(event.target.value)} placeholder="Leave blank if none was provided" /></label>
            <button onClick={() => transitionReport('FILED').catch((error) => setLifecycleStatus(error.message))}>I filed this complaint</button>
            {!currentCoordinates && <small>Dedupe will be marked DEDUPE_NOT_EVALUATED because no coordinates are available.</small>}
          </>}
          {duplicateWarning && <div className="duplicate-warning">
            <b>Possible duplicate</b>
            <span>A same-category report is {duplicateWarning.measuredDistanceMeters?.toFixed(1)} m away. The 75 m threshold is an MVP heuristic, not a civic boundary.</span>
            <button className="secondary" onClick={() => transitionReport('FILED', true).catch((error) => setLifecycleStatus(error.message))}>This is a different issue — file with 0 points</button>
          </div>}
          {['FILED', 'OVERDUE', 'REOPENED'].includes(reportStatus) && <>
            {reportStatus === 'FILED' && <div className="overdue-unknown"><b>Overdue: unknown</b><span>No verified SLA or due date exists in Civic Pack v0.2, so Seewik will not invent one.</span></div>}
            <button onClick={() => transitionReport('CLAIMED_FIXED').catch((error) => setLifecycleStatus(error.message))}>Record a repair claim</button>
          </>}
          {reportStatus === 'CLAIMED_FIXED' && <div className="lifecycle-actions">
            <button onClick={() => transitionReport('VERIFIED_FIXED').catch((error) => setLifecycleStatus(error.message))}>Verify fixed by citizen attestation</button>
            <button className="secondary" onClick={() => transitionReport('REOPENED').catch((error) => setLifecycleStatus(error.message))}>Reject repair claim</button>
          </div>}
          {reportStatus === 'VERIFIED_FIXED' && <button className="secondary" onClick={() => transitionReport('REOPENED').catch((error) => setLifecycleStatus(error.message))}>Report that the issue recurred</button>}
          {lifecycleStatus && <div aria-live="polite" className="status-panel state-warning">{lifecycleStatus}</div>}
          <ol className="timeline">
            {timeline.map((item, index) => <li key={`${item.occurredAt}-${index}`}>
              <span>{index + 1}</span><div><b>{item.toStatus.replaceAll('_', ' ')}</b><small>{item.eventType} · {item.verificationBasis}{item.pointsAwarded ? ` · +${item.pointsAwarded}` : ''}</small></div>
            </li>)}
          </ol>
        </div>}
      </>}
    </section>
    <section className="card demo-card">
      <div className="demo-banner">DEMO DATA · SYNTHETIC CLOCK · EXCLUDED FROM ANALYTICS AND REWARDS</div>
      <h2>90-second lifecycle demo</h2>
      <p>This local walkthrough demonstrates every state without creating a report or changing your real points.</p>
      <div className="demo-current"><small>Simulated report state</small><strong>{demoStates[demoStep][0]}</strong><span>{demoStates[demoStep][1]}</span></div>
      <ol className="timeline compact">
        {demoStates.slice(0, demoStep + 1).map(([state, description], index) => <li key={state}><span>{index + 1}</span><div><b>{state}</b><small>{description}</small></div></li>)}
      </ol>
      <div className="lifecycle-actions">
        <button disabled={demoStep === demoStates.length - 1} onClick={() => setDemoStep((step) => Math.min(step + 1, demoStates.length - 1))}>Next simulated transition</button>
        <button className="secondary" disabled={demoStep === 0} onClick={() => setDemoStep(0)}>Reset demo</button>
      </div>
    </section>
    <section className="card systems"><h2>{status}</h2><p>The secure cloud path remains available for technical validation.</p><button onClick={() => verifyFirebase().catch((error) => add(`Firebase check failed: ${error.message}`))}>Verify Firebase services</button>{details.length > 0 && <ul>{details.map((detail, index) => <li key={`${index}-${detail}`}>{detail}</li>)}</ul>}</section>
    <footer>Built for local civic action</footer>
  </main>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'));
