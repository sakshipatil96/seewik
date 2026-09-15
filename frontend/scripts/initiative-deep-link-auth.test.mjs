import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const mainSource = readFileSync(new URL('../src/main.tsx', import.meta.url), 'utf8');
const translations = readFileSync(new URL('../src/i18n.ts', import.meta.url), 'utf8');

test('signed-out activity deep links preserve their destination through Google recovery', () => {
  assert.match(mainSource, /screen === 'initiative-detail' && accountState !== 'SIGNED_OUT'/);
  assert.match(mainSource, /\[screen, locationKey, accountState\]/);
  assert.match(mainSource, /accountState === 'SIGNED_OUT' && <div className="community-empty-state account-recovery-state">/);
  assert.match(mainSource, /This shared activity link will stay open while you connect Google\./);
  assert.match(mainSource, /<button onClick=\{openAccount\}>\{t\('Continue with Google'\)\}<\/button>/);
});

test('not-found activity copy is reserved for an authenticated lookup result', () => {
  assert.match(mainSource, /accountState !== 'SIGNED_OUT' && !initiativeDetailLoading && !selectedInitiative/);
  assert.match(mainSource, /This activity could not be found\./);
});

test('activity-link recovery guidance is translated in Marathi and Hindi', () => {
  for (const message of [
    'Sign in to view and join this activity.',
    'This shared activity link will stay open while you connect Google.',
    'The link keeps this activity ready if the recipient needs to sign in.',
  ]) {
    const line = translations.split('\n').find((entry) => entry.includes(`'${message}'`));
    assert.ok(line?.includes('mr:') && line.includes('hi:'), `${message} must have Marathi and Hindi translations`);
  }
});
