import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('reward catalogue keeps the example and simulated-use boundary visible', async () => {
  const component = await readFile(new URL('../src/RewardCatalogue.tsx', import.meta.url), 'utf8');
  assert.match(component, /Example local reward/);
  assert.match(component, /Simulate using this reward/);
  assert.match(component, /This is a demonstration\. No shop has verified or accepted this code\./);
  assert.match(component, /Claiming never reduces your points/);
  assert.match(component, /LOCKED/);
  assert.match(component, /UNLOCKED/);
  assert.match(component, /CLAIMED/);
  assert.match(component, /USED/);
  assert.match(component, /EXPIRED/);
  assert.doesNotMatch(component, /\b(?:redeem|redeemed|spent|balance|cash value)\b/i);
});

test('reward mutations go only through authenticated backend endpoints', async () => {
  const client = await readFile(new URL('../src/recognitionClient.ts', import.meta.url), 'utf8');
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  assert.match(client, /\/api\/recognition\/me\/rewards/);
  assert.match(client, /\/claims/);
  assert.match(client, /\/simulate-use/);
  const rewardFunctions = source.slice(source.indexOf('async function refreshRewards'), source.indexOf('async function transitionReport'));
  assert.match(rewardFunctions, /sessionToken\(true\)/);
  assert.doesNotMatch(rewardFunctions, /recognitionRewardClaims|recognitionRewardEvents|setDoc|updateDoc/);
});

test('all reward-facing copy is localized in Marathi and Hindi', async () => {
  const copy = await readFile(new URL('../src/i18n.ts', import.meta.url), 'utf8');
  for (const phrase of [
    'Example local reward',
    'Contribution rewards',
    'Claim example reward',
    'Simulate using this reward',
    'Used in simulation',
    'Expired',
    'This is a demonstration. No shop has verified or accepted this code.',
  ]) {
    const escaped = phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    assert.match(copy, new RegExp(`'${escaped}': \\{ mr: '.+', hi: '.+' \\}`));
  }
});

test('reward cards adapt from desktop to narrow screens and expose status updates', async () => {
  const component = await readFile(new URL('../src/RewardCatalogue.tsx', import.meta.url), 'utf8');
  const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');
  assert.match(component, /aria-live="polite"/);
  assert.match(component, /aria-label=\{t\('Progress to the next reward tier'\)\}/);
  assert.match(styles, /\.reward-grid \{[^}]*grid-template-columns: repeat\(3, minmax\(0, 1fr\)\)/);
  assert.match(styles, /@media \(max-width: 520px\)[\s\S]*\.reward-grid \{ grid-template-columns: 1fr; \}/);
});
