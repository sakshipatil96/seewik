import React, { useEffect, useRef, useState } from 'react';
import ReactDOM from 'react-dom/client';
import type { AuthCredential } from 'firebase/auth';
import { collection, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc, where } from 'firebase/firestore';
import { db } from './firebase';
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
  syncPrivateProfile,
} from './accountService';
import { accountErrorMessage, isCredentialCollisionCode, reportsViewState, safeAccountErrorCode, type AccountIdentityState } from './accountIdentity';
import { API_URL } from './apiConfig';
import { LANGUAGE_STORAGE_KEY, classificationSuggestionMessage, formatDateTime, initialLanguage, localizedMonthLabel, localizedRuntimeMessage, localizedStatus, translate, type InterfaceLanguage } from './i18n';
import { RecognitionPanel } from './RecognitionPanel';
import { RecognitionSettings } from './RecognitionSettings';
import { ContributionPoster } from './ContributionPoster';
import { CivicAwarenessPage } from './CivicAwarenessPage';
import { EmergencyInformationPage } from './EmergencyInformationPage';
import { RewardCatalogue } from './RewardCatalogue';
import { AppIcon, type AppIconName } from './AppIcon';
import { TemplatePicker } from './TemplatePicker';
import { extractPhotoCoordinates } from './photoLocation';
import InitiativeMeetingPointPicker, { type MeetingPointPosition } from './InitiativeMeetingPointPicker';
import PrabhagBoundaryMap from './PrabhagBoundaryMap';
import GoogleMeetingPointSearch, { type GoogleMeetingPointSelection } from './GoogleMeetingPointSearch';
import { reverseGeocodeGoogleLocation } from './googleMapsPlaces';
import { PRABHAG_BOUNDS } from './prabhagBoundaryData';
import {
  claimReward,
  fetchCurrentRecognition,
  fetchPrivatePoints,
  fetchRewards,
  fetchRecognitionSettings,
  reportRecognitionName,
  saveRecognitionSettings,
  simulateRewardUse,
  type PrivatePointsSummary,
  type PublicRecognitionPanel,
  type RewardOverview,
  type RecognitionSettings as RecognitionSettingsState,
} from './recognitionClient';
import { canEditReport, canResumeReport, draftRouteIsCurrent, initiativeIdFromPath, pathForScreen, reportIdFromPath, reportIdFromReviewSearch, screenFromPath, type AppScreen } from './reportNavigation';
import { citizenSafeError } from './uiErrors';
import { createFilingActionReceipt, filingActionReceiptMatches, readFilingActionReceipt, removeFilingActionReceipt, writeFilingActionReceipt, type FilingDraftIdentity, type FilingMethod } from './filingActionReceipt';
import { readFilingContactDraft, removeFilingContactDraft, writeFilingContactDraft } from './filingContactDraft';
import { routeSnapshotHashAfterTransition } from './reportRouteSnapshot';
import { automaticEscalationLanguageTransition } from './escalationDraftLanguage';
import './styles.css';

const DEBUG_MODE = new URLSearchParams(window.location.search).get('debug') === '1';
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

const INITIATIVE_TEMPLATES = [
  { value: 'PLANTATION_DRIVE', label: 'Plantation', example: 'Planting and caring for saplings', title: 'Community plantation drive', description: 'Bring neighbours together to plant suitable saplings and plan how they will be cared for after the activity.', neededItems: ['Saplings', 'Gloves', 'Water bottles'] },
  { value: 'DONATION', label: 'Food donation', example: 'Meals or ration for those in need', title: 'Community food donation', description: 'Collect and distribute meals or ration responsibly to people who need them.', neededItems: ['Food packets', 'Volunteers'] },
  { value: 'BIRTHDAY_DONATION', label: 'Birthday giving', example: 'Mark a birthday by giving', title: 'Birthday donation drive', description: 'Celebrate a birthday by collecting useful items and donating them responsibly to people who need them.', neededItems: ['Donation items', 'Volunteers'] },
  { value: 'HEALTH_ACTIVITY', label: 'Health activity', example: 'A responsible community health activity', title: 'Community health activity', description: 'Organise a responsible community health activity with clear participation details and appropriate support.', neededItems: ['Qualified volunteers'] },
  { value: 'BOOK_SUPPLY_DRIVE', label: 'Book or school-supply drive', example: 'Collect and hand over useful learning supplies', title: 'Book and school-supply drive', description: 'Collect useful books and school supplies and arrange a responsible handover.', neededItems: ['Books', 'School supplies', 'Volunteers'] },
  { value: 'CLEANUP', label: 'Community cleanup', example: 'Clean a colony, society, open area or park', title: 'Community cleanup', description: 'Bring neighbours together to clean a shared public or community space responsibly.', neededItems: ['Gloves', 'Waste bags', 'Volunteers'] },
  { value: 'AWARENESS_SESSION', label: 'Awareness session', example: 'Share practical civic, health or safety knowledge', title: 'Community awareness session', description: 'Host a practical awareness session for neighbours on a useful civic, health, safety, or environmental topic.', neededItems: ['Session materials'] },
  { value: 'COMMUNITY_YOGA', label: 'Community yoga', example: 'An inclusive yoga session for neighbours', title: 'Community yoga session', description: 'Organise an inclusive community yoga session at a safe public meeting place.', neededItems: ['Yoga mat', 'Water bottle'] },
  { value: 'MEDITATION_WORKSHOP', label: 'Meditation workshop', example: 'A guided community meditation session', title: 'Community meditation workshop', description: 'Organise a guided meditation workshop for neighbours in a calm and accessible public setting.', neededItems: ['Floor mat'] },
  { value: 'OTHER_CIVIC_ACTIVITY', label: 'Other', example: 'Tell people what it is', title: '', description: '', neededItems: [] },
] as const;

const issueTypeIcon = (issueType: string): AppIconName => ({
  GARBAGE_SOLID_WASTE: 'trash', ILLEGAL_DUMPING: 'trash', PUBLIC_AREA_CLEANLINESS: 'bench',
  POTHOLE_ROAD_DAMAGE: 'road', STREETLIGHT: 'bulb', DRAINAGE_SEWAGE: 'drop', WATER_SUPPLY: 'tap',
  PUBLIC_TOILET_SANITATION: 'bench', MOSQUITO_FOGGING: 'dots', DEAD_ANIMAL_REMOVAL: 'dots',
  PUBLIC_ROAD_OBSTRUCTION: 'road',
}[issueType] as AppIconName | undefined) ?? 'dots';

const initiativeTypeIcon = (category: string): AppIconName => ({
  PLANTATION: 'leaf', PLANTATION_DRIVE: 'leaf', DONATION: 'food', BIRTHDAY_DONATION: 'gift',
  HEALTH_ACTIVITY: 'dumbbell', COMMUNITY_FITNESS: 'dumbbell', COMMUNITY_YOGA: 'dumbbell',
  BOOK_SUPPLY_DRIVE: 'book', CLEANUP: 'broom', AWARENESS_SESSION: 'info',
  MEDITATION_WORKSHOP: 'info', OTHER_CIVIC_ACTIVITY: 'dots',
}[category] as AppIconName | undefined) ?? 'dots';

const officialChannelIcon = (type: OfficialChannel['type']): AppIconName => type === 'EMAIL' ? 'mail' : type === 'ONLINE_FORM' ? 'form' : 'building';

const PRIVATE_POINTS_CACHE_PREFIX = 'seewik.private-points.';
const COMMUNITY_CACHE_PREFIX = 'seewik.community-feed.';
const NANDURBAR_COMMUNITY_ORIGIN = { latitude: 21.3707, longitude: 74.2403 };
const NANDURBAR_COMMUNITY_RADIUS_KM = 25;

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
  localizedValues?: Partial<Record<'MR' | 'EN', string>>;
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
  authorityLocalName?: string;
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

type FilingDraftSnapshot = {
  result: ComplaintDraftResult;
  subject: string;
  body: string;
};

type LifecycleResponse = {
  status: 'TRANSITION_RECORDED' | 'POSSIBLE_DUPLICATE';
  eventId?: string;
  reportId: string;
  fromStatus: string;
  toStatus: string;
  eventType?: string;
  verificationBasis: string;
  schemaVersion?: string;
  packVersion?: string;
  occurredAt: string;
  idempotentReplay?: boolean;
  dedupeDisposition?: string;
  measuredDistanceMeters?: number;
  pointsAwarded: number;
  pointsWeight: number;
  routeSnapshotHash?: string;
  analyticsOutboxId?: string;
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
  routeSnapshotHash?: string;
};

type FollowUpEvent = {
  eventId: string;
  action: string;
  cycleNumber: number;
  recurrence: boolean;
  channelId?: string;
  language?: 'MR' | 'EN';
  occurredAt: string;
  nextPromptAt?: string;
  pointsAwarded: number;
};

type FollowUpSummary = {
  status: string;
  reportId: string;
  state: 'NOT_DUE' | 'DUE' | 'SNOOZED' | 'UNRESOLVED' | 'CLOSED' | 'UNAVAILABLE';
  promptDue: boolean;
  escalationAvailable: boolean;
  cycleNumber: number;
  recurrence: boolean;
  anchorAt?: string;
  followUpDueAt?: string;
  nextPromptAt?: string;
  routeId?: string;
  packVersion?: string;
  routeSnapshotHash?: string;
  events: FollowUpEvent[];
  schemaVersion: string;
};

type EscalationChannelId = 'NMC_FOLLOW_UP' | 'DISTRICT_JOINT_COMMISSIONER' | 'DMA_DESK_6';

const ESCALATION_CHANNELS: ReadonlyArray<{
  id: EscalationChannelId;
  title: string;
  description: string;
  recipient: string;
  sourceUrl?: string;
}> = [
  { id: 'NMC_FOLLOW_UP', title: 'Follow up with Nagar Palika', description: 'Ask the original municipal office for an update.', recipient: '' },
  { id: 'DISTRICT_JOINT_COMMISSIONER', title: 'District Joint Commissioner Office', description: 'Escalate the unresolved municipal complaint at district level.', recipient: 'dponppcollndb@gmail.com', sourceUrl: 'https://mahadma.maharashtra.gov.in/en/regional-offices-contact-information/page/5/?dma_tab=ulb&lang=en&rom_page=2' },
  { id: 'DMA_DESK_6', title: 'DMA Desk 6', description: 'Escalate municipal local issues and complaints to the state directorate desk.', recipient: 'desk6.dma@maharashtra.gov.in', sourceUrl: 'https://mahadma.maharashtra.gov.in/en/desk-structure-2/' },
];

type Initiative = {
  initiativeId: string;
  title: string;
  category: string;
  description: string;
  publicOrganiserName: string;
  startAt: string;
  endAt: string;
  placeName: string;
  mapsUrl?: string;
  meetingPointSchemaVersion?: string;
  legacyMeetingPoint?: boolean;
  needs: string;
  neededItems: string[];
  organiserMessage: string;
  capacity: number | null;
  participationMode: 'OPEN' | 'CAPPED' | 'APPROVAL_REQUIRED';
  status: string;
  cancellationReason: string;
  participantCount: number | null;
  distanceKm: number;
  joined: boolean;
  joinRequestedByMe: boolean;
  role: string;
  canManage: boolean;
  joinerCount: number | null;
  joiningCountVisible: boolean;
  full: boolean;
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

type InitiativeJoinRequest = {
  requestId: string;
  requestedAt: string;
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
    routeSnapshotHash: data.routeSnapshotHash ? String(data.routeSnapshotHash) : undefined,
  };
}

function App() {
  const [language, setLanguage] = useState<InterfaceLanguage>(() => initialLanguage(
    window.localStorage.getItem(LANGUAGE_STORAGE_KEY),
    navigator.languages?.length ? navigator.languages : [navigator.language],
  ));
  const [screen, setScreen] = useState<AppScreen>(() => screenFromPath(window.location.pathname));
  const [locationKey, setLocationKey] = useState(() => `${window.location.pathname}${window.location.search}`);
  const [issueType, setIssueType] = useState<(typeof ISSUE_TYPES)[number][0] | ''>('');
  const [prabhagId, setPrabhagId] = useState('');
  const [routeResult, setRouteResult] = useState<RouteResult | null>(null);
  const [resolution, setResolution] = useState<PrabhagResolution | null>(null);
  const [selectionMethod, setSelectionMethod] = useState('SELF_REPORTED');
  const [citizenConfirmed, setCitizenConfirmed] = useState(false);
  const [manualPrabhagSelected, setManualPrabhagSelected] = useState(false);
  const [reportLocationSource, setReportLocationSource] = useState<'PHOTO' | 'DEVICE' | 'GOOGLE' | 'MANUAL' | ''>('');
  const [boundaryDatasetVersion, setBoundaryDatasetVersion] = useState<string | undefined>();
  const [evidenceText, setEvidenceText] = useState('');
  const [evidenceImage, setEvidenceImage] = useState<File | null>(null);
  const [evidencePreviewUrl, setEvidencePreviewUrl] = useState('');
  const [classification, setClassification] = useState<ClassificationResult | null>(null);
  const [classificationStatus, setClassificationStatus] = useState('');
  const [classificationSource, setClassificationSource] = useState('SELF_REPORTED');
  const [complaintFacts, setComplaintFacts] = useState('');
  const [locationDetails, setLocationDetails] = useState('');
  const [draftLanguage, setDraftLanguage] = useState<'MR' | 'EN'>(() => language === 'en' ? 'EN' : 'MR');
  const [complaintLanguageManuallySelected, setComplaintLanguageManuallySelected] = useState(false);
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
  const [savedReports, setSavedReports] = useState<SavedReport[]>([]);
  const [followUpsByReport, setFollowUpsByReport] = useState<Record<string, FollowUpSummary>>({});
  const [followUpStatus, setFollowUpStatus] = useState('');
  const [followUpBusy, setFollowUpBusy] = useState(false);
  const [selectedEscalationChannel, setSelectedEscalationChannel] = useState<EscalationChannelId | ''>('');
  const [escalationLanguage, setEscalationLanguage] = useState<'MR' | 'EN'>(() => language === 'en' ? 'EN' : 'MR');
  const [escalationLanguageManuallySelected, setEscalationLanguageManuallySelected] = useState(false);
  const [escalationSubject, setEscalationSubject] = useState('');
  const [escalationBody, setEscalationBody] = useState('');
  const [escalationActionOpened, setEscalationActionOpened] = useState(false);
  const [reportsStatus, setReportsStatus] = useState('');
  const [selectedReport, setSelectedReport] = useState<SavedReport | null>(null);
  const [initiatives, setInitiatives] = useState<Initiative[]>([]);
  const [myInitiatives, setMyInitiatives] = useState<Initiative[]>([]);
  const [initiativeStatus, setInitiativeStatus] = useState('');
  const [communityNearMe, setCommunityNearMe] = useState(false);
  const [communityFiltersOpen, setCommunityFiltersOpen] = useState(false);
  const [communityCategory, setCommunityCategory] = useState('ALL');
  const [communityDateRange, setCommunityDateRange] = useState('ALL');
  const [communityLoading, setCommunityLoading] = useState(() => screen === 'initiatives');
  const [communityOffline, setCommunityOffline] = useState(false);
  const [communityCachedAt, setCommunityCachedAt] = useState<number | null>(null);
  const [joiningInitiativeId, setJoiningInitiativeId] = useState('');
  const [initiativeJoinRequests, setInitiativeJoinRequests] = useState<Record<string, InitiativeJoinRequest[]>>({});
  const [selectedInitiative, setSelectedInitiative] = useState<Initiative | null>(null);
  const [initiativeDetailLoading, setInitiativeDetailLoading] = useState(false);
  const [cancellationReasons, setCancellationReasons] = useState<Record<string, string>>({});
  const [attendanceCodeInputs, setAttendanceCodeInputs] = useState<Record<string, string>>({});
  const [activeAttendanceCodes, setActiveAttendanceCodes] = useState<Record<string, ActiveAttendanceCode>>({});
  const [initiativeCoordinates, setInitiativeCoordinates] = useState<{ latitude: number; longitude: number } | null>(null);
  const [initiativeRadiusKm, setInitiativeRadiusKm] = useState(5);
  const [initiativeTitle, setInitiativeTitle] = useState('');
  const [initiativeCategory, setInitiativeCategory] = useState('');
  const [initiativeDescription, setInitiativeDescription] = useState('');
  const [initiativePublicOrganiserName, setInitiativePublicOrganiserName] = useState('');
  const [initiativeStartAt, setInitiativeStartAt] = useState('');
  const [initiativeEndAt, setInitiativeEndAt] = useState('');
  const [initiativePlaceName, setInitiativePlaceName] = useState('');
  const [initiativeMeetingPoint, setInitiativeMeetingPoint] = useState<MeetingPointPosition | null>(null);
  const [initiativeMeetingPointConfirmed, setInitiativeMeetingPointConfirmed] = useState(false);
  const [initiativeCapacity, setInitiativeCapacity] = useState('');
  const [initiativeNeededItems, setInitiativeNeededItems] = useState<string[]>([]);
  const [initiativeNeededItemDraft, setInitiativeNeededItemDraft] = useState('');
  const [initiativeOrganiserMessage, setInitiativeOrganiserMessage] = useState('');
  const [initiativeParticipationMode, setInitiativeParticipationMode] = useState<'OPEN' | 'CAPPED' | 'APPROVAL_REQUIRED'>('OPEN');
  const [initiativeReviewing, setInitiativeReviewing] = useState(false);
  const [initiativeFormStep, setInitiativeFormStep] = useState(1);
  const [initiativeHighestStep, setInitiativeHighestStep] = useState(1);
  const [filingEmail, setFilingEmail] = useState('');
  const initialFilingActionReceipt = useRef(readFilingActionReceipt(window.sessionStorage)).current;
  const [filingActionStatus, setFilingActionStatus] = useState('');
  const [selectedFilingMethod, setSelectedFilingMethod] = useState<FilingMethod | ''>(initialFilingActionReceipt?.method ?? '');
  const [filingActionReceipt, setFilingActionReceipt] = useState(initialFilingActionReceipt);
  const [complainantName, setComplainantName] = useState('');
  const [complainantEmail, setComplainantEmail] = useState('');
  const [complainantPhone, setComplainantPhone] = useState('');
  const [complainantAddress, setComplainantAddress] = useState('');
  const [complainantCity, setComplainantCity] = useState('Nandurbar');
  const [complainantPincode, setComplainantPincode] = useState('425412');
  const [complainantState, setComplainantState] = useState('Maharashtra');
  const filingContactRestoredReportId = useRef('');
  const [accountState, setAccountState] = useState<AccountIdentityState>('ANONYMOUS_SESSION');
  const [accountUid, setAccountUid] = useState<string | null>(null);
  const [accountName, setAccountName] = useState<string | null>(null);
  const [accountEmail, setAccountEmail] = useState<string | null>(null);
  const [accountProfileLoading, setAccountProfileLoading] = useState(true);
  const [headerPoints, setHeaderPoints] = useState<number | null>(null);
  const [accountDialog, setAccountDialog] = useState<AccountDialog>('CLOSED');
  const [accountBusy, setAccountBusy] = useState(false);
  const [accountError, setAccountError] = useState('');
  const [accountErrorCode, setAccountErrorCode] = useState('');
  const [collisionCredential, setCollisionCredential] = useState<AuthCredential | null>(null);
  const [publicRecognition, setPublicRecognition] = useState<PublicRecognitionPanel | null>(null);
  const [publicRecognitionLoading, setPublicRecognitionLoading] = useState(true);
  const [publicRecognitionStatus, setPublicRecognitionStatus] = useState('');
  const [recognitionSettings, setRecognitionSettings] = useState<RecognitionSettingsState | null>(null);
  const [recognitionSettingsStatus, setRecognitionSettingsStatus] = useState('');
  const [recognitionSettingsBusy, setRecognitionSettingsBusy] = useState(false);
  const [privatePoints, setPrivatePoints] = useState<PrivatePointsSummary | null>(null);
  const [privatePointsStatus, setPrivatePointsStatus] = useState('');
  const [rewardOverview, setRewardOverview] = useState<RewardOverview | null>(null);
  const [rewardStatus, setRewardStatus] = useState('');
  const [rewardBusyId, setRewardBusyId] = useState('');
  const [rewardUseConfirmation, setRewardUseConfirmation] = useState('');
  const pendingMutation = useRef<(() => Promise<void>) | null>(null);
  const syncedProfileUid = useRef<string | null>(null);
  const initiativeCreateRequestId = useRef(crypto.randomUUID());
  const classificationTimer = useRef<number | null>(null);
  const classificationRequestSequence = useRef(0);
  const reportLocationRequestSequence = useRef(0);
  const reportDeviceLocationAttempted = useRef(false);
  const routeResultHeadingRef = useRef<HTMLHeadingElement | null>(null);
  const filingPanelRef = useRef<HTMLDivElement | null>(null);
  const filingDrafts = useRef<Record<string, FilingDraftSnapshot>>({});
  const escalationDrafts = useRef<Record<string, { subject: string; body: string }>>({});
  const activeFilingMethod = useRef<FilingMethod | ''>('');
  const filingDraftRequestSequence = useRef(0);
  const t = (source: string) => translate(language, source);
  const runtimeMessage = (message: string) => localizedRuntimeMessage(language, message);
  const initiativePublishRequirements = [
    !initiativeCategory ? 'Choose an activity type.' : '',
    !initiativeTitle.trim() ? 'Add an activity title.' : '',
    !initiativeDescription.trim() ? 'Add an activity description.' : '',
    !initiativePublicOrganiserName.trim() ? 'Add the public organiser display name.' : '',
    !initiativeStartAt || Date.parse(initiativeStartAt) <= Date.now() ? 'Choose a future date and time.' : '',
    !initiativeEndAt || Date.parse(initiativeEndAt) <= Date.parse(initiativeStartAt) ? 'Choose an end time after the start time.' : '',
    initiativeParticipationMode === 'CAPPED' && !initiativeCapacity ? 'Add the maximum number of participants.' : '',
    initiativeParticipationMode === 'CAPPED' && initiativeCapacity && (Number(initiativeCapacity) < 1 || Number(initiativeCapacity) > 500) ? 'Participant capacity must be from 1 to 500.' : '',
    !initiativePlaceName.trim() ? 'Add a public meeting-point label.' : '',
    !initiativeMeetingPoint ? 'Place the meeting-point pin.' : '',
    !initiativeMeetingPointConfirmed ? 'Confirm the meeting-point label and pin.' : '',
  ].filter(Boolean);
  const highlightedPrabhagId = prabhagId || (resolution?.status === 'CANDIDATE_PRABHAG' ? resolution.prabhagId : undefined);
  const boundarySelectionKind = manualPrabhagSelected
    ? 'MANUAL' as const
    : citizenConfirmed
      ? 'CONFIRMED' as const
      : resolution?.status === 'CANDIDATE_PRABHAG'
        ? 'AUTOMATIC_CANDIDATE' as const
        : undefined;
  const reportLocationSourceLabel = reportLocationSource === 'PHOTO'
    ? t('Suggested from photo location')
    : reportLocationSource === 'DEVICE'
      ? t('Suggested from current location')
      : reportLocationSource === 'GOOGLE'
        ? t('Selected from Google location')
        : reportLocationSource === 'MANUAL'
          ? t('Selected manually')
          : '';
  const reportsView = reportsViewState(accountState, savedReports.length, reportsStatus.startsWith('Loading'));
  const initiativeCategoryLabel = (category: string) => ({
    PLANTATION: t('Plantation'),
    COMMUNITY_FITNESS: t('Community fitness'),
    BIRTHDAY_DONATION: t('Birthday Donations'),
    PLANTATION_DRIVE: t('Plantation Drive'),
    AWARENESS_SESSION: t('Awareness Session'),
    COMMUNITY_YOGA: t('Community Yoga'),
    MEDITATION_WORKSHOP: t('Meditation Workshop'),
    HEALTH_ACTIVITY: t('Health Activity'),
    BOOK_SUPPLY_DRIVE: t('Book or school-supply drive'),
    DONATION: t('Food donation'),
    CLEANUP: t('Community cleanup'),
    OTHER_CIVIC_ACTIVITY: t('Other civic activity'),
  }[category] ?? category);
  const contributionTypeLabel = (reason: string) => ({
    REPORT_FILED: t('Accepted report filing'),
    INITIATIVE_ATTENDANCE_ORGANISER_CODE_ATTESTED: t('Organiser-code attendance'),
    INITIATIVE_ORGANISER_COMPLETED_REWARDED: t('Eligible completed organiser activity'),
    FIX_VERIFIED: t('Verified civic fix'),
  }[reason] ?? reason);
  useEffect(() => {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
    document.documentElement.lang = language;
    document.title = language === 'mr' ? 'सीविक · स्थानिक नागरी कृती' : language === 'hi' ? 'सीविक · स्थानीय नागरिक कार्रवाई' : 'Seewik · Local civic action';
  }, [language]);

  useEffect(() => {
    if (complaintLanguageManuallySelected) return;
    setDraftLanguage(language === 'en' ? 'EN' : 'MR');
  }, [language, complaintLanguageManuallySelected]);

  useEffect(() => {
    if (accountName?.trim()) setComplainantName((value) => value.trim() ? value : accountName.trim());
    if (accountEmail?.trim()) setComplainantEmail((value) => value.trim() ? value : accountEmail.trim());
  }, [accountName, accountEmail]);

  useEffect(() => {
    if (!evidenceImage) {
      setEvidencePreviewUrl('');
      return;
    }
    const previewUrl = URL.createObjectURL(evidenceImage);
    setEvidencePreviewUrl(previewUrl);
    return () => URL.revokeObjectURL(previewUrl);
  }, [evidenceImage]);

  useEffect(() => observeAccount(({ state, user }) => {
    setAccountState(state);
    setAccountUid(user?.uid ?? null);
    setAccountName(user?.displayName ? user.displayName.trim() : null);
    setAccountEmail(user?.email ?? null);
    setAccountProfileLoading(state === 'GOOGLE_LINKED');
    if (state === 'GOOGLE_LINKED' && user) {
      const cachedPoints = window.localStorage.getItem(`${PRIVATE_POINTS_CACHE_PREFIX}${user.uid}`);
      const parsedPoints = cachedPoints === null ? Number.NaN : Number(cachedPoints);
      setHeaderPoints(Number.isFinite(parsedPoints) && parsedPoints >= 0 ? parsedPoints : null);
    } else {
      setHeaderPoints(null);
    }
    if (state === 'GOOGLE_LINKED' && user && syncedProfileUid.current !== user.uid) {
      syncedProfileUid.current = user.uid;
      void syncPrivateProfile(user).then((profile) => {
        setAccountName((profile.privateGoogleName || user.displayName || '').trim() || null);
        setAccountEmail(profile.privateGoogleEmail || user.email);
      }).catch(() => {
        syncedProfileUid.current = null;
      }).finally(() => setAccountProfileLoading(false));
      return;
    }
    setAccountProfileLoading(false);
  }), []);

  useEffect(() => {
    if (accountState !== 'GOOGLE_LINKED' || !accountUid) return;
    void refreshDerivedPoints().catch(() => undefined);
  }, [accountState, accountUid]);

  useEffect(() => {
    if (!draftDocumentId) {
      filingContactRestoredReportId.current = '';
      return;
    }
    if (filingContactRestoredReportId.current !== draftDocumentId) {
      filingContactRestoredReportId.current = draftDocumentId;
      const restored = readFilingContactDraft(window.sessionStorage, draftDocumentId);
      if (restored) {
        setComplainantName(restored.complainantName);
        setComplainantEmail(restored.complainantEmail);
        setComplainantPhone(restored.complainantPhone);
        setComplainantAddress(restored.complainantAddress);
        setComplainantCity(restored.complainantCity);
        setComplainantPincode(restored.complainantPincode);
        setComplainantState(restored.complainantState);
        return;
      }
    }
    writeFilingContactDraft(window.sessionStorage, {
      reportId: draftDocumentId,
      complainantName,
      complainantEmail,
      complainantPhone,
      complainantAddress,
      complainantCity,
      complainantPincode,
      complainantState,
    });
  }, [draftDocumentId, complainantName, complainantEmail, complainantPhone, complainantAddress, complainantCity, complainantPincode, complainantState]);

  useEffect(() => {
    if (accountState !== 'GOOGLE_LINKED' || !accountName?.trim()) return;
    setInitiativePublicOrganiserName((currentName) => currentName.trim() ? currentName : accountName.trim());
  }, [accountState, accountName]);

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
    setDraftStatus('');
    setDraftDocumentId(null);
    setDraftReviewed(false);
    setReportStatus('DRAFT');
    setTimeline([]);
    setLifecycleStatus('');
    setDuplicateWarning(null);
    setFilingChannelId('');
    setFilingEmail('');
    setFilingActionStatus('');
    setSelectedFilingMethod('');
    clearFilingActionReceipt();
    activeFilingMethod.current = '';
    filingDrafts.current = {};
    filingDraftRequestSequence.current += 1;
    setAcknowledgementId('');
    setSelectedReport(null);
  }

  function resetEvidenceDerivedState() {
    if (classificationTimer.current !== null) window.clearTimeout(classificationTimer.current);
    classificationTimer.current = null;
    classificationRequestSequence.current += 1;
    setClassification(null);
    setClassificationStatus('');
    setClassificationSource('SELF_REPORTED');
    setComplaintFacts('');
    setRouteResult(null);
    resetDraft();
  }

  function startOver() {
    removeFilingContactDraft(window.sessionStorage);
    if (classificationTimer.current !== null) window.clearTimeout(classificationTimer.current);
    classificationTimer.current = null;
    classificationRequestSequence.current += 1;
    setIssueType('');
    setPrabhagId('');
    setRouteResult(null);
    setResolution(null);
    setSelectionMethod('SELF_REPORTED');
    setCitizenConfirmed(false);
    setManualPrabhagSelected(false);
    setReportLocationSource('');
    setBoundaryDatasetVersion(undefined);
    setEvidenceText('');
    setEvidenceImage(null);
    setClassification(null);
    setClassificationStatus('');
    setClassificationSource('SELF_REPORTED');
    setComplaintFacts('');
    setLocationDetails('');
    setDraftLanguage(language === 'en' ? 'EN' : 'MR');
    setComplaintLanguageManuallySelected(false);
    setComplainantName(accountName?.trim() || '');
    setComplainantEmail(accountEmail?.trim() || '');
    setComplainantPhone('');
    setComplainantAddress('');
    setComplainantCity('Nandurbar');
    setComplainantPincode('425412');
    setComplainantState('Maharashtra');
    setCurrentCoordinates(null);
    reportLocationRequestSequence.current += 1;
    reportDeviceLocationAttempted.current = false;
    resetDraft();
    navigate('new-report');
  }

  function startInitiativeOver() {
    setInitiativeTitle('');
    setInitiativeCategory('');
    setInitiativeDescription('');
    setInitiativePublicOrganiserName(accountState === 'GOOGLE_LINKED' ? accountName?.trim() || '' : '');
    setInitiativeStartAt('');
    setInitiativeEndAt('');
    setInitiativePlaceName('');
    setInitiativeMeetingPoint(null);
    setInitiativeMeetingPointConfirmed(false);
    setInitiativeCapacity('');
    setInitiativeNeededItems([]);
    setInitiativeNeededItemDraft('');
    setInitiativeOrganiserMessage('');
    setInitiativeParticipationMode('OPEN');
    setInitiativeReviewing(false);
    setInitiativeFormStep(1);
    setInitiativeHighestStep(1);
    setInitiativeStatus('');
    initiativeCreateRequestId.current = crypto.randomUUID();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function clearAccountBoundState() {
    removeFilingContactDraft(window.sessionStorage);
    setSavedReports([]);
    setSelectedReport(null);
    setReportsStatus('');
    setPointsTotal(0);
    setPrivatePoints(null);
    setPrivatePointsStatus('');
    setRewardOverview(null);
    setRewardStatus('');
    setRewardBusyId('');
    setRewardUseConfirmation('');
    setRecognitionSettings(null);
    setRecognitionSettingsStatus('');
    setInitiatives([]);
    setMyInitiatives([]);
    setInitiativeStatus('');
    setCancellationReasons({});
    setInitiativeCoordinates(null);
    setInitiativeTitle('');
    setInitiativeCategory('');
    setInitiativeDescription('');
    setInitiativePublicOrganiserName('');
    setInitiativeStartAt('');
    setInitiativeEndAt('');
    setInitiativePlaceName('');
    setInitiativeMeetingPoint(null);
    setInitiativeMeetingPointConfirmed(false);
    setInitiativeCapacity('');
    setInitiativeNeededItems([]);
    setInitiativeNeededItemDraft('');
    setInitiativeOrganiserMessage('');
    setInitiativeParticipationMode('OPEN');
    setInitiativeReviewing(false);
    setEvidenceText('');
    setEvidenceImage(null);
    setComplaintFacts('');
    setLocationDetails('');
    syncedProfileUid.current = null;
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
      setAccountName(user.displayName);
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
      setAccountName(user.displayName);
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
      if (accountUid) window.localStorage.removeItem(`${PRIVATE_POINTS_CACHE_PREFIX}${accountUid}`);
      clearAccountBoundState();
      setAccountState('SIGNED_OUT');
      setAccountName(null);
      setAccountEmail(null);
      setAccountUid(null);
      setAccountDialog('CLOSED');
      navigate('home');
    } catch (error) {
      const code = typeof error === 'object' && error && 'code' in error ? String(error.code) : undefined;
      showAccountError(code);
    } finally {
      setAccountBusy(false);
    }
  }

  async function loadPublicRecognition() {
    setPublicRecognitionLoading(true);
    setPublicRecognitionStatus('');
    try {
      setPublicRecognition(await fetchCurrentRecognition());
    } catch (error) {
      setPublicRecognition(null);
      setPublicRecognitionStatus(citizenSafeError(error, 'Monthly recognition could not be loaded.'));
    } finally {
      setPublicRecognitionLoading(false);
    }
  }

  async function loadRecognitionSettings() {
    if (accountState !== 'GOOGLE_LINKED') {
      setRecognitionSettings(null);
      return;
    }
    setRecognitionSettingsStatus('Loading your recognition choice…');
    try {
      const token = await sessionToken(true);
      setRecognitionSettings(await fetchRecognitionSettings(token));
      setRecognitionSettingsStatus('');
    } catch (error) {
      setRecognitionSettingsStatus(citizenSafeError(error, 'Recognition settings could not be loaded.'));
    }
  }

  async function updateRecognitionSettings(publicDisplayName: string, recognitionActive: boolean) {
    setRecognitionSettingsBusy(true);
    setRecognitionSettingsStatus(recognitionActive ? 'Saving your public recognition choice…' : 'Withdrawing public recognition…');
    try {
      const token = await sessionToken(true);
      const saved = await saveRecognitionSettings(token, publicDisplayName, recognitionActive);
      setRecognitionSettings(saved);
      setRecognitionSettingsStatus(recognitionActive
        ? 'Your recognition choice was saved.'
        : 'Your public recognition was withdrawn. Your points were not changed.');
      await loadPublicRecognition();
    } catch (error) {
      setRecognitionSettingsStatus(citizenSafeError(error, 'Recognition settings could not be saved.'));
    } finally {
      setRecognitionSettingsBusy(false);
    }
  }

  async function sendRecognitionReport(position: number, targetDisplayName: string, reason: string, reportDetails: string) {
    const send = async () => {
      setPublicRecognitionStatus('Sending your concern…');
      try {
        const token = await sessionToken(true);
        const result = await reportRecognitionName(token, position, targetDisplayName, reason, reportDetails);
        setPublicRecognitionStatus(result.message);
      } catch (error) {
        setPublicRecognitionStatus(citizenSafeError(error, 'The concern could not be sent.'));
      }
    };
    if (accountState === 'GOOGLE_LINKED') await send();
    else requestLinkedMutation(send);
  }

  useEffect(() => {
    void loadPublicRecognition();
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
        setMyInitiatives([]);
        setReportsStatus('');
      } else {
        loadMyReports().catch((error) => setReportsStatus(citizenSafeError(error, 'Your reports could not be loaded.')));
        loadMyInitiatives().catch((error) => setInitiativeStatus(citizenSafeError(error, 'Your initiatives could not be loaded.')));
      }
    }
    if (screen === 'points') {
      if (accountState === 'SIGNED_OUT') {
        setPrivatePoints(null);
        setPrivatePointsStatus('');
        setRewardOverview(null);
        setRewardStatus('');
      } else {
        Promise.allSettled([refreshDerivedPoints(), refreshRewards()]).catch(() => undefined);
      }
      if (accountState === 'GOOGLE_LINKED') loadRecognitionSettings().catch(() => undefined);
      else setRecognitionSettings(null);
    }
    if (screen === 'report-detail' && accountState !== 'SIGNED_OUT') {
      const reportId = reportIdFromPath(window.location.pathname);
      if (reportId && selectedReport?.id !== reportId) {
        loadReportById(reportId, false).catch((error) => setReportsStatus(citizenSafeError(error, 'The report could not be loaded.')));
      }
    }
    if (screen === 'review') {
      const reportId = reportIdFromReviewSearch(window.location.search);
      if (reportId && selectedReport?.id !== reportId) {
        loadReportById(reportId, true).catch((error) => setDraftStatus(citizenSafeError(error, 'The draft could not be resumed.')));
      }
    }
    if (screen === 'initiative-detail' && accountState !== 'SIGNED_OUT') {
      const initiativeId = initiativeIdFromPath(window.location.pathname);
      if (initiativeId && selectedInitiative?.initiativeId !== initiativeId) {
        loadInitiativeDetail(initiativeId).catch((error) => setInitiativeStatus(citizenSafeError(error, 'The activity could not be loaded.')));
      }
    }
  }, [screen, locationKey, accountState]);

  useEffect(() => {
    const transition = automaticEscalationLanguageTransition(language, escalationLanguageManuallySelected, escalationLanguage);
    if (!transition.shouldSwitch) return;
    cacheEscalationDraft();
    setEscalationLanguage(transition.nextLanguage);
    setEscalationActionOpened(false);
    if (!selectedEscalationChannel) return;
    const cached = escalationDrafts.current[escalationDraftKey(selectedEscalationChannel, transition.nextLanguage)];
    const draft = cached ?? buildEscalationDraft(selectedEscalationChannel, transition.nextLanguage);
    setEscalationSubject(draft.subject);
    setEscalationBody(draft.body);
  }, [language, escalationLanguageManuallySelected]);

  useEffect(() => {
    setSelectedEscalationChannel('');
    setEscalationSubject('');
    setEscalationBody('');
    setEscalationActionOpened(false);
    setFollowUpStatus('');
  }, [selectedReport?.id]);

  useEffect(() => {
    if (screen !== 'initiatives') return;
    discoverInitiatives(false).catch((error) => setInitiativeStatus(citizenSafeError(error, 'Community activities could not be loaded.')));
    const interval = window.setInterval(() => {
      discoverInitiatives(true).catch(() => undefined);
    }, 30_000);
    return () => window.clearInterval(interval);
  }, [screen, communityNearMe, initiativeCoordinates, initiativeRadiusKm, accountUid]);

  useEffect(() => {
    if (screen !== 'reports') return;
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
    const usingCitizenLocation = communityNearMe && initiativeCoordinates;
    const coordinates = usingCitizenLocation ? initiativeCoordinates : NANDURBAR_COMMUNITY_ORIGIN;
    const radiusKm = usingCitizenLocation ? initiativeRadiusKm : NANDURBAR_COMMUNITY_RADIUS_KM;
    const cacheKey = `${COMMUNITY_CACHE_PREFIX}${accountUid ?? 'device'}.${usingCitizenLocation ? `near-${radiusKm}` : 'nandurbar'}`;
    if (!background) {
      setCommunityLoading(true);
      setInitiativeStatus('');
    }
    try {
      const idToken = await authenticatedToken();
      const response = await fetch(`${API_URL}/api/initiatives/nearby`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${idToken}` },
        body: JSON.stringify({ ...coordinates, radiusKm }),
      });
      const result: InitiativeDiscovery = await response.json();
      if (!response.ok) throw new Error(result.message ?? `Discovery failed (${response.status})`);
      const ordered = [...result.initiatives].sort((left, right) => Date.parse(left.startAt) - Date.parse(right.startAt));
      setInitiatives(ordered);
      setCommunityOffline(false);
      setCommunityCachedAt(null);
      window.localStorage.setItem(cacheKey, JSON.stringify({ savedAt: Date.now(), initiatives: ordered }));
    } catch (error) {
      const cachedValue = window.localStorage.getItem(cacheKey);
      if (!cachedValue) throw error;
      const cached = JSON.parse(cachedValue) as { savedAt?: number; initiatives?: Initiative[] };
      if (!Array.isArray(cached.initiatives)) throw error;
      setInitiatives(cached.initiatives);
      setCommunityOffline(true);
      setCommunityCachedAt(typeof cached.savedAt === 'number' ? cached.savedAt : null);
    } finally {
      if (!background) setCommunityLoading(false);
    }
  }

  async function loadMyInitiatives(background = false) {
    if (!background) setInitiativeStatus('');
    const idToken = await authenticatedToken();
    const response = await fetch(`${API_URL}/api/initiatives/mine`, {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    const result: InitiativeDiscovery = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Your activities could not be loaded (${response.status})`);
    setMyInitiatives(result.initiatives);
    const organisers = result.initiatives.filter((initiative) => initiative.canManage && initiative.participationMode === 'APPROVAL_REQUIRED');
    const requestEntries = await Promise.all(organisers.map(async (initiative) => {
      const requestResponse = await fetch(`${API_URL}/api/initiatives/${encodeURIComponent(initiative.initiativeId)}/join-requests`, {
        headers: { Authorization: `Bearer ${idToken}` },
      });
      const requestResult = await requestResponse.json();
      if (!requestResponse.ok) throw new Error(requestResult.message ?? `Join requests could not be loaded (${requestResponse.status})`);
      return [initiative.initiativeId, requestResult.requests as InitiativeJoinRequest[]] as const;
    }));
    setInitiativeJoinRequests(Object.fromEntries(requestEntries));
    if (!background && result.count > 0) setInitiativeStatus(`${result.count} joined or organised ${result.count === 1 ? 'activity' : 'activities'} loaded.`);
  }

  function locateForInitiatives() {
    setInitiativeStatus('Checking your location…');
    if (!navigator.geolocation) {
      setInitiativeStatus('Location is unavailable in this browser.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const coordinates = { latitude: position.coords.latitude, longitude: position.coords.longitude };
        setInitiativeCoordinates(coordinates);
        setCommunityNearMe(true);
        setInitiativeStatus('Location captured. Finding activities…');
      },
      () => setInitiativeStatus('Location permission was not provided.'),
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    );
  }

  async function createInitiative() {
    if (!initiativeMeetingPoint || !initiativeMeetingPointConfirmed) {
      setInitiativeStatus('Confirm the meeting-point label and pin before publishing.');
      return;
    }
    if (!initiativeStartAt) {
      setInitiativeStatus('Choose the activity date and time.');
      return;
    }
    if (!initiativeEndAt || Date.parse(initiativeEndAt) <= Date.parse(initiativeStartAt)) {
      setInitiativeStatus('Choose an end time after the start time.');
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
        publicOrganiserName: initiativePublicOrganiserName,
        publicOrganiserNameConfirmed: true,
        startAt: new Date(initiativeStartAt).toISOString(),
        endAt: new Date(initiativeEndAt).toISOString(),
        placeName: initiativePlaceName,
        ...initiativeMeetingPoint,
        neededItems: initiativeNeededItems,
        organiserMessage: initiativeOrganiserMessage,
        participationMode: initiativeParticipationMode,
        clientRequestId: initiativeCreateRequestId.current,
        ...(initiativeParticipationMode === 'CAPPED' && initiativeCapacity
          ? { capacity: Number(initiativeCapacity) }
          : {}),
      }),
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Publish failed (${response.status})`);
    setInitiativeTitle('');
    setInitiativeCategory('');
    setInitiativeDescription('');
    setInitiativePublicOrganiserName('');
    setInitiativeStartAt('');
    setInitiativeEndAt('');
    setInitiativePlaceName('');
    setInitiativeMeetingPoint(null);
    setInitiativeMeetingPointConfirmed(false);
    setInitiativeCapacity('');
    setInitiativeNeededItems([]);
    setInitiativeNeededItemDraft('');
    setInitiativeOrganiserMessage('');
    setInitiativeParticipationMode('OPEN');
    setInitiativeReviewing(false);
    initiativeCreateRequestId.current = crypto.randomUUID();
    await loadMyInitiatives(true);
    navigate('reports');
    setInitiativeStatus('Activity published. You are included as the organiser.');
    window.setTimeout(() => document.getElementById('my-initiatives')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100);
  }

  async function loadInitiativeDetail(initiativeId: string) {
    setInitiativeDetailLoading(true);
    setInitiativeStatus('');
    try {
      const idToken = await authenticatedToken();
      const response = await fetch(`${API_URL}/api/initiatives/${encodeURIComponent(initiativeId)}`, {
        headers: { Authorization: `Bearer ${idToken}` },
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.message ?? `Activity could not be loaded (${response.status})`);
      setSelectedInitiative(result as Initiative);
    } finally {
      setInitiativeDetailLoading(false);
    }
  }

  async function joinInitiative(initiativeId: string) {
    setJoiningInitiativeId(initiativeId);
    setInitiativeStatus('Joining activity…');
    try {
      const idToken = await authenticatedToken();
      const response = await fetch(`${API_URL}/api/initiatives/${encodeURIComponent(initiativeId)}/join`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${idToken}` },
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.message ?? `Join failed (${response.status})`);
      if (result.status === 'APPROVAL_REQUESTED') {
        setInitiatives((items) => items.map((item) => item.initiativeId === initiativeId
          ? { ...item, joinRequestedByMe: true }
          : item));
        setSelectedInitiative((item) => item?.initiativeId === initiativeId
          ? { ...item, joinRequestedByMe: true }
          : item);
        setInitiativeStatus('Your request was sent to the organiser.');
        await loadMyInitiatives(true);
        return;
      }
      setInitiatives((items) => items.map((item) => item.initiativeId === initiativeId
        ? { ...item, participantCount: result.participantCount, joinerCount: result.participantCount - 1, joiningCountVisible: true, joined: true, full: item.capacity !== null && result.participantCount - 1 >= item.capacity }
        : item));
      setSelectedInitiative((item) => item?.initiativeId === initiativeId
        ? { ...item, participantCount: result.participantCount, joinerCount: result.participantCount - 1, joiningCountVisible: true, joined: true, full: item.capacity !== null && result.participantCount - 1 >= item.capacity }
        : item);
      setInitiativeStatus(result.status === 'ALREADY_JOINED' ? 'You already joined this activity.' : 'You joined. The live participant count has been updated.');
      await loadMyInitiatives(true);
    } finally {
      setJoiningInitiativeId('');
    }
  }

  async function reviewInitiativeJoinRequest(
    initiativeId: string,
    requestId: string,
    decision: 'approve' | 'decline',
  ) {
    setInitiativeStatus(decision === 'approve' ? 'Accepting join request…' : 'Declining join request…');
    const idToken = await authenticatedToken();
    const response = await fetch(
      `${API_URL}/api/initiatives/${encodeURIComponent(initiativeId)}/join-requests/${encodeURIComponent(requestId)}/${decision}`,
      { method: 'POST', headers: { Authorization: `Bearer ${idToken}` } },
    );
    const result = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Join request could not be reviewed (${response.status})`);
    setInitiativeJoinRequests((values) => ({
      ...values,
      [initiativeId]: (values[initiativeId] ?? []).filter((request) => request.requestId !== requestId),
    }));
    setInitiativeStatus(decision === 'approve' ? 'Join request accepted.' : 'Join request declined.');
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

  async function resolveCoordinates(latitude: number, longitude: number, source: 'PHOTO' | 'DEVICE' | 'GOOGLE', requestSequence = ++reportLocationRequestSequence.current, address = '') {
    const response = await fetch(`${API_URL}/api/civic/resolve-prabhag`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ latitude, longitude }),
    });
    if (!response.ok) throw new Error(`Location request failed (${response.status})`);
    const result: PrabhagResolution = await response.json();
    if (requestSequence !== reportLocationRequestSequence.current) return null;
    if (address.trim()) setLocationDetails(address.trim());
    setResolution(result);
    if (result.status === 'CANDIDATE_PRABHAG' && result.prabhagId && result.datasetVersion) {
      setPrabhagId(result.prabhagId);
      setSelectionMethod(result.resolutionMethod ?? 'BIGQUERY_ST_COVERS');
      setCitizenConfirmed(false);
      setManualPrabhagSelected(false);
      setReportLocationSource(source);
      setBoundaryDatasetVersion(result.datasetVersion);
      setCurrentCoordinates({ latitude, longitude });
      setRouteResult(null);
      resetDraft();
    } else {
      setPrabhagId('');
      setSelectionMethod('SELF_REPORTED');
      setCitizenConfirmed(false);
      setManualPrabhagSelected(false);
      setReportLocationSource(source);
      setBoundaryDatasetVersion(undefined);
      setCurrentCoordinates({ latitude, longitude });
      setRouteResult(null);
      resetDraft();
    }
    return result;
  }

  function requestDeviceReportLocation() {
    if (reportDeviceLocationAttempted.current || !navigator.geolocation) return;
    reportDeviceLocationAttempted.current = true;
    const requestSequence = ++reportLocationRequestSequence.current;
    navigator.geolocation.getCurrentPosition(
      (position) => {
        void reverseGeocodeGoogleLocation(position.coords.latitude, position.coords.longitude)
          .catch(() => '')
          .then((address) => resolveCoordinates(position.coords.latitude, position.coords.longitude, 'DEVICE', requestSequence, address))
          .catch(() => undefined);
      },
      () => undefined,
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    );
  }

  async function prefillReportLocation(file: File) {
    const extractionSequence = ++reportLocationRequestSequence.current;
    const coordinates = await extractPhotoCoordinates(file).catch(() => null);
    if (extractionSequence !== reportLocationRequestSequence.current) return;
    if (coordinates) {
      const address = await reverseGeocodeGoogleLocation(coordinates.latitude, coordinates.longitude).catch(() => '');
      if (extractionSequence !== reportLocationRequestSequence.current) return;
      const result = await resolveCoordinates(coordinates.latitude, coordinates.longitude, 'PHOTO', extractionSequence, address).catch(() => null);
      if (result?.status === 'CANDIDATE_PRABHAG') return;
    }
    requestDeviceReportLocation();
  }

  function selectManualPrabhag(value: string) {
    if (!PRABHAGS.includes(value)) return;
    reportLocationRequestSequence.current += 1;
    reportDeviceLocationAttempted.current = true;
    setPrabhagId(value);
    setSelectionMethod('SELF_REPORTED');
    setCitizenConfirmed(false);
    setManualPrabhagSelected(true);
    if (!currentCoordinates) setReportLocationSource('MANUAL');
    setBoundaryDatasetVersion(undefined);
    setResolution(null);
    setRouteResult(null);
    resetDraft();
  }

  function editReportLocationAddress(value: string) {
    setLocationDetails(value);
    setCitizenConfirmed(false);
    setRouteResult(null);
    resetDraft();
  }

  function selectGoogleReportLocation(selection: GoogleMeetingPointSelection) {
    const requestSequence = ++reportLocationRequestSequence.current;
    reportDeviceLocationAttempted.current = true;
    setLocationDetails(selection.address || selection.label);
    setPrabhagId('');
    setResolution(null);
    setSelectionMethod('SELF_REPORTED');
    setCitizenConfirmed(false);
    setManualPrabhagSelected(false);
    setBoundaryDatasetVersion(undefined);
    setRouteResult(null);
    resetDraft();
    void resolveCoordinates(
      selection.position.latitude,
      selection.position.longitude,
      'GOOGLE',
      requestSequence,
      selection.address || selection.label,
    ).catch(() => undefined);
  }

  function chooseIssueType(value: string) {
    if (classificationTimer.current !== null) window.clearTimeout(classificationTimer.current);
    classificationTimer.current = null;
    classificationRequestSequence.current += 1;
    setIssueType(value as typeof issueType);
    setClassificationSource(classification?.issueType === value ? 'GEMINI_SUGGESTED' : 'CITIZEN_SELECTED');
    setRouteResult(null);
    resetDraft();
  }

  function chooseEvidenceImage(file: File | null) {
    setEvidenceImage(file);
    resetEvidenceDerivedState();
    scheduleEvidenceClassification(file, evidenceText, file ? 0 : 650);
    if (file && reportLocationSource !== 'MANUAL') {
      void prefillReportLocation(file);
    } else if (!file && reportLocationSource === 'PHOTO') {
      reportLocationRequestSequence.current += 1;
      reportDeviceLocationAttempted.current = false;
      setPrabhagId('');
      setResolution(null);
      setSelectionMethod('SELF_REPORTED');
      setCitizenConfirmed(false);
      setManualPrabhagSelected(false);
      setReportLocationSource('');
      setBoundaryDatasetVersion(undefined);
      setCurrentCoordinates(null);
      setLocationDetails('');
      requestDeviceReportLocation();
    }
  }

  function scheduleEvidenceClassification(image: File | null, text: string, delay: number) {
    if (classificationTimer.current !== null) window.clearTimeout(classificationTimer.current);
    classificationTimer.current = null;
    classificationRequestSequence.current += 1;
    if (!image && !text.trim()) return;
    classificationTimer.current = window.setTimeout(() => {
      classificationTimer.current = null;
      void classifyEvidence(image, text);
    }, delay);
  }

  async function classifyEvidence(image: File | null, text: string) {
    const requestSequence = ++classificationRequestSequence.current;
    setClassification(null);
    setComplaintFacts('');
    setRouteResult(null);
    resetDraft();
    const trimmedText = text.trim();
    if (!image && !trimmedText) {
      setClassificationStatus('Add a photo or a short description first.');
      return;
    }
    if (image && image.size > 5 * 1024 * 1024) {
      setClassificationStatus('Please choose a photo that is 5 MB or smaller.');
      return;
    }
    setClassificationStatus('Checking the issue category…');
    const form = new FormData();
    if (image) form.append('image', image);
    if (trimmedText) form.append('text', trimmedText);
    let response: Response;
    let result: ClassificationResult;
    try {
      const idToken = await sessionToken();
      response = await fetch(`${API_URL}/api/civic/classify`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${idToken}` },
        body: form,
      });
      result = await response.json();
    } catch {
      if (requestSequence !== classificationRequestSequence.current) return;
      setClassificationStatus('The category could not be checked. Choose it manually below.');
      setClassificationSource('CITIZEN_SELECTED');
      return;
    }
    if (requestSequence !== classificationRequestSequence.current) return;
    setClassification(result);
    setComplaintFacts(trimmedText || result.description || '');
    if (!trimmedText && result.description) setEvidenceText(result.description);
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

  async function findCivicRoute() {
    setRouteResult(null);
    resetDraft();
    const preparedComplaintFacts = complaintFacts.trim() || evidenceText.trim() || classification?.description?.trim() || '';
    if (!issueType) {
      setRouteResult({ status: 'CATEGORY_REQUIRED' });
      return;
    }
    if (!preparedComplaintFacts) {
      setRouteResult({ status: evidenceImage ? 'PHOTO_ANALYSIS_REQUIRED' : 'DETAILS_REQUIRED' });
      window.setTimeout(() => document.getElementById('report-describe-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0);
      return;
    }
    if (!prabhagId) {
      setRouteResult({ status: 'PRABHAG_REQUIRED' });
      return;
    }
    const acceptedAutomaticLocation = !manualPrabhagSelected && resolution?.prabhagId === prabhagId;
    setCitizenConfirmed(acceptedAutomaticLocation);
    const response = await fetch(`${API_URL}/api/civic/route`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ issueType, prabhagId, resolutionMethod: selectionMethod, citizenConfirmed: acceptedAutomaticLocation, boundaryDatasetVersion }),
    });
    if (!response.ok) throw new Error(`Routing request failed (${response.status})`);
    const result: RouteResult = await response.json();
    setRouteResult(result);
    setFilingChannelId('');
    setFilingEmail(result.officialChannels?.find((channel) => channel.type === 'EMAIL')?.value ?? '');
    if (result.status === 'SUPPORTED_ROUTE' && complaintFacts.trim() !== preparedComplaintFacts) {
      setComplaintFacts(preparedComplaintFacts);
    }
    if (result.status === 'SUPPORTED_ROUTE') {
      window.setTimeout(() => routeResultHeadingRef.current?.focus(), 0);
    }
  }

  async function persistNewDraft(result: ComplaintDraftResult) {
    if (!result.routeId || !result.prabhagId || !result.authority || !result.language || !result.subject || !result.body || !result.packVersion || !result.schemaVersion) {
      throw new Error('Draft metadata is incomplete and was not saved.');
    }
    const user = await ensureAnonymousSession();
    if (draftDocumentId && reportStatus === 'DRAFT') {
      await updateDoc(doc(db, 'reports', draftDocumentId), {
        draftLanguage: result.language,
        draftSubject: result.subject,
        draftBody: result.body,
        updatedAt: serverTimestamp(),
      });
      const updatedAt = new Date();
      setSelectedReport((report) => report?.id === draftDocumentId ? { ...report, draftLanguage: result.language!, draftSubject: result.subject!, draftBody: result.body!, updatedAt } : report);
      setSavedReports((reports) => reports.map((report) => report.id === draftDocumentId ? { ...report, draftLanguage: result.language!, draftSubject: result.subject!, draftBody: result.body!, updatedAt } : report));
      return draftDocumentId;
    }
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
      setDraftStatus(`Draft saved · ${reportId.slice(0, 8)}… Choose how you want to file it below.`);
    } catch (error) {
      setDraftStatus(`Draft created but could not be saved: ${(error as Error).message}`);
    }
  }

  async function createComplaintDraft(languageToUse: 'MR' | 'EN', method: FilingMethod, requestSequence: number) {
    if (routeResult?.status !== 'SUPPORTED_ROUTE') {
      setDraftStatus('Get a supported civic route before drafting.');
      return;
    }
    if (!complaintFacts.trim()) {
      setDraftStatus('Add the factual issue details before drafting.');
      return;
    }
    setComplaintDraft(null);
    setDraftSubject('');
    setDraftBody('');
    setDraftReviewed(false);
    setDraftStatus(`Creating ${languageToUse === 'MR' ? 'Marathi' : 'English'} complaint draft…`);
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
        citizenDescription: complaintFacts.trim(),
        locationDetails: locationDetails.trim() || null,
        draftLanguage: languageToUse,
        filingFormat: method,
      }),
    });
    const result: ComplaintDraftResult = await response.json();
    if (requestSequence !== filingDraftRequestSequence.current || activeFilingMethod.current !== method) return;
    if (!response.ok || result.status !== 'DRAFT_READY' || !result.subject || !result.body) {
      const fallback: ComplaintDraftResult = {
        status: 'DRAFT_READY',
        draftVersion: 'manual-v0.1',
        schemaVersion: 'complaint-draft-v0.1',
        packVersion: routeResult.packVersion,
        language: languageToUse,
        routeId: routeResult.routeId,
        prabhagId,
        authority: routeResult.authority,
        authorityLocalName: routeResult.authorityLocalName,
        subject: languageToUse === 'MR' ? 'नागरी समस्येबाबत तक्रार' : 'Civic issue complaint',
        body: complaintFacts.trim(),
        citizenReviewRequired: true,
      };
      setComplaintDraft(fallback);
      setDraftSubject(fallback.subject!);
      setDraftBody(fallback.body!);
      filingDrafts.current[`${method}:${languageToUse}`] = { result: fallback, subject: fallback.subject!, body: fallback.body! };
      setDraftStatus(result.message ?? 'Automatic drafting is unavailable. Review and edit the manual draft before filing.');
      requestLinkedMutation(() => saveGeneratedDraft(fallback));
      return;
    }
    setComplaintDraft(result);
    setDraftSubject(result.subject);
    setDraftBody(result.body);
    filingDrafts.current[`${method}:${languageToUse}`] = { result, subject: result.subject, body: result.body };
    setDraftReviewed(false);
    if (accountState !== 'GOOGLE_LINKED') {
      setDraftStatus('Draft ready. Connect Google to save it without losing this form.');
    }
    requestLinkedMutation(() => saveGeneratedDraft(result));
  }

  function cacheCurrentFilingDraft() {
    if (!selectedFilingMethod || complaintDraft?.status !== 'DRAFT_READY' || !draftSubject.trim() || !draftBody.trim()) return;
    filingDrafts.current[`${selectedFilingMethod}:${draftLanguage}`] = {
      result: { ...complaintDraft, language: draftLanguage, subject: draftSubject.trim(), body: draftBody.trim() },
      subject: draftSubject,
      body: draftBody,
    };
  }

  function markFilingDraftEdited() {
    clearFilingActionReceipt();
  }

  function currentFilingDraftIdentity(method = selectedFilingMethod): FilingDraftIdentity | null {
    if (!method) return null;
    return {
      ownerUid: accountUid ?? selectedReport?.ownerUid ?? '',
      reportId: draftDocumentId ?? '',
      method,
      routeId: routeResult?.routeId ?? complaintDraft?.routeId ?? '',
      packVersion: routeResult?.packVersion ?? complaintDraft?.packVersion ?? '',
      language: draftLanguage,
      subject: draftSubject,
      body: draftBody,
      filingEmail,
      complainantName,
      complainantEmail,
      complainantPhone,
      complainantAddress,
      complainantCity,
      complainantPincode,
      complainantState,
    };
  }

  function clearFilingActionReceipt() {
    setFilingActionReceipt(null);
    removeFilingActionReceipt(window.sessionStorage);
  }

  function recordFilingAction(method: FilingMethod) {
    const identity = currentFilingDraftIdentity(method);
    if (!identity) return;
    const receipt = createFilingActionReceipt(identity);
    setFilingActionReceipt(receipt);
    writeFilingActionReceipt(window.sessionStorage, receipt);
  }

  function filingActionIsCurrent() {
    return filingActionReceiptMatches(filingActionReceipt, currentFilingDraftIdentity());
  }

  function filingChannelForMethod(method: FilingMethod) {
    const type = method === 'EMAIL' ? 'EMAIL' : method === 'DMA' ? 'ONLINE_FORM' : 'IN_PERSON';
    return routeResult?.officialChannels?.find((channel) => channel.type === type);
  }

  async function prepareFilingMethod(method: FilingMethod, languageToUse: 'MR' | 'EN' = draftLanguage) {
    cacheCurrentFilingDraft();
    activeFilingMethod.current = method;
    setSelectedFilingMethod(method);
    clearFilingActionReceipt();
    setFilingActionStatus('');
    setFilingChannelId(filingChannelForMethod(method)?.channelId ?? '');
    setDraftLanguage(languageToUse);
    const exactKey = `${method}:${languageToUse}`;
    const cached = filingDrafts.current[exactKey]
      ?? Object.entries(filingDrafts.current).find(([key]) => key.endsWith(`:${languageToUse}`))?.[1];
    if (cached) {
      const restored = { ...cached.result, language: languageToUse, subject: cached.subject, body: cached.body };
      filingDrafts.current[exactKey] = { result: restored, subject: cached.subject, body: cached.body };
      setComplaintDraft(restored);
      setDraftSubject(cached.subject);
      setDraftBody(cached.body);
      setDraftStatus('Prepared complaint restored. Review and edit it before filing.');
    } else {
      const requestSequence = ++filingDraftRequestSequence.current;
      await createComplaintDraft(languageToUse, method, requestSequence);
    }
    window.setTimeout(() => filingPanelRef.current?.focus(), 0);
  }

  async function changeComplaintLanguage(languageToUse: 'MR' | 'EN') {
    cacheCurrentFilingDraft();
    setComplaintLanguageManuallySelected(true);
    setDraftLanguage(languageToUse);
    if (selectedFilingMethod) await prepareFilingMethod(selectedFilingMethod, languageToUse);
  }

  function editRoutedReport(sectionId: 'report-describe-section' | 'report-location-section') {
    setRouteResult(null);
    resetDraft();
    window.setTimeout(() => document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0);
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
      setDraftStatus('The selected report no longer matches this editor. Reopen it from My Actions.');
      return false;
    }
    await updateDoc(doc(db, 'reports', draftDocumentId), {
      draftLanguage,
      draftSubject: draftSubject.trim(),
      draftBody: draftBody.trim(),
      updatedAt: serverTimestamp(),
    });
    const updatedAt = new Date();
    setSelectedReport((report) => report?.id === draftDocumentId ? { ...report, draftLanguage, draftSubject: draftSubject.trim(), draftBody: draftBody.trim(), updatedAt } : report);
    setSavedReports((reports) => reports.map((report) => report.id === draftDocumentId ? { ...report, draftLanguage, draftSubject: draftSubject.trim(), draftBody: draftBody.trim(), updatedAt } : report));
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

  function printableLetterText() {
    const marathi = draftLanguage === 'MR';
    const blank = '______________________________';
    const authority = complaintDraft?.authorityLocalName || complaintDraft?.authority || routeResult?.authority || '';
    const senderCityLine = [complainantCity.trim(), complainantState.trim(), complainantPincode.trim()].filter(Boolean).join(', ');
    const date = new Intl.DateTimeFormat(marathi ? 'mr-IN' : 'en-IN', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());
    const copy = marathi ? {
      address: 'पत्ता', phone: 'दूरध्वनी', email: 'ईमेल', date: 'दिनांक', to: 'प्रति',
      designation: 'मुख्याधिकारी', subject: 'विषय', salutation: 'आदरणीय महोदय/महोदया,',
      thanks: 'आपल्या सकारात्मक कार्यवाहीच्या अपेक्षेत धन्यवाद.', closing: 'आपला/आपली विश्वासू,',
      signature: 'स्वाक्षरी', printedName: 'नाव', prepared: 'सीविकने तयार केलेले नागरी तक्रारपत्र',
      footer: 'सीविकच्या मदतीने तयार केले आहे. हे पत्र तपासा, स्वाक्षरी करा आणि संबंधित कार्यालयात जमा करा.',
    } : {
      address: 'Address', phone: 'Phone', email: 'Email', date: 'Date', to: 'To',
      designation: 'The Chief Officer', subject: 'Subject', salutation: 'Respected Sir/Madam,',
      thanks: 'Thanking you in anticipation.', closing: 'Yours faithfully,',
      signature: 'Signature', printedName: 'Printed name', prepared: 'Prepared civic complaint letter',
      footer: 'Prepared with Seewik. Review, sign and submit this letter to the responsible office.',
    };
    return [
      complainantName.trim() || blank,
      `${copy.address}: ${complainantAddress.trim() || blank}`,
      senderCityLine || blank,
      `${copy.phone}: ${complainantPhone.trim() || blank}`,
      `${copy.email}: ${complainantEmail.trim() || blank}`,
      '',
      `${copy.date}: ${date}`,
      '',
      `${copy.to},`,
      `${copy.designation},`,
      `${authority},`,
      'Nandurbar, Maharashtra',
      '',
      `${copy.subject}: ${draftSubject.trim()}`,
      '',
      copy.salutation,
      '',
      draftBody.trim(),
      '',
      copy.thanks,
      '',
      copy.closing,
      '',
      `${copy.signature}: ${blank}`,
      `${copy.printedName}: ${complainantName.trim() || blank}`,
    ].join('\n');
  }

  function openEmailDraft() {
    const channel = routeResult?.officialChannels?.find((item) => item.type === 'EMAIL');
    const recipient = filingEmail.trim() || channel?.value || '';
    if (!/^\S+@\S+\.\S+$/.test(recipient.trim())) {
      setFilingActionStatus('No verified recipient email is available for this route.');
      return;
    }
    if (!draftSubject.trim() || !draftBody.trim()) {
      setFilingActionStatus('Add both subject and complaint body before opening email.');
      return;
    }
    if (channel) setFilingChannelId(channel.channelId);
    cacheCurrentFilingDraft();
    const marathi = draftLanguage === 'MR';
    const senderLocation = [complainantAddress.trim(), complainantCity.trim(), complainantState.trim(), complainantPincode.trim()].filter(Boolean).join(', ');
    const contactLines = [
      complainantName.trim(),
      senderLocation,
      complainantPhone.trim(),
      complainantEmail.trim(),
    ].filter(Boolean);
    const emailBody = [
      marathi ? 'आदरणीय महोदय/महोदया,' : 'Respected Sir/Madam,',
      '',
      draftBody.trim(),
      ...(evidenceImage ? ['', marathi ? 'संदर्भासाठी सहाय्यक छायाचित्र उपलब्ध आहे.' : 'A supporting photograph is available for reference.'] : []),
      '',
      marathi ? 'आपल्या कार्यवाहीबद्दल धन्यवाद.' : 'Thank you for your attention to this matter.',
      '',
      marathi ? 'आपला/आपली विश्वासू,' : 'Yours sincerely,',
      ...contactLines,
    ].join('\n');
    const mailtoUrl = `mailto:${recipient.trim()}?subject=${encodeURIComponent(draftSubject.trim())}&body=${encodeURIComponent(emailBody)}`;
    try {
      window.location.href = mailtoUrl;
    } catch (error) {
      void navigator.clipboard.writeText(`${t('Complaint subject')}: ${draftSubject.trim()}\n\n${draftBody.trim()}`);
      setFilingActionStatus('Could not open email app. Complaint text copied so you can paste it manually.');
      return;
    }
    recordFilingAction('EMAIL');
    setFilingActionStatus('Your email app was opened with an editable draft. Seewik did not send it.');
  }

  function dmaFilingPackText() {
    return `${t('Complaint subject')}: ${draftSubject.trim()}\n${t('Full name')}: ${complainantName.trim()}\n${t('Email address')}: ${complainantEmail.trim()}\n${t('Phone number (optional)')}: ${complainantPhone.trim()}\n${t('Correspondence address')}: ${complainantAddress.trim()}\n${t('City')}: ${complainantCity.trim()}\n${t('Pincode')}: ${complainantPincode.trim()}\n${t('State')}: ${complainantState.trim()}\n\n${t('Description of Complaint/Grievance')}:\n${draftBody.trim()}`;
  }

  async function copyDmaPack() {
    await navigator.clipboard.writeText(dmaFilingPackText());
    setFilingActionStatus('The editable DMA filing fields were copied.');
  }

  async function copyFilingField(label: string, value: string) {
    await navigator.clipboard.writeText(value);
    setFilingActionStatus(`${label} copied.`);
  }

  function openDmaForm() {
    const channel = routeResult?.officialChannels?.find((item) => item.type === 'ONLINE_FORM');
    if (!channel) {
      setFilingActionStatus('No verified official complaint form is available for this route.');
      return;
    }
    cacheCurrentFilingDraft();
    window.open(channel.localizedValues?.[draftLanguage] ?? channel.value, '_blank', 'noopener,noreferrer');
    setFilingChannelId(channel.channelId);
    recordFilingAction('DMA');
    setFilingActionStatus('The official DMA form was opened. Copy the prepared fields into it and attach the photo manually if needed.');
  }

  async function shareLetter() {
    const text = printableLetterText();
    const channel = routeResult?.officialChannels?.find((item) => item.type === 'IN_PERSON');
    if (channel) setFilingChannelId(channel.channelId);
    if (navigator.share) {
      try {
        await navigator.share({ title: draftSubject.trim(), text });
        recordFilingAction('PRINT');
        setFilingActionStatus('Your device share sheet was opened. Seewik did not submit the letter.');
        return;
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') return;
      }
    }
    await navigator.clipboard.writeText(text);
    recordFilingAction('PRINT');
    setFilingActionStatus('The letter was copied because sharing is unavailable on this device.');
  }

  function printLetter() {
    const channel = routeResult?.officialChannels?.find((item) => item.type === 'IN_PERSON');
    if (channel) setFilingChannelId(channel.channelId);
    cacheCurrentFilingDraft();
    const printWindow = window.open('', '_blank', 'popup,width=860,height=1000');
    if (!printWindow) {
      setFilingActionStatus('The print window was blocked. Allow pop-ups for Seewik and try again.');
      return;
    }
    printWindow.opener = null;
    const printDocument = printWindow.document;
    printDocument.title = `${draftSubject.trim() || 'Civic complaint'} - Seewik`;
    printDocument.documentElement.lang = draftLanguage === 'MR' ? 'mr' : 'en';
    const style = printDocument.createElement('style');
    style.textContent = `
      @page { size: A4; margin: 22mm 20mm; }
      * { box-sizing: border-box; }
      body { margin: 0; background: #fff; color: #17202a; font-family: Georgia, 'Times New Roman', serif; font-size: 12pt; line-height: 1.6; }
      main { max-width: 170mm; margin: 0 auto; }
      header { margin-bottom: 28px; padding-bottom: 14px; border-bottom: 2px solid #173f67; }
      header strong { display: block; color: #173f67; font-family: Arial, sans-serif; font-size: 18pt; letter-spacing: .08em; }
      header span { color: #526273; font-family: Arial, sans-serif; font-size: 9.5pt; }
      pre { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; }
      footer { margin-top: 32px; padding-top: 12px; border-top: 1px solid #ccd5df; color: #526273; font-family: Arial, sans-serif; font-size: 9pt; }
      @media screen { body { padding: 32px; } main { padding: 28px 34px; border: 1px solid #d8e0e8; box-shadow: 0 12px 32px rgba(20, 48, 76, .12); } }
      @media print { footer { color: #333; } }
    `;
    printDocument.head.appendChild(style);
    const letter = printDocument.createElement('main');
    const header = printDocument.createElement('header');
    const brand = printDocument.createElement('strong');
    brand.textContent = 'SEEWIK';
    const descriptor = printDocument.createElement('span');
    descriptor.textContent = draftLanguage === 'MR' ? 'सीविकने तयार केलेले नागरी तक्रारपत्र' : 'Prepared civic complaint letter';
    header.append(brand, descriptor);
    const content = printDocument.createElement('pre');
    content.textContent = printableLetterText();
    const footer = printDocument.createElement('footer');
    footer.textContent = draftLanguage === 'MR'
      ? 'सीविकच्या मदतीने तयार केले आहे. हे पत्र तपासा, स्वाक्षरी करा आणि संबंधित कार्यालयात जमा करा.'
      : 'Prepared with Seewik. Review, sign and submit this letter to the responsible office.';
    letter.append(header, content, footer);
    printDocument.body.appendChild(letter);
    printDocument.close();
    recordFilingAction('PRINT');
    setFilingActionStatus('The print window was opened. Write your name and sign the printed letter before submitting it.');
    window.setTimeout(() => {
      printWindow.focus();
      printWindow.print();
    }, 250);
  }

  function applyInitiativeTemplate(value: string) {
    setInitiativeCategory(value);
    const template = INITIATIVE_TEMPLATES.find((item) => item.value === value);
    if (!template) return;
    setInitiativeTitle(template.title ? t(template.title) : '');
    setInitiativeDescription(template.description ? t(template.description) : '');
    setInitiativeNeededItems(template.neededItems.map((item) => t(item)));
    setInitiativeStatus('Activity details prefilled. Review and edit every field before publishing.');
  }

  function addInitiativeNeededItem() {
    const item = initiativeNeededItemDraft.trim();
    if (!item || initiativeNeededItems.includes(item) || initiativeNeededItems.length >= 8) return;
    setInitiativeNeededItems((items) => [...items, item]);
    setInitiativeNeededItemDraft('');
  }

  function activityDateTimeValue(date: Date) {
    return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
  }

  function normaliseActivityTime(value: string) {
    if (!value) return '';
    const selected = new Date(value);
    if (!Number.isFinite(selected.getTime())) return value;
    const hour = new Date(selected);
    hour.setMinutes(0, 0, 0);
    const allowedMinutes = [0, 10, 15, 20, 30, 40, 45, 50, 60];
    const nearestMinute = allowedMinutes.reduce((nearest, minute) => (
      Math.abs(minute - selected.getMinutes()) < Math.abs(nearest - selected.getMinutes()) ? minute : nearest
    ), 0);
    hour.setMinutes(nearestMinute);
    return activityDateTimeValue(hour);
  }

  function changeInitiativeStartAt(rawValue: string) {
    const value = normaliseActivityTime(rawValue);
    const previousStart = Date.parse(initiativeStartAt);
    const previousEnd = Date.parse(initiativeEndAt);
    const nextStart = Date.parse(value);
    setInitiativeStartAt(value);
    if (!Number.isFinite(nextStart)) return;
    const currentDuration = Number.isFinite(previousStart)
      && Number.isFinite(previousEnd)
      && previousEnd > previousStart
      ? previousEnd - previousStart
      : 2 * 60 * 60 * 1000;
    setInitiativeEndAt(activityDateTimeValue(new Date(nextStart + currentDuration)));
  }

  function initialiseInitiativeStartTime() {
    if (initiativeStartAt) return;
    const start = new Date();
    if (start.getMinutes() || start.getSeconds() || start.getMilliseconds()) start.setHours(start.getHours() + 1);
    start.setMinutes(0, 0, 0);
    changeInitiativeStartAt(activityDateTimeValue(start));
  }

  function activityJoiningLabel(initiative: Initiative) {
    if (initiative.joinerCount === null) return '';
    return initiative.capacity
      ? `${initiative.joinerCount} ${t('of')} ${initiative.capacity} ${t('joining')}`
      : `${initiative.joinerCount} ${t('joining')}`;
  }

  function activityReminderLabel(initiative: Initiative) {
    const date = new Date(initiative.startAt);
    return new Intl.DateTimeFormat(communityLocale, { weekday: 'long', hour: 'numeric', minute: '2-digit' }).format(date);
  }

  function addActivityToCalendar(initiative: Initiative) {
    const calendarDate = (value: string) => new Date(value).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
    const escapeCalendar = (value: string) => value.replace(/\\/g, '\\\\').replace(/\n/g, '\\n').replace(/,/g, '\\,').replace(/;/g, '\\;');
    const calendar = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Seewik//Community Activity//EN', 'BEGIN:VEVENT', `UID:${initiative.initiativeId}@seewik.web.app`, `DTSTART:${calendarDate(initiative.startAt)}`, `DTEND:${calendarDate(initiative.endAt || initiative.startAt)}`, `SUMMARY:${escapeCalendar(initiative.title)}`, `DESCRIPTION:${escapeCalendar(initiative.description)}`, `LOCATION:${escapeCalendar(initiative.placeName)}`, `URL:${window.location.href}`, 'END:VEVENT', 'END:VCALENDAR'].join('\r\n');
    const url = URL.createObjectURL(new Blob([calendar], { type: 'text/calendar;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `${initiative.title.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '') || 'seewik-activity'}.ics`;
    link.click();
    URL.revokeObjectURL(url);
    setInitiativeStatus('Calendar reminder downloaded.');
  }

  async function shareActivity(initiative: Initiative) {
    const url = `${window.location.origin}/initiatives/${encodeURIComponent(initiative.initiativeId)}`;
    if (navigator.share) {
      try {
        await navigator.share({ title: initiative.title, text: `${initiative.title} · ${initiative.placeName}`, url });
        setInitiativeStatus('Your device share menu was opened.');
        return;
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') return;
      }
    }
    try {
      await navigator.clipboard.writeText(url);
    } catch {
      const field = document.createElement('textarea');
      field.value = url;
      document.body.appendChild(field);
      field.select();
      document.execCommand('copy');
      field.remove();
    }
    setInitiativeStatus('Activity link copied.');
  }

  async function fileReviewedReport(dedupeOverride = false, requireActionConfirmation = true) {
    if (requireActionConfirmation && (!selectedFilingMethod || !filingActionIsCurrent())) {
      setLifecycleStatus('Open, print or share the current prepared complaint before confirming submission.');
      return;
    }
    if (!draftSubject.trim() || !draftBody.trim()) {
      setLifecycleStatus('Add a subject and complaint before confirming submission.');
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
    try {
      const token = await user.getIdToken();
      const eligible = reports.filter((report) => report.status !== 'DRAFT');
      const summaries = await Promise.all(eligible.map(async (report) => {
        const summary = await fetchFollowUpSummary(report.id, token);
        return [report.id, summary] as const;
      }));
      setFollowUpsByReport(Object.fromEntries(summaries));
    } catch {
      setFollowUpsByReport({});
    }
    setReportsStatus(reports.length ? `${reports.length} saved report${reports.length === 1 ? '' : 's'}` : 'No saved reports yet.');
  }

  async function fetchFollowUpSummary(reportId: string, token?: string) {
    const idToken = token ?? await sessionToken(true);
    const response = await fetch(`${API_URL}/api/reports/${encodeURIComponent(reportId)}/follow-ups`, {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message ?? `Follow-up could not be loaded (${response.status})`);
    return result as FollowUpSummary;
  }

  async function refreshFollowUp(reportId: string) {
    const summary = await fetchFollowUpSummary(reportId);
    setFollowUpsByReport((current) => ({ ...current, [reportId]: summary }));
    return summary;
  }

  async function recordFollowUp(action: 'UNRESOLVED' | 'UNSURE' | 'ESCALATION_SENT', channelId?: EscalationChannelId) {
    if (!selectedReport) return;
    setFollowUpBusy(true);
    setFollowUpStatus(action === 'ESCALATION_SENT' ? 'Recording your confirmation…' : 'Saving your answer…');
    try {
      const idToken = await sessionToken(true);
      const response = await fetch(`${API_URL}/api/reports/${encodeURIComponent(selectedReport.id)}/follow-ups`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${idToken}` },
        body: JSON.stringify({
          action,
          idempotencyKey: crypto.randomUUID(),
          channelId: action === 'ESCALATION_SENT' ? channelId : null,
          language: action === 'ESCALATION_SENT' ? escalationLanguage : null,
        }),
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.message ?? `Follow-up could not be saved (${response.status})`);
      setFollowUpsByReport((current) => ({ ...current, [selectedReport.id]: result.summary as FollowUpSummary }));
      setFollowUpStatus(action === 'UNSURE'
        ? 'We will ask again in 3 days.'
        : action === 'UNRESOLVED'
          ? 'Your answer was saved. Escalation options are ready.'
          : 'Your sent confirmation was recorded. No points were awarded.');
    } catch (error) {
      setFollowUpStatus(citizenSafeError(error, 'The follow-up could not be saved.'));
    } finally {
      setFollowUpBusy(false);
    }
  }

  function currentFollowUp() {
    return selectedReport ? followUpsByReport[selectedReport.id] ?? null : null;
  }

  function escalationRouteIsCurrent(summary = currentFollowUp()) {
    return Boolean(selectedReport && summary
      && summary.routeId && summary.routeId === selectedReport.routeId
      && summary.packVersion && summary.packVersion === selectedReport.packVersion
      && summary.routeSnapshotHash && summary.routeSnapshotHash === selectedReport.routeSnapshotHash);
  }

  function escalationRecipient(channelId: EscalationChannelId) {
    if (channelId !== 'NMC_FOLLOW_UP') return ESCALATION_CHANNELS.find((item) => item.id === channelId)?.recipient ?? '';
    return selectedReport?.routeSnapshot?.officialChannels?.find((channel) => channel.type === 'EMAIL')?.value || 'conandurbarnmc@gmail.com';
  }

  function escalationDraftKey(channelId: EscalationChannelId, languageToUse: 'MR' | 'EN') {
    const summary = currentFollowUp();
    return [selectedReport?.id, summary?.cycleNumber, channelId, languageToUse, summary?.routeSnapshotHash].join('|');
  }

  function cacheEscalationDraft() {
    if (!selectedEscalationChannel || !escalationSubject.trim() || !escalationBody.trim()) return;
    escalationDrafts.current[escalationDraftKey(selectedEscalationChannel, escalationLanguage)] = {
      subject: escalationSubject,
      body: escalationBody,
    };
  }

  function buildEscalationDraft(channelId: EscalationChannelId, languageToUse: 'MR' | 'EN') {
    if (!selectedReport) return { subject: '', body: '' };
    const summary = currentFollowUp();
    const marathi = languageToUse === 'MR';
    const issue = issueLabel(selectedReport.confirmedIssueType, marathi ? 'mr' : 'en');
    const filed = timestampMillis(selectedReport.filedAt)
      ? new Intl.DateTimeFormat(marathi ? 'mr-IN' : 'en-IN', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(timestampMillis(selectedReport.filedAt)))
      : '';
    const recipientTitle = channelId === 'NMC_FOLLOW_UP'
      ? (marathi ? 'नंदुरबार नगर परिषद' : 'Nandurbar Nagar Palika')
      : channelId === 'DISTRICT_JOINT_COMMISSIONER'
        ? (marathi ? 'जिल्हा सह आयुक्त कार्यालय, नंदुरबार' : 'District Joint Commissioner Office, Nandurbar')
        : (marathi ? 'नगर परिषद स्थानिक समस्या व तक्रारी कक्ष क्रमांक ६' : 'DMA Desk 6 - Municipal Council Local Issues and Complaints');
    const recurrence = summary?.recurrence
      ? (marathi ? 'ही समस्या यापूर्वी पडताळलेल्या निराकरणानंतर पुन्हा उद्भवली आहे.' : 'This issue recurred after a previously verified resolution.')
      : '';
    const acknowledgement = selectedReport.acknowledgementId
      ? (marathi ? `पोच क्रमांक: ${selectedReport.acknowledgementId}` : `Acknowledgement: ${selectedReport.acknowledgementId}`)
      : '';
    const originalSummary = selectedReport.draftBody.trim().slice(0, 700);
    if (marathi) {
      return {
        subject: `पाठपुरावा: ${issue} - ${selectedReport.prabhagId}`,
        body: [
          'आदरणीय महोदय/महोदया,',
          '',
          `मी ${complainantName.trim() || 'नागरिक'} असून ${issue} या न सुटलेल्या नागरी समस्येबाबत ${recipientTitle} यांच्याकडे पाठपुरावा करत आहे.`,
          '',
          `समस्येचा प्रकार: ${issue}`,
          `स्थान / प्रभाग: ${selectedReport.prabhagId}`,
          filed ? `मूळ तक्रार दाखल केल्याची तारीख: ${filed}` : '',
          acknowledgement,
          recurrence,
          '',
          'मूळ तक्रारीचा संक्षिप्त मजकूर:',
          originalSummary,
          '',
          channelId === 'NMC_FOLLOW_UP'
            ? 'कृपया या तक्रारीची सद्यस्थिती कळवून आवश्यक कार्यवाही करावी.'
            : 'ही तक्रार नगर परिषदेकडे नोंदवल्यानंतरही समस्या सुटलेली नाही. कृपया प्रकरणाची तपासणी करून योग्य कार्यवाही करावी.',
          '',
          'धन्यवाद.',
          complainantName.trim(),
          complainantEmail.trim(),
          complainantPhone.trim(),
        ].filter(Boolean).join('\n'),
      };
    }
    return {
      subject: `Follow-up: ${issue} - ${selectedReport.prabhagId}`,
      body: [
        'Dear Sir/Madam,',
        '',
        `I am ${complainantName.trim() || 'a resident'} and I am following up with ${recipientTitle} regarding an unresolved civic issue involving ${issue}.`,
        '',
        `Issue category: ${issue}`,
        `Location / Prabhag: ${selectedReport.prabhagId}`,
        filed ? `Original filing date: ${filed}` : '',
        acknowledgement,
        recurrence,
        '',
        'Original complaint summary:',
        originalSummary,
        '',
        channelId === 'NMC_FOLLOW_UP'
          ? 'Please provide the current status of this complaint and arrange the necessary action.'
          : 'The issue remains unresolved after it was reported to Nandurbar Nagar Palika. Please review the matter and arrange the appropriate action.',
        '',
        'Thank you.',
        complainantName.trim(),
        complainantEmail.trim(),
        complainantPhone.trim(),
      ].filter(Boolean).join('\n'),
    };
  }

  function selectEscalationChannel(channelId: EscalationChannelId, languageToUse = escalationLanguage) {
    cacheEscalationDraft();
    setSelectedEscalationChannel(channelId);
    setEscalationActionOpened(false);
    if (!escalationRouteIsCurrent()) {
      setEscalationSubject('');
      setEscalationBody('');
      setFollowUpStatus('The verified route changed. Refresh this report before preparing an escalation.');
      return;
    }
    const cached = escalationDrafts.current[escalationDraftKey(channelId, languageToUse)];
    const draft = cached ?? buildEscalationDraft(channelId, languageToUse);
    setEscalationSubject(draft.subject);
    setEscalationBody(draft.body);
    setFollowUpStatus('');
    window.setTimeout(() => document.getElementById('escalation-preparation')?.focus(), 0);
  }

  function changeEscalationLanguage(languageToUse: 'MR' | 'EN') {
    cacheEscalationDraft();
    setEscalationLanguage(languageToUse);
    setEscalationLanguageManuallySelected(true);
    setEscalationActionOpened(false);
    if (!selectedEscalationChannel) return;
    const cached = escalationDrafts.current[escalationDraftKey(selectedEscalationChannel, languageToUse)];
    const draft = cached ?? buildEscalationDraft(selectedEscalationChannel, languageToUse);
    setEscalationSubject(draft.subject);
    setEscalationBody(draft.body);
  }

  async function copyEscalationEmail() {
    if (!selectedEscalationChannel || !escalationRouteIsCurrent()) {
      setFollowUpStatus('This escalation draft is stale. Refresh the report before using it.');
      return;
    }
    await navigator.clipboard.writeText(`To: ${escalationRecipient(selectedEscalationChannel)}\nSubject: ${escalationSubject.trim()}\n\n${escalationBody.trim()}`);
    setEscalationActionOpened(true);
    setFollowUpStatus('The editable email was copied. Seewik has not sent it.');
  }

  async function openEscalationEmail() {
    if (!selectedEscalationChannel || !escalationRouteIsCurrent()) {
      setFollowUpStatus('This escalation draft is stale. Refresh the report before using it.');
      return;
    }
    const recipient = escalationRecipient(selectedEscalationChannel);
    const mailto = `mailto:${encodeURIComponent(recipient)}?subject=${encodeURIComponent(escalationSubject.trim())}&body=${encodeURIComponent(escalationBody.trim())}`;
    if (mailto.length > 1900) {
      await copyEscalationEmail();
      setFollowUpStatus('The draft was copied because it is too long for a reliable email link. Paste it into your email app.');
      return;
    }
    setEscalationActionOpened(true);
    window.location.href = mailto;
    setFollowUpStatus('Your email app was opened. Seewik has not sent the message.');
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
    setComplaintLanguageManuallySelected(true);
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
    setPrivatePointsStatus('Loading your contribution record…');
    try {
      const token = await sessionToken();
      const summary = await fetchPrivatePoints(token);
      setPrivatePoints(summary);
      setPointsTotal(summary.lifetimePoints);
      setHeaderPoints(summary.lifetimePoints);
      if (accountUid) window.localStorage.setItem(`${PRIVATE_POINTS_CACHE_PREFIX}${accountUid}`, String(summary.lifetimePoints));
      setPrivatePointsStatus('');
    } catch (error) {
      setPrivatePointsStatus(citizenSafeError(error, 'Your points could not be loaded.'));
      throw error;
    }
  }

  async function refreshRewards() {
    setRewardStatus('Loading example rewards…');
    try {
      const token = await sessionToken();
      setRewardOverview(await fetchRewards(token));
      setRewardStatus('');
    } catch (error) {
      setRewardStatus(citizenSafeError(error, 'Example rewards could not be loaded.'));
      throw error;
    }
  }

  async function createRewardClaim(couponId: string) {
    setRewardBusyId(couponId);
    setRewardStatus('Creating your example code…');
    try {
      const token = await sessionToken(true);
      await claimReward(token, couponId);
      await Promise.all([refreshRewards(), refreshDerivedPoints()]);
      setRewardStatus('Example reward claimed. Your points did not decrease.');
    } catch (error) {
      setRewardStatus(citizenSafeError(error, 'The example reward could not be claimed.'));
    } finally {
      setRewardBusyId('');
    }
  }

  async function confirmRewardUse(claimId: string) {
    setRewardBusyId(claimId);
    setRewardStatus('Marking the example code as used…');
    try {
      const token = await sessionToken(true);
      await simulateRewardUse(token, claimId);
      setRewardUseConfirmation('');
      await Promise.all([refreshRewards(), refreshDerivedPoints()]);
      setRewardStatus('Used in simulation. No shop verified or accepted this code, and your points did not decrease.');
    } catch (error) {
      setRewardStatus(citizenSafeError(error, 'The simulated use could not be recorded.'));
    } finally {
      setRewardBusyId('');
    }
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
      routeSnapshotHash: routeSnapshotHashAfterTransition(isFiling, result.routeSnapshotHash, report.routeSnapshotHash),
      overdueEligibility: isFiling ? 'OVERDUE_UNKNOWN' : report.overdueEligibility,
    } : report);
    setSavedReports((reports) => reports.map((report) => report.id === draftDocumentId ? { ...report, status: result.toStatus, updatedAt, acknowledgementId: isFiling ? acknowledgementId.trim() || undefined : report.acknowledgementId, filingChannelId: isFiling ? filingChannelId || undefined : report.filingChannelId, routeSnapshot: isFiling && routeResult ? routeResult : report.routeSnapshot, routeSnapshotHash: routeSnapshotHashAfterTransition(isFiling, result.routeSnapshotHash, report.routeSnapshotHash), overdueEligibility: isFiling ? 'OVERDUE_UNKNOWN' : report.overdueEligibility } : report));
    await refreshDerivedPoints();
    if (result.toStatus === 'FILED') {
      removeFilingContactDraft(window.sessionStorage);
      clearFilingActionReceipt();
      navigate('report-detail', false, draftDocumentId);
    }
  }

  const myInitiativesSection = <section id="my-initiatives" className="actions-subsection initiative-memberships">
    <div className="actions-section-heading">
      <div><h2>{t('My Initiatives')}</h2><p>{t('Initiatives you organise or join stay here, including their final status.')}</p></div>
      <button className="secondary" onClick={() => loadMyInitiatives().catch((error) => setInitiativeStatus(citizenSafeError(error, 'Your initiatives could not be loaded.')))}>{t('Refresh')}</button>
    </div>
    {initiativeStatus && <div className="status-panel state-warning" role="status" aria-live="polite">{runtimeMessage(initiativeStatus)}</div>}
    {!myInitiatives.length && <div className="empty-state"><b>{t('No joined initiatives yet.')}</b><p>{t('Create an initiative or join one nearby.')}</p><button onClick={() => navigate('initiatives')}>{t('Explore initiatives')}</button></div>}
    <div className="initiative-list compact-list" aria-live="polite">
      {myInitiatives.map((initiative) => <article className={`card initiative-card my-action-initiative-card ${initiative.status === 'COMPLETED' ? 'is-completed' : initiative.status === 'CANCELLED' ? 'is-cancelled' : ''}`} key={`mine-${initiative.initiativeId}`}>
        <div className="initiative-card-top">
          <span className={`initiative-role-chip ${initiative.status === 'COMPLETED' ? 'role-completed' : initiative.status === 'CANCELLED' ? 'role-cancelled' : initiative.role === 'ORGANISER' ? 'role-organising' : initiative.role === 'REQUESTED' ? 'role-requested' : 'role-joined'}`}>{initiative.role === 'ORGANISER' ? t('Organising') : initiative.role === 'REQUESTED' ? t('Approval requested') : t('Joined')}</span>
          <span className="initiative-card-status">{localizedStatus(language, initiative.status)} · {initiative.joinerCount} {t('joined')}</span>
        </div>
        <h3>{initiative.title}</h3>
        <p className="my-action-meeting-point"><span>{timestampLabel(initiative.startAt, language)} · {initiative.placeName}</span>{initiative.mapsUrl && <a href={initiative.mapsUrl} target="_blank" rel="noreferrer">⌖ {t('Open in Google Maps')}</a>}</p>
        {initiative.status === 'CANCELLED' && initiative.cancellationReason && <p><b>{t('Cancellation reason')}:</b> {initiative.cancellationReason}</p>}
        {initiative.canManage && initiative.status === 'PUBLISHED' && <div className="initiative-manage">
          {initiative.participationMode === 'APPROVAL_REQUIRED' && <section className="join-request-panel">
            <div><b>{t('Join requests')}</b><span>{(initiativeJoinRequests[initiative.initiativeId] ?? []).length} {t('pending')}</span></div>
            {(initiativeJoinRequests[initiative.initiativeId] ?? []).map((request, index) => <article key={request.requestId}><span><strong>{t('Citizen request')} {index + 1}</strong><small>{timestampLabel(request.requestedAt, language)}</small></span><div><button className="secondary" onClick={() => requestLinkedMutation(() => reviewInitiativeJoinRequest(initiative.initiativeId, request.requestId, 'decline').catch((error) => setInitiativeStatus(citizenSafeError(error, 'The join request could not be reviewed.'))))}>{t('Decline')}</button><button onClick={() => requestLinkedMutation(() => reviewInitiativeJoinRequest(initiative.initiativeId, request.requestId, 'approve').catch((error) => setInitiativeStatus(citizenSafeError(error, 'The join request could not be reviewed.'))))}>{t('Accept')}</button></div></article>)}
            {!(initiativeJoinRequests[initiative.initiativeId] ?? []).length && <small>{t('No pending join requests.')}</small>}
          </section>}
          <label htmlFor={`cancel-reason-${initiative.initiativeId}`}>{t('Cancellation reason')}
            <input id={`cancel-reason-${initiative.initiativeId}`} maxLength={300} value={cancellationReasons[initiative.initiativeId] ?? ''} onChange={(event) => setCancellationReasons((values) => ({ ...values, [initiative.initiativeId]: event.target.value }))} placeholder={t('Required only when cancelling')} />
          </label>
          <div className="draft-actions">
            <button className="secondary" disabled={!cancellationReasons[initiative.initiativeId]?.trim() || initiative.codeAttendanceCount > 0} title={initiative.codeAttendanceCount > 0 ? t('Cancellation is unavailable after code attendance is recorded') : undefined} onClick={() => requestLinkedMutation(() => changeInitiativeStatus(initiative.initiativeId, 'CANCELLED').catch((error) => setInitiativeStatus(citizenSafeError(error, 'The initiative could not be updated.'))))}>{t('Cancel activity')}</button>
            <button disabled={Date.now() < Date.parse(initiative.endAt || initiative.startAt)} title={Date.now() < Date.parse(initiative.endAt || initiative.startAt) ? t('Available after the scheduled activity time') : undefined} onClick={() => requestLinkedMutation(() => changeInitiativeStatus(initiative.initiativeId, 'COMPLETED').catch((error) => setInitiativeStatus(citizenSafeError(error, 'The initiative could not be updated.'))))}>{t('Mark completed')}</button>
          </div>
        </div>}
        {initiative.role !== 'REQUESTED' && <div className="attendance-summary" aria-label={t('Attendance summary')}>
          <p><strong>{initiative.codeAttendanceCount} {t('of')} {initiative.joinerCount}</strong> {t('joiners recorded attendance using the organiser’s code.')}</p>
          <p><strong>{initiative.selfAttendanceCount} {t('of')} {initiative.joinerCount}</strong> {t('joiners reported attending.')}</p>
          <small>{t('The organiser is not included in the joiner count. Neither attendance method is independently verified.')}</small>
        </div>}
        {initiative.attendanceBasis === 'ORGANISER_CODE_ATTESTED' && <div className="attendance-result state-success" role="status"><b>{t('Attendance recorded using the organiser’s code')}</b><span>{t('You earned 20 points. This is organiser-mediated attendance, not independent verification.')}</span></div>}
        {initiative.attendanceBasis === 'SELF_ATTESTED' && <div className="attendance-result state-success" role="status"><b>{t('You reported attending')}</b><span>{t('Self-attendance earns zero points and is not verified.')}</span></div>}
        {initiative.canViewAttendanceCode && <div className="attendance-code-panel">
          <div><b>{t('Organiser attendance code')}</b><span>{t('Share this six-digit code only with joined participants who attended.')}</span></div>
          {activeAttendanceCodes[initiative.initiativeId]
            ? <><output aria-label={t('Current organiser attendance code')} className="attendance-code">{activeAttendanceCodes[initiative.initiativeId].code}</output><small>{t('Rotates at')} {timestampLabel(activeAttendanceCodes[initiative.initiativeId].rotatesAt, language)} · {t('Code window closes')} {timestampLabel(activeAttendanceCodes[initiative.initiativeId].codeWindowEndsAt, language)}</small><button className="secondary" onClick={() => requestLinkedMutation(() => loadAttendanceCode(initiative.initiativeId).catch((error) => setInitiativeStatus(citizenSafeError(error, 'The attendance code could not be loaded.'))))}>{t('Refresh code')}</button></>
            : <button onClick={() => requestLinkedMutation(() => loadAttendanceCode(initiative.initiativeId).catch((error) => setInitiativeStatus(citizenSafeError(error, 'The attendance code could not be loaded.'))))}>{t('Show attendance code')}</button>}
        </div>}
        {initiative.canUseOrganiserCode && !initiative.attendanceBasis && <div className="attendance-entry-panel">
          <label htmlFor={`attendance-code-${initiative.initiativeId}`}>{t('Enter organiser attendance code')}
            <input id={`attendance-code-${initiative.initiativeId}`} inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]*" maxLength={6} value={attendanceCodeInputs[initiative.initiativeId] ?? ''} onChange={(event) => setAttendanceCodeInputs((values) => ({ ...values, [initiative.initiativeId]: event.target.value.replace(/\D/g, '').slice(0, 6) }))} />
          </label>
          <button disabled={!/^\d{6}$/.test(attendanceCodeInputs[initiative.initiativeId] ?? '')} onClick={() => requestLinkedMutation(() => submitAttendanceCode(initiative.initiativeId).catch((error) => setInitiativeStatus(citizenSafeError(error, 'Attendance could not be recorded.'))))}>{t('Record code attendance · 20 points')}</button>
          <small>{t('The code rotates every 10 minutes. Five incorrect attempts are allowed per code period.')}</small>
        </div>}
        {initiative.canSelfAttend && !initiative.attendanceBasis && <div className="attendance-entry-panel">
          <p>{t('The organiser-code window has closed. You can report your own attendance for seven days after completion.')}</p>
          <button className="secondary" onClick={() => requestLinkedMutation(() => recordSelfAttendance(initiative.initiativeId).catch((error) => setInitiativeStatus(citizenSafeError(error, 'Attendance could not be recorded.'))))}>{t('I attended · 0 points')}</button>
          <small>{t('This is a self-report. Seewik does not verify it.')}</small>
        </div>}
        {initiative.role === 'PARTICIPANT' && !initiative.attendanceBasis && !initiative.canUseOrganiserCode && !initiative.canSelfAttend && <small>{initiative.status === 'CANCELLED' ? t('Attendance is unavailable because this activity was cancelled.') : initiative.status === 'COMPLETED' ? t('No attendance option is currently available.') : t('Code attendance opens at the scheduled start time.')}</small>}
        <small>{t('Creating or joining alone does not earn points.')}</small>
      </article>)}
    </div>
  </section>;

  const demoStates = [
    ['DRAFT', t('Draft saved; nothing submitted')],
    ['FILED', t('Citizen confirms manual filing · +5 demo points')],
    ['OVERDUE', t('Synthetic verified dueAt passes on the simulated clock')],
    ['CLAIMED_FIXED', t('Repair claim recorded')],
    ['VERIFIED_FIXED', t('Citizen attestation recorded · +60 demo points')],
    ['REOPENED', t('Issue recurred; no points awarded')],
  ];

  const navCurrent = (active: boolean) => active ? 'page' as const : undefined;
  const homeFirstName = accountName?.trim().split(/\s+/)[0] || t('Citizen');
  const linkedAccount = accountState === 'GOOGLE_LINKED';
  const communityLocale = language === 'mr' ? 'mr-IN' : language === 'hi' ? 'hi-IN' : 'en-IN';
  const communityDistanceLabel = (distanceKm: number) => distanceKm < 1
    ? `${Math.round(distanceKm * 1000).toLocaleString(communityLocale)} m`
    : `${distanceKm.toLocaleString(communityLocale, { minimumFractionDigits: 1, maximumFractionDigits: 1 })} km`;
  const communityDateLimit = communityDateRange === 'WEEK' ? Date.now() + 7 * 24 * 60 * 60 * 1000 : communityDateRange === 'MONTH' ? Date.now() + 30 * 24 * 60 * 60 * 1000 : Number.POSITIVE_INFINITY;
  const visibleCommunityInitiatives = initiatives.filter((initiative) => (communityCategory === 'ALL' || initiative.category === communityCategory) && Date.parse(initiative.startAt) <= communityDateLimit);
  const selectedInitiativeTemplate = INITIATIVE_TEMPLATES.find((template) => template.value === initiativeCategory);
  const openInitiativeFormStep = (step: number) => {
    if (step > initiativeHighestStep) return;
    setInitiativeFormStep(step);
    window.setTimeout(() => document.querySelector(`[data-initiative-step="${step}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0);
  };
  const advanceInitiativeForm = (step: number) => {
    const nextStep = Math.min(5, step + 1);
    setInitiativeHighestStep((highest) => Math.max(highest, nextStep));
    setInitiativeFormStep(nextStep);
    window.setTimeout(() => document.querySelector(`[data-initiative-step="${nextStep}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0);
  };
  const communityIcon = (category: string) => <AppIcon name={initiativeTypeIcon(category)} />;
  const emailChannel = routeResult?.officialChannels?.find((channel) => channel.type === 'EMAIL');
  const formChannel = routeResult?.officialChannels?.find((channel) => channel.type === 'ONLINE_FORM');
  const officeChannel = routeResult?.officialChannels?.find((channel) => channel.type === 'IN_PERSON');
  const filingAuthority = complaintDraft?.authorityLocalName || complaintDraft?.authority || routeResult?.authority || '';
  const filingRecipientEmail = filingEmail || emailChannel?.value || '';
  const filingLanguageChoices = [
    { value: 'EN' as const, label: 'English' },
    { value: 'MR' as const, label: language === 'hi' ? 'मराठी' : t('मराठी') },
  ];
  const filingLanguageLabel = (value: 'MR' | 'EN') => filingLanguageChoices.find((item) => item.value === value)?.label || (value === 'EN' ? 'English' : 'मराठी');
  const selectedFilingLabel = selectedFilingMethod === 'PRINT'
    ? t('Print and submit')
    : selectedFilingMethod === 'EMAIL'
      ? t('Email Nagar Palika')
      : selectedFilingMethod === 'DMA'
        ? t('Directorate of Municipal Administration')
        : '';
  const canConfirmFilings = Boolean(selectedFilingMethod && complaintDraft?.status === 'DRAFT_READY' && draftDocumentId && draftSubject.trim() && draftBody.trim() && filingActionIsCurrent());
  const activeFollowUp = selectedReport ? followUpsByReport[selectedReport.id] ?? null : null;
  const activeEscalationRoute = ESCALATION_CHANNELS.find((item) => item.id === selectedEscalationChannel);
  const followUpLifecycleActive = ['FILED', 'OVERDUE', 'REOPENED'].includes(reportStatus);

  return <>
    <a className="skip-link" href="#main-content">{t('Skip to main content')}</a>
    <main id="main-content">
    <header className="app-header">
      <button className="brand-button" onClick={() => navigate('home')} aria-label={t('Seewik home')}><strong>SEEWIK</strong><small>{t('Civic Action to Community Impact')}</small></button>
      <div className="header-actions">
        <nav className="desktop-nav" aria-label={t('Primary navigation')}>
          <button aria-current={navCurrent(screen === 'home')} className={screen === 'home' ? 'active' : ''} onClick={() => navigate('home')}>{t('Home')}</button>
          <button aria-current={navCurrent(screen === 'reports' || screen === 'report-detail')} className={screen === 'reports' || screen === 'report-detail' ? 'active' : ''} onClick={() => navigate('reports')}>{t('My Actions')}</button>
          <button aria-current={navCurrent(screen === 'initiatives' || screen === 'initiative-detail')} className={screen === 'initiatives' || screen === 'initiative-detail' ? 'active' : ''} onClick={() => navigate('initiatives')}>{t('Community')}</button>
          <button aria-current={navCurrent(screen === 'points')} className={screen === 'points' ? 'active' : ''} onClick={() => navigate('points')}>{t('Civic Card')}</button>
        </nav>
        <div className="language-switcher" role="group" aria-label={t('Language')}>
          {([{ value: 'en', label: 'EN', compactLabel: 'E', accessibleLabel: 'English', lang: 'en' }, { value: 'mr', label: 'मराठी', compactLabel: 'म', accessibleLabel: 'मराठी', lang: 'mr' }, { value: 'hi', label: 'हिंदी', compactLabel: 'ह', accessibleLabel: 'हिंदी', lang: 'hi' }] as const).map((option) => <button type="button" key={option.value} lang={option.lang} className={`language-option ${language === option.value ? 'active' : ''}`} aria-label={option.accessibleLabel} aria-pressed={language === option.value} onClick={() => setLanguage(option.value)}><span className="language-label-full">{option.label}</span><span className="language-label-compact" aria-hidden="true">{option.compactLabel}</span></button>)}
        </div>
        <button className="header-emergency" onClick={() => navigate('emergency')} aria-label={t('Open Emergency Information — 112')}><AppIcon name="phone" className="header-emergency-icon" /><strong>112</strong><span>{t('Emergency')}</span></button>
        <AccountControl
          state={accountState}
          dialog={accountDialog}
          name={accountName}
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
      <div><strong>{t('Device-only access')}</strong><span>{t('This work is saved only on this device until you connect Google.')}</span></div>
      <button className="secondary" onClick={openAccount}>{t('Connect Google')}</button>
    </aside>}
    {screen === 'review' && <div className="page-tools"><button className="secondary" onClick={startOver}>{t('Start over')}</button></div>}

    {screen === 'home' && <>
      <section className="home-greeting" aria-labelledby="home-greeting-title">
        {accountProfileLoading ? <div className="home-greeting-skeleton" role="status" aria-label={t('Loading your profile')}><span /><span /></div> : <>
          <div className="home-greeting-line">
            <h1 className="page-title" id="home-greeting-title">{t('Namaste')}, {linkedAccount ? homeFirstName : t('Citizen')}</h1>
            <button className="home-civic-points" onClick={() => navigate('points')} aria-label={`${headerPoints ?? 0} ${t('points')}`}>
              <AppIcon name="star" className="points-star" />
              <b>{headerPoints ?? 0}</b>
              <span>{t('points')}</span>
            </button>
          </div>
        </>}
      </section>
      <section className="home-actions" aria-label={t('Start using Seewik')}>
        <article className="home-primary-action improve-action">
          <h2>{t('Improve')}</h2>
          <p className="home-action-subtext">{t('Something needs fixing')}</p>
          <p className="home-action-examples">{t('Garbage · Roads · Drainage · Water · Streetlights')}</p>
          <button className="icon-button" onClick={() => navigate('new-report')}><AppIcon name="camera" />{t('Report a civic problem')}</button>
        </article>
        <article className="home-primary-action initiate-action">
          <h2>{t('Initiate')}</h2>
          <p className="home-action-subtext">{t('Start something good')}</p>
          <p className="home-action-examples">{t('Donation · Plantation · Cleanup · Fitness')}</p>
          <button className="icon-button" onClick={() => navigate('new-initiative')}><AppIcon name="users" />{t('Start a community activity')}</button>
        </article>
        <article><h2>{t('My Actions')}</h2><p>{t('Resume drafts and review filed reports. Filed reports can’t be edited.')}</p><button className="secondary" onClick={() => navigate('reports')}>{t('Open My Actions')}</button></article>
        <article><h2>{t('My Civic Card')}</h2><p>{t('See your civic contribution record and how your points were earned.')}</p><button className="secondary" onClick={() => navigate('points')}>{t('Open my Civic Card')}</button></article>
      </section>
      <section className="home-utility-links" aria-label={t('Learn and get help')}>
        <button onClick={() => navigate('awareness')}><span aria-hidden="true">?</span><span><strong>{t('Civic Awareness')}</strong><small>{t('Did you know? Duties, municipal work and official programmes')}</small></span></button>
        <button className="emergency-link" onClick={() => navigate('emergency')}><span aria-hidden="true">＋</span><span><strong>{t('Emergency Information')}</strong><small>{t('112 and verified Nandurbar helplines')}</small></span></button>
      </section>
      <RecognitionPanel panel={publicRecognition ? { ...publicRecognition, monthLabel: localizedMonthLabel(language, publicRecognition.monthLabel) } : null} loading={publicRecognitionLoading} status={publicRecognitionStatus} t={t} onReport={sendRecognitionReport} onRetry={loadPublicRecognition} />
    </>}

    {screen === 'initiatives' && <>
      <section className="community-page">
        <div className="community-heading"><h1 className="page-title">{t('Happening in your community')}</h1><p>{t('Browse upcoming civic activities across Nandurbar. Share your location only if you want to narrow the list.')}</p></div>
        <div className="community-controls" aria-label={t('Community activity controls')}>
          <button className={`community-chip ${communityNearMe ? 'active' : ''}`} onClick={() => communityNearMe ? setCommunityNearMe(false) : locateForInitiatives()}>{communityNearMe ? `${t('Within')} ${initiativeRadiusKm} km` : t('All Nandurbar')}</button>
          <button className={`community-chip ${communityFiltersOpen ? 'active' : ''}`} aria-expanded={communityFiltersOpen} aria-controls="community-filters" onClick={() => setCommunityFiltersOpen((open) => !open)}>≡ {communityFiltersOpen ? t('Hide filters') : t('Filter')}</button>
          <button className="community-chip community-start-chip" onClick={() => navigate('new-initiative')}>＋ {t('Start an initiative')}</button>
        </div>
        {communityFiltersOpen && <div id="community-filters" className="community-filter-panel">
          <label>{t('Activity type')}<select value={communityCategory} onChange={(event) => setCommunityCategory(event.target.value)}><option value="ALL">{t('All activity types')}</option>{INITIATIVE_TEMPLATES.map((template) => <option key={template.value} value={template.value}>{t(template.label)}</option>)}</select></label>
          <label>{t('Date')}<select value={communityDateRange} onChange={(event) => setCommunityDateRange(event.target.value)}><option value="ALL">{t('Any upcoming date')}</option><option value="WEEK">{t('Next 7 days')}</option><option value="MONTH">{t('Next 30 days')}</option></select></label>
          {communityNearMe && <label>{t('Distance')}<select value={initiativeRadiusKm} onChange={(event) => setInitiativeRadiusKm(Number(event.target.value))}><option value={2}>2 km</option><option value={5}>5 km</option><option value={10}>10 km</option><option value={25}>25 km</option></select></label>}
        </div>}
        {communityOffline && <div className="community-offline-note" role="status">{t('Offline cached activities')}{communityCachedAt ? ` · ${t('Last updated')} ${formatDateTime(language, communityCachedAt)}` : ''}</div>}
        {initiativeStatus && <div className="status-panel state-warning" role="status" aria-live="polite">{runtimeMessage(initiativeStatus)}</div>}
        <section className="community-feed" aria-live="polite" aria-busy={communityLoading}>
          {communityLoading && !initiatives.length && Array.from({ length: 3 }, (_, index) => <div className="community-card-skeleton" key={`community-skeleton-${index}`} aria-hidden="true"><span /><div><b /><i /><i /><em /></div></div>)}
          {!communityLoading && visibleCommunityInitiatives.map((initiative, index) => {
            const joining = joiningInitiativeId === initiative.initiativeId;
            const joinDisabled = initiative.joined || initiative.joinRequestedByMe || initiative.full || joining || communityOffline || !navigator.onLine;
            return <article className={`community-activity-card ${index === 0 ? 'is-next' : 'is-compact'}`} key={initiative.initiativeId}>
              <div className="community-card-visual"><span className="community-type-icon" aria-hidden="true">{communityIcon(initiative.category)}</span><span className="community-type-chip">{initiativeCategoryLabel(initiative.category)}</span>{index === 0 && <small>{t('Next upcoming')}</small>}</div>
              <div className="community-card-content">
                <h2>{initiative.title}</h2>
                <div className="community-card-meta">
                  <span><i aria-hidden="true">◷</i>{timestampLabel(initiative.startAt, language)}</span>
                  <span><i aria-hidden="true">⌖</i>{initiative.placeName}{communityNearMe && Number.isFinite(initiative.distanceKm) ? ` · ${communityDistanceLabel(initiative.distanceKm)}` : ''}</span>
                  <span><i aria-hidden="true">♙</i>{initiative.publicOrganiserName || t('Citizen organiser')}{initiative.joiningCountVisible ? ` · ${activityJoiningLabel(initiative)}` : ''}</span>
                </div>
                {initiative.neededItems?.length > 0 && <div className="activity-needed-chips">{initiative.neededItems.map((item) => <span key={item}>{item}</span>)}</div>}
                <div className="community-card-actions"><button className="secondary" onClick={() => { setSelectedInitiative(initiative); navigate('initiative-detail', false, initiative.initiativeId); }}>{t('View activity')}</button><button className={initiative.joined || initiative.joinRequestedByMe ? 'secondary joined-button' : ''} disabled={joinDisabled} onClick={() => requestLinkedMutation(() => joinInitiative(initiative.initiativeId).catch((error) => setInitiativeStatus(citizenSafeError(error, 'The initiative could not be joined.'))))}>{initiative.joined ? t('Joined') : initiative.joinRequestedByMe ? t('Approval requested') : initiative.full ? t('Full') : joining ? t('Joining…') : communityOffline || !navigator.onLine ? t('Connect to join') : initiative.participationMode === 'APPROVAL_REQUIRED' ? t('Request to join') : t('Join')}</button></div>
              </div>
            </article>;
          })}
          {!communityLoading && !visibleCommunityInitiatives.length && <div className="community-empty-state"><b>{initiatives.length ? t('No activities match these filters.') : communityNearMe ? t('No upcoming activities were found within this distance.') : t('No upcoming activities are listed in Nandurbar yet.')}</b><p>{t('You can adjust the list or start a useful activity for your community.')}</p><div>{communityCategory !== 'ALL' || communityDateRange !== 'ALL' ? <button className="secondary" onClick={() => { setCommunityCategory('ALL'); setCommunityDateRange('ALL'); }}>{t('Clear filters')}</button> : communityNearMe ? <><button className="secondary" onClick={() => setCommunityNearMe(false)}>{t('Show all Nandurbar')}</button>{initiativeRadiusKm < 25 && <button className="secondary" onClick={() => setInitiativeRadiusKm((radius) => radius < 5 ? 5 : radius < 10 ? 10 : 25)}>{t('Widen distance')}</button>}</> : null}<button onClick={() => navigate('new-initiative')}>{t('Start an initiative')}</button></div></div>}
        </section>
        <button className="community-bottom-start secondary" onClick={() => navigate('new-initiative')}>＋ {t('Start an initiative')}</button>
      </section>
    </>}

    {screen === 'initiative-detail' && <section className="activity-detail-page">
      <button className="activity-back secondary" onClick={() => navigate('initiatives')}>‹ {t('Community')}</button>
      {initiativeDetailLoading && !selectedInitiative && <div className="activity-detail-skeleton" aria-label={t('Loading activity')}><span /><b /><i /><i /><em /></div>}
      {!initiativeDetailLoading && !selectedInitiative && <div className="community-empty-state"><b>{t('This activity could not be found.')}</b><button onClick={() => navigate('initiatives')}>{t('Browse community activities')}</button></div>}
      {selectedInitiative && <article className="activity-detail-card">
        <div className="activity-detail-banner"><span className="community-type-chip">{initiativeCategoryLabel(selectedInitiative.category)}</span><span className="activity-banner-icon" aria-hidden="true">{communityIcon(selectedInitiative.category)}</span></div>
        <div className="activity-detail-content">
          <h1>{selectedInitiative.title}</h1>
          <p className="activity-time icon-copy"><AppIcon name="clock" />{timestampLabel(selectedInitiative.startAt, language)}{selectedInitiative.endAt && selectedInitiative.endAt !== selectedInitiative.startAt ? ` · ${t('ends')} ${new Intl.DateTimeFormat(communityLocale, { hour: 'numeric', minute: '2-digit' }).format(new Date(selectedInitiative.endAt))}` : ''}</p>
          <dl className="activity-facts">
            <div><dt><AppIcon name="pin" />{t('Where')}</dt><dd>{selectedInitiative.mapsUrl ? <a href={selectedInitiative.mapsUrl} target="_blank" rel="noreferrer">{selectedInitiative.placeName} ↗</a> : selectedInitiative.placeName}</dd></div>
            <div><dt><AppIcon name="users" />{t('Organised by')}</dt><dd>{selectedInitiative.publicOrganiserName || t('Citizen organiser')}</dd></div>
            {selectedInitiative.joiningCountVisible && <div><dt><AppIcon name="users" />{t('Joining')}</dt><dd>{activityJoiningLabel(selectedInitiative)}</dd></div>}
          </dl>
          <section className="activity-description"><h2>{t('About this activity')}</h2><p>{selectedInitiative.description}</p></section>
          {selectedInitiative.neededItems?.length > 0 && <section className="activity-needed"><h2>{t('What is needed')}</h2><div className="activity-needed-chips">{selectedInitiative.neededItems.map((item) => <span key={item}>{item}</span>)}</div></section>}
          {selectedInitiative.organiserMessage && <blockquote className="organiser-message"><strong>{t('From the organiser:')}</strong> {selectedInitiative.organiserMessage}</blockquote>}
          {selectedInitiative.joined && !selectedInitiative.canManage ? <>
            <div className="joined-confirmation"><strong className="icon-copy"><AppIcon name="check" />{t('Joined')}</strong><span>{t('See you on')} {activityReminderLabel(selectedInitiative)}.</span></div>
            <div className="joined-actions"><button className="secondary icon-button" onClick={() => addActivityToCalendar(selectedInitiative)}><AppIcon name="clock" />{t('Calendar')}</button><button className="secondary icon-button" onClick={() => void shareActivity(selectedInitiative)}><AppIcon name="share" />{t('Share')}</button></div>
          </> : selectedInitiative.joinRequestedByMe && !selectedInitiative.canManage ? <div className="joined-confirmation"><strong>{t('Approval requested')}</strong><span>{t('The organiser will accept or decline your request.')}</span></div> : !selectedInitiative.canManage && <button className="activity-join-button" disabled={selectedInitiative.full || joiningInitiativeId === selectedInitiative.initiativeId} onClick={() => requestLinkedMutation(() => joinInitiative(selectedInitiative.initiativeId).catch((error) => setInitiativeStatus(citizenSafeError(error, 'The initiative could not be joined.'))))}>{selectedInitiative.full ? t('Full') : joiningInitiativeId === selectedInitiative.initiativeId ? t('Joining…') : selectedInitiative.participationMode === 'APPROVAL_REQUIRED' ? t('Request to join') : t('Join Activity')}</button>}
          {selectedInitiative.canManage && <section className="organiser-tools"><span className="eyebrow icon-copy"><AppIcon name="shield" />{t('ORGANISER TOOLS')}</span>{Date.now() >= Date.parse(selectedInitiative.endAt || selectedInitiative.startAt) ? <><h2>{t('Activity day is over — record what happened')}</h2><p>{t('Completion and attendance tools remain protected in My Actions.')}</p></> : <p>{t('Manage this activity and attendance securely in My Actions.')}</p>}<button className="secondary icon-button" onClick={() => { navigate('reports'); window.setTimeout(() => document.getElementById('my-initiatives')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100); }}><AppIcon name="shield" />{t('Open organiser tools in My Actions')}</button></section>}
          {initiativeStatus && <div className="status-panel state-warning" role="status" aria-live="polite">{runtimeMessage(initiativeStatus)}</div>}
        </div>
      </article>}
    </section>}

    {screen === 'new-initiative' && <section className="initiative-create-page">
      <header className="flow-page-heading initiative-page-heading"><h1 className="page-title">{t('New Initiative')}</h1><button type="button" className="secondary flow-start-over" onClick={startInitiativeOver}>{t('Start over')}</button></header>
      {!initiativeReviewing ? <div className="initiative-form">
      <section className={`initiative-form-block ${initiativeFormStep === 1 ? 'is-active' : 'is-complete'}`} data-initiative-step="1">
      <div className="initiative-block-header"><span className="initiative-step">{initiativeFormStep === 1 ? '1' : '✓'}</span><div className="initiative-block-heading"><h2 className="initiative-type-heading">{t('What are you organising?')}</h2>{initiativeFormStep === 1 && <p>{t('Pick the closest one. You can edit details next.')}</p>}</div>{initiativeFormStep !== 1 && <button type="button" className="initiative-step-edit" onClick={() => openInitiativeFormStep(1)}>{t('Edit')}</button>}</div>
      {initiativeFormStep === 1 ? <>
      <TemplatePicker id="initiative-type" label={t('Activity type')} placeholder={t('Choose an activity type')} searchPlaceholder={t('Filter activities')} emptyMessage={t('No activities match your search.')} clearSearchLabel={t('Clear search')} value={initiativeCategory} options={INITIATIVE_TEMPLATES.map((template) => ({ value: template.value, icon: <AppIcon name={initiativeTypeIcon(template.value)} />, title: t(template.label), description: t(template.example) }))} onChange={applyInitiativeTemplate} />
      <label>{t('Title')}<input type="text" maxLength={100} value={initiativeTitle} onChange={(event) => setInitiativeTitle(event.target.value)} /></label>
      <label>{t('Description')}<textarea maxLength={1200} value={initiativeDescription} onChange={(event) => setInitiativeDescription(event.target.value)} /></label>
      <div className="initiative-next-row"><button type="button" disabled={!initiativeCategory || !initiativeTitle.trim() || !initiativeDescription.trim()} onClick={() => advanceInitiativeForm(1)}>{t('Next')}</button></div>
      </> : <button type="button" className="initiative-step-summary" onClick={() => openInitiativeFormStep(1)}><strong>{selectedInitiativeTemplate ? t(selectedInitiativeTemplate.label) : t('Activity')}</strong><span>{initiativeTitle}</span></button>}
      </section>
      <section className={`initiative-form-block ${initiativeFormStep === 2 ? 'is-active' : initiativeHighestStep >= 2 ? 'is-complete' : 'is-upcoming'}`} data-initiative-step="2">
      <div className="initiative-block-header"><span className="initiative-step">{initiativeHighestStep > 2 && initiativeFormStep !== 2 ? '✓' : '2'}</span><div className="initiative-block-heading"><h2>{t('Where and when?')}</h2>{initiativeFormStep === 2 && <p>{t('Choose the public meeting place and the complete activity time.')}</p>}</div>{initiativeHighestStep >= 2 && initiativeFormStep !== 2 && <button type="button" className="initiative-step-edit" onClick={() => openInitiativeFormStep(2)}>{t('Edit')}</button>}</div>
      {initiativeFormStep === 2 ? <>
      <InitiativeMeetingPointPicker language={language} position={initiativeMeetingPoint} onChange={(position) => {
        setInitiativeMeetingPoint(position);
        setInitiativeMeetingPointConfirmed(false);
        setInitiativeStatus('');
      }} onGooglePlaceSelect={({ position, label }) => {
        setInitiativeMeetingPoint({
          latitude: Number(position.latitude.toFixed(6)),
          longitude: Number(position.longitude.toFixed(6)),
        });
        setInitiativePlaceName(label.slice(0, 200));
        setInitiativeMeetingPointConfirmed(false);
        setInitiativeStatus('');
      }} />
      <label>{t('Public meeting-point label')}<input type="text" maxLength={200} value={initiativePlaceName} onChange={(event) => {
        setInitiativePlaceName(event.target.value);
        setInitiativeMeetingPointConfirmed(false);
        setInitiativeStatus('');
      }} /><small>{t('Use a clear public landmark or meeting-place name. Participants will see this label.')}</small></label>
      <button type="button" className="secondary" disabled={!initiativeMeetingPoint || !initiativePlaceName.trim()} onClick={() => {
        setInitiativeMeetingPointConfirmed(true);
        setInitiativeStatus('Meeting point confirmed.');
      }}>{initiativeMeetingPointConfirmed ? `✓ ${t('Meeting point confirmed')}` : t('Confirm meeting point')}</button>
      {initiativeMeetingPointConfirmed && initiativeMeetingPoint && <div className="confirmed-meeting-point" role="status" aria-live="polite"><div><b>{initiativePlaceName.trim()}</b><span>{t('Confirmed public meeting point')}</span></div><a className="icon-copy" href={`https://www.google.com/maps/search/?api=1&query=${initiativeMeetingPoint.latitude.toFixed(6)}%2C${initiativeMeetingPoint.longitude.toFixed(6)}`} target="_blank" rel="noreferrer"><AppIcon name="pin" />{t('Preview in Google Maps')}</a></div>}
      <div className="initiative-time-grid"><label>{t('Start time')}<input type="datetime-local" step={300} value={initiativeStartAt} onFocus={initialiseInitiativeStartTime} onChange={(event) => changeInitiativeStartAt(event.target.value)} /></label><label>{t('End time')}<input type="datetime-local" step={300} min={initiativeStartAt || undefined} value={initiativeEndAt} onChange={(event) => setInitiativeEndAt(normaliseActivityTime(event.target.value))} /></label></div>
      <div className="initiative-next-row"><button type="button" disabled={!initiativeMeetingPoint || !initiativeMeetingPointConfirmed || !initiativePlaceName.trim() || !initiativeStartAt || !initiativeEndAt || Date.parse(initiativeEndAt) <= Date.parse(initiativeStartAt)} onClick={() => advanceInitiativeForm(2)}>{t('Next')}</button></div>
      </> : initiativeHighestStep >= 2 ? <button type="button" className="initiative-step-summary" onClick={() => openInitiativeFormStep(2)}><strong>{initiativePlaceName}</strong><span>{initiativeStartAt ? new Intl.DateTimeFormat(communityLocale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(initiativeStartAt)) : ''}</span></button> : <p className="initiative-step-locked">{t('Complete the previous step to continue.')}</p>}
      </section>
      <section className={`initiative-form-block ${initiativeFormStep === 3 ? 'is-active' : initiativeHighestStep >= 3 ? 'is-complete' : 'is-upcoming'}`} data-initiative-step="3">
      <div className="initiative-block-header"><span className="initiative-step">{initiativeHighestStep > 3 && initiativeFormStep !== 3 ? '✓' : '3'}</span><div className="initiative-block-heading"><h2>{t('What is needed?')}</h2>{initiativeFormStep === 3 && <p>{t('People bring what they can. Keep it simple.')}</p>}</div>{initiativeHighestStep >= 3 && initiativeFormStep !== 3 && <button type="button" className="initiative-step-edit" onClick={() => openInitiativeFormStep(3)}>{t('Edit')}</button>}</div>
      {initiativeFormStep === 3 ? <>
      <fieldset className="needed-items-editor"><legend>{t('What is needed (optional)')}</legend>{initiativeNeededItems.length > 0 && <div className="activity-needed-chips editable-needed-chips">{initiativeNeededItems.map((item) => <span className="needed-item-chip" key={item}>{item}<button type="button" className="needed-item-remove" aria-label={`${t('Remove item')}: ${item}`} title={t('Remove item')} onClick={() => setInitiativeNeededItems((items) => items.filter((value) => value !== item))}>×</button></span>)}</div>}<div className="needed-item-entry"><input type="text" maxLength={80} value={initiativeNeededItemDraft} placeholder={t('For example, gloves')} onChange={(event) => setInitiativeNeededItemDraft(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); addInitiativeNeededItem(); } }} /><button type="button" className="secondary icon-button" disabled={!initiativeNeededItemDraft.trim() || initiativeNeededItems.length >= 8} onClick={addInitiativeNeededItem}><AppIcon name="plus" />{t('Add')}</button></div><small>{t('Suggested items are prefilled from the activity type. Remove any with × or add your own.')}</small></fieldset>
      <div className="initiative-next-row"><button type="button" onClick={() => advanceInitiativeForm(3)}>{t('Next')}</button></div>
      </> : initiativeHighestStep >= 3 ? <button type="button" className="initiative-step-summary" onClick={() => openInitiativeFormStep(3)}><strong>{initiativeNeededItems.length} {t('items')}</strong><span>{initiativeNeededItems.slice(0, 3).join(' · ') || t('Nothing needed')}</span></button> : <p className="initiative-step-locked">{t('Complete the previous step to continue.')}</p>}
      </section>
      <section className={`initiative-form-block ${initiativeFormStep === 4 ? 'is-active' : initiativeHighestStep >= 4 ? 'is-complete' : 'is-upcoming'}`} data-initiative-step="4">
      <div className="initiative-block-header"><span className="initiative-step">{initiativeHighestStep > 4 && initiativeFormStep !== 4 ? '✓' : '4'}</span><div className="initiative-block-heading"><h2>{t('Who can participate?')}</h2>{initiativeFormStep === 4 && <p>{t('Open is friendliest for a first activity.')}</p>}</div>{initiativeHighestStep >= 4 && initiativeFormStep !== 4 && <button type="button" className="initiative-step-edit" onClick={() => openInitiativeFormStep(4)}>{t('Edit')}</button>}</div>
      {initiativeFormStep === 4 ? <>
      <div className="participation-options" role="radiogroup" aria-label={t('Who can participate?')}>
        {([
          ['OPEN', 'Anyone can join', 'No approval needed'],
          ['CAPPED', 'Cap the numbers', 'Stops at your maximum'],
          ['APPROVAL_REQUIRED', 'Approval required', 'You accept each request'],
        ] as const).map(([value, title, description]) => <label className={initiativeParticipationMode === value ? 'selected' : ''} key={value}><input type="radio" name="participation-mode" value={value} checked={initiativeParticipationMode === value} onChange={() => { setInitiativeParticipationMode(value); if (value !== 'CAPPED') setInitiativeCapacity(''); }} /><AppIcon name={value === 'APPROVAL_REQUIRED' ? 'shield' : 'users'} /><span><strong>{t(title)}</strong><small>{t(description)}</small></span></label>)}
      </div>
      {initiativeParticipationMode === 'CAPPED' && <label>{t('Maximum participants')}<input type="number" inputMode="numeric" min={1} max={500} value={initiativeCapacity} onChange={(event) => setInitiativeCapacity(event.target.value)} /></label>}
      <div className="initiative-next-row"><button type="button" disabled={initiativeParticipationMode === 'CAPPED' && (!initiativeCapacity || Number(initiativeCapacity) < 1 || Number(initiativeCapacity) > 500)} onClick={() => advanceInitiativeForm(4)}>{t('Next')}</button></div>
      </> : initiativeHighestStep >= 4 ? <button type="button" className="initiative-step-summary" onClick={() => openInitiativeFormStep(4)}><strong>{t(initiativeParticipationMode === 'OPEN' ? 'Anyone can join' : initiativeParticipationMode === 'CAPPED' ? 'Cap the numbers' : 'Approval required')}</strong>{initiativeParticipationMode === 'CAPPED' && <span>{initiativeCapacity} {t('maximum')}</span>}</button> : <p className="initiative-step-locked">{t('Complete the previous step to continue.')}</p>}
      </section>
      <section className={`initiative-form-block ${initiativeFormStep === 5 ? 'is-active' : 'is-upcoming'}`} data-initiative-step="5">
      <div className="initiative-block-header"><span className="initiative-step">5</span><div className="initiative-block-heading"><h2>{t('Message and review')}</h2>{initiativeFormStep === 5 && <p>{t('Add a final note, then check the activity before publishing.')}</p>}</div></div>
      {initiativeFormStep === 5 ? <>
      <label>{t('Your name, as the city will see it')}<input type="text" maxLength={60} value={initiativePublicOrganiserName} onChange={(event) => setInitiativePublicOrganiserName(event.target.value)} /><small>{t('This name will appear publicly on the activity.')}</small></label>
      <label>{t('Message from the organiser (optional)')}<textarea maxLength={500} value={initiativeOrganiserMessage} placeholder={t('For example: Saplings are arranged. Bring a cap and water.')} onChange={(event) => setInitiativeOrganiserMessage(event.target.value)} /></label>
      <div className={`publish-requirements${initiativePublishRequirements.length ? '' : ' is-complete'}`} role="status" aria-live="polite">
        <b>{initiativePublishRequirements.length ? t('Before you can review') : `✓ ${t('Ready to review')}`}</b>
        {initiativePublishRequirements.length > 0 && <ul>{initiativePublishRequirements.map((requirement) => <li key={requirement}>{t(requirement)}</li>)}</ul>}
        {!initiativePublishRequirements.length && <span>{t('The meeting point and required activity details are confirmed.')}</span>}
      </div>
      <div className="draft-actions"><button className="secondary" onClick={() => navigate('initiatives')}>{t('Cancel')}</button><button disabled={initiativePublishRequirements.length > 0} onClick={() => { setInitiativeReviewing(true); setInitiativeStatus(''); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>{t('Review activity')}</button></div>
      {initiativeStatus && <div className="status-panel state-warning" role="status" aria-live="polite">{runtimeMessage(initiativeStatus)}</div>}
      </> : <p className="initiative-step-locked">{t('Complete the previous step to continue.')}</p>}
      </section>
      </div> : <section className="initiative-review">
        <div className="initiative-review-heading"><span className="eyebrow">{t('PRIVATE REVIEW')}</span><h2>{t('Check what joiners will see')}</h2><p>{t('Nothing has been published yet. Check every prefilled detail before continuing.')}</p></div>
        <article className="activity-detail-card">
          <div className="activity-detail-banner"><span className="community-type-chip">{initiativeCategoryLabel(initiativeCategory)}</span><span className="activity-banner-icon" aria-hidden="true">{communityIcon(initiativeCategory)}</span></div>
          <div className="activity-detail-content">
            <h2 className="initiative-preview-title">{initiativeTitle}</h2>
            <p className="activity-time">{timestampLabel(new Date(initiativeStartAt).toISOString(), language)} · {t('ends')} {new Intl.DateTimeFormat(communityLocale, { hour: 'numeric', minute: '2-digit' }).format(new Date(initiativeEndAt))}</p>
            <dl className="activity-facts">
              <div><dt>{t('Where')}</dt><dd>{initiativePlaceName}</dd></div>
              <div><dt>{t('Organised by')}</dt><dd>{initiativePublicOrganiserName}</dd></div>
              <div><dt>{t('Participation')}</dt><dd>{t(initiativeParticipationMode === 'OPEN' ? 'Anyone can join' : initiativeParticipationMode === 'CAPPED' ? 'Cap the numbers' : 'Approval required')}{initiativeParticipationMode === 'CAPPED' ? ` · ${initiativeCapacity} ${t('maximum')}` : ''}</dd></div>
            </dl>
            <section className="activity-description"><h3>{t('About this activity')}</h3><p>{initiativeDescription}</p></section>
            {initiativeNeededItems.length > 0 && <section className="activity-needed"><h3>{t('What is needed')}</h3><div className="activity-needed-chips">{initiativeNeededItems.map((item) => <span key={item}>{item}</span>)}</div></section>}
            {initiativeOrganiserMessage && <blockquote className="organiser-message"><strong>{t('From the organiser:')}</strong> {initiativeOrganiserMessage}</blockquote>}
            <div className="draft-actions initiative-review-actions"><button className="secondary" onClick={() => { setInitiativeReviewing(false); setInitiativeFormStep(5); setInitiativeHighestStep(5); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>{t('Edit activity')}</button><button onClick={() => requestLinkedMutation(() => createInitiative().catch((error) => setInitiativeStatus(citizenSafeError(error, 'The initiative could not be published.'))))}>{t('Publish activity')}</button></div>
            {initiativeStatus && <div className="status-panel state-warning" role="status" aria-live="polite">{runtimeMessage(initiativeStatus)}</div>}
          </div>
        </article>
      </section>}
    </section>}

    {screen === 'new-report' && <>
    <header className="flow-page-heading report-page-heading"><h1 className="page-title">{t('New Report')}</h1><button type="button" className="secondary flow-start-over" onClick={startOver}>{t('Start over')}</button></header>
    <section className="card report-flow-card">
      <div className="report-purpose"><span className="signal" aria-hidden="true" /><p>{t("Let's find how to report this issue")}</p></div>
      {routeResult?.status === 'SUPPORTED_ROUTE' ? <div className="report-collapsed-steps">
        <article className="report-step-summary"><div><span>1</span><div><b>{t('Describe the issue')}</b><small>{issueLabel(issueType, language)} · {evidenceImage ? t('Photo added') : t('No photo')}</small></div></div><button type="button" className="secondary" onClick={() => editRoutedReport('report-describe-section')}>{t('Edit')}</button></article>
        <article className="report-step-summary"><div><span>2</span><div><b>{t('Location')}</b><small>{locationDetails.trim() || `${t('Prabhag')} ${Number(prabhagId.slice(-2)) || ''}`} · {reportLocationSourceLabel || t('Selected manually')}</small></div></div><button type="button" className="secondary" onClick={() => editRoutedReport('report-location-section')}>{t('Edit')}</button></article>
      </div> : <>
      <div id="report-describe-section">
      <div className="flow-step"><span>1</span><b>{t('Describe the issue')}</b></div>
        <div className="photo-input-grid">
          <div className="photo-upload-field">
            <label className="photo-upload-label">
              <input className="photo-file-input" type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => chooseEvidenceImage(event.target.files?.[0] ?? null)} />
              <span className={`photo-upload-surface ${evidencePreviewUrl ? 'has-preview' : ''}`}>
                {evidencePreviewUrl ? <img src={evidencePreviewUrl} alt="" /> : <span className="photo-upload-icon"><AppIcon name="camera" /></span>}
                <span className="photo-upload-copy"><strong>{t(evidenceImage ? 'Replace photo' : 'Take or add a photo')}</strong><small>{evidenceImage?.name || t('Camera or photo library')}</small></span>
              </span>
            </label>
            {evidenceImage && <button type="button" className="photo-remove-button" onClick={() => chooseEvidenceImage(null)}>{t('Remove photo')}</button>}
          </div>
        </div>
        <label>{t('Short description (optional)')}<textarea maxLength={2000} value={evidenceText} placeholder="उदा. रस्त्यावर मोठा खड्डा आहे" onChange={(event) => {
          const nextText = event.target.value;
          setEvidenceText(nextText);
          resetEvidenceDerivedState();
          scheduleEvidenceClassification(evidenceImage, nextText, 650);
          if (nextText.trim() && !prabhagId && reportLocationSource !== 'MANUAL') requestDeviceReportLocation();
        }} /></label>
        {classificationStatus && <div role="status" aria-live="polite" className={`status-panel ${classification?.status === 'CLASSIFICATION_ERROR' ? 'state-error' : classification?.status === 'CLASSIFIED' ? 'state-success' : 'state-warning'}`}>
          <strong>{classification?.status === 'CLASSIFIED' ? t('Category suggestion ready') : classification?.status === 'CLARIFICATION_REQUIRED' ? t('Please clarify') : t('Category suggestion')}</strong>
          <span>{runtimeMessage(classificationStatus)}</span>
          {classification?.description && <small>{classification.description}</small>}
          {classification?.detectedLanguage && <small>{t('Detected language')}: {classification.detectedLanguage}</small>}
        </div>}
        <TemplatePicker id="issue-category" label={t('Issue category')} placeholder={t('Choose an issue category')} searchPlaceholder={t('Filter categories')} emptyMessage={t('No categories match your search.')} clearSearchLabel={t('Clear search')} value={issueType} options={ISSUE_TYPES.map(([value]) => ({ value, icon: <AppIcon name={issueTypeIcon(value)} />, title: issueLabel(value, language) }))} onChange={(value) => chooseIssueType(value)} />
      </div>

      <div id="report-location-section">
      <div className="flow-step"><span>2</span><b>{t('Location')}</b></div>
      <GoogleMeetingPointSearch
        language={language}
        bounds={PRABHAG_BOUNDS ? {
          south: PRABHAG_BOUNDS.minLatitude,
          west: PRABHAG_BOUNDS.minLongitude,
          north: PRABHAG_BOUNDS.maxLatitude,
          east: PRABHAG_BOUNDS.maxLongitude,
        } : null}
        mode="report-location"
        value={locationDetails}
        onQueryChange={editReportLocationAddress}
        onSelect={selectGoogleReportLocation}
      />
      <div className="report-location-field">
        <span className="report-location-icon" aria-hidden="true"><AppIcon name="pin" /></span>
        <label>{t('Prabhag')}<select value={prabhagId} onChange={(event) => selectManualPrabhag(event.target.value)}>
            <option value="" disabled>{t('Choose Prabhag 1–20')}</option>
            {PRABHAGS.map((value, index) => <option key={value} value={value}>{t('Prabhag')} {index + 1}</option>)}
        </select>{prabhagId && reportLocationSourceLabel && <small>{reportLocationSourceLabel}</small>}</label>
      </div>
      <div className="report-location-map">
        <BoundaryMapErrorBoundary fallback={<div className="status-panel state-warning" role="status">{t('The boundary guide is unavailable. Choose Prabhag 1–20 manually.')}</div>}>
          <PrabhagBoundaryMap
            language={language}
            highlightedPrabhagId={highlightedPrabhagId}
            selectionKind={boundarySelectionKind}
            currentPosition={currentCoordinates}
            onManualSelect={selectManualPrabhag}
          />
        </BoundaryMapErrorBoundary>
      </div>
      </div>
      </>}
      {routeResult?.status !== 'SUPPORTED_ROUTE' && <>
      <div className="flow-step"><span>3</span><b>{t('Find the right route')}</b></div>
      <button onClick={() => findCivicRoute().catch((error) => setRouteResult({ status: citizenSafeError(error, 'The right route could not be found. Try again.') }))}>{t('Find official route')}</button>
      </>}
      {routeResult && routeResult.status !== 'SUPPORTED_ROUTE' && <div aria-live="polite" className="route-result state-error"><strong>{routeResult.status === 'CATEGORY_REQUIRED' ? t('Choose an issue category to continue.') : routeResult.status === 'DETAILS_REQUIRED' ? t('Add a photo or short description before finding the official route.') : routeResult.status === 'PHOTO_ANALYSIS_REQUIRED' ? t('Wait for the photo analysis to finish, or add a short description.') : routeResult.status === 'PRABHAG_REQUIRED' ? t('Choose a Prabhag to continue.') : routeResult.status}</strong></div>}
      {routeResult?.status === 'SUPPORTED_ROUTE' && <>
        <section className="civic-router-section" aria-labelledby="civic-router-title">
          <span className="eyebrow">{t('CIVIC RESPONSIBILITY ROUTER')}</span>
          <h2 id="civic-router-title" ref={routeResultHeadingRef} tabIndex={-1}>{t('Here is the responsible authority')}</h2>
          <article className="responsible-authority-card">
            <span className="authority-icon"><AppIcon name="building" /></span>
            <small>{t('Responsible authority')}</small>
            <h3>{routeResult.authority}</h3>
            <dl>
              {routeResult.department && <div><dt>{routeResult.department.status === 'TYPICAL_STRUCTURE_UNVERIFIED' ? t('Likely department') : t('Department')}</dt><dd>{routeResult.department.displayName}</dd></div>}
              <div><dt>{t('Selected issue')}</dt><dd>{issueLabel(issueType, language)}</dd></div>
              <div><dt>{t('Location')}</dt><dd>{locationDetails.trim() || `${t('Prabhag')} ${Number(prabhagId.slice(-2)) || ''}`}</dd></div>
            </dl>
            {(routeResult.knownLimitations?.length ?? 0) > 0 && <div className="route-limitations"><b>{t('Please check before filing')}</b>{routeResult.knownLimitations?.map((limitation) => <span key={limitation.code}>{limitation.citizenMessage}</span>)}</div>}
          </article>
        </section>
        <section className="filing-choice-panel" aria-labelledby="filing-choice-title">
          <h2 id="filing-choice-title">{t('Official filing routes')}</h2>
          <p>{t('Review and edit the prepared content before filing. Seewik helps you prepare the complaint but does not submit it for you.')}</p>
          <div className="filing-choice-grid">
            <button type="button" className={`filing-choice-card ${selectedFilingMethod === 'PRINT' ? 'selected' : ''}`} aria-pressed={selectedFilingMethod === 'PRINT'} onClick={() => prepareFilingMethod('PRINT').catch((error) => setDraftStatus(citizenSafeError(error, 'The complaint draft could not be created.')))}><span className="filing-choice-number"><AppIcon name="building" /></span><span><strong>{t('Print and submit')}</strong><small>{t('Prepare a formal letter to print, save or share.')}</small></span></button>
            <button type="button" className={`filing-choice-card ${selectedFilingMethod === 'EMAIL' ? 'selected' : ''}`} aria-pressed={selectedFilingMethod === 'EMAIL'} disabled={!emailChannel} onClick={() => prepareFilingMethod('EMAIL').catch((error) => setDraftStatus(citizenSafeError(error, 'The complaint draft could not be created.')))}><span className="filing-choice-number"><AppIcon name="mail" /></span><span><strong>{t('Email Nagar Palika')}</strong><small>{t('Open an editable email addressed to the council.')}</small></span></button>
            <button type="button" className={`filing-choice-card ${selectedFilingMethod === 'DMA' ? 'selected' : ''}`} aria-pressed={selectedFilingMethod === 'DMA'} disabled={!formChannel} onClick={() => prepareFilingMethod('DMA').catch((error) => setDraftStatus(citizenSafeError(error, 'The complaint draft could not be created.')))}><span className="filing-choice-number"><AppIcon name="form" /></span><span><strong>{t('Directorate of Municipal Administration')}</strong><small>{t('Prepare copy-ready fields for the official form.')}</small></span></button>
          </div>
        </section>
        {selectedFilingMethod && complaintDraft?.status !== 'DRAFT_READY' && draftStatus && <div role="status" aria-live="polite" className="status-panel state-warning">{runtimeMessage(draftStatus)}</div>}
        {selectedFilingMethod && complaintDraft?.status === 'DRAFT_READY' && <>
          <section className="filing-prep-panel" ref={filingPanelRef} tabIndex={-1}>
            <div className="lifecycle-heading filing-prep-heading"><div><small>{t('Prepare filing')}</small><strong>{selectedFilingLabel}</strong></div><div className="points-pill"><span>{t('Possible reward')}</span><b>+5</b></div></div>
            <label>{t('Complaint language')}<select value={draftLanguage} onChange={(event) => { const nextLanguage = event.target.value as 'MR' | 'EN'; markFilingDraftEdited(); void changeComplaintLanguage(nextLanguage); }}>
              {filingLanguageChoices.map((option) => <option key={option.value} value={option.value}>{filingLanguageLabel(option.value)}</option>)}
            </select><small className="field-help">{t('Your complaint is prepared in Marathi or English. We save this choice so your manual edits are preserved.')}</small></label>
            <label>{t('Recipient')}
              <input type="text" maxLength={220} value={filingAuthority} readOnly disabled />
            </label>
            <label>{t('Subject')}<input type="text" maxLength={160} value={draftSubject} onChange={(event) => { setDraftSubject(event.target.value); markFilingDraftEdited(); }} /></label>
            <label>{t(selectedFilingMethod === 'DMA' ? 'Description of Complaint/Grievance' : selectedFilingMethod === 'EMAIL' ? 'Email complaint content' : 'Complaint body')}<textarea className="draft-body" maxLength={2500} value={draftBody} onChange={(event) => { setDraftBody(event.target.value); markFilingDraftEdited(); }} /></label>

            {selectedFilingMethod === 'PRINT' && <>
              <div className="filing-info-note">{t('Use this letter for the office route; print, save, or share, then submit it in person.')}</div>
              <label>{t('Citizen name')}<input type="text" maxLength={120} value={complainantName} onChange={(event) => { setComplainantName(event.target.value); markFilingDraftEdited(); }} /></label>
              <label>{t('Correspondence address')}<input type="text" maxLength={220} value={complainantAddress} onChange={(event) => { setComplainantAddress(event.target.value); markFilingDraftEdited(); }} /></label>
              <div className="filing-dual-fields">
                <label>{t('City')}<input type="text" maxLength={90} value={complainantCity} onChange={(event) => { setComplainantCity(event.target.value); markFilingDraftEdited(); }} /></label>
                <label>{t('State')}<input type="text" maxLength={90} value={complainantState} onChange={(event) => { setComplainantState(event.target.value); markFilingDraftEdited(); }} /></label>
                <label>{t('Pincode')}<input type="text" maxLength={12} value={complainantPincode} onChange={(event) => { setComplainantPincode(event.target.value); markFilingDraftEdited(); }} /></label>
              </div>
              <label>{t('Email address')}<input type="email" maxLength={200} value={complainantEmail} onChange={(event) => { setComplainantEmail(event.target.value); markFilingDraftEdited(); }} /></label>
              <label>{t('Phone number (optional)')}<input type="tel" maxLength={20} value={complainantPhone} onChange={(event) => { setComplainantPhone(event.target.value); markFilingDraftEdited(); }} /></label>
              <div className="filing-method-actions">
                <button className="secondary" onClick={() => shareLetter().catch((error) => setFilingActionStatus(citizenSafeError(error, 'The letter could not be shared.')))}>{t('Share letter')}</button>
                <button onClick={printLetter}>{t('Print or save as PDF')}</button>
              </div>
            </>}

            {selectedFilingMethod === 'EMAIL' && <>
              <label>{t('Recipient email')}<input type="email" maxLength={200} value={filingRecipientEmail} onChange={(event) => {
                setFilingEmail(event.target.value);
                markFilingDraftEdited();
              }} /></label>
              <label>{t('Full name')}<input type="text" maxLength={120} value={complainantName} onChange={(event) => { setComplainantName(event.target.value); markFilingDraftEdited(); }} /></label>
              <label>{t('Email address')}<input type="email" maxLength={200} value={complainantEmail} onChange={(event) => { setComplainantEmail(event.target.value); markFilingDraftEdited(); }} /></label>
              <label>{t('Phone number (optional)')}<input type="tel" maxLength={20} value={complainantPhone} onChange={(event) => { setComplainantPhone(event.target.value); markFilingDraftEdited(); }} /></label>
              <label>{t('Correspondence address')}<input type="text" maxLength={220} value={complainantAddress} onChange={(event) => { setComplainantAddress(event.target.value); markFilingDraftEdited(); }} /></label>
              <div className="filing-dual-fields">
                <label>{t('City')}<input type="text" maxLength={90} value={complainantCity} onChange={(event) => { setComplainantCity(event.target.value); markFilingDraftEdited(); }} /></label>
                <label>{t('State')}<input type="text" maxLength={90} value={complainantState} onChange={(event) => { setComplainantState(event.target.value); markFilingDraftEdited(); }} /></label>
                <label>{t('Pincode')}<input type="text" maxLength={12} value={complainantPincode} onChange={(event) => { setComplainantPincode(event.target.value); markFilingDraftEdited(); }} /></label>
              </div>
              {evidenceImage && <div className="filing-info-note">{t('Photo ready to attach. Your email app cannot receive attachments automatically, so attach the photo before sending.')}</div>}
              <div className="filing-method-actions">
                <button className="secondary" onClick={() => copyFilingField(t('Complaint subject'), draftSubject).catch((error) => setFilingActionStatus(citizenSafeError(error, 'Could not copy the subject.')))}>{t('Copy subject')}</button>
                <button className="secondary" onClick={() => copyFilingField(t('Complaint body'), draftBody).catch((error) => setFilingActionStatus(citizenSafeError(error, 'Could not copy the body.')))}>{t('Copy complaint content')}</button>
                <button onClick={() => openEmailDraft()}>{t('Open email app')}</button>
              </div>
            </>}

            {selectedFilingMethod === 'DMA' && <>
              <label>{t('Full name')}<input type="text" maxLength={120} value={complainantName} onChange={(event) => { setComplainantName(event.target.value); markFilingDraftEdited(); }} /></label>
              <label>{t('Email address')}<input type="email" maxLength={200} value={complainantEmail} onChange={(event) => { setComplainantEmail(event.target.value); markFilingDraftEdited(); }} /></label>
              <label>{t('Phone number (optional)')}<input type="tel" maxLength={20} value={complainantPhone} onChange={(event) => { setComplainantPhone(event.target.value); markFilingDraftEdited(); }} /></label>
              <label>{t('Correspondence address')}<input type="text" maxLength={220} value={complainantAddress} onChange={(event) => { setComplainantAddress(event.target.value); markFilingDraftEdited(); }} /></label>
              <div className="filing-dual-fields">
                <label>{t('City')}<input type="text" maxLength={90} value={complainantCity} onChange={(event) => { setComplainantCity(event.target.value); markFilingDraftEdited(); }} /></label>
                <label>{t('State')}<input type="text" maxLength={90} value={complainantState} onChange={(event) => { setComplainantState(event.target.value); markFilingDraftEdited(); }} /></label>
                <label>{t('Pincode')}<input type="text" maxLength={12} value={complainantPincode} onChange={(event) => { setComplainantPincode(event.target.value); markFilingDraftEdited(); }} /></label>
              </div>
              {evidenceImage && <div className="filing-info-note">{t('A supporting photo is ready. Upload it manually in the official DMA form.')}</div>}
              <div className="filing-method-actions">
                <button className="secondary" onClick={() => copyFilingField(t('Complaint subject'), draftSubject).catch((error) => setFilingActionStatus(citizenSafeError(error, 'Could not copy the subject.')))}>{t('Copy subject')}</button>
                <button className="secondary" onClick={() => copyFilingField(t('Description of Complaint/Grievance'), draftBody).catch((error) => setFilingActionStatus(citizenSafeError(error, 'Could not copy the body.')))}>{t('Copy complaint description')}</button>
                <button className="secondary" onClick={() => copyDmaPack().catch((error) => setFilingActionStatus(citizenSafeError(error, 'The DMA fields could not be copied.')))}>{t('Copy all fields')}</button>
                <button onClick={() => openDmaForm()}>{t('Open official DMA form')}</button>
              </div>
            </>}

            {filingActionStatus && <div className="status-panel state-warning" role="status" aria-live="polite">{runtimeMessage(filingActionStatus)}</div>}
            <div className="filing-confirmation-panel">
              <div className="lifecycle-heading filing-confirmation-heading"><div><small>{t('Did you submit this report?')}</small><strong>{t('Confirm real-world action')}</strong></div></div>
              <div className="filing-method-actions filing-confirmation-actions">
                <button onClick={() => requestLinkedMutation(() => fileReviewedReport(false).catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))} disabled={!canConfirmFilings}>{t('I filed this report')}</button>
                <button className="secondary" onClick={() => { clearFilingActionReceipt(); setLifecycleStatus('Continue filing and confirm once submitted.'); }}>{t('I have not filed it yet')}</button>
              </div>
              {!currentCoordinates && <small className="filing-dedupe-note">{t('Dedupe is not evaluated when location is unavailable.')}</small>}
              <div className="filing-confirmation-fields">
                {(routeResult?.officialChannels?.length ?? 0) > 0 && <label>{t('Channel you used')}<select value={filingChannelId} onChange={(event) => setFilingChannelId(event.target.value)}>
                  <option value="">{t('Not recorded')}</option>
                  {routeResult?.officialChannels?.map((channel) => <option key={channel.channelId} value={channel.channelId}>{channel.label}</option>)}
                </select></label>}
                <label>{t('Acknowledgement / tracking ID (optional)')}<input maxLength={200} value={acknowledgementId} onChange={(event) => setAcknowledgementId(event.target.value)} /></label>
              </div>
              {duplicateWarning && <div className="duplicate-warning"><b>{t('Possible duplicate')}</b><span>A same-category report is {duplicateWarning.measuredDistanceMeters?.toFixed(1)} m away.</span><button className="secondary" onClick={() => requestLinkedMutation(() => fileReviewedReport(true, false).catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('This is a different issue — file with 0 points')}</button></div>}
            </div>
          </section>

          {lifecycleStatus && <div role="status" aria-live="polite" className="status-panel state-warning">{runtimeMessage(lifecycleStatus)}</div>}

          {['FILED', 'OVERDUE', 'REOPENED'].includes(reportStatus) && <div className="lifecycle-panel filing-status-panel">
            {reportStatus === 'FILED' && <div className="overdue-unknown"><b>{t('Overdue: unknown')}</b><span>{t('No verified SLA exists, so Seewik will not invent a due date.')}</span></div>}
            <div className="lifecycle-actions">
              <button onClick={() => requestLinkedMutation(() => transitionReport('CLAIMED_FIXED').catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('Record a repair claim')}</button>
              {reportStatus === 'CLAIMED_FIXED' ? <button className="secondary" onClick={() => requestLinkedMutation(() => transitionReport('REOPENED').catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('Reject repair claim')}</button> : null}
              {reportStatus === 'VERIFIED_FIXED' ? <button className="secondary" onClick={() => requestLinkedMutation(() => transitionReport('REOPENED').catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('Report recurrence')}</button> : null}
            </div>
            <ol className="timeline compact">
              {timeline.map((item, index) => <li key={`${item.occurredAt}-${index}`}><span>{index + 1}</span><div><b>{localizedStatus(language, item.toStatus)}</b><small>{item.eventType} · {item.verificationBasis}{item.pointsAwarded ? ` · +${item.pointsAwarded}` : ''}</small></div></li>)}
            </ol>
          </div>}
        </>}
      </>}
    </section>
    </>}

    {screen === 'review' && <section className="card page-card">
      <span className="eyebrow">{t('COMPLAINT REVIEW')}</span><h2>{t('Review your saved complaint')}</h2>
      {!draftDocumentId || complaintDraft?.status !== 'DRAFT_READY' ? <div className="empty-state"><b>{t('No draft is ready in this session.')}</b><p>{t('Create or open a saved draft before reviewing it.')}</p><button onClick={() => navigate('new-report')}>{t('Start a report')}</button></div> : <>
        <div className={`report-status-banner ${canEditReport(reportStatus) ? 'editable' : 'immutable'}`}><b>{localizedStatus(language, reportStatus)}</b><span>{canEditReport(reportStatus) ? t('Editable draft') : t('Filed report — it can’t be changed')}</span></div>
        <div className="locked-recipient"><small>{t('Locked recipient')}</small><strong>{complaintDraft.authorityLocalName || complaintDraft.authority}</strong><span>{complaintDraft.prabhagId}</span></div>
        <label>{t('Subject')}<input type="text" maxLength={160} readOnly={!canEditReport(reportStatus)} value={draftSubject} onChange={(event) => { setDraftSubject(event.target.value); setDraftReviewed(false); }} /></label>
        <label>{t('Complaint body')}<textarea className="draft-body" maxLength={2500} readOnly={!canEditReport(reportStatus)} value={draftBody} onChange={(event) => { setDraftBody(event.target.value); setDraftReviewed(false); }} /></label>
        {canEditReport(reportStatus) ? <>
          <label className="review-check"><input type="checkbox" checked={draftReviewed} onChange={(event) => setDraftReviewed(event.target.checked)} /><span>{t('I reviewed the facts, recipient and wording.')}</span></label>
          <div className="draft-actions"><button className="secondary" onClick={() => requestLinkedMutation(() => saveDraftEdits().then(() => undefined).catch((error) => setDraftStatus(citizenSafeError(error, 'The draft could not be saved.'))))}>{t('Save changes')}</button><button disabled={!draftReviewed} onClick={() => requestLinkedMutation(() => copyReviewedDraft().catch((error) => setDraftStatus(citizenSafeError(error, 'The complaint could not be copied.'))))}>{t('Copy reviewed complaint')}</button></div>
          <div className="lifecycle-panel filing-panel"><div className="lifecycle-heading"><div><small>{t('Record real-world filing')}</small><strong>{t('DRAFT')}</strong></div><div className="points-pill"><span>{t('Possible reward')}</span><b>+5</b></div></div><p>{t('Seewik never submits the complaint. Use this only after you file it yourself.')}</p>
            {(routeResult?.officialChannels?.length ?? 0) > 0 && <label>{t('Channel you used')}<select value={filingChannelId} onChange={(event) => setFilingChannelId(event.target.value)}><option value="">{t('Not recorded')}</option>{routeResult?.officialChannels?.map((channel) => <option key={channel.channelId} value={channel.channelId}>{channel.label}</option>)}</select></label>}
            <label>{t('Acknowledgement / tracking ID (optional)')}<input maxLength={200} value={acknowledgementId} onChange={(event) => setAcknowledgementId(event.target.value)} /></label>
            <button disabled={!draftReviewed} onClick={() => requestLinkedMutation(() => fileReviewedReport(false, false).catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('I filed this complaint')}</button>
            {!currentCoordinates && <small>{t('Dedupe is not evaluated when location is unavailable.')}</small>}
            {duplicateWarning && <div className="duplicate-warning"><b>{t('Possible duplicate')}</b><span>A same-category report is {duplicateWarning.measuredDistanceMeters?.toFixed(1)} m away.</span><button className="secondary" onClick={() => requestLinkedMutation(() => fileReviewedReport(true, false).catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('This is a different issue — file with 0 points')}</button></div>}
            {lifecycleStatus && <div role="status" aria-live="polite" className="status-panel state-warning">{runtimeMessage(lifecycleStatus)}</div>}
          </div>
          <button className="text-action" onClick={() => navigate('new-report')}>{t('Return to report builder')}</button>
        </> : <><div className="status-panel state-warning"><strong>{t('This report cannot be resumed')}</strong><span>{t('Filed reports can’t be edited.')}</span></div><button onClick={() => navigate('report-detail', false, draftDocumentId)}>{t('View report timeline')}</button></>}
        {draftStatus && <div role="status" aria-live="polite" className="status-panel state-warning">{runtimeMessage(draftStatus)}</div>}
      </>}
    </section>}

    {screen === 'reports' && <section className="card page-card my-actions-page">
      <h1 className="page-title">{t('My Actions')}</h1>
      {reportsView === 'SIGNED_OUT' ? <div className="empty-state account-recovery-state">
        <b>{t('Sign in to view your saved civic work.')}</b>
        <p>{t("Signing out doesn't delete anything.")}</p>
        <p>{t('Your saved work stays attached to your Google account — sign in to see it.')}</p>
        <button onClick={openAccount}>{t('Continue with Google')}</button>
      </div> : <>
        <section className="actions-subsection reports-subsection">
          <div className="actions-section-heading"><div><h2>{t('My Reports')}</h2><p>{t('Only you can see your drafts. Filed reports can’t be changed.')}</p></div><button className="secondary" onClick={() => loadMyReports().catch((error) => setReportsStatus(citizenSafeError(error, 'Your reports could not be loaded.'))) }>{t('Refresh')}</button></div>
          {reportsView === 'HAS_REPORTS' && <div className="reports-toolbar"><span role="status" aria-live="polite">{runtimeMessage(reportsStatus)}</span></div>}
          {reportsView === 'LINKED_EMPTY' && <div className="empty-state"><b>{t('Signed in. No saved reports yet.')}</b><p>{t('Create a report to keep a draft only you can see.')}</p><button onClick={() => navigate('new-report')}>{t('Create a report')}</button></div>}
          {reportsView === 'ANONYMOUS_EMPTY' && <div className="empty-state"><b>{t('No reports are saved for this device-only account.')}</b><p>{t('Create a report to keep a draft only you can see.')}</p><button onClick={() => navigate('new-report')}>{t('Create a report')}</button></div>}
          {reportsView === 'HAS_REPORTS' && <div className="report-list">{savedReports.map((report) => { const followUp = followUpsByReport[report.id]; return <article className="report-list-item" key={report.id}><div><span className={`status-chip status-${report.status.toLowerCase()}`}>{localizedStatus(language, report.status)}</span>{followUp?.promptDue && <span className="follow-up-chip">{t('Follow-up due')}</span>}{followUp?.escalationAvailable && <span className="follow-up-chip escalation-ready">{t('Escalation ready')}</span>}<h3>{issueLabel(report.confirmedIssueType, language)}</h3><p>{report.prabhagId} · {t('Updated')} {timestampLabel(report.updatedAt, language)}</p><small>{report.id.slice(0, 12)}…</small></div>{canResumeReport(report.status) ? <button onClick={() => resumeSavedReport(report).catch((error) => setReportsStatus(citizenSafeError(error, 'The draft could not be resumed.')))}>{t('Resume draft')}</button> : <button onClick={() => openSavedReport(report).catch((error) => setReportsStatus(citizenSafeError(error, 'The report could not be loaded.')))}>{t('View report')}</button>}</article>; })}</div>}
        </section>
        {myInitiativesSection}
      </>}
    </section>}

    {screen === 'report-detail' && <section className="card page-card">
      <span className="eyebrow">{t('REPORT DETAILS')}</span><h2>{selectedReport ? issueLabel(selectedReport.confirmedIssueType, language) : t('Loading report')}</h2>
      {accountState === 'SIGNED_OUT' ? <div className="empty-state account-recovery-state"><b>{t('Sign in to view your saved civic work.')}</b><p>{t('Your saved work stays attached to your Google account — sign in to see it.')}</p><button onClick={openAccount}>{t('Continue with Google')}</button></div> : !selectedReport ? <div className="empty-state"><p>{runtimeMessage(reportsStatus) || t('Choose a report from My Actions.')}</p><button onClick={() => navigate('reports')}>{t('Open My Actions')}</button></div> : <>
        <div className="report-status-banner immutable"><b>{localizedStatus(language, reportStatus)}</b><span>{reportStatus === 'DRAFT' ? t('Open the review screen to edit this draft.') : t('Filed report — it can’t be changed')}</span></div>
        <dl className="report-facts"><div><dt>{t('Prabhag')}</dt><dd>{selectedReport.prabhagId}</dd></div><div><dt>{t('Authority')}</dt><dd>{selectedReport.routeSnapshot?.authority || selectedReport.authority}</dd></div><div><dt>{t('Acknowledgement')}</dt><dd>{selectedReport.acknowledgementId || t('Not provided')}</dd></div><div><dt>{t('Updated')}</dt><dd>{timestampLabel(selectedReport.updatedAt, language)}</dd></div></dl>
        {selectedReport.routeSnapshot?.department && <div className="locked-recipient"><small>{t('Frozen route department')}</small><strong>{selectedReport.routeSnapshot.department.displayName}</strong><span>{selectedReport.routeSnapshot.department.status} · {selectedReport.routeSnapshot.sourceStatus} · {selectedReport.routeSnapshot.reviewStatus}</span></div>}
        {(selectedReport.routeSnapshot?.knownLimitations?.length ?? 0) > 0 && <div className="route-limitations"><b>{t('Please keep in mind')}</b><ul>{selectedReport.routeSnapshot?.knownLimitations?.map((limitation) => <li key={limitation.code}>{limitation.citizenMessage}</li>)}</ul></div>}
        <div className="points-summary"><span>{t('Derived points for this profile')}</span><b>{pointsTotal}</b></div>
        {followUpLifecycleActive && !activeFollowUp && <button className="secondary" onClick={() => refreshFollowUp(selectedReport.id).catch((error) => setFollowUpStatus(citizenSafeError(error, 'The follow-up could not be loaded.')))}>{t('Check follow-up')}</button>}
        {followUpLifecycleActive && activeFollowUp?.promptDue && <section className="follow-up-panel" aria-labelledby="follow-up-heading">
          <span className="eyebrow">{t('SEVEN-DAY FOLLOW-UP')}</span>
          <h3 id="follow-up-heading">{t('Was this issue resolved?')}</h3>
          <p>{activeFollowUp.recurrence ? t('This follow-up belongs to a recurrence after a previously verified resolution.') : t('Your report has reached its follow-up date. Tell us what happened.')}</p>
          <div className="follow-up-actions">
            <button disabled={followUpBusy} onClick={() => requestLinkedMutation(() => transitionReport('CLAIMED_FIXED').catch((error) => setFollowUpStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('Yes, it was resolved')}</button>
            <button disabled={followUpBusy} className="secondary" onClick={() => requestLinkedMutation(() => recordFollowUp('UNRESOLVED'))}>{t('No, it is still unresolved')}</button>
            <button disabled={followUpBusy} className="secondary" onClick={() => requestLinkedMutation(() => recordFollowUp('UNSURE'))}>{t("I'm not sure yet")}</button>
          </div>
          <small>{t('Follow-up timing is calculated from server records. No points are awarded for an answer or escalation.')}</small>
        </section>}
        {followUpLifecycleActive && activeFollowUp?.state === 'SNOOZED' && <div className="follow-up-reminder"><b>{t('We will ask again')}</b><span>{activeFollowUp.nextPromptAt ? timestampLabel(activeFollowUp.nextPromptAt, language) : t('In 3 days')}</span></div>}
        {followUpLifecycleActive && activeFollowUp?.escalationAvailable && <section className="escalation-section" aria-labelledby="escalation-heading">
          <span className="eyebrow">{t('OFFICIAL ESCALATION ROUTES')}</span>
          <h3 id="escalation-heading">{t('Choose the next follow-up route')}</h3>
          <p>{t('The order below is recommended, not mandatory. Seewik prepares an editable email and never sends it automatically.')}</p>
          {activeFollowUp.recurrence && <div className="recurrence-note">{t('This issue recurred after a previously verified resolution. That supported fact will be included in the draft; the internal cycle number will not.')}</div>}
          <div className="escalation-route-grid">
            {ESCALATION_CHANNELS.map((route) => <button type="button" key={route.id} className={selectedEscalationChannel === route.id ? 'selected' : ''} aria-pressed={selectedEscalationChannel === route.id} onClick={() => selectEscalationChannel(route.id)}><strong>{t(route.title)}</strong><small>{t(route.description)}</small></button>)}
          </div>
          {selectedEscalationChannel && activeEscalationRoute && <div className="escalation-preparation" id="escalation-preparation" tabIndex={-1}>
            <div className="escalation-preparation-heading"><div><small>{t('Preparing for')}</small><strong>{t(activeEscalationRoute.title)}</strong></div>{activeEscalationRoute.sourceUrl && <a href={activeEscalationRoute.sourceUrl} target="_blank" rel="noreferrer">{t('Official source')} ↗</a>}</div>
            {!escalationRouteIsCurrent(activeFollowUp) ? <div className="status-panel state-warning">{t('The verified route changed. Refresh this report before preparing an escalation.')}</div> : <>
              <div className="locked-recipient"><small>{t('Verified recipient')}</small><strong>{escalationRecipient(selectedEscalationChannel)}</strong><span>{t('This address cannot be edited inside Seewik.')}</span></div>
              <label>{t('Escalation language')}<select value={escalationLanguage} onChange={(event) => changeEscalationLanguage(event.target.value as 'MR' | 'EN')}><option value="EN">English</option><option value="MR">मराठी</option></select><small>{t('Hindi interface defaults to a Marathi government draft. You may switch it to English.')}</small></label>
              <label>{t('Email subject')}<input value={escalationSubject} maxLength={160} onChange={(event) => { setEscalationSubject(event.target.value); setEscalationActionOpened(false); }} /></label>
              <label>{t('Email message')}<textarea rows={14} value={escalationBody} maxLength={3500} onChange={(event) => { setEscalationBody(event.target.value); setEscalationActionOpened(false); }} /></label>
              <div className="evidence-reminder"><b>{t('Before sending')}</b><span>{t('Attach your original complaint, acknowledgement and relevant photo manually. Email links cannot attach files for you.')}</span></div>
              <div className="escalation-actions"><button className="secondary" onClick={() => void copyEscalationEmail()}>{t('Copy email')}</button><button onClick={() => void openEscalationEmail()}>{t('Open email app')}</button></div>
              <div className="sent-confirmation"><b>{t('Did you send this follow-up?')}</b><p>{t('Opening or copying the draft does not mean it was sent.')}</p><button disabled={!escalationActionOpened || followUpBusy} onClick={() => requestLinkedMutation(() => recordFollowUp('ESCALATION_SENT', selectedEscalationChannel))}>{t('I sent this follow-up')}</button></div>
            </>}
          </div>}
          <button className="secondary resolved-later" onClick={() => requestLinkedMutation(() => transitionReport('CLAIMED_FIXED').catch((error) => setFollowUpStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('It has now been resolved')}</button>
        </section>}
        {reportStatus === 'DRAFT' && <button onClick={() => resumeSavedReport(selectedReport).catch((error) => setReportsStatus(citizenSafeError(error, 'The draft could not be resumed.')))}>{t('Resume draft')}</button>}
        {['FILED', 'OVERDUE', 'REOPENED'].includes(reportStatus) && <><div className="overdue-unknown"><b>{t('Overdue: unknown')}</b><span>{t('No verified SLA exists, so Seewik will not invent a due date.')}</span></div>{!activeFollowUp?.promptDue && !activeFollowUp?.escalationAvailable && <button onClick={() => requestLinkedMutation(() => transitionReport('CLAIMED_FIXED').catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('Record a repair claim')}</button>}</>}
        {reportStatus === 'CLAIMED_FIXED' && <div className="lifecycle-actions"><button onClick={() => requestLinkedMutation(() => transitionReport('VERIFIED_FIXED').catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('Verify fixed')}</button><button className="secondary" onClick={() => requestLinkedMutation(() => transitionReport('REOPENED').catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('Reject repair claim')}</button></div>}
        {reportStatus === 'VERIFIED_FIXED' && <button className="secondary" onClick={() => requestLinkedMutation(() => transitionReport('REOPENED').catch((error) => setLifecycleStatus(citizenSafeError(error, 'The report could not be updated.'))))}>{t('Report recurrence')}</button>}
        {lifecycleStatus && <div role="status" aria-live="polite" className="status-panel state-warning">{runtimeMessage(lifecycleStatus)}</div>}
        {followUpStatus && <div role="status" aria-live="polite" className="status-panel state-warning">{t(followUpStatus)}</div>}
        <ol className="timeline">{timeline.map((item, index) => <li key={`${item.occurredAt}-${index}`}><span>{index + 1}</span><div><b>{localizedStatus(language, item.toStatus)}</b><small>{item.eventType} · {item.verificationBasis}{item.pointsAwarded ? ` · +${item.pointsAwarded}` : ''}</small></div></li>)}</ol>
      </>}
    </section>}

    {screen === 'points' && <>
      <section className="card page-card points-page">
        <div className="civic-card-heading"><h1 className="page-title">{t('My Civic Card')}</h1><p className="icon-copy"><AppIcon name="info" />{t('A record of what you have actually done.')}</p></div>
        {accountState === 'SIGNED_OUT' ? <div className="empty-state account-recovery-state"><b>{t('Sign in to view your contribution record.')}</b><p>{t('Your points remain attached to your account and are never shown in the public recognition panel.')}</p><button onClick={openAccount}>{t('Continue with Google')}</button></div> : <>
          {privatePoints && <div className="private-month-points"><span>{localizedMonthLabel(language, privatePoints.monthLabel)}</span><strong>{privatePoints.currentMonthPoints} {t('points')}</strong></div>}
          {privatePoints?.breakdown.length ? <div className="points-breakdown">{privatePoints.breakdown.map((item) => <div key={item.contributionType}><span><b>{contributionTypeLabel(item.contributionType)}</b><small>{item.lifetimeAwards} {t('recorded awards')}</small></span><strong>{item.lifetimePoints}</strong></div>)}</div> : null}
          <RewardCatalogue
            language={language}
            overview={rewardOverview}
            loading={rewardStatus === 'Loading example rewards…'}
            busyId={rewardBusyId}
            confirmUseId={rewardUseConfirmation}
            status={rewardStatus}
            t={t}
            onClaim={(couponId) => requestLinkedMutation(() => createRewardClaim(couponId))}
            onBeginUse={setRewardUseConfirmation}
            onCancelUse={() => setRewardUseConfirmation('')}
            onConfirmUse={(claimId) => requestLinkedMutation(() => confirmRewardUse(claimId))}
            onRefresh={() => refreshRewards().catch(() => undefined)}
          />
          <button className="secondary" onClick={() => Promise.allSettled([refreshDerivedPoints(), refreshRewards()])}>{t('Refresh my Civic Card')}</button>
          {privatePointsStatus && <div className="status-panel state-warning" role="status" aria-live="polite">{t(privatePointsStatus)}</div>}
        </>}
      </section>
      {accountState !== 'SIGNED_OUT' && <ContributionPoster
        defaultDisplayName={recognitionSettings?.publicDisplayName || accountName || ''}
        lifetimePoints={privatePoints?.lifetimePoints ?? pointsTotal}
        currentMonthPoints={privatePoints?.currentMonthPoints ?? 0}
        monthLabel={privatePoints ? localizedMonthLabel(language, privatePoints.monthLabel) : t('This month')}
        contributionLabels={privatePoints?.breakdown.map((item) => contributionTypeLabel(item.contributionType)) ?? []}
        t={t}
      />}
      <RecognitionSettings connected={accountState === 'GOOGLE_LINKED'} settings={recognitionSettings} busy={recognitionSettingsBusy} status={recognitionSettingsStatus} t={t} onConnect={openAccount} onSave={updateRecognitionSettings} />
      <RecognitionPanel panel={publicRecognition ? { ...publicRecognition, monthLabel: localizedMonthLabel(language, publicRecognition.monthLabel) } : null} loading={publicRecognitionLoading} status={publicRecognitionStatus} t={t} onReport={sendRecognitionReport} onRetry={loadPublicRecognition} />
      <section className="card points-rules-card"><span className="eyebrow">{t('HOW POINTS ARE EARNED')}</span><h2>{t('How you get points')}</h2><div className="points-rules"><div><b>+5</b><span>{t('First accepted filing')}</span></div><div><b>+20</b><span>{t('Organiser-code attendance')}</span></div><div><b>+40</b><span>{t('Completed organiser with two code attendees')}</span></div><div><b>+60</b><span>{t('First verified fix')}</span></div><div><b>0</b><span>{t('Self-attendance, duplicate override, reopening or re-verification')}</span></div></div></section>
    </>}

    {screen === 'awareness' && <CivicAwarenessPage
      t={t}
      onReportIssue={(selectedIssueType) => { chooseIssueType(selectedIssueType); navigate('new-report'); }}
      onStartInitiative={() => navigate('initiatives')}
    />}

    {screen === 'emergency' && <EmergencyInformationPage t={t} />}

    {screen === 'home' && DEBUG_MODE && <>
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
    </>}
    <nav className="utility-nav" aria-label={t('Civic information')}>
      <button className={screen === 'awareness' ? 'active' : ''} onClick={() => navigate('awareness')}>{t('Civic Awareness')}</button>
      <button className={screen === 'emergency' ? 'active emergency-utility' : 'emergency-utility'} onClick={() => navigate('emergency')}>{t('Emergency Information')}</button>
    </nav>
    <footer>{t('Built for local civic action')}</footer>
    <nav className="mobile-nav" aria-label={t('Mobile navigation')}>
      <button aria-current={navCurrent(screen === 'home')} className={screen === 'home' ? 'active' : ''} onClick={() => navigate('home')}><span aria-hidden="true">⌂</span>{t('Home')}</button>
      <button aria-current={navCurrent(screen === 'new-report' || screen === 'review')} className={screen === 'new-report' || screen === 'review' ? 'active' : ''} onClick={() => navigate('new-report')}><span aria-hidden="true">＋</span>{t('Report')}</button>
      <button aria-current={navCurrent(screen === 'initiatives')} className={screen === 'initiatives' ? 'active' : ''} onClick={() => navigate('initiatives')}><span aria-hidden="true">◎</span>{t('Community')}</button>
      <button aria-current={navCurrent(screen === 'reports' || screen === 'report-detail')} className={screen === 'reports' || screen === 'report-detail' ? 'active' : ''} onClick={() => navigate('reports')}><span aria-hidden="true">≡</span>{t('Actions')}</button>
      <button aria-current={navCurrent(screen === 'points')} className={screen === 'points' ? 'active' : ''} onClick={() => navigate('points')}><span aria-hidden="true">◆</span>{t('Civic Card')}</button>
    </nav>
  </main>
  </>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'));
