import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveFilingRecipientEmail } from '../src/filingChannels.ts';

const channels = [
  { type: 'ONLINE_FORM', value: 'https://example.invalid/form' },
  { type: 'EMAIL', value: ' conandurbarnmc@gmail.com ' },
];

test('the verified Civic Pack email prefills the filing recipient', () => {
  assert.equal(resolveFilingRecipientEmail('', channels), 'conandurbarnmc@gmail.com');
});

test('a citizen edit remains authoritative and missing channels stay empty', () => {
  assert.equal(resolveFilingRecipientEmail(' citizen@example.com ', channels), 'citizen@example.com');
  assert.equal(resolveFilingRecipientEmail('', []), '');
  assert.equal(resolveFilingRecipientEmail('', undefined), '');
});
