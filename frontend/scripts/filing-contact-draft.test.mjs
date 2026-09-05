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
  assert.deepEqual(readFilingContactDraft(storage, 'report-1'), contact);
});

test('filing contact fields never cross report boundaries and can be cleared', () => {
  const storage = memoryStorage();
  writeFilingContactDraft(storage, contact);
  assert.equal(readFilingContactDraft(storage, 'report-2'), null);
  removeFilingContactDraft(storage);
  assert.equal(readFilingContactDraft(storage, 'report-1'), null);
});
