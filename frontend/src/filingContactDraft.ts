export type FilingContactDraft = {
  reportId: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  complainantAddress: string;
  complainantCity: string;
  complainantPincode: string;
  complainantState: string;
};

const STORAGE_KEY = 'seewik:filing-contact-draft:v0.1';

export function readFilingContactDraft(
  storage: Pick<Storage, 'getItem'>,
  reportId: string,
): FilingContactDraft | null {
  if (!reportId) return null;
  try {
    const parsed = JSON.parse(storage.getItem(STORAGE_KEY) ?? 'null') as FilingContactDraft | null;
    return parsed?.reportId === reportId ? parsed : null;
  } catch {
    return null;
  }
}

export function writeFilingContactDraft(
  storage: Pick<Storage, 'setItem'>,
  draft: FilingContactDraft,
) {
  if (!draft.reportId) return;
  storage.setItem(STORAGE_KEY, JSON.stringify(draft));
}

export function removeFilingContactDraft(storage: Pick<Storage, 'removeItem'>) {
  storage.removeItem(STORAGE_KEY);
}
