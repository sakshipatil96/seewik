import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const accountService = readFileSync(new URL('../src/accountService.ts', import.meta.url), 'utf8');
const firebase = readFileSync(new URL('../src/firebase.ts', import.meta.url), 'utf8');

test('local E2E auth avoids popup relay while production retains popup sign-in', () => {
  assert.match(firebase, /VITE_USE_FIREBASE_EMULATORS/);
  assert.match(accountService, /useFirebaseEmulators[\s\S]*linkWithCredential/);
  assert.match(accountService, /localE2EGoogleCredential/);
  assert.doesNotMatch(accountService, /linkWithRedirect|signInWithRedirect|getRedirectResult/);
  assert.match(accountService, /linkWithPopup\(user, providers\[provider\]\)/);
  assert.match(accountService, /signInWithPopup\(auth, providers\[provider\]\)/);
});
