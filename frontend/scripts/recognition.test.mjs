import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('the public contract exposes ordered names without private selection fields', async () => {
  const client = await readFile(new URL('../src/recognitionClient.ts', import.meta.url), 'utf8');
  const publicContract = client.slice(
    client.indexOf('export type PublicRecognitionPanel'),
    client.indexOf('export type RecognitionSettings'),
  );
  assert.match(publicContract, /names: string\[\]/);
  assert.doesNotMatch(publicContract, /uid|email|point|rank|photo|reportId|initiativeId/i);
  assert.match(client, /\/api\/recognition\/current/);
});

test('the panel shows equal names-only cards and honest empty or partial arrays', async () => {
  const panel = await readFile(new URL('../src/RecognitionPanel.tsx', import.meta.url), 'utf8');
  const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');
  assert.match(panel, /Thanks to Our Top Three Citizens of the Month/);
  assert.match(panel, /panel\.names\.map/);
  assert.match(panel, /panel\.names\.length === 0/);
  assert.match(panel, /recognition-name-card/);
  assert.match(panel, /A Seewik thank-you for recorded civic contributions\./);
  assert.doesNotMatch(panel, /not government certification or independent verification of impact/i);
  assert.doesNotMatch(panel, /monthlyPoints|ownerUid|privateGoogle|photoURL|1st|2nd|3rd/);
  assert.match(styles, /\.recognition-names[\s\S]*grid-template-columns: repeat\(3, minmax\(0, 1fr\)\)/);
  assert.match(styles, /\.recognition-name-card[\s\S]*min-height: 150px/);
});

test('recognition consent is explicit, editable, withdrawable, and separate from sign-in', async () => {
  const settings = await readFile(new URL('../src/RecognitionSettings.tsx', import.meta.url), 'utf8');
  const cors = await readFile(new URL('../../backend/src/main/java/com/seewik/api/WebConfig.java', import.meta.url), 'utf8');
  assert.match(settings, /Exact public preview/);
  assert.match(settings, /I choose to make this display name eligible/);
  assert.match(settings, /Opt in with this public name/);
  assert.match(settings, /Save public name/);
  assert.match(settings, /Withdraw from public recognition/);
  assert.match(settings, /without deleting or changing your points/);
  assert.doesNotMatch(settings, /onGoogle|signInWithPopup|linkWithPopup/);
  assert.match(cors, /allowedMethods\("GET", "POST", "PUT", "OPTIONS"\)/);
});

test('private point details use an owner-authenticated API rather than direct ledger reads', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const client = await readFile(new URL('../src/recognitionClient.ts', import.meta.url), 'utf8');
  const refresh = source.slice(source.indexOf('async function refreshDerivedPoints'), source.indexOf('async function transitionReport'));
  assert.match(refresh, /fetchPrivatePoints/);
  assert.doesNotMatch(refresh, /pointsLedger|getDocs|where\(/);
  assert.match(client, /\/api\/recognition\/me\/points/);
  assert.match(source, /currentMonthPoints/);
  assert.match(source, /breakdown\.map/);
});

test('Firestore blocks forged profiles, consents, snapshots and recognition reports', async () => {
  const rules = await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8');
  for (const collection of [
    'profiles',
    'recognitionConsents',
    'recognitionConsentEvents',
    'recognitionNameCollisions',
    'recognitionMonths',
    'recognitionAbuseReports',
    'recognitionRewardClaims',
    'recognitionRewardEvents',
  ]) {
    const start = rules.indexOf(`match /${collection}/`);
    assert.notEqual(start, -1, `${collection} rules must exist`);
    const block = rules.slice(start, rules.indexOf('\n    match /', start + 1));
    assert.match(block, /allow .*create.*update.*delete: if false;/s, `${collection} writes must be backend-only`);
  }
});
