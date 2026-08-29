import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {
  ACCOUNT_AUDIT_SCHEMA_VERSION,
  PROFILE_SCHEMA_VERSION,
  accountErrorMessage,
  accountIdentityState,
  collisionAuditData,
  isCredentialCollisionCode,
  minimalProfileData,
  reportsViewState,
  safeAccountErrorCode,
} from '../src/accountIdentity.ts';

test('identity states distinguish anonymous, linked, and deliberate sign-out', () => {
  assert.equal(accountIdentityState({ uid: 'anon-1', isAnonymous: true, providerIds: [] }), 'GOOGLE_LINK_REQUIRED');
  assert.equal(accountIdentityState({ uid: 'linked-1', isAnonymous: false, providerIds: ['google.com'] }), 'GOOGLE_LINKED');
  assert.equal(accountIdentityState({ uid: null, isAnonymous: false, providerIds: [], deliberatelySignedOut: true }), 'SIGNED_OUT');
  assert.equal(accountIdentityState({ uid: null, isAnonymous: false, providerIds: [] }), 'ANONYMOUS_SESSION');
});

test('report states distinguish sign-out, recovery, and zero-report ownership', () => {
  assert.equal(reportsViewState('SIGNED_OUT', 0, false), 'SIGNED_OUT');
  assert.equal(reportsViewState('GOOGLE_LINK_REQUIRED', 0, true), 'LOADING');
  assert.equal(reportsViewState('GOOGLE_LINK_REQUIRED', 0, false), 'ANONYMOUS_EMPTY');
  assert.equal(reportsViewState('GOOGLE_LINKED', 0, false), 'LINKED_EMPTY');
  assert.equal(reportsViewState('GOOGLE_LINKED', 2, true), 'HAS_REPORTS');
});

test('credential collision codes are separated from ordinary popup failures', () => {
  assert.equal(isCredentialCollisionCode('auth/credential-already-in-use'), true);
  assert.equal(isCredentialCollisionCode('auth/account-exists-with-different-credential'), true);
  assert.equal(isCredentialCollisionCode('auth/popup-blocked'), false);
  assert.match(accountErrorMessage('auth/popup-closed-by-user'), /form is still here/i);
  assert.match(accountErrorMessage('auth/operation-not-allowed'), /temporarily unavailable/i);
  assert.equal(safeAccountErrorCode('auth/internal-error'), 'auth/internal-error');
  assert.equal(safeAccountErrorCode('auth/unauthorized-domain'), 'auth/unauthorized-domain');
  assert.equal(safeAccountErrorCode('permission-denied'), 'permission-denied');
  assert.equal(safeAccountErrorCode('contains private details'), 'auth/unknown');
  assert.equal(safeAccountErrorCode(undefined), 'auth/unknown');
});

test('caught Google failures expose only a privacy-safe diagnostic code', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const control = await readFile(new URL('../src/AccountControl.tsx', import.meta.url), 'utf8');
  assert.match(source, /setAccountErrorCode\(safeAccountErrorCode\(code\)\)/);
  assert.match(control, /Diagnostic code/);
  assert.doesNotMatch(control, /error\.message/);
});

test('production deploy includes the profile security rules required after linking', async () => {
  const workflow = await readFile(new URL('../../.github/workflows/deploy.yml', import.meta.url), 'utf8');
  assert.match(workflow, /--only hosting,firestore:rules,storage/);
});

test('profile contract is versioned, UID-preserving, and contains no copied Google PII', () => {
  const profile = minimalProfileData('uid-before-link');
  assert.deepEqual(profile, {
    ownerUid: 'uid-before-link',
    authProvider: 'GOOGLE',
    recoverable: true,
    schemaVersion: PROFILE_SCHEMA_VERSION,
  });
  assert.equal('email' in profile, false);
  assert.equal('displayName' in profile, false);
  assert.equal('photoURL' in profile, false);
});

test('collision audit records only the winning UID and fixed privacy-safe codes', () => {
  const audit = collisionAuditData('winning-uid');
  assert.deepEqual(audit, {
    ownerUid: 'winning-uid',
    eventType: 'ACCOUNT_COLLISION_EXISTING_ACCOUNT_WON',
    outcome: 'EXISTING_ACCOUNT_SELECTED',
    schemaVersion: ACCOUNT_AUDIT_SCHEMA_VERSION,
  });
  assert.equal('losingUid' in audit, false);
  assert.equal('reportId' in audit, false);
  assert.equal('initiativeId' in audit, false);
});

test('all durable frontend mutations pass through the reusable link gate', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const guardedActions = [
    'createInitiative',
    'joinInitiative',
    'changeInitiativeStatus',
    'saveDraftEdits',
    'copyReviewedDraft',
    'fileReviewedReport',
    'transitionReport',
    'verifyFirebase',
  ];
  for (const action of guardedActions) {
    assert.match(source, new RegExp(`requestLinkedMutation\\(\\(\\) => ${action}\\(`), `${action} must be invoked through the link gate`);
  }
  assert.match(source, /requestLinkedMutation\(\(\) => saveGeneratedDraft\(result\)\)/);
});

test('Firestore profile and audit writes require a linked Google token', async () => {
  const rules = await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8');
  assert.match(rules, /firebase\.identities\["google\.com"\] != null/);
  assert.doesNotMatch(rules, /sign_in_provider == 'google\.com'/);
  assert.match(rules, /match \/profiles\/\{uid\}/);
  assert.match(rules, /match \/accountAuditEvents\/\{eventId\}/);
  assert.match(rules, /allow delete: if false;/);
});

test('anonymous callers cannot bypass report or technical write gates', async () => {
  const firestoreRules = await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8');
  const storageRules = await readFile(new URL('../../storage.rules', import.meta.url), 'utf8');
  const initiativeController = await readFile(new URL('../../backend/src/main/java/com/seewik/api/InitiativeController.java', import.meta.url), 'utf8');
  const lifecycleController = await readFile(new URL('../../backend/src/main/java/com/seewik/api/ReportLifecycleController.java', import.meta.url), 'utf8');
  assert.match(firestoreRules, /match \/day1_checks\/\{uid\}[\s\S]*?allow write: if googleLinked\(\)/);
  assert.match(firestoreRules, /match \/reports\/\{reportId\}[\s\S]*?allow create: if googleLinked\(\)/);
  assert.match(firestoreRules, /allow update: if googleLinked\(\)/);
  assert.match(firestoreRules, /allow delete: if googleLinked\(\)/);
  assert.match(storageRules, /allow write: if googleLinked\(\)/);
  assert.match(initiativeController, /requireGoogleLinked/);
  assert.match(lifecycleController, /requireGoogleLinked/);
  assert.match(initiativeController, /GOOGLE_LINK_REQUIRED/);
  assert.match(lifecycleController, /GOOGLE_LINK_REQUIRED/);
});

test('linking preserves the UID, refreshes the token, and deliberate sign-out suppresses anonymous recreation', async () => {
  const service = await readFile(new URL('../src/accountService.ts', import.meta.url), 'utf8');
  assert.match(service, /const beforeUid = user\.uid/);
  assert.match(service, /credential\.user\.uid !== beforeUid/);
  assert.match(service, /credential\.user\.getIdToken\(true\)/);
  assert.match(service, /localStorage\.setItem\(SIGNED_OUT_STORAGE_KEY, 'true'\)/);
  assert.match(service, /if \(signedOutDeliberately\(\)\) throw new Error\('ACCOUNT_SIGNED_OUT'\)/);
});

test('existing-account collision never retries the losing session mutation', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const collisionHandler = source.slice(
    source.indexOf('async function acceptExistingGoogleAccount'),
    source.indexOf('async function signOutAccount'),
  );
  assert.match(collisionHandler, /pendingMutation\.current = null/);
  assert.doesNotMatch(collisionHandler, /resumePendingMutation/);
  assert.match(collisionHandler, /loadMyReports\(\), refreshDerivedPoints\(\), loadMyInitiatives\(\)/);
});

test('an unlinked anonymous session receives an explicit device-only recovery warning', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  assert.match(source, /accountState === 'GOOGLE_LINK_REQUIRED'/);
  assert.match(source, /Device-only access/);
  assert.match(source, /clear this browser before connecting Google/);
});

test('signed-out reports show recovery copy without raw codes or misleading controls', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const i18n = await readFile(new URL('../src/i18n.ts', import.meta.url), 'utf8');
  assert.match(source, /reportsView === 'SIGNED_OUT'/);
  assert.match(source, /Sign in to view your saved civic work/);
  assert.match(source, /Signing out doesn't delete anything/);
  assert.match(source, /reportsView === 'LINKED_EMPTY'/);
  assert.match(source, /\['new-report', 'review'\]\.includes\(screen\)/);
  assert.doesNotMatch(source, /\['new-report', 'review', 'reports', 'report-detail'\]\.includes\(screen\)/);
  assert.match(i18n, /message\.includes\('ACCOUNT_SIGNED_OUT'\)/);
});
