import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { translate } from '../src/i18n.ts';

const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');
const rules = await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8');
const contract = await readFile(new URL('../../data/contracts/day11-attendance-reward-contract-v0.1.md', import.meta.url), 'utf8');

test('Day 11 interface exposes owner-scoped self and organiser-code attendance paths', () => {
  assert.match(source, /\/attendance\/self/);
  assert.match(source, /\/attendance\/code/);
  assert.match(source, /inputMode="numeric"/);
  assert.match(source, /autoComplete="one-time-code"/);
  assert.match(source, /maxLength=\{6\}/);
  assert.match(source, /Record code attendance · 20 points/);
  assert.match(source, /I attended · 0 points/);
  assert.match(source, /joiners recorded attendance using the organiser’s code/);
  assert.match(source, /joiners reported attending/);
});

test('attendance UI remains usable on narrow screens and by keyboard', () => {
  assert.match(styles, /\.attendance-code/);
  assert.match(styles, /\.attendance-entry-panel input/);
  assert.match(styles, /font-size: clamp\(/);
  assert.match(styles, /width: 100%/);
  assert.match(source, /aria-label=\{t\('Current organiser attendance code'\)\}/);
  assert.match(source, /role="status"/);
});

test('Initiate is for creating or joining while personal initiatives live in My Actions', () => {
  assert.match(source, /Create or join an initiative/);
  assert.match(source, /<h2>\{t\('Create an Initiative'\)\}<\/h2>/);
  assert.match(source, /<h2>\{t\('Join an Initiative'\)\}<\/h2>/);
  assert.match(source, /My Reports/);
  assert.match(source, /My Initiatives/);
  assert.match(source, /initiative-role-chip/);
  assert.match(styles, /\.initiative-role-chip\.role-completed/);
  assert.doesNotMatch(source, /initiative-duty-link/);
  assert.doesNotMatch(source, /<h2>\{t\('My activities'\)\}<\/h2>/);
});

test('attendance and reward language is available in Marathi and Hindi', () => {
  for (const key of [
    'Organiser attendance code',
    'Record code attendance · 20 points',
    'I attended · 0 points',
    'The organiser is not included in the joiner count. Neither attendance method is independently verified.',
  ]) {
    assert.notEqual(translate('mr', key), key);
    assert.notEqual(translate('hi', key), key);
  }
});

test('backend-only attendance collections and corrected reward contract are frozen', () => {
  assert.match(rules, /match \/initiativeParticipations\/\{participationId\}/);
  assert.match(rules, /match \/initiativeAttendanceAttempts\/\{attemptId\}/);
  assert.match(rules, /allow read, create, update, delete: if false/);
  assert.match(contract, /First accepted report filing \| 5/);
  assert.match(contract, /Organiser-code attendance \| 20/);
  assert.match(contract, /Completed Initiative organiser threshold \| 40/);
  assert.match(contract, /First confirmed civic fix \| 60/);
  assert.match(contract, /No attendance flow requests geolocation, QR scanning or photo evidence/);
});
