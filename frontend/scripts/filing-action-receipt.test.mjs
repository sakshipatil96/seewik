import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {
  createFilingActionReceipt,
  filingActionReceiptMatches,
  readFilingActionReceipt,
  removeFilingActionReceipt,
  writeFilingActionReceipt,
} from '../src/filingActionReceipt.ts';

class MemoryStorage {
  values = new Map();
  getItem(key) { return this.values.get(key) ?? null; }
  setItem(key, value) { this.values.set(key, value); }
  removeItem(key) { this.values.delete(key); }
}

const identity = {
  ownerUid: 'owner-1', reportId: 'report-1', method: 'PRINT', routeId: 'route-1', packVersion: 'v0.2',
  language: 'EN', subject: 'Broken streetlight', body: 'Please repair the streetlight.', filingEmail: '',
  complainantName: 'Citizen', complainantEmail: 'citizen@example.test', complainantPhone: '',
  complainantAddress: 'Ward 4', complainantCity: 'Nandurbar', complainantPincode: '425412', complainantState: 'Maharashtra',
};

test('a filing action receipt survives a same-session reload', () => {
  const storage = new MemoryStorage();
  const receipt = createFilingActionReceipt(identity, '2026-09-05T12:00:00Z');
  writeFilingActionReceipt(storage, receipt);
  assert.equal(filingActionReceiptMatches(readFilingActionReceipt(storage), identity), true);
});

test('editing filing content invalidates the action receipt', () => {
  const receipt = createFilingActionReceipt(identity);
  assert.equal(filingActionReceiptMatches(receipt, { ...identity, body: `${identity.body} Updated.` }), false);
});

test('changing the filing method or report invalidates the receipt', () => {
  const receipt = createFilingActionReceipt(identity);
  assert.equal(filingActionReceiptMatches(receipt, { ...identity, method: 'EMAIL' }), false);
  assert.equal(filingActionReceiptMatches(receipt, { ...identity, reportId: 'report-2' }), false);
  const storage = new MemoryStorage();
  writeFilingActionReceipt(storage, receipt);
  removeFilingActionReceipt(storage);
  assert.equal(readFilingActionReceipt(storage), null);
});

test('a guest filing action is rebound only while its linked draft save is pending', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  assert.match(source, /pendingSavedFilingAction\.current = \{ method, reportId \}/);
  assert.match(source, /draftDocumentId !== pending\.reportId \|\| selectedFilingMethod !== pending\.method/);
  assert.match(source, /recordFilingAction\(pending\.method, pending\.reportId\)/);
  assert.match(source, /function clearFilingActionReceipt\(\) \{\s*pendingSavedFilingAction\.current = null/);
});
