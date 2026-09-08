import type { FilingMethod } from './filingActionReceipt';

export type GuestComplaintPreview = {
  schemaVersion: 'guest-complaint-preview-v0.1';
  ownerUid: string;
  routeId: string;
  packVersion: string;
  prabhagId: string;
  method: FilingMethod;
  language: 'MR' | 'EN';
  draftVersion: string;
  complaintSchemaVersion: string;
  authority: string;
  authorityLocalName?: string;
  subject: string;
  body: string;
};

export type GuestComplaintPreviewIdentity = Pick<
  GuestComplaintPreview,
  'ownerUid' | 'routeId' | 'packVersion' | 'prabhagId' | 'method' | 'language'
>;

export const GUEST_COMPLAINT_PREVIEW_KEY = 'seewik:guest-complaint-preview:v0.1';

function validPreview(value: unknown): value is GuestComplaintPreview {
  if (!value || typeof value !== 'object') return false;
  const preview = value as Partial<GuestComplaintPreview>;
  return preview.schemaVersion === 'guest-complaint-preview-v0.1'
    && typeof preview.ownerUid === 'string' && preview.ownerUid.length > 0
    && typeof preview.routeId === 'string' && preview.routeId.length > 0
    && typeof preview.packVersion === 'string' && preview.packVersion.length > 0
    && typeof preview.prabhagId === 'string' && preview.prabhagId.length > 0
    && ['PRINT', 'EMAIL', 'DMA'].includes(preview.method ?? '')
    && ['MR', 'EN'].includes(preview.language ?? '')
    && typeof preview.draftVersion === 'string' && preview.draftVersion.length > 0
    && typeof preview.complaintSchemaVersion === 'string' && preview.complaintSchemaVersion.length > 0
    && typeof preview.authority === 'string' && preview.authority.length > 0
    && (preview.authorityLocalName === undefined || typeof preview.authorityLocalName === 'string')
    && typeof preview.subject === 'string' && preview.subject.trim().length > 0
    && typeof preview.body === 'string' && preview.body.trim().length > 0;
}

export function readGuestComplaintPreview(
  storage: Pick<Storage, 'getItem'>,
  identity: GuestComplaintPreviewIdentity,
): GuestComplaintPreview | null {
  if (Object.values(identity).some((value) => !value)) return null;
  try {
    const parsed = JSON.parse(storage.getItem(GUEST_COMPLAINT_PREVIEW_KEY) ?? 'null') as unknown;
    if (!validPreview(parsed)) return null;
    return parsed.ownerUid === identity.ownerUid
      && parsed.routeId === identity.routeId
      && parsed.packVersion === identity.packVersion
      && parsed.prabhagId === identity.prabhagId
      && parsed.method === identity.method
      && parsed.language === identity.language
      ? parsed
      : null;
  } catch {
    return null;
  }
}

export function writeGuestComplaintPreview(
  storage: Pick<Storage, 'setItem'>,
  preview: GuestComplaintPreview,
) {
  if (!validPreview(preview)) return;
  try {
    storage.setItem(GUEST_COMPLAINT_PREVIEW_KEY, JSON.stringify(preview));
  } catch {
    // The in-memory preview remains available when session storage is unavailable.
  }
}

export function removeGuestComplaintPreview(storage: Pick<Storage, 'removeItem'>) {
  try {
    storage.removeItem(GUEST_COMPLAINT_PREVIEW_KEY);
  } catch {
    // Storage can be unavailable in privacy-restricted browser contexts.
  }
}
