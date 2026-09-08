export type FilingContactDraft = {
  schemaVersion: 'filing-contact-draft-v0.2';
  ownerUid: string;
  reportId: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  complainantAddress: string;
  complainantCity: string;
  complainantPincode: string;
  complainantState: string;
};

export const FILING_CONTACT_DRAFT_KEY = 'seewik:filing-contact-draft:v0.2';

export function readFilingContactDraft(
  storage: Pick<Storage, 'getItem'>,
  ownerUid: string,
  reportId: string,
): FilingContactDraft | null {
  if (!ownerUid || !reportId) return null;
  try {
    const parsed = JSON.parse(storage.getItem(FILING_CONTACT_DRAFT_KEY) ?? 'null') as FilingContactDraft | null;
    if (parsed?.schemaVersion !== 'filing-contact-draft-v0.2') return null;
    return parsed.ownerUid === ownerUid && parsed.reportId === reportId ? parsed : null;
  } catch {
    return null;
  }
}

export function writeFilingContactDraft(
  storage: Pick<Storage, 'setItem'>,
  draft: FilingContactDraft,
) {
  if (!draft.ownerUid || !draft.reportId || draft.schemaVersion !== 'filing-contact-draft-v0.2') return;
  try {
    storage.setItem(FILING_CONTACT_DRAFT_KEY, JSON.stringify(draft));
  } catch {
    // The in-memory fields remain available when session storage is unavailable.
  }
}

export function removeFilingContactDraft(storage: Pick<Storage, 'removeItem'>) {
  try {
    storage.removeItem(FILING_CONTACT_DRAFT_KEY);
  } catch {
    // Storage can be unavailable in privacy-restricted browser contexts.
  }
}
