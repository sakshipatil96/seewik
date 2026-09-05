export type FilingMethod = 'PRINT' | 'EMAIL' | 'DMA';

export type FilingDraftIdentity = {
  ownerUid: string;
  reportId: string;
  method: FilingMethod;
  routeId: string;
  packVersion: string;
  language: 'MR' | 'EN';
  subject: string;
  body: string;
  filingEmail: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  complainantAddress: string;
  complainantCity: string;
  complainantPincode: string;
  complainantState: string;
};

export type FilingActionReceipt = {
  schemaVersion: 'filing-action-receipt-v0.1';
  ownerUid: string;
  reportId: string;
  method: FilingMethod;
  fingerprint: string;
  openedAt: string;
};

export const FILING_ACTION_RECEIPT_KEY = 'seewik:filing-action-receipt:v0.1';

export function filingDraftFingerprint(identity: FilingDraftIdentity) {
  const material = JSON.stringify({
    method: identity.method,
    routeId: identity.routeId,
    packVersion: identity.packVersion,
    language: identity.language,
    subject: identity.subject.trim(),
    body: identity.body.trim(),
    filingEmail: identity.filingEmail.trim(),
    complainantName: identity.complainantName.trim(),
    complainantEmail: identity.complainantEmail.trim(),
    complainantPhone: identity.complainantPhone.trim(),
    complainantAddress: identity.complainantAddress.trim(),
    complainantCity: identity.complainantCity.trim(),
    complainantPincode: identity.complainantPincode.trim(),
    complainantState: identity.complainantState.trim(),
  });
  let hash = 2166136261;
  for (let index = 0; index < material.length; index += 1) {
    hash ^= material.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
}

export function createFilingActionReceipt(identity: FilingDraftIdentity, openedAt = new Date().toISOString()): FilingActionReceipt {
  return {
    schemaVersion: 'filing-action-receipt-v0.1',
    ownerUid: identity.ownerUid,
    reportId: identity.reportId,
    method: identity.method,
    fingerprint: filingDraftFingerprint(identity),
    openedAt,
  };
}

export function filingActionReceiptMatches(receipt: FilingActionReceipt | null, identity: FilingDraftIdentity | null) {
  if (!receipt || !identity || receipt.schemaVersion !== 'filing-action-receipt-v0.1') return false;
  if (receipt.method !== identity.method) return false;
  if (receipt.ownerUid && identity.ownerUid && receipt.ownerUid !== identity.ownerUid) return false;
  if (receipt.reportId && identity.reportId && receipt.reportId !== identity.reportId) return false;
  return receipt.fingerprint === filingDraftFingerprint(identity);
}

export function readFilingActionReceipt(storage: Storage): FilingActionReceipt | null {
  try {
    const parsed = JSON.parse(storage.getItem(FILING_ACTION_RECEIPT_KEY) ?? 'null') as FilingActionReceipt | null;
    if (!parsed || parsed.schemaVersion !== 'filing-action-receipt-v0.1') return null;
    if (!['PRINT', 'EMAIL', 'DMA'].includes(parsed.method)) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function writeFilingActionReceipt(storage: Storage, receipt: FilingActionReceipt) {
  try {
    storage.setItem(FILING_ACTION_RECEIPT_KEY, JSON.stringify(receipt));
  } catch {
    // The in-memory receipt still protects this page when storage is unavailable.
  }
}

export function removeFilingActionReceipt(storage: Storage) {
  try {
    storage.removeItem(FILING_ACTION_RECEIPT_KEY);
  } catch {
    // Storage can be unavailable in privacy-restricted browser contexts.
  }
}
