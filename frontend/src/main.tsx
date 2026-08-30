import React, { lazy, Suspense, useEffect, useRef, useState } from 'react';
import ReactDOM from 'react-dom/client';
import type { AuthCredential } from 'firebase/auth';
import { collection, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc, where } from 'firebase/firestore';
import { getBytes, ref, uploadBytes } from 'firebase/storage';
import { db, storage } from './firebase';
import { AccountControl, type AccountDialog } from './AccountControl';
import {
  accountCredentialFromError,
  continueWithExistingAccount,
  ensureAnonymousSession,
  finalizeExistingAccountCollision,
  linkCurrentSession,
  observeAccount,
  sessionToken,
  signIntoAccount,
  signOutWithoutStartingAnonymousWork,
} from './accountService';
import { accountErrorMessage, isCredentialCollisionCode, reportsViewState, safeAccountErrorCode, type AccountIdentityState } from './accountIdentity';
import { LANGUAGE_OPTIONS, LANGUAGE_STORAGE_KEY, classificationConfirmedMessage, classificationSuggestionMessage, formatDateTime, initialLanguage, localizedRuntimeMessage, localizedStatus, prabhagConfirmedMessage, translate, type InterfaceLanguage } from './i18n';
import { canEditReport, canResumeReport, draftRouteIsCurrent, pathForScreen, reportIdFromPath, reportIdFromReviewSearch, screenFromPath, type AppScreen } from './reportNavigation';
import './styles.css';

const API_URL = import.meta.env.VITE_API_URL || 'https://seewik-api-528138216934.asia-south1.run.app';
const PRABHAGS = Array.from({ length: 20 }, (_, index) => `PRABHAG-${String(index + 1).padStart(2, '0')}`);
const LazyPrabhagBoundaryMap = lazy(() => import('./PrabhagBoundaryMap'));
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
  resolutionMethod?: string;
  resolutionQuality?: string;
  requiresCitizenConfirmation: boolean;
  datasetVersion?: string;
  queryLatencyMs?: number;
  fallbackReason?: string;
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

type SavedReport = {
  id: string;
  ownerUid: string;
  status: string;
  confirmedIssueType: string;
  prabhagId: string;
  routeId: string;
  authority: string;
  draftLanguage: 'MR' | 'EN';
  draftSubject: string;
  draftBody: string;
  packVersion: string;
  schemaVersion: string;
  createdAt?: unknown;
  updatedAt?: unknown;
  filedAt?: unknown;
  acknowledgementId?: string;
  filingChannelId?: string;
  overdueEligibility?: string;
  routeSnapshot?: RouteResult;
};

type Initiative = {
  initiativeId: string;
  title: string;
  category: string;
  description: string;
  startAt: string;
  placeName: string;
  needs: string;
  status: string;
  cancellationReason: string;
  participantCount: number;
  distanceKm: number;
  joined: boolean;
  role: string;
  canManage: boolean;
  joinerCount: number;
  selfAttendanceCount: number;
  codeAttendanceCount: number;
  attendanceStatus: string;
  attendanceBasis: string;
  attendanceReportedAt: string;
  codeWindowEndsAt: string;
  canUseOrganiserCode: boolean;
  canSelfAttend: boolean;
  canViewAttendanceCode: boolean;
  schemaVersion: string;
};

type InitiativeAttendanceResponse = {
  status: string;
  initiativeId: string;
  attendanceStatus: string;
  attendanceBasis: string;
  attendanceReportedAt: string;
  joinerCount: number;
  selfAttendanceCount: number;
  codeAttendanceCount: number;
  idempotentReplay: boolean;
  participantPointsAwarded: number;
  organiserPointsAwarded: number;
  schemaVersion: string;
  rewardPolicyVersion: string;
  errorCode?: string;
  message?: string;
};

type ActiveAttendanceCode = {
  status: string;
  initiativeId: string;
  code: string;
  rotatesAt: string;
  codeWindowEndsAt: string;
  schemaVersion: string;
};

type InitiativeDiscovery = {
  status: string;
  radiusKm: number;
  count: number;
  initiatives: Initiative[];
  errorCode?: string;
  message?: string;
};

class BoundaryMapErrorBoundary extends React.Component<React.PropsWithChildren<{ fallback: React.ReactNode }>, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}

const ISSUE_VALUES = new Set<string>(ISSUE_TYPES.map(([value]) => value));

function issueLabel(value: string, language: InterfaceLanguage = 'en') {
  return translate(language, ISSUE_TYPES.find(([issueType]) => issueType === value)?.[1] ?? value);
}

function timestampMillis(value: unknown) {
  if (value instanceof Date) return value.getTime();
  if (value && typeof value === 'object' && 'toMillis' in value && typeof value.toMillis === 'function') return value.toMillis();
  if (value && typeof value === 'object' && 'seconds' in value && typeof value.seconds === 'number') return value.seconds * 1000;
  if (typeof value === 'string') return Date.parse(value) || 0;
  return 0;
}

function timestampLabel(value: unknown, language: InterfaceLanguage = 'en') {
  const milliseconds = timestampMillis(value);
  return milliseconds ? formatDateTime(language, milliseconds) : translate(language, 'Time pending');
}

function savedReport(id: string, data: Record<string, unknown>): SavedReport {
  return {
    id,
    ownerUid: String(data.ownerUid ?? ''),
    status: String(data.status ?? 'DRAFT'),
    confirmedIssueType: String(data.confirmedIssueType ?? 'UNKNOWN'),
    prabhagId: String(data.prabhagId ?? 'UNKNOWN'),
    routeId: String(data.routeId ?? ''),
    authority: String(data.authority ?? ''),
    draftLanguage: data.draftLanguage === 'EN' ? 'EN' : 'MR',
    draftSubject: String(data.draftSubject ?? ''),
    draftBody: String(data.draftBody ?? ''),
    packVersion: String(data.packVersion ?? ''),
    schemaVersion: String(data.schemaVersion ?? ''),
    createdAt: data.createdAt,
    updatedAt: data.updatedAt,
    filedAt: data.filedAt,
    acknowledgementId: data.acknowledgementId ? String(data.acknowledgementId) : undefined,
    filingChannelId: data.filingChannelId ? String(data.filingChannelId) : undefined,
    overdueEligibility: data.overdueEligibility ? String(data.overdueEligibility) : undefined,
    routeSnapshot: data.routeSnapshot as RouteResult | undefined,
  };
}

function App() {
  const [language, setLanguage] = useState<InterfaceLanguage>(() => initialLanguage(
    window.localStorage.getItem(LANGUAGE_STORAGE_KEY),
    navigator.languages?.length ? navigator.languages : [navigator.language],
  ));
  const [screen, setScreen] = useState<AppScreen>(() => screenFromPath(window.location.pathname));
  const [locationKey, setLocationKey] = useState(() => `${window.location.pathname}${window.location.search}`);
  const [status, setStatus] = useState('Connecting…');
  const [details, setDetails] = useState<string[]>([]);
  const [issueType, setIssueType] = useState(ISSUE_TYPES[0][0]);
  const [prabhagId, setPrabhagId] = useState('');
  const [routeResult, setRouteResult] = useState<RouteResult | null>(null);
  const [resolution, setResolution] = useState<PrabhagResolution | null>(null);
  const [locationStatus, setLocationStatus] = useState('');
  const [selectionMethod, setSelectionMethod] = useState('SELF_REPORTED');
  const [citizenConfirmed, setCitizenConfirmed] = useState(false);
  const [manualPrabhagSelected, setManualPrabhagSelected] = useState(false);
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
  const [manualComplaintBody, setManualComplaintBody] = useState('');
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
  const [savedReports, setSavedReports] = useState<SavedReport[]>([]);
  const [reportsStatus, setReportsStatus] = useState('');
  const [selectedReport, setSelectedReport] = useState<SavedReport | null>(null);
  const [initiatives, setInitiatives] = useState<Initiative[]>([]);
  const [myInitiatives, setMyInitiatives] = useState<Initiative[]>([]);
  const [initiativeStatus, setInitiativeStatus] = useState('');
  const [cancellationReasons, setCancellationReasons] = useState<Record<string, string>>({});
  const [attendanceCodeInputs, setAttendanceCodeInputs] = useState<Record<string, string>>({});
  const [activeAttendanceCodes, setActiveAttendanceCodes] = useState<Record<string, ActiveAttendanceCode>>({});
  const [initiativeCoordinates, setInitiativeCoordinates] = useState<{ latitude: number; longitude: number } | null>(null);
  const [initiativeRadiusKm, setInitiativeRadiusKm] = useState(5);
  const [initiativeTitle, setInitiativeTitle] = useState('');
  const [initiativeCategory, setInitiativeCategory] = useState('CLEANUP');
  const [initiativeDescription, setInitiativeDescription] = useState('');
  const [initiativeStartAt, setInitiativeStartAt] = useState('');
  const [initiativePlaceName, setInitiativePlaceName] = useState('');
  const [initiativeNeeds, setInitiativeNeeds] = useState('');
  const [accountState, setAccountState] = useState<AccountIdentityState>('ANONYMOUS_SESSION');
  const [accountEmail, setAccountEmail] = useState<string | null>(null);
  const [accountDialog, setAccountDialog] = useState<AccountDialog>('CLOSED');
  const [accountBusy, setAccountBusy] = useState(false);
  const [accountError, setAccountError] = useState('');
  const [accountErrorCode, setAccountErrorCode] = useState('');
  const [collisionCredential, setCollisionCredential] = useState<AuthCredential | null>(null);
  const pendingMutation = useRef<(() => Promise<void>) | null>(null);
  const t = (source: string) => translate(language, source);
  const runtimeMessage = (message: string) => localizedRuntimeMessage(language, message);
  const classificationSourceLabel = (source: string) => source === 'CITIZEN_CONFIRMED_GEMINI' || source === 'GEMINI_SUGGESTED' ? t('Automatic suggestion confirmed') : t('Selected manually');
  const prabhagSelectionMade = manualPrabhagSelected || citizenConfirmed;
  const highlightedPrabhagId = manualPrabhagSelected || citizenConfirmed
    ? prabhagId
    : resolution?.status === 'CANDIDATE_PRABHAG' ? resolution.prabhagId : undefined;
  const boundarySelectionKind = manualPrabhagSelected
    ? 'MANUAL' as const
    : citizenConfirmed
      ? 'CONFIRMED' as const
      : resolution?.status === 'CANDIDATE_PRABHAG'
        ? 'AUTOMATIC_CANDIDATE' as const
        : undefined;
  const reportsView = reportsViewState(accountState, savedReports.length, reportsStatus.startsWith('Loading'));
  const initiativeCategoryLabel = (category: string) => ({
    CLEANUP: t('Neighbourhood clean-up'),
    PLANTATION: t('Plantation'),
    DONATION: t('Donation activity'),
    COMMUNITY_FITNESS: t('Community fitness'),
    OTHER_CIVIC_ACTIVITY: t('Other civic activity'),
  }[category] ?? category);
  const add = (line: string) => setDetails((old) => [...old, line]);

  useEffect(() => {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
    document.documentElement.lang = language;
    document.title = language === 'mr' ? 'सीविक · स्थानिक नागरी कृती' : language === 'hi' ? 'सीविक · स्थानीय नागरिक कार्रवाई' : 'Seewik · Local civic action';
  }, [language]);

  useEffect(() => observeAccount(({ state, user }) => {
    setAccountState(state);
    setAccountEmail(user?.email ?? null);
  }), []);

  function navigate(nextScreen: AppScreen, replace = false, reportId?: string) {
    const path = pathForScreen(nextScreen, reportId);
    if (replace) window.history.replaceState({}, '', path);
    else if (`${window.location.pathname}${window.location.search}` !== path) window.history.pushState({}, '', path);
    setScreen(nextScreen);
    setLocationKey(`${window.location.pathname}${window.location.search}`);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function resetDraft() {
    setComplaintDraft(null);
    setDraftSubject('');
    setDraftBody('');
    setManualComplaintBody('');
    setDraftStatus('');
    setDraftDocumentId(null);
    setDraftReviewed(false);
    setReportStatus('DRAFT');
    setTimeline([]);
    setLifecycleStatus('');
    setDuplicateWarning(null);
    setFilingChannelId('');
    setAcknowledgementId('');
    setSelectedReport(null);
  }

  function resetEvidenceDerivedState() {
    setClassification(null);
    setClassificationStatus('');
    setClassificationConfirmed(false);
    setClassificationSource('SELF_REPORTED');
    setComplaintFacts('');
    setRouteResult(null);
    resetDraft();
  }

  function startOver() {
    setIssueType(ISSUE_TYPES[0][0]);
    setPrabhagId('');
    setRouteResult(null);
    setResolution(null);
    setLocationStatus('');
    setSelectionMethod('SELF_REPORTED');
    setCitizenConfirmed(false);
    setManualPrabhagSelected(false);
    setBoundaryDatasetVersion(undefined);
    setEvidenceText('');
    setEvidenceImage(null);
    setClassification(null);
    setClassificationStatus('');
    setClassificationConfirmed(false);
    setClassificationSource('SELF_REPORTED');
    setComplaintFacts('');
    setLocationDetails('');
    setDraftLanguage('MR');
    setCurrentCoordinates(null);
    resetDraft();
    navigate('new-report');
  }

  function clearAccountBoundState() {
    setSavedReports([]);
    setSelectedReport(null);
    setReportsStatus('');
    setPointsTotal(0);
    setInitiatives([]);
    setMyInitiatives([]);
    setInitiativeStatus('');
    setCancellationReasons({});
    setInitiativeCoordinates(null);
    setInitiativeTitle('');
    setInitiativeDescription('');
    setInitiativeStartAt('');
    setInitiativePlaceName('');
    setInitiativeNeeds('');
    setEvidenceText('');
    setEvidenceImage(null);
    setComplaintFacts('');
    setLocationDetails('');
    resetDraft();
  }

  function clearAccountError() {
    setAccountError('');
    setAccountErrorCode('');
  }

  function showAccountError(code: string | undefined) {
    setAccountError(accountErrorMessage(code));
    setAccountErrorCode(safeAccountErrorCode(code));
  }

  function requestLinkedMutation(action: () => Promise<void>) {
    if (accountState === 'GOOGLE_LINKED') {
      void action();
      return;
    }
    pendingMutation.current = action;
    clearAccountError();
    setAccountDialog('LINK');
  }

  function openAccount() {
    clearAccountError();
    setAccountDialog(accountState === 'GOOGLE_LINKED' ? 'PROFILE' : 'LINK');
  }

  function closeAccount() {
    if (accountBusy) return;
    pendingMutation.current = null;
    setCollisionCredential(null);
    clearAccountError();
    setAccountDialog('CLOSED');
  }

  async function resumePendingMutation() {
    const action = pendingMutation.current;
    pendingMutation.current = null;
    setAccountDialog('CLOSED');
    if (action) await action();
  }

  async function connectGoogleAccount() {
    setAccountBusy(true);
    clearAccountError();
    try {
      const user = accountState === 'SIGNED_OUT'
        ? await signIntoAccount('GOOGLE')
        : await linkCurrentSession('GOOGLE');
      setAccountState('GOOGLE_LINKED');
      setAccountEmail(user.email);
      await resumePendingMutation();
    } catch (error) {
      const code = typeof error === 'object' && error && 'code' in error ? String(error.code) : undefined;
      if (isCredentialCollisionCode(code)) {
        const credential = accountCredentialFromError('GOOGLE', error);
        if (credential) {
          setCollisionCredential(credential);
          setAccountDialog('COLLISION');
        } else {
          setAccountError('The existing Google account was found, but Firebase did not return a safe sign-in credential. Cancel and try again.');
          setAccountErrorCode(safeAccountErrorCode(code));
        }
      } else {
        showAccountError(code);
      }
    } finally {
      setAccountBusy(false);
    }
  }

  async function acceptExistingGoogleAccount() {
    if (!collisionCredential) return;
    setAccountBusy(true);
    clearAccountError();
    try {
      const user = await continueWithExistingAccount(collisionCredential);
      clearAccountBoundState();
      pendingMutation.current = null;
      setAccountState('GOOGLE_LINKED');
      setAccountEmail(user.email);
      setCollisionCredential(null);
      navigate('home');
      try {
        await finalizeExistingAccountCollision(user);
      } catch {
        setAccountDialog('PROFILE');
        setAccountError('The existing account opened, but its local profile record could not be updated. No accounts or civic records were merged.');
        setAccountErrorCode('auth/profile-write-failed');
        return;
      }
      setAccountDialog('CLOSED');
      await Promise.allSettled([loadMyReports(), refreshDerivedPoints(), loadMyInitiatives()]);
    } catch (error) {
      const code = typeof error === 'object' && error && 'code' in error ? String(error.code) : undefined;
      showAccountError(code);
    } finally {
      setAccountBusy(false);
    }
  }

  async function signOutAccount() {
    setAccountBusy(true);
    clearAccountError();
    try {
      await signOutWithoutStartingAnonymousWork();
      clearAccountBoundState();
      setAccountState('SIGNED_OUT');
      setAccountEmail(null);
      setAccountDialog('CLOSED');
      navigate('home');
    } catch (error) {
      const code = typeof error === 'object' && error && 'code' in error ? String(error.code) : undefined;
      showAccountError(code);
    } finally {
      setAccountBusy(false);
    }
  }

  useEffect(() => {
    fetch(`${API_URL}/health`).then((response) => response.json()).then((data) => {
      setStatus(data.status === 'ok' ? 'Seewik systems online' : 'Backend returned an unexpected response');
      add(`Cloud API: ${data.status}`);
    }).catch((error) => setStatus(`API check failed: ${error.message}`));
  }, []);

  useEffect(() => {
    const onPopState = () => {
      setScreen(screenFromPath(window.location.pathname));
      setLocationKey(`${window.location.pathname}${window.location.search}`);
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  useEffect(() => {
    if (screen === 'reports') {
      if (accountState === 'SIGNED_OUT') {
        setSavedReports([]);
        setReportsStatus('');
      } else {
        loadMyReports().catch((error) => setReportsStatus(`Reports could not be loaded: ${error.message}`));
      }
    }
    if (screen === 'points') refreshDerivedPoints().catch(() => undefined);
    if (screen === 'report-detail' && accountState !== 'SIGNED_OUT') {
      const reportId = reportIdFromPath(window.location.pathname);
      if (reportId && selectedReport?.id !== reportId) {
        loadReportById(reportId, false).catch((error) => setReportsStatus(`Report could not be loaded: ${error.message}`));
      }
    }
    if (screen === 'review') {
      const reportId = reportIdFromReviewSearch(window.location.search);
      if (reportId && selectedReport?.id !== reportId) {
        loadReportById(reportId, true).catch((error) => setDraftStatus(`Draft could not be resumed: ${error.message}`));
      }
    }
    if (screen === 'initiatives') {
      loadMyInitiatives().catch((error) => setInitiativeStatus(`Your activities could not be loaded: ${error.message}`));
      if (initiativeCoordinates) {
        discoverInitiatives(false).catch((error) => setInitiativeStatus(`Activities could not be loaded: ${error.message}`));
      }
    }
  }, [screen, locationKey, accountState]);

  useEffect(() => {
    if (screen !== 'initiatives' || !initiativeCoordinates) return;
    const interval = window.setInterval(() => {
      discoverInitiatives(true).catch(() => undefined);
      loadMyInitiatives(true).catch(() => undefined);
    }, 5000);
    return () => window.clearInterval(interval);
  }, [screen, initiativeCoordinates, initiativeRadiusKm]);

  useEffect(() => {
    if (screen !== 'initiatives') return;
    const entries = Object.entries(activeAttendanceCodes);
    if (!entries.length) return;
    const nextRotation = Math.min(...entries.map(([, value]) => Date.parse(value.rotatesAt)));
    const delay = Math.max(500, nextRotation - Date.now() + 250);
    const timeout = window.setTimeout(() => {
      for (const [initiativeId] of entries) {
        loadAttendanceCode(initiativeId, true).catch(() => {
          setActiveAttendanceCodes((values) => {
            const updated = { ...values };
            delete updated[initiativeId];
            return updated;
          });
          loadMyInitiatives(true).catch(() => undefined);
        });
      }
    }, delay);
    return () => window.clearTimeout(timeout);
  }, [screen, activeAttendanceCodes]);

  async function authenticatedToken() {
    return sessionToken();
  }

  async function discoverInitiatives(background = false) {
    if (!initiativeCoordinates) {
      setInitiativeStatus('Share your location to discover activities nearby.');
      return;
    }
    if (!background) setInitiativeStatus('Finding nearby activities…');
    const idToken = await authenticatedToken();
    const response = await fetch(`${API_URL}/api/initiatives/nearby`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${idToken}` },
      body: JSON.stringify({ ...initiativeCoordinates, radiusKm: initiativeRadiusKm }),
    });
    const result: InitiativeDiscovery = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Discovery failed (${response.status})`);
    setInitiatives(result.initiatives);
    if (!background) setInitiativeStatus(result.count ? `${result.count} upcoming activities within ${result.radiusKm} km.` : `No upcoming activities found within ${result.radiusKm} km.`);
  }

  async function loadMyInitiatives(background = false) {
    const idToken = await authenticatedToken();
    const response = await fetch(`${API_URL}/api/initiatives/mine`, {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    const result: InitiativeDiscovery = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Your activities could not be loaded (${response.status})`);
    setMyInitiatives(result.initiatives);
    if (!background && result.count > 0) setInitiativeStatus(`${result.count} joined or organised ${result.count === 1 ? 'activity' : 'activities'} loaded.`);
  }

  function locateForInitiatives(afterLocation: 'DISCOVER' | 'CREATE') {
    setInitiativeStatus('Checking your location…');
    if (!navigator.geolocation) {
      setInitiativeStatus('Location is unavailable in this browser.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const coordinates = { latitude: position.coords.latitude, longitude: position.coords.longitude };
        setInitiativeCoordinates(coordinates);
        setInitiativeStatus(afterLocation === 'CREATE' ? 'Activity location captured. Confirm the public place name below.' : 'Location captured. Finding activities…');
      },
      () => setInitiativeStatus('Location permission was not provided.'),
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    );
  }

  async function createInitiative() {
    if (!initiativeCoordinates) {
      setInitiativeStatus('Capture the activity location before publishing.');
      return;
    }
    if (!initiativeStartAt) {
      setInitiativeStatus('Choose the activity date and time.');
      return;
    }
    setInitiativeStatus('Publishing activity…');
    const idToken = await authenticatedToken();
    const response = await fetch(`${API_URL}/api/initiatives`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${idToken}` },
      body: JSON.stringify({
        title: initiativeTitle,
        category: initiativeCategory,
        description: initiativeDescription,
        startAt: new Date(initiativeStartAt).toISOString(),
        placeName: initiativePlaceName,
        ...initiativeCoordinates,
        needs: initiativeNeeds,
      }),
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Publish failed (${response.status})`);
    setInitiativeTitle('');
    setInitiativeDescription('');
    setInitiativeStartAt('');
    setInitiativePlaceName('');
    setInitiativeNeeds('');
    setInitiativeStatus('Activity published. You are included as the organiser.');
    navigate('initiatives');
  }

  async function joinInitiative(initiativeId: string) {
    setInitiativeStatus('Joining activity…');
    const idToken = await authenticatedToken();
    const response = await fetch(`${API_URL}/api/initiatives/${encodeURIComponent(initiativeId)}/join`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${idToken}` },
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Join failed (${response.status})`);
    setInitiatives((items) => items.map((item) => item.initiativeId === initiativeId
      ? { ...item, participantCount: result.participantCount, joined: true }
      : item));
    setInitiativeStatus(result.status === 'ALREADY_JOINED' ? 'You already joined this activity.' : 'You joined. The live participant count has been updated.');
    await loadMyInitiatives(true);
  }

  async function changeInitiativeStatus(initiativeId: string, target: 'CANCELLED' | 'COMPLETED') {
    const reason = cancellationReasons[initiativeId]?.trim() ?? '';
    if (target === 'CANCELLED' && !reason) {
      setInitiativeStatus('Add a short cancellation reason first.');
      return;
    }
    setInitiativeStatus(target === 'CANCELLED' ? 'Cancelling activity…' : 'Marking activity completed…');
    const idToken = await authenticatedToken();
    const response = await fetch(`${API_URL}/api/initiatives/${encodeURIComponent(initiativeId)}/${target === 'CANCELLED' ? 'cancel' : 'complete'}`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${idToken}`,
        ...(target === 'CANCELLED' ? { 'Content-Type': 'application/json' } : {}),
      },
      body: target === 'CANCELLED' ? JSON.stringify({ reason }) : undefined,
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Activity update failed (${response.status})`);
    setInitiatives((items) => items.filter((item) => item.initiativeId !== initiativeId));
    await loadMyInitiatives(true);
    if (result.pointsAwarded) await refreshDerivedPoints();
    setInitiativeStatus(result.idempotentReplay
      ? `This activity was already ${target.toLowerCase()}.`
      : result.pointsAwarded === 40
        ? 'Activity completed. You earned 40 organiser points because two joiners used the organiser code.'
        : target === 'COMPLETED'
          ? 'Activity completed. The organiser award needs two code-attending joiners.'
          : 'Activity cancelled.');
  }

  function applyAttendanceResult(result: InitiativeAttendanceResponse) {
    setMyInitiatives((items) => items.map((item) => item.initiativeId === result.initiativeId
      ? {
          ...item,
          attendanceStatus: result.attendanceStatus,
          attendanceBasis: result.attendanceBasis,
          attendanceReportedAt: result.attendanceReportedAt,
          joinerCount: result.joinerCount,
          selfAttendanceCount: result.selfAttendanceCount,
          codeAttendanceCount: result.codeAttendanceCount,
          canUseOrganiserCode: false,
          canSelfAttend: false,
        }
      : item));
  }

  async function recordSelfAttendance(initiativeId: string) {
    setInitiativeStatus('Recording your attendance report…');
    const idToken = await authenticatedToken();
    const response = await fetch(`${API_URL}/api/initiatives/${encodeURIComponent(initiativeId)}/attendance/self`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${idToken}` },
    });
    const result: InitiativeAttendanceResponse = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Attendance could not be recorded (${response.status})`);
    applyAttendanceResult(result);
    setInitiativeStatus(result.idempotentReplay
      ? 'Your self-reported attendance was already recorded.'
      : 'Your attendance report was recorded. Self-attendance earns zero points.');
  }

  async function submitAttendanceCode(initiativeId: string) {
    const code = attendanceCodeInputs[initiativeId]?.trim() ?? '';
    if (!/^\d{6}$/.test(code)) {
      setInitiativeStatus('Enter the six-digit organiser code.');
      return;
    }
    setInitiativeStatus('Checking the organiser code…');
    const idToken = await authenticatedToken();
    const response = await fetch(`${API_URL}/api/initiatives/${encodeURIComponent(initiativeId)}/attendance/code`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${idToken}` },
      body: JSON.stringify({ code }),
    });
    const result: InitiativeAttendanceResponse = await response.json();
    setAttendanceCodeInputs((values) => ({ ...values, [initiativeId]: '' }));
    if (!response.ok) throw new Error(result.message ?? `Attendance code could not be accepted (${response.status})`);
    applyAttendanceResult(result);
    await refreshDerivedPoints();
    setInitiativeStatus(result.idempotentReplay
      ? 'Your organiser-code attendance was already recorded.'
      : 'Attendance recorded using the organiser’s code. You earned 20 points.');
  }

  async function loadAttendanceCode(initiativeId: string, background = false) {
    if (!background) setInitiativeStatus('Loading the organiser attendance code…');
    const idToken = await authenticatedToken();
    const response = await fetch(`${API_URL}/api/initiatives/${encodeURIComponent(initiativeId)}/attendance/code`, {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Attendance code could not be loaded (${response.status})`);
    setActiveAttendanceCodes((values) => ({ ...values, [initiativeId]: result as ActiveAttendanceCode }));
    if (!background) setInitiativeStatus('The organiser code is active. It rotates every 10 minutes.');
  }

  async function verifyFirebase() {
    setDetails([]);
    const user = await ensureAnonymousSession();
    add(`Firebase auth: ${user.uid.slice(0, 8)}…`);
    const testRef = doc(db, 'day1_checks', user.uid);
    await setDoc(testRef, { ok: true, checkedAt: serverTimestamp() });
    const snapshot = await getDoc(testRef);
    add(`Firestore write/read: ${snapshot.data()?.ok === true ? 'ok' : 'failed'}`);
    const pixel = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='), (character) => character.charCodeAt(0));
    const objectRef = ref(storage, `day1_checks/${user.uid}/pixel.png`);
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
    if (result.status === 'MANUAL_SELECTION_REQUIRED') {
      setSelectionMethod('SELF_REPORTED');
      setCitizenConfirmed(false);
      setBoundaryDatasetVersion(undefined);
      setRouteResult(null);
      resetDraft();
    }
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
    const confirmedPrabhagName = resolution.prabhagName ?? `Prabhag ${Number(resolution.prabhagId.slice(-2))}`;
    setPrabhagId(resolution.prabhagId);
    setSelectionMethod(resolution.resolutionMethod ?? 'BIGQUERY_ST_COVERS');
    setCitizenConfirmed(true);
    setManualPrabhagSelected(false);
    setBoundaryDatasetVersion(resolution.datasetVersion);
    setLocationStatus(prabhagConfirmedMessage(language, confirmedPrabhagName));
    setRouteResult(null);
    resetDraft();
  }

  function selectManualPrabhag(value: string) {
    if (!PRABHAGS.includes(value)) return;
    setPrabhagId(value);
    setSelectionMethod('SELF_REPORTED');
    setCitizenConfirmed(false);
    setManualPrabhagSelected(true);
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
    setComplaintFacts('');
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
    const idToken = await sessionToken();
    const response = await fetch(`${API_URL}/api/civic/classify`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${idToken}` },
      body: form,
    });
    const result: ClassificationResult = await response.json();
    setClassification(result);
    setComplaintFacts(evidenceText.trim() || result.description || '');
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
        ? classificationSuggestionMessage(language, issueLabel(result.issueType, language))
        : result.clarificationQuestion ?? 'The category is unclear. Please choose the best match below.',
    );
  }

  function confirmIssueType() {
    const source = classification?.issueType === issueType && classification.status === 'CLASSIFIED'
      ? 'CITIZEN_CONFIRMED_GEMINI'
      : 'CITIZEN_SELECTED';
    setClassificationSource(source);
    setClassificationConfirmed(true);
    setClassificationStatus(classificationConfirmedMessage(language, issueLabel(issueType, language)));
  }

  async function findCivicRoute() {
    setRouteResult(null);
    resetDraft();
    if (!classificationConfirmed) {
      setRouteResult({ status: 'CATEGORY_CONFIRMATION_REQUIRED' });
      return;
    }
    if (!prabhagSelectionMade) {
      setRouteResult({ status: 'PRABHAG_CONFIRMATION_REQUIRED' });
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
    const user = await ensureAnonymousSession();
    const reportRef = doc(collection(db, 'reports'));
    await setDoc(reportRef, {
      ownerUid: user.uid,
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
    const now = new Date();
    const localReport: SavedReport = {
      id: reportRef.id,
      ownerUid: user.uid,
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
      createdAt: now,
      updatedAt: now,
    };
    setSelectedReport(localReport);
    setSavedReports((reports) => [localReport, ...reports.filter((report) => report.id !== localReport.id)]);
    return reportRef.id;
  }

  async function saveGeneratedDraft(result: ComplaintDraftResult) {
    try {
      const reportId = await persistNewDraft(result);
      setDraftStatus(`Saved as Firestore DRAFT · ${reportId.slice(0, 8)}…`);
      navigate('review', false, reportId);
    } catch (error) {
      setDraftStatus(`Draft created but could not be saved: ${(error as Error).message}`);
    }
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
    const idToken = await sessionToken();
    const response = await fetch(`${API_URL}/api/civic/draft-complaint`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${idToken}` },
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
      setManualComplaintBody(complaintFacts.trim());
      setDraftStatus(result.message ?? 'The complaint draft could not be created. Your confirmed route is unchanged.');
      return;
    }
    setDraftSubject(result.subject);
    setDraftBody(result.body);
    setDraftReviewed(false);
    if (accountState !== 'GOOGLE_LINKED') {
      setDraftStatus('Draft ready. Connect Google to save it without losing this form.');
    }
    requestLinkedMutation(() => saveGeneratedDraft(result));
  }

  async function copyManualComplaint() {
    if (!manualComplaintBody.trim()) {
      setDraftStatus('Write the complaint text before copying it.');
      return;
    }
    const recipient = routeResult?.authority ?? 'Confirmed civic authority';
    await navigator.clipboard.writeText(`${recipient}\n\n${manualComplaintBody.trim()}`);
    setDraftStatus('Your manually written complaint and confirmed recipient were copied.');
  }

  async function saveDraftEdits() {
    if (!canEditReport(reportStatus)) {
      setDraftStatus('Filed reports are immutable and cannot be edited or resumed as drafts.');
      return false;
    }
    if (!draftDocumentId || !draftSubject.trim() || !draftBody.trim()) {
      setDraftStatus('No saved draft is available to update.');
      return false;
    }
    if (!selectedReport || selectedReport.id !== draftDocumentId) {
      setDraftStatus('The selected report no longer matches this editor. Reopen it from My reports.');
      return false;
    }
    await updateDoc(doc(db, 'reports', draftDocumentId), {
      draftSubject: draftSubject.trim(),
      draftBody: draftBody.trim(),
      updatedAt: serverTimestamp(),
    });
    const updatedAt = new Date();
    setSelectedReport((report) => report?.id === draftDocumentId ? { ...report, draftSubject: draftSubject.trim(), draftBody: draftBody.trim(), updatedAt } : report);
    setSavedReports((reports) => reports.map((report) => report.id === draftDocumentId ? { ...report, draftSubject: draftSubject.trim(), draftBody: draftBody.trim(), updatedAt } : report));
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

  async function fileReviewedReport(dedupeOverride = false) {
    if (!draftReviewed) {
      setDraftStatus('Review the final draft before recording that it was filed.');
      return;
    }
    const currentRouteId = routeResult?.routeId ?? complaintDraft?.routeId ?? '';
    const currentPackVersion = routeResult?.packVersion ?? complaintDraft?.packVersion ?? '';
    if (!selectedReport || !draftRouteIsCurrent(
      { issueType: selectedReport.confirmedIssueType, prabhagId: selectedReport.prabhagId, routeId: selectedReport.routeId, packVersion: selectedReport.packVersion },
      { issueType, prabhagId, routeId: currentRouteId, packVersion: currentPackVersion },
    )) {
      setDraftStatus('This draft has stale category, prabhag or route facts. Reopen the saved draft before filing.');
      return;
    }
    if (!await saveDraftEdits()) return;
    await transitionReport('FILED', dedupeOverride);
  }

  async function loadMyReports() {
    setReportsStatus('Loading your reports…');
    const user = await ensureAnonymousSession();
    const snapshot = await getDocs(query(collection(db, 'reports'), where('ownerUid', '==', user.uid)));
    const reports = snapshot.docs
      .map((item) => savedReport(item.id, item.data()))
      .sort((left, right) => timestampMillis(right.updatedAt) - timestampMillis(left.updatedAt));
    setSavedReports(reports);
    setReportsStatus(reports.length ? `${reports.length} owner-protected report${reports.length === 1 ? '' : 's'}` : 'No saved reports yet.');
  }

  async function loadTimeline(report: SavedReport) {
    const snapshot = await getDocs(collection(db, 'reports', report.id, 'lifecycleEvents'));
    const events = snapshot.docs.map((item) => {
      const data = item.data();
      return {
        eventType: String(data.eventType ?? 'UNKNOWN_EVENT'),
        toStatus: String(data.toStatus ?? report.status),
        occurredAt: timestampMillis(data.occurredAt) ? new Date(timestampMillis(data.occurredAt)).toISOString() : new Date().toISOString(),
        verificationBasis: String(data.verificationBasis ?? 'NONE'),
        pointsAwarded: Number(data.pointsAwarded ?? 0),
      } satisfies TimelineItem;
    }).sort((left, right) => Date.parse(left.occurredAt) - Date.parse(right.occurredAt));
    if (!events.length && report.status === 'DRAFT') {
      events.push({ eventType: 'DRAFT_SAVED', toStatus: 'DRAFT', occurredAt: timestampMillis(report.createdAt) ? new Date(timestampMillis(report.createdAt)).toISOString() : new Date().toISOString(), verificationBasis: 'NONE', pointsAwarded: 0 });
    }
    setTimeline(events);
  }

  async function hydrateReport(report: SavedReport) {
    setSelectedReport(report);
    setDraftDocumentId(report.id);
    setReportStatus(report.status);
    setIssueType(report.confirmedIssueType as typeof issueType);
    setPrabhagId(report.prabhagId);
    setSelectionMethod('SELF_REPORTED');
    setCitizenConfirmed(false);
    setManualPrabhagSelected(true);
    setDraftLanguage(report.draftLanguage);
    setDraftSubject(report.draftSubject);
    setDraftBody(report.draftBody);
    setDraftReviewed(false);
    setAcknowledgementId(report.acknowledgementId ?? '');
    setFilingChannelId(report.filingChannelId ?? '');
    setComplaintDraft({
      status: 'DRAFT_READY',
      draftVersion: 'v0.1',
      schemaVersion: report.schemaVersion,
      packVersion: report.packVersion,
      language: report.draftLanguage,
      routeId: report.routeId,
      prabhagId: report.prabhagId,
      authority: report.authority,
      subject: report.draftSubject,
      body: report.draftBody,
      citizenReviewRequired: true,
    });
    const frozen = report.routeSnapshot;
    if (frozen) {
      setRouteResult({ ...frozen, status: 'SUPPORTED_ROUTE', routeId: frozen.routeId ?? report.routeId, prabhagId: frozen.prabhagId ?? report.prabhagId, authority: frozen.authority ?? report.authority, packVersion: frozen.packVersion ?? report.packVersion });
    } else {
      const response = await fetch(`${API_URL}/api/civic/route`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ issueType: report.confirmedIssueType, prabhagId: report.prabhagId, resolutionMethod: 'SELF_REPORTED', citizenConfirmed: false }),
      });
      if (response.ok) setRouteResult(await response.json());
      else setRouteResult({ status: 'SUPPORTED_ROUTE', routeId: report.routeId, prabhagId: report.prabhagId, authority: report.authority, packVersion: report.packVersion });
    }
    await Promise.all([loadTimeline(report), refreshDerivedPoints()]);
  }

  async function loadReportById(reportId: string, resumeRequested: boolean) {
    setReportsStatus('Loading report…');
    const user = await ensureAnonymousSession();
    const snapshot = await getDoc(doc(db, 'reports', reportId));
    if (!snapshot.exists() || snapshot.data().ownerUid !== user.uid) throw new Error('The report was not found for this account.');
    const report = savedReport(snapshot.id, snapshot.data());
    await hydrateReport(report);
    setReportsStatus('Report loaded.');
    if (resumeRequested && !canResumeReport(report.status)) {
      setDraftStatus('Filed reports are immutable and cannot be edited or resumed as drafts.');
      navigate('report-detail', true, report.id);
    }
  }

  async function resumeSavedReport(report: SavedReport) {
    if (!canResumeReport(report.status)) {
      setDraftStatus('Filed reports are immutable and cannot be edited or resumed as drafts.');
      await hydrateReport(report);
      navigate('report-detail', false, report.id);
      return;
    }
    await hydrateReport(report);
    setDraftStatus(`Resumed Firestore DRAFT · ${report.id.slice(0, 8)}…`);
    navigate('review', false, report.id);
  }

  async function openSavedReport(report: SavedReport) {
    await hydrateReport(report);
    navigate('report-detail', false, report.id);
  }

  async function refreshDerivedPoints() {
    const user = await ensureAnonymousSession();
    const snapshot = await getDocs(query(
      collection(db, 'pointsLedger'),
      where('ownerUid', '==', user.uid),
    ));
    setPointsTotal(snapshot.docs.reduce((total, item) => total + Number(item.data().awardedPoints ?? 0), 0));
  }

  async function transitionReport(toStatus: string, dedupeOverride = false) {
    if (!draftDocumentId) {
      setLifecycleStatus('Save a draft before changing its lifecycle.');
      return;
    }
    const idToken = await sessionToken();
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
    const updatedAt = new Date(result.occurredAt);
    setSelectedReport((report) => report ? {
      ...report,
      status: result.toStatus,
      updatedAt,
      acknowledgementId: isFiling ? acknowledgementId.trim() || undefined : report.acknowledgementId,
      filingChannelId: isFiling ? filingChannelId || undefined : report.filingChannelId,
      routeSnapshot: isFiling && routeResult ? routeResult : report.routeSnapshot,
      overdueEligibility: isFiling ? 'OVERDUE_UNKNOWN' : report.overdueEligibility,
    } : report);
    setSavedReports((reports) => reports.map((report) => report.id === draftDocumentId ? { ...report, status: result.toStatus, updatedAt, acknowledgementId: isFiling ? acknowledgementId.trim() || undefined : report.acknowledgementId, filingChannelId: isFiling ? filingChannelId || undefined : report.filingChannelId, routeSnapshot: isFiling && routeResult ? routeResult : report.routeSnapshot, overdueEligibility: isFiling ? 'OVERDUE_UNKNOWN' : report.overdueEligibility } : report));
    await refreshDerivedPoints();
    if (result.toStatus === 'FILED') navigate('report-detail', false, draftDocumentId);
  }

  const demoStates = [
    ['DRAFT', t('Draft saved; nothing submitted')],
    ['FILED', t('Citizen confirms manual filing · +5 demo points')],
    ['OVERDUE', t('Synthetic verified dueAt passes on the simulated clock')],
    ['CLAIMED_FIXED', t('Repair claim recorded')],
    ['VERIFIED_FIXED', t('Citizen attestation recorded · +60 demo points')],
    ['REOPENED', t('Issue recurred; no points awarded')],
  ];

  const navCurrent = (active: boolean) => active ? 'page' as const : undefined;

  return <>
    <a className="skip-link" href="#main-content">{t('Skip to main content')}</a>
    <main id="main-content">
    <header className="app-header">
      <button className="brand-button" onClick={() => navigate('home')} aria-label={t('Seewik home')}>SEEWIK</button>
      <div className="header-actions">
        <nav className="desktop-nav" aria-label={t('Primary navigation')}>
          <button aria-current={navCurrent(screen === 'home')} className={screen === 'home' ? 'active' : ''} onClick={() => navigate('home')}>{t('Home')}</button>
          <button aria-current={navCurrent(screen === 'new-report' || screen === 'review')} className={screen === 'new-report' || screen === 'review' ? 'active' : ''} onClick={() => navigate('new-report')}>{t('Report an issue')}</button>
          <button aria-current={navCurrent(screen === 'reports' || screen === 'report-detail')} className={screen === 'reports' || screen === 'report-detail' ? 'active' : ''} onClick={() => navigate('reports')}>{t('My reports')}</button>
          <button aria-current={navCurrent(screen === 'initiatives' || screen === 'new-initiative')} className={screen === 'initiatives' || screen === 'new-initiative' ? 'active' : ''} onClick={() => navigate('initiatives')}>{t('Initiate')}</button>
          <button aria-current={navCurrent(screen === 'points')} className={screen === 'points' ? 'active' : ''} onClick={() => navigate('points')}>{t('My points')}</button>
        </nav>
        <label className="language-switcher"><span>{t('Language')}</span><select aria-label={t('Language')} value={language} onChange={(event) => setLanguage(event.target.value as InterfaceLanguage)}>{LANGUAGE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
        <AccountControl
          state={accountState}
          dialog={accountDialog}
          email={accountEmail}
          busy={accountBusy}
          error={accountError}
          errorCode={accountErrorCode}
          t={t}
          onOpen={openAccount}
          onClose={closeAccount}
          onGoogle={() => { void connectGoogleAccount(); }}
          onCollisionContinue={() => { void acceptExistingGoogleAccount(); }}
          onSignOut={() => { void signOutAccount(); }}
        />
      </div>
    </header>
    {accountState === 'GOOGLE_LINK_REQUIRED' && <aside className="account-device-warning" role="status" aria-live="polite">
      <div><strong>{t('Device-only access')}</strong><span>{t('This temporary account is not recoverable yet. If you clear this browser before connecting Google, you may permanently lose access to its reports, drafts, points and Initiative activity.')}</span></div>
      <button className="secondary" onClick={openAccount}>{t('Connect Google')}</button>
    </aside>}
    {['new-report', 'review'].includes(screen) && <div className="page-tools"><span>{t('Saved reports are not deleted by Start over.')}</span><button className="secondary" onClick={startOver}>{t('Start over')}</button></div>}

    {screen === 'home' && <>
      <section className="hero"><span className="eyebrow">{t('LOCAL CIVIC ACTION')}</span><h1>{t('A Civic Intelligence Platform')}</h1><p>{t('Identify a civic issue, find the confirmed route, prepare a complaint and track the outcome.')}</p></section>
      <section className="home-actions" aria-label={t('Start using Seewik')}>
        <article><span>01</span><h2>{t('Report an issue')}</h2><p>{t('Describe the problem, confirm its category and find the deterministic civic route.')}</p><button onClick={() => navigate('new-report')}>{t('Start a report')}</button></article>
        <article><span>02</span><h2>{t('Initiate something good')}</h2><p>{t('Create a useful local activity, discover what is nearby and join neighbours taking action.')}</p><button onClick={() => navigate('initiatives')}>{t('Explore activities')}</button></article>
        <article><span>03</span><h2>{t('My reports')}</h2><p>{t('Resume drafts and inspect filed reports without rewriting their frozen facts.')}</p><button className="secondary" onClick={() => navigate('reports')}>{t('Open my reports')}</button></article>
        <article><span>04</span><h2>{t('My points')}</h2><p>{t('See rewards derived from filing and verified outcomes, never complaint volume alone.')}</p><button className="secondary" onClick={() => navigate('points')}>{t('View my points')}</button></article>
      </section>
    </>}

    {screen === 'initiatives' && <>
      <section className="hero page-hero initiative-hero"><span className="eyebrow">{t('INITIATE WHAT IS GOOD')}</span><h1>{t('Take action with neighbours')}</h1><p>{t('Discover upcoming civic activities near you, join once, and see the participant count update.')}</p></section>
      <section className="card initiative-memberships">
        <h2>{t('My activities')}</h2>
        <p>{t('Activities you organise or join stay here after cancellation or completion, so their final status remains clear.')}</p>
        {!myInitiatives.length && <div className="empty-state"><b>{t('No joined activities yet.')}</b><p>{t('Create an activity or join one nearby.')}</p></div>}
        <div className="initiative-list compact-list" aria-live="polite">
          {myInitiatives.map((initiative) => <article className="card initiative-card" key={`mine-${initiative.initiativeId}`}>
            <div className="initiative-card-top"><span className={`status-chip status-${initiative.status.toLowerCase()}`}>{localizedStatus(language, initiative.status)}</span><span>{initiative.role === 'ORGANISER' ? t('Organiser') : t('Joined')}</span></div>
            <h3>{initiative.title}</h3>
            <p>{timestampLabel(initiative.startAt, language)} · {initiative.placeName}</p>
            {initiative.status === 'CANCELLED' && initiative.cancellationReason && <p><b>{t('Cancellation reason')}:</b> {initiative.cancellationReason}</p>}
            {initiative.canManage && initiative.status === 'PUBLISHED' && <div className="initiative-manage">
              <label htmlFor={`cancel-reason-${initiative.initiativeId}`}>{t('Cancellation reason')}
                <input id={`cancel-reason-${initiative.initiativeId}`} maxLength={300} value={cancellationReasons[initiative.initiativeId] ?? ''} onChange={(event) => setCancellationReasons((values) => ({ ...values, [initiative.initiativeId]: event.target.value }))} placeholder={t('Required only when cancelling')} />
              </label>
              <div className="draft-actions">
                <button className="secondary" disabled={!cancellationReasons[initiative.initiativeId]?.trim() || initiative.codeAttendanceCount > 0} title={initiative.codeAttendanceCount > 0 ? t('Cancellation is unavailable after code attendance is recorded') : undefined} onClick={() => requestLinkedMutation(() => changeInitiativeStatus(initiative.initiativeId, 'CANCELLED').catch((error) => setInitiativeStatus(error.message)))}>{t('Cancel activity')}</button>
                <button disabled={Date.now() < Date.parse(initiative.startAt)} title={Date.now() < Date.parse(initiative.startAt) ? t('Available after the scheduled activity time') : undefined} onClick={() => requestLinkedMutation(() => changeInitiativeStatus(initiative.initiativeId, 'COMPLETED').catch((error) => setInitiativeStatus(error.message)))}>{t('Mark completed')}</button>
              </div>
            </div>}
            <div className="attendance-summary" aria-label={t('Attendance summary')}>
              <p><strong>{initiative.codeAttendanceCount} {t('of')} {initiative.joinerCount}</strong> {t('joiners recorded attendance using the organiser’s code.')}</p>
              <p><strong>{initiative.selfAttendanceCount} {t('of')} {initiative.joinerCount}</strong> {t('joiners reported attending.')}</p>
              <small>{t('The organiser is not included in the joiner count. Neither attendance method is independently verified.')}</small>
            </div>
            {initiative.attendanceBasis === 'ORGANISER_CODE_ATTESTED' && <div className="attendance-result state-success" role="status"><b>{t('Attendance recorded using the organiser’s code')}</b><span>{t('You earned 20 points. This is organiser-mediated attendance, not independent verification.')}</span></div>}
            {initiative.attendanceBasis === 'SELF_ATTESTED' && <div className="attendance-result state-success" role="status"><b>{t('You reported attending')}</b><span>{t('Self-attendance earns zero points and is not verified.')}</span></div>}
            {initiative.canViewAttendanceCode && <div className="attendance-code-panel">
              <div><b>{t('Organiser attendance code')}</b><span>{t('Share this six-digit code only with joined participants who attended.')}</span></div>
              {activeAttendanceCodes[initiative.initiativeId]
                ? <><output aria-label={t('Current organiser attendance code')} className="attendance-code">{activeAttendanceCodes[initiative.initiativeId].code}</output><small>{t('Rotates at')} {timestampLabel(activeAttendanceCodes[initiative.initiativeId].rotatesAt, language)} · {t('Code window closes')} {timestampLabel(activeAttendanceCodes[initiative.initiativeId].codeWindowEndsAt, language)}</small><button className="secondary" onClick={() => requestLinkedMutation(() => loadAttendanceCode(initiative.initiativeId).catch((error) => setInitiativeStatus(error.message)))}>{t('Refresh code')}</button></>
                : <button onClick={() => requestLinkedMutation(() => loadAttendanceCode(initiative.initiativeId).catch((error) => setInitiativeStatus(error.message)))}>{t('Show attendance code')}</button>}
            </div>}
            {initiative.canUseOrganiserCode && !initiative.attendanceBasis && <div className="attendance-entry-panel">
              <label htmlFor={`attendance-code-${initiative.initiativeId}`}>{t('Enter organiser attendance code')}
                <input id={`attendance-code-${initiative.initiativeId}`} inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]*" maxLength={6} value={attendanceCodeInputs[initiative.initiativeId] ?? ''} onChange={(event) => setAttendanceCodeInputs((values) => ({ ...values, [initiative.initiativeId]: event.target.value.replace(/\D/g, '').slice(0, 6) }))} />
              </label>
              <button disabled={!/^\d{6}$/.test(attendanceCodeInputs[initiative.initiativeId] ?? '')} onClick={() => requestLinkedMutation(() => submitAttendanceCode(initiative.initiativeId).catch((error) => setInitiativeStatus(error.message)))}>{t('Record code attendance · 20 points')}</button>
              <small>{t('The code rotates every 10 minutes. Five incorrect attempts are allowed per code period.')}</small>
            </div>}
            {initiative.canSelfAttend && !initiative.attendanceBasis && <div className="attendance-entry-panel">
              <p>{t('The organiser-code window has closed. You can report your own attendance for seven days after completion.')}</p>
              <button className="secondary" onClick={() => requestLinkedMutation(() => recordSelfAttendance(initiative.initiativeId).catch((error) => setInitiativeStatus(error.message)))}>{t('I attended · 0 points')}</button>
              <small>{t('This is a self-report. Seewik does not verify it.')}</small>
            </div>}
            {initiative.role === 'PARTICIPANT' && !initiative.attendanceBasis && !initiative.canUseOrganiserCode && !initiative.canSelfAttend && <small>{initiative.status === 'CANCELLED' ? t('Attendance is unavailable because this activity was cancelled.') : initiative.status === 'COMPLETED' ? t('No attendance option is currently available.') : t('Code attendance opens at the scheduled start time.')}</small>}
            <small>{t('Creating or joining alone does not earn points.')}</small>
          </article>)}
        </div>
      </section>
      <section className="initiative-toolbar card">
        <div><h2>{t('Nearby activities')}</h2><p>{t('Distance is calculated from the location you choose to share. Your coordinates are used for this request and are not shown to other citizens.')}</p></div>
        <button onClick={() => navigate('new-initiative')}>{t('Create an activity')}</button>
        <label>{t('Search radius')}<select value={initiativeRadiusKm} onChange={(event) => setInitiativeRadiusKm(Number(event.target.value))}><option value={2}>2 km</option><option value={5}>5 km</option><option value={10}>10 km</option><option value={25}>25 km</option></select></label>
        <button className="secondary" onClick={() => initiativeCoordinates ? discoverInitiatives(false).catch((error) => setInitiativeStatus(error.message)) : locateForInitiatives('DISCOVER')}>{initiativeCoordinates ? t('Refresh nearby') : t('Use my location')}</button>
        {initiativeStatus && <div className="status-panel state-warning" role="status" aria-live="polite">{runtimeMessage(initiativeStatus)}</div>}
      </section>
      <section className="initiative-list" aria-live="polite">
        {initiatives.map((initiative) => <article className="card initiative-card" key={initiative.initiativeId}>
          <div className="initiative-card-top"><span className="status-chip">{initiativeCategoryLabel(initiative.category)}</span><span className="live-count"><i aria-hidden="true" />{localizedStatus(language, initiative.status)} · {initiative.participantCount} {initiative.participantCount === 1 ? t('person') : t('people')}</span></div>
          <h2>{initiative.title}</h2><p>{initiative.description}</p>
          <dl><div><dt>{t('When')}</dt><dd>{timestampLabel(initiative.startAt, language)}</dd></div><div><dt>{t('Where')}</dt><dd>{initiative.placeName}</dd></div><div><dt>{t('Distance')}</dt><dd>{initiative.distanceKm.toFixed(2)} km</dd></div><div><dt>{t('What is needed')}</dt><dd>{initiative.needs || t('Just bring yourself')}</dd></div></dl>
          <button disabled={initiative.joined} onClick={() => requestLinkedMutation(() => joinInitiative(initiative.initiativeId).catch((error) => setInitiativeStatus(error.message)))}>{initiative.joined ? t('Joined') : t('Join this activity')}</button>
          <small>{t('Creating or joining alone earns no points. Joined participants can record attendance during the organiser-code window.')}</small>
        </article>)}
        {!initiatives.length && initiativeCoordinates && !initiativeStatus.startsWith('Finding') && <div className="card empty-state"><b>{t('No nearby activity is listed yet.')}</b><p>{t('You can create the first one without inventing an impact claim.')}</p><button onClick={() => navigate('new-initiative')}>{t('Create an activity')}</button></div>}
      </section>
    </>}

    {screen === 'new-initiative' && <section className="card page-card initiative-form">
      <span className="eyebrow">{t('CREATE AN ACTIVITY')}</span><h2>{t('Start something useful nearby')}</h2><p>{t('Publish the real date, public meeting place and what neighbours should bring. Seewik does not claim participation or impact until it happens.')}</p>
      <label>{t('Activity type')}<select value={initiativeCategory} onChange={(event) => setInitiativeCategory(event.target.value)}><option value="CLEANUP">{t('Neighbourhood clean-up')}</option><option value="PLANTATION">{t('Plantation')}</option><option value="DONATION">{t('Donation activity')}</option><option value="COMMUNITY_FITNESS">{t('Community fitness')}</option><option value="OTHER_CIVIC_ACTIVITY">{t('Other civic activity')}</option></select></label>
      <label>{t('Title')}<input maxLength={100} value={initiativeTitle} onChange={(event) => setInitiativeTitle(event.target.value)} /></label>
      <label>{t('Description')}<textarea maxLength={1200} value={initiativeDescription} onChange={(event) => setInitiativeDescription(event.target.value)} /></label>
      <label>{t('Date and time')}<input type="datetime-local" value={initiativeStartAt} onChange={(event) => setInitiativeStartAt(event.target.value)} /></label>
      <label>{t('Public meeting place')}<input maxLength={200} value={initiativePlaceName} onChange={(event) => setInitiativePlaceName(event.target.value)} /></label>
      <label>{t('Supplies or volunteers needed (optional)')}<textarea maxLength={500} value={initiativeNeeds} onChange={(event) => setInitiativeNeeds(event.target.value)} /></label>
      <button className="secondary" onClick={() => locateForInitiatives('CREATE')}>{initiativeCoordinates ? t('Activity location captured') : t('Use my location for discovery')}</button>
      <small>{t('Coordinates support nearby discovery. Other citizens see the public place name and distance, not your raw coordinates.')}</small>
      <div className="draft-actions"><button className="secondary" onClick={() => navigate('initiatives')}>{t('Cancel')}</button><button disabled={!initiativeCoordinates || !initiativeTitle.trim() || !initiativeDescription.trim() || !initiativePlaceName.trim() || !initiativeStartAt} onClick={() => requestLinkedMutation(() => createInitiative().catch((error) => setInitiativeStatus(error.message)))}>{t('Publish activity')}</button></div>
      {initiativeStatus && <div className="status-panel state-warning" role="status" aria-live="polite">{runtimeMessage(initiativeStatus)}</div>}
    </section>}

    {screen === 'new-report' && <>
    <section className="hero page-hero"><span className="eyebrow">{t('NEW REPORT')}</span><h1>{t('Find the right civic route')}</h1><p>{t('Automatic classification may suggest a category. You confirm it, and Civic Pack determines the route.')}</p></section>
    <section className="card report-flow-card">
      <div className="signal" aria-hidden="true" /><h2>{t('Find the civic route')}</h2>
      <p>{t('Start with a photo or short description. Automatic classification may suggest an issue category, but you confirm it. Authority and department always come from Civic Pack v0.2.')}</p>
      <div className="flow-step"><span>1</span><b>{t('Describe the issue')}</b></div>
      <label>{t('Photo (optional)')}<input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => {
        setEvidenceImage(event.target.files?.[0] ?? null);
        resetEvidenceDerivedState();
      }} /></label>
      <label>{t('Short description (optional)')}<textarea maxLength={2000} value={evidenceText} placeholder="उदा. रस्त्यावर मोठा खड्डा आहे" onChange={(event) => {
        setEvidenceText(event.target.value);
        resetEvidenceDerivedState();
      }} /></label>
      <button className="secondary" onClick={() => classifyEvidence().catch(() => {
        setClassificationStatus('The category could not be checked. Choose it manually below.');
        setClassificationSource('CITIZEN_SELECTED');
      })}>{t('Suggest issue category')}</button>
      {classificationStatus && <div role="status" aria-live="polite" className={`status-panel ${classification?.status === 'CLASSIFICATION_ERROR' ? 'state-error' : classification?.status === 'CLASSIFIED' ? 'state-success' : 'state-warning'}`}>
        <strong>{classification?.status === 'CLASSIFIED' ? t('Category suggestion ready') : classification?.status === 'CLARIFICATION_REQUIRED' ? t('Please clarify') : t('Category confirmation')}</strong>
        <span>{runtimeMessage(classificationStatus)}</span>
        {classification?.description && <small>{classification.description}</small>}
        {classification?.detectedLanguage && <small>{t('Detected language')}: {classification.detectedLanguage}</small>}
      </div>}
      <label>{t('Issue category')}<select value={issueType} onChange={(event) => chooseIssueType(event.target.value)}>{ISSUE_TYPES.map(([value]) => <option key={value} value={value}>{issueLabel(value, language)}</option>)}</select></label>
      <button onClick={confirmIssueType}>{classificationConfirmed ? t('Category confirmed') : t('Confirm this category')}</button>
      {classificationConfirmed && <div className="confirmed-line" role="status" aria-live="polite">✓ {issueLabel(issueType, language)} · {classificationSourceLabel(classificationSource)}</div>}

      <div className="flow-step"><span>2</span><b>{t('Confirm your prabhag')}</b></div>
      <p>{t('Location can suggest a prabhag using development boundaries. The suggestion is not an official boundary determination and must be confirmed. Manual selection always overrides it.')}</p>
      <button className="secondary" onClick={useMyLocation}>{t('Suggest from my location')}</button>
      {locationStatus && <div role="status" aria-live="polite" className={`status-panel ${resolution?.status === 'CANDIDATE_PRABHAG' ? 'state-success' : resolution?.status === 'OUTSIDE_SUPPORTED_AREA' || resolution?.status === 'RESOLUTION_UNAVAILABLE' || resolution?.status === 'INVALID_COORDINATES' ? 'state-error' : 'state-warning'}`}>{runtimeMessage(locationStatus)}</div>}
      {resolution?.status === 'CANDIDATE_PRABHAG' && resolution.prabhagId && <div className="candidate"><strong>{resolution.prabhagName}</strong><span>{resolution.resolutionQuality} · {resolution.datasetVersion}</span><span>{resolution.resolutionMethod}: {resolution.queryLatencyMs} ms</span>{resolution.fallbackReason && <small>{resolution.fallbackReason}</small>}<button onClick={confirmCandidate}>{t('Confirm this suggested prabhag')}</button></div>}
      <div className="boundary-selection-layout">
        <div className="boundary-manual-choice">
          <h3>{t('Choose manually')}</h3>
          <p>{t('The list is a complete non-map option. Choosing here overrides any automatic suggestion.')}</p>
          <label>{t('Prabhag number')}<select value={prabhagId} onChange={(event) => selectManualPrabhag(event.target.value)}>
            <option value="" disabled>{t('Choose Prabhag 1–20')}</option>
            {PRABHAGS.map((value, index) => <option key={value} value={value}>{t('Prabhag')} {index + 1}</option>)}
          </select></label>
          {prabhagSelectionMade && <div className="confirmed-line" role="status" aria-live="polite">✓ {t('Prabhag')} {Number(prabhagId.slice(-2))} · {manualPrabhagSelected ? t('Selected manually') : t('Confirmed selection')}</div>}
        </div>
        <BoundaryMapErrorBoundary fallback={<div className="status-panel state-warning" role="status">{t('The boundary guide is unavailable. Choose Prabhag 1–20 manually.')}</div>}>
          <Suspense fallback={<div className="status-panel state-warning" role="status">{t('Loading approximate boundary guide…')}</div>}>
            <LazyPrabhagBoundaryMap
              language={language}
              highlightedPrabhagId={highlightedPrabhagId}
              selectionKind={boundarySelectionKind}
              currentPosition={currentCoordinates}
              onManualSelect={selectManualPrabhag}
            />
          </Suspense>
        </BoundaryMapErrorBoundary>
      </div>
      <div className="flow-step"><span>3</span><b>{t('Get the deterministic route')}</b></div>
      <button disabled={!classificationConfirmed || !prabhagSelectionMade} onClick={() => findCivicRoute().catch((error) => setRouteResult({ status: `Routing failed: ${error.message}` }))}>{t('Find official route')}</button>
      {routeResult && <div aria-live="polite" className={`route-result ${routeResult.status === 'SUPPORTED_ROUTE' ? 'state-success' : 'state-error'}`}>
        <strong>{routeResult.status === 'SUPPORTED_ROUTE' ? routeResult.authority : routeResult.status === 'CATEGORY_CONFIRMATION_REQUIRED' ? t('Confirm the issue category first') : routeResult.status === 'PRABHAG_CONFIRMATION_REQUIRED' ? t('Choose and confirm a prabhag first') : routeResult.status}</strong>
        {routeResult.routeId && <>
          {routeResult.department && <div className="department-result">
            <b>{routeResult.department.status === 'TYPICAL_STRUCTURE_UNVERIFIED' ? t('Likely department') : t('Department')}: {routeResult.department.displayName}</b>
            <span>{routeResult.department.localName}</span>
            <span>{routeResult.department.basis}</span>
            <span>{routeResult.department.status}</span>
          </div>}
          <span>{routeResult.routeId}</span>
          <span>{routeResult.prabhagId} · {routeResult.resolutionMethod}</span>
          <span>{routeResult.sourceStatus} · {routeResult.reviewStatus} · {routeResult.packVersion}</span>
          {(routeResult.knownLimitations?.length ?? 0) > 0 && <div className="route-limitations">
            <b>{t('Please check before filing')}</b>
            <ul>{routeResult.knownLimitations?.map((limitation) => <li key={limitation.code}>{limitation.citizenMessage}</li>)}</ul>
          </div>}
          {(routeResult.officialChannels?.length ?? 0) > 0 && <div className="route-channels">
            <b>{t('Official contact options')}</b>
            <ul>{routeResult.officialChannels?.map((channel) => <li key={channel.channelId}>
              {channel.type === 'EMAIL' && <a href={`mailto:${channel.value}`}>{channel.label}</a>}
              {channel.type === 'ONLINE_FORM' && <a href={channel.value} target="_blank" rel="noreferrer">{channel.label}</a>}
              {channel.type === 'IN_PERSON' && <span>{channel.label}: {channel.value}</span>}
              <small>{channel.scopeNote}</small>
            </li>)}</ul>
          </div>}
          {(routeResult.informationalLinks?.length ?? 0) > 0 && <div className="informational-links">
            <b>{t('Information only — not a verified filing channel')}</b>
            <ul>{routeResult.informationalLinks?.map((link) => <li key={link.linkId}><a href={link.value} target="_blank" rel="noreferrer">{link.label}</a><small>{link.scopeNote}</small></li>)}</ul>
          </div>}
        </>}
      </div>}
      {routeResult?.status === 'SUPPORTED_ROUTE' && <>
        <div className="flow-step"><span>4</span><b>{t('Create and review the complaint draft')}</b></div>
        <p>{t('The recipient and route stay fixed from Civic Pack. Automatic drafting only prepares wording from the facts you confirm below.')}</p>
        <div className="locked-recipient">
          <small>{t('Locked recipient')}</small>
          <strong>{routeResult.authority}</strong>
          <span>{routeResult.routeId} · {routeResult.prabhagId}</span>
        </div>
        <label>{t('Confirmed complaint facts')}<textarea maxLength={2000} value={complaintFacts} onChange={(event) => {
          setComplaintFacts(event.target.value);
          resetDraft();
        }} /></label>
        <label>{t('Location or landmark (optional)')}<input type="text" maxLength={500} value={locationDetails} placeholder="उदा. बस स्थानकाजवळ" onChange={(event) => {
          setLocationDetails(event.target.value);
          resetDraft();
        }} /></label>
        <label>{t('Draft language')}<select value={draftLanguage} onChange={(event) => {
          setDraftLanguage(event.target.value as 'MR' | 'EN');
          resetDraft();
        }}><option value="MR">मराठी</option><option value="EN">English</option></select><small className="field-help">{t('Drafts are currently available in Marathi and English.')}</small></label>
        <button onClick={() => createComplaintDraft().catch((error) => setDraftStatus(`Drafting failed: ${error.message}`))}>{t('Create complaint draft')}</button>
        {draftStatus && <div role="status" aria-live="polite" className={`status-panel ${complaintDraft?.status === 'DRAFT_ERROR' || draftStatus.includes('could not') || draftStatus.includes('failed') ? 'state-error' : 'state-warning'}`}>{runtimeMessage(draftStatus)}</div>}
        {complaintDraft?.status === 'DRAFT_ERROR' && <div className="draft-panel">
          <div className="draft-heading"><div><small>{t('Confirmed recipient')}</small><strong>{routeResult.authority}</strong></div><span>{t('Manual fallback')}</span></div>
          <p>{t('Automatic drafting is unavailable. Write or edit your complaint below; the confirmed route above is unchanged.')}</p>
          <label>{t('Complaint body')}<textarea className="draft-body" maxLength={2500} value={manualComplaintBody} onChange={(event) => setManualComplaintBody(event.target.value)} /></label>
          <button className="secondary" onClick={() => copyManualComplaint().catch((error) => setDraftStatus(`Copy failed: ${error.message}`))}>{t('Copy manual complaint')}</button>
        </div>}
        {complaintDraft?.status === 'DRAFT_READY' && <div className="draft-panel">
          <div className="draft-heading"><div><small>{t('Recipient')}</small><strong>{complaintDraft.authorityLocalName || complaintDraft.authority}</strong></div><span>{complaintDraft.language} · {complaintDraft.draftVersion}</span></div>
          {(complaintDraft.missingDetails?.length ?? 0) > 0 && <div className="missing-facts"><b>{t('Missing fact')}</b><span>{t('Add a location or landmark before submitting if one is available. It was not invented in this draft.')}</span></div>}
          <label>{t('Subject')}<input type="text" maxLength={160} value={draftSubject} onChange={(event) => {
            setDraftSubject(event.target.value);
            setDraftReviewed(false);
          }} /></label>
          <label>{t('Complaint body')}<textarea className="draft-body" maxLength={2500} value={draftBody} onChange={(event) => {
            setDraftBody(event.target.value);
            setDraftReviewed(false);
          }} /></label>
          <label className="review-check"><input type="checkbox" checked={draftReviewed} onChange={(event) => setDraftReviewed(event.target.checked)} /><span>{t('I reviewed the facts, recipient and wording.')}</span></label>
          <div className="draft-actions">
            <button className="secondary" disabled={!draftDocumentId} onClick={() => requestLinkedMutation(() => saveDraftEdits().then(() => undefined).catch((error) => setDraftStatus(`Draft save failed: ${error.message}`)))}>{t('Save changes')}</button>
            <button disabled={!draftReviewed || !draftDocumentId} onClick={() => requestLinkedMutation(() => copyReviewedDraft().catch((error) => setDraftStatus(`Copy failed: ${error.message}`)))}>{t('Copy reviewed complaint')}</button>
          </div>
          <small>{t('No complaint is submitted automatically. The saved record remains a DRAFT owned by your recoverable profile.')}</small>
        </div>}
        {draftDocumentId && <div className="lifecycle-panel">
          <div className="lifecycle-heading">
            <div><small>{t('Report lifecycle')}</small><strong>{localizedStatus(language, reportStatus)}</strong></div>
            <div className="points-pill"><span>{t('Derived points')}</span><b>{pointsTotal}</b></div>
          </div>
          <p>{t('Confirm real-world actions here. Seewik records them but never files a complaint for you.')}</p>
          {reportStatus === 'DRAFT' && <>
            {(routeResult.officialChannels?.length ?? 0) > 0 && <label>{t('Channel you used')}<select value={filingChannelId} onChange={(event) => setFilingChannelId(event.target.value)}>
              <option value="">{t('Not recorded')}</option>
              {routeResult.officialChannels?.map((channel) => <option key={channel.channelId} value={channel.channelId}>{channel.label}</option>)}
            </select></label>}
            <label>{t('Acknowledgement / tracking ID (optional)')}<input maxLength={200} value={acknowledgementId} onChange={(event) => setAcknowledgementId(event.target.value)} /></label>
            <button onClick={() => requestLinkedMutation(() => transitionReport('FILED').catch((error) => setLifecycleStatus(error.message)))}>{t('I filed this complaint')}</button>
            {!currentCoordinates && <small>{t('Dedupe is not evaluated when location is unavailable.')}</small>}
          </>}
          {duplicateWarning && <div className="duplicate-warning">
            <b>{t('Possible duplicate')}</b>
            <span>A same-category report is {duplicateWarning.measuredDistanceMeters?.toFixed(1)} m away. The 75 m threshold is an MVP heuristic, not a civic boundary.</span>
            <button className="secondary" onClick={() => requestLinkedMutation(() => transitionReport('FILED', true).catch((error) => setLifecycleStatus(error.message)))}>{t('This is a different issue — file with 0 points')}</button>
          </div>}
          {['FILED', 'OVERDUE', 'REOPENED'].includes(reportStatus) && <>
            {reportStatus === 'FILED' && <div className="overdue-unknown"><b>{t('Overdue: unknown')}</b><span>{t('No verified SLA exists, so Seewik will not invent a due date.')}</span></div>}
            <button onClick={() => requestLinkedMutation(() => transitionReport('CLAIMED_FIXED').catch((error) => setLifecycleStatus(error.message)))}>{t('Record a repair claim')}</button>
          </>}
          {reportStatus === 'CLAIMED_FIXED' && <div className="lifecycle-actions">
            <button onClick={() => requestLinkedMutation(() => transitionReport('VERIFIED_FIXED').catch((error) => setLifecycleStatus(error.message)))}>{t('Verify fixed')}</button>
            <button className="secondary" onClick={() => requestLinkedMutation(() => transitionReport('REOPENED').catch((error) => setLifecycleStatus(error.message)))}>{t('Reject repair claim')}</button>
          </div>}
          {reportStatus === 'VERIFIED_FIXED' && <button className="secondary" onClick={() => requestLinkedMutation(() => transitionReport('REOPENED').catch((error) => setLifecycleStatus(error.message)))}>{t('Report recurrence')}</button>}
          {lifecycleStatus && <div role="status" aria-live="polite" className="status-panel state-warning">{runtimeMessage(lifecycleStatus)}</div>}
          <ol className="timeline">
            {timeline.map((item, index) => <li key={`${item.occurredAt}-${index}`}>
              <span>{index + 1}</span><div><b>{localizedStatus(language, item.toStatus)}</b><small>{item.eventType} · {item.verificationBasis}{item.pointsAwarded ? ` · +${item.pointsAwarded}` : ''}</small></div>
            </li>)}
          </ol>
        </div>}
      </>}
    </section>
    </>}

    {screen === 'review' && <section className="card page-card">
      <span className="eyebrow">{t('COMPLAINT REVIEW')}</span><h2>{t('Review your saved complaint')}</h2>
      {!draftDocumentId || complaintDraft?.status !== 'DRAFT_READY' ? <div className="empty-state"><b>{t('No draft is ready in this session.')}</b><p>{t('Create or open a saved draft before reviewing it.')}</p><button onClick={() => navigate('new-report')}>{t('Start a report')}</button></div> : <>
        <div className={`report-status-banner ${canEditReport(reportStatus) ? 'editable' : 'immutable'}`}><b>{localizedStatus(language, reportStatus)}</b><span>{canEditReport(reportStatus) ? t('Editable draft') : t('Filed report — facts and wording are frozen')}</span></div>
        <div className="locked-recipient"><small>{t('Locked recipient')}</small><strong>{complaintDraft.authorityLocalName || complaintDraft.authority}</strong><span>{complaintDraft.routeId} · {complaintDraft.prabhagId} · {complaintDraft.packVersion}</span></div>
        <label>{t('Subject')}<input type="text" maxLength={160} readOnly={!canEditReport(reportStatus)} value={draftSubject} onChange={(event) => { setDraftSubject(event.target.value); setDraftReviewed(false); }} /></label>
        <label>{t('Complaint body')}<textarea className="draft-body" maxLength={2500} readOnly={!canEditReport(reportStatus)} value={draftBody} onChange={(event) => { setDraftBody(event.target.value); setDraftReviewed(false); }} /></label>
        {canEditReport(reportStatus) ? <>
          <label className="review-check"><input type="checkbox" checked={draftReviewed} onChange={(event) => setDraftReviewed(event.target.checked)} /><span>{t('I reviewed the facts, recipient and wording.')}</span></label>
          <div className="draft-actions"><button className="secondary" onClick={() => requestLinkedMutation(() => saveDraftEdits().then(() => undefined).catch((error) => setDraftStatus(`Draft save failed: ${error.message}`)))}>{t('Save changes')}</button><button disabled={!draftReviewed} onClick={() => requestLinkedMutation(() => copyReviewedDraft().catch((error) => setDraftStatus(`Copy failed: ${error.message}`)))}>{t('Copy reviewed complaint')}</button></div>
          <div className="lifecycle-panel filing-panel"><div className="lifecycle-heading"><div><small>{t('Record real-world filing')}</small><strong>{t('DRAFT')}</strong></div><div className="points-pill"><span>{t('Possible reward')}</span><b>+5</b></div></div><p>{t('Seewik never submits the complaint. Use this only after you file it yourself.')}</p>
            {(routeResult?.officialChannels?.length ?? 0) > 0 && <label>{t('Channel you used')}<select value={filingChannelId} onChange={(event) => setFilingChannelId(event.target.value)}><option value="">{t('Not recorded')}</option>{routeResult?.officialChannels?.map((channel) => <option key={channel.channelId} value={channel.channelId}>{channel.label}</option>)}</select></label>}
            <label>{t('Acknowledgement / tracking ID (optional)')}<input maxLength={200} value={acknowledgementId} onChange={(event) => setAcknowledgementId(event.target.value)} /></label>
            <button disabled={!draftReviewed} onClick={() => requestLinkedMutation(() => fileReviewedReport().catch((error) => setLifecycleStatus(error.message)))}>{t('I filed this complaint')}</button>
            {!currentCoordinates && <small>{t('Dedupe is not evaluated when location is unavailable.')}</small>}
            {duplicateWarning && <div className="duplicate-warning"><b>{t('Possible duplicate')}</b><span>A same-category report is {duplicateWarning.measuredDistanceMeters?.toFixed(1)} m away.</span><button className="secondary" onClick={() => requestLinkedMutation(() => fileReviewedReport(true).catch((error) => setLifecycleStatus(error.message)))}>{t('This is a different issue — file with 0 points')}</button></div>}
            {lifecycleStatus && <div role="status" aria-live="polite" className="status-panel state-warning">{runtimeMessage(lifecycleStatus)}</div>}
          </div>
          <button className="text-action" onClick={() => navigate('new-report')}>{t('Return to report builder')}</button>
        </> : <><div className="status-panel state-warning"><strong>{t('This report cannot be resumed')}</strong><span>{t('Once filed, the complaint wording and frozen route facts cannot be edited.')}</span></div><button onClick={() => navigate('report-detail', false, draftDocumentId)}>{t('View report timeline')}</button></>}
        {draftStatus && <div role="status" aria-live="polite" className="status-panel state-warning">{runtimeMessage(draftStatus)}</div>}
      </>}
    </section>}

    {screen === 'reports' && <section className="card page-card">
      <span className="eyebrow">{t('MY REPORTS')}</span><h2>{t('Your saved civic work')}</h2><p>{t('Drafts can be resumed. Filed reports open as immutable records.')}</p>
      {reportsView === 'SIGNED_OUT' ? <div className="empty-state account-recovery-state">
        <b>{t('Sign in to view your saved civic work.')}</b>
        <p>{t("Signing out doesn't delete anything.")}</p>
        <p>{t('Your saved work stays attached to your Google account — sign in to see it.')}</p>
        <button onClick={openAccount}>{t('Continue with Google')}</button>
      </div> : <>
        <div className="reports-toolbar"><span role="status" aria-live="polite">{runtimeMessage(reportsStatus)}</span><button className="secondary" onClick={() => loadMyReports().catch((error) => setReportsStatus(`Reports could not be loaded: ${error.message}`))}>{t('Refresh')}</button></div>
        {reportsView === 'LINKED_EMPTY' && <div className="empty-state"><b>{t('Signed in. No saved reports yet.')}</b><p>{t('Create a report to save an owner-protected draft.')}</p><button onClick={() => navigate('new-report')}>{t('Create a report')}</button></div>}
        {reportsView === 'ANONYMOUS_EMPTY' && <div className="empty-state"><b>{t('No reports are saved for this anonymous account.')}</b><p>{t('Create a report to save an owner-protected draft.')}</p><button onClick={() => navigate('new-report')}>{t('Create a report')}</button></div>}
        {reportsView === 'HAS_REPORTS' && <div className="report-list">{savedReports.map((report) => <article className="report-list-item" key={report.id}><div><span className={`status-chip status-${report.status.toLowerCase()}`}>{localizedStatus(language, report.status)}</span><h3>{issueLabel(report.confirmedIssueType, language)}</h3><p>{report.prabhagId} · {t('Updated')} {timestampLabel(report.updatedAt, language)}</p><small>{report.id.slice(0, 12)}… · {report.packVersion}</small></div>{canResumeReport(report.status) ? <button onClick={() => resumeSavedReport(report).catch((error) => setReportsStatus(`Draft could not be resumed: ${error.message}`))}>{t('Resume draft')}</button> : <button onClick={() => openSavedReport(report).catch((error) => setReportsStatus(`Report could not be opened: ${error.message}`))}>{t('View report')}</button>}</article>)}</div>}
      </>}
    </section>}

    {screen === 'report-detail' && <section className="card page-card">
      <span className="eyebrow">{t('REPORT DETAILS')}</span><h2>{selectedReport ? issueLabel(selectedReport.confirmedIssueType, language) : t('Loading report')}</h2>
      {accountState === 'SIGNED_OUT' ? <div className="empty-state account-recovery-state"><b>{t('Sign in to view your saved civic work.')}</b><p>{t('Your saved work stays attached to your Google account — sign in to see it.')}</p><button onClick={openAccount}>{t('Continue with Google')}</button></div> : !selectedReport ? <div className="empty-state"><p>{runtimeMessage(reportsStatus) || t('Choose a report from My reports.')}</p><button onClick={() => navigate('reports')}>{t('Open my reports')}</button></div> : <>
        <div className="report-status-banner immutable"><b>{localizedStatus(language, reportStatus)}</b><span>{reportStatus === 'DRAFT' ? t('Open the review screen to edit this draft.') : t('Filed record — complaint and route facts are immutable')}</span></div>
        <dl className="report-facts"><div><dt>{t('Prabhag')}</dt><dd>{selectedReport.prabhagId}</dd></div><div><dt>{t('Route')}</dt><dd>{selectedReport.routeSnapshot?.routeId || selectedReport.routeId}</dd></div><div><dt>{t('Pack')}</dt><dd>{selectedReport.routeSnapshot?.packVersion || selectedReport.packVersion}</dd></div><div><dt>{t('Authority')}</dt><dd>{selectedReport.routeSnapshot?.authority || selectedReport.authority}</dd></div><div><dt>{t('Acknowledgement')}</dt><dd>{selectedReport.acknowledgementId || t('Not provided')}</dd></div><div><dt>{t('Updated')}</dt><dd>{timestampLabel(selectedReport.updatedAt, language)}</dd></div></dl>
        {selectedReport.routeSnapshot?.department && <div className="locked-recipient"><small>{t('Frozen route department')}</small><strong>{selectedReport.routeSnapshot.department.displayName}</strong><span>{selectedReport.routeSnapshot.department.status} · {selectedReport.routeSnapshot.sourceStatus} · {selectedReport.routeSnapshot.reviewStatus}</span></div>}
        {(selectedReport.routeSnapshot?.knownLimitations?.length ?? 0) > 0 && <div className="route-limitations"><b>{t('Please keep in mind')}</b><ul>{selectedReport.routeSnapshot?.knownLimitations?.map((limitation) => <li key={limitation.code}>{limitation.citizenMessage}</li>)}</ul></div>}
        <div className="points-summary"><span>{t('Derived points for this profile')}</span><b>{pointsTotal}</b></div>
        {reportStatus === 'DRAFT' && <button onClick={() => resumeSavedReport(selectedReport).catch((error) => setReportsStatus(error.message))}>{t('Resume draft')}</button>}
        {['FILED', 'OVERDUE', 'REOPENED'].includes(reportStatus) && <><div className="overdue-unknown"><b>{t('Overdue: unknown')}</b><span>{t('No verified SLA exists, so Seewik will not invent a due date.')}</span></div><button onClick={() => requestLinkedMutation(() => transitionReport('CLAIMED_FIXED').catch((error) => setLifecycleStatus(error.message)))}>{t('Record a repair claim')}</button></>}
        {reportStatus === 'CLAIMED_FIXED' && <div className="lifecycle-actions"><button onClick={() => requestLinkedMutation(() => transitionReport('VERIFIED_FIXED').catch((error) => setLifecycleStatus(error.message)))}>{t('Verify fixed')}</button><button className="secondary" onClick={() => requestLinkedMutation(() => transitionReport('REOPENED').catch((error) => setLifecycleStatus(error.message)))}>{t('Reject repair claim')}</button></div>}
        {reportStatus === 'VERIFIED_FIXED' && <button className="secondary" onClick={() => requestLinkedMutation(() => transitionReport('REOPENED').catch((error) => setLifecycleStatus(error.message)))}>{t('Report recurrence')}</button>}
        {lifecycleStatus && <div role="status" aria-live="polite" className="status-panel state-warning">{runtimeMessage(lifecycleStatus)}</div>}
        <ol className="timeline">{timeline.map((item, index) => <li key={`${item.occurredAt}-${index}`}><span>{index + 1}</span><div><b>{localizedStatus(language, item.toStatus)}</b><small>{item.eventType} · {item.verificationBasis}{item.pointsAwarded ? ` · +${item.pointsAwarded}` : ''}</small></div></li>)}</ol>
      </>}
    </section>}

    {screen === 'points' && <section className="card page-card points-page">
      <span className="eyebrow">{t('MY POINTS')}</span><h2>{pointsTotal} {t('derived points')}</h2><p>{t('Totals are calculated from immutable ledger entries rather than stored as an editable score.')}</p>
      <div className="points-rules"><div><b>+5</b><span>{t('First accepted filing')}</span></div><div><b>+20</b><span>{t('Organiser-code attendance')}</span></div><div><b>+40</b><span>{t('Completed organiser with two code attendees')}</span></div><div><b>+60</b><span>{t('First verified fix')}</span></div><div><b>0</b><span>{t('Self-attendance, duplicate override, reopening or re-verification')}</span></div></div>
      <button className="secondary" onClick={() => refreshDerivedPoints().catch(() => undefined)}>{t('Refresh my points')}</button>
    </section>}

    {screen === 'home' && <>
    <section className="card demo-card">
      <div className="demo-banner">{t('DEMO DATA · SYNTHETIC CLOCK · EXCLUDED FROM ANALYTICS AND REWARDS')}</div>
      <h2>{t('90-second lifecycle demo')}</h2>
      <p>{t('This local walkthrough demonstrates every state without creating a report or changing your real points.')}</p>
      <div className="demo-current"><small>{t('Simulated report state')}</small><strong>{localizedStatus(language, demoStates[demoStep][0])}</strong><span>{demoStates[demoStep][1]}</span></div>
      <ol className="timeline compact">
        {demoStates.slice(0, demoStep + 1).map(([state, description], index) => <li key={state}><span>{index + 1}</span><div><b>{localizedStatus(language, state)}</b><small>{description}</small></div></li>)}
      </ol>
      <div className="lifecycle-actions">
        <button disabled={demoStep === demoStates.length - 1} onClick={() => setDemoStep((step) => Math.min(step + 1, demoStates.length - 1))}>{t('Next simulated transition')}</button>
        <button className="secondary" disabled={demoStep === 0} onClick={() => setDemoStep(0)}>{t('Reset demo')}</button>
      </div>
    </section>
    <section className="card systems"><h2>{runtimeMessage(status)}</h2><p>{t('The secure service path remains available for technical validation.')}</p><button onClick={() => requestLinkedMutation(() => verifyFirebase().catch((error) => add(`Service check failed: ${error.message}`)))}>{t('Verify cloud services')}</button>{details.length > 0 && <ul>{details.map((detail, index) => <li key={`${index}-${detail}`}>{detail}</li>)}</ul>}</section>
    </>}
    <footer>{t('Built for local civic action')}</footer>
    <nav className="mobile-nav" aria-label={t('Mobile navigation')}>
      <button aria-current={navCurrent(screen === 'home')} className={screen === 'home' ? 'active' : ''} onClick={() => navigate('home')}><span aria-hidden="true">⌂</span>{t('Home')}</button>
      <button aria-current={navCurrent(screen === 'new-report' || screen === 'review')} className={screen === 'new-report' || screen === 'review' ? 'active' : ''} onClick={() => navigate('new-report')}><span aria-hidden="true">＋</span>{t('Report')}</button>
      <button aria-current={navCurrent(screen === 'initiatives' || screen === 'new-initiative')} className={screen === 'initiatives' || screen === 'new-initiative' ? 'active' : ''} onClick={() => navigate('initiatives')}><span aria-hidden="true">◎</span>{t('Initiate')}</button>
      <button aria-current={navCurrent(screen === 'reports' || screen === 'report-detail')} className={screen === 'reports' || screen === 'report-detail' ? 'active' : ''} onClick={() => navigate('reports')}><span aria-hidden="true">≡</span>{t('Reports')}</button>
      <button aria-current={navCurrent(screen === 'points')} className={screen === 'points' ? 'active' : ''} onClick={() => navigate('points')}><span aria-hidden="true">◆</span>{t('Points')}</button>
    </nav>
  </main>
  </>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'));
