import assert from 'node:assert/strict';
import test from 'node:test';
import {
  readFilingContactDraft,
  removeFilingContactDraft,
  writeFilingContactDraft,
} from '../src/filingContactDraft.ts';

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}

const contact = {
  schemaVersion: 'filing-contact-draft-v0.2',
  ownerUid: 'anonymous-1',
  reportId: 'report-1',
  complainantName: 'Test Citizen',
  complainantEmail: 'citizen@example.test',
  complainantPhone: '9999999999',
  complainantAddress: 'Main Market',
  complainantCity: 'Nandurbar',
  complainantPincode: '425412',
  complainantState: 'Maharashtra',
};

test('filing contact fields survive a refresh for the same saved report', () => {
  const storage = memoryStorage();
  writeFilingContactDraft(storage, contact);
  assert.deepEqual(readFilingContactDraft(storage, 'anonymous-1', 'report-1'), contact);
});

test('filing contact fields never cross owner or report boundaries and can be cleared', () => {
  const storage = memoryStorage();
  writeFilingContactDraft(storage, contact);
  assert.equal(readFilingContactDraft(storage, 'anonymous-2', 'report-1'), null);
  assert.equal(readFilingContactDraft(storage, 'anonymous-1', 'report-2'), null);
  removeFilingContactDraft(storage);
  assert.equal(readFilingContactDraft(storage, 'anonymous-1', 'report-1'), null);
});

test('legacy or malformed filing contact records are ignored', () => {
  const storage = memoryStorage();
  storage.setItem('seewik:filing-contact-draft:v0.2', JSON.stringify({ ...contact, schemaVersion: 'filing-contact-draft-v0.1' }));
  assert.equal(readFilingContactDraft(storage, 'anonymous-1', 'report-1'), null);
});
