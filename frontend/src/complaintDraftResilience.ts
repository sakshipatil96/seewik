export const INITIAL_COMPLAINT_DRAFT_TIMEOUT_MS = 25_000;
export const RETRY_COMPLAINT_DRAFT_TIMEOUT_MS = 10_000;

export type ComplaintDraftAttemptOutcome = 'SUCCESS' | 'TIMEOUT' | 'HTTP_ERROR' | 'INVALID_RESPONSE' | 'NETWORK_ERROR';

export class ComplaintDraftTimeoutError extends Error {
  readonly timeoutMs: number;

  constructor(timeoutMs: number) {
    super(`Complaint drafting exceeded ${timeoutMs}ms`);
    this.name = 'ComplaintDraftTimeoutError';
    this.timeoutMs = timeoutMs;
  }
}

export async function fetchWithDeadline(
  input: RequestInfo | URL,
  init: RequestInit,
  timeoutMs: number,
  fetcher: typeof fetch = fetch,
) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetcher(input, { ...init, signal: controller.signal });
  } catch (error) {
    if (controller.signal.aborted) throw new ComplaintDraftTimeoutError(timeoutMs);
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export function isComplaintDraftTimeout(error: unknown): error is ComplaintDraftTimeoutError {
  return error instanceof ComplaintDraftTimeoutError;
}

export function createDeterministicComplaintDraft(input: {
  language: 'MR' | 'EN';
  facts: string;
  locationDetails?: string | null;
  packVersion: string;
  routeId: string;
  prabhagId: string;
  authority: string;
  authorityLocalName?: string;
}) {
  const facts = input.facts.trim();
  const location = input.locationDetails?.trim();
  const subject = input.language === 'MR' ? 'नागरी समस्येबाबत तक्रार' : 'Civic issue complaint';
  const body = input.language === 'MR'
    ? `मी${location ? ` ${location} येथे` : ''} पुढील नागरी समस्येबाबत तक्रार नोंदवत आहे: ${facts}\n\nकृपया या समस्येची पाहणी करून योग्य कार्यवाही करावी. उपलब्ध असल्यास कृपया पोच किंवा स्थिती अद्यतन द्यावे.`
    : `I am writing to report the following civic issue${location ? ` at ${location}` : ''}: ${facts}\n\nPlease inspect this issue and take the appropriate action. Please provide an acknowledgement or status update when available.`;
  return {
    status: 'DRAFT_READY' as const,
    draftVersion: 'manual-v0.1',
    schemaVersion: 'complaint-draft-v0.1',
    packVersion: input.packVersion,
    language: input.language,
    routeId: input.routeId,
    prabhagId: input.prabhagId,
    authority: input.authority,
    authorityLocalName: input.authorityLocalName,
    subject,
    body,
    citizenReviewRequired: true,
  };
}
