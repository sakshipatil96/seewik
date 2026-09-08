import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import {
  INITIAL_COMPLAINT_DRAFT_TIMEOUT_MS,
  RETRY_COMPLAINT_DRAFT_TIMEOUT_MS,
  createDeterministicComplaintDraft,
  fetchWithDeadline,
  isComplaintDraftTimeout,
} from '../src/complaintDraftResilience.ts';

test('draft deadlines keep the first attempt bounded and make retry shorter', () => {
  assert.equal(INITIAL_COMPLAINT_DRAFT_TIMEOUT_MS, 25_000);
  assert.equal(RETRY_COMPLAINT_DRAFT_TIMEOUT_MS, 10_000);
});

test('deterministic fallback is a complete editable complaint rather than raw facts', () => {
  const draft = createDeterministicComplaintDraft({
    language: 'EN',
    facts: 'Large pothole on a public road.',
    locationDetails: 'Near the bus stand',
    packVersion: 'v0.2',
    routeId: 'route-1',
    prabhagId: 'PRABHAG-01',
    authority: 'Nandurbar Municipal Council',
  });
  assert.equal(draft.status, 'DRAFT_READY');
  assert.equal(draft.draftVersion, 'manual-v0.1');
  assert.match(draft.body, /Large pothole on a public road/);
  assert.match(draft.body, /Near the bus stand/);
  assert.match(draft.body, /Please inspect/);
});

test('deadline aborts a hanging draft request', async () => {
  const hangingFetch = (_input, init) => new Promise((_resolve, reject) => {
    init.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
  });
  await assert.rejects(
    fetchWithDeadline('/draft', {}, 5, hangingFetch),
    (error) => isComplaintDraftTimeout(error) && error.timeoutMs === 5,
  );
});

test('background retry cannot overwrite citizen wording without an explicit choice', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const retry = source.slice(source.indexOf('async function retryComplaintDraft'), source.indexOf('async function useSuggestedComplaintDraft'));
  const apply = source.slice(source.indexOf('async function useSuggestedComplaintDraft'), source.indexOf('function cacheCurrentFilingDraft'));
  assert.doesNotMatch(retry, /setDraftSubject|setDraftBody/);
  assert.match(retry, /setDraftSuggestion/);
  assert.match(apply, /setDraftSubject\(result\.subject/);
  assert.match(apply, /setDraftBody\(result\.body/);
  assert.match(source, /Use AI wording/);
  assert.match(source, /Keep my wording/);
});
