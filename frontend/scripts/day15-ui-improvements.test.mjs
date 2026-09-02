import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const app = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');
const backend = await readFile(new URL('../../backend/src/main/java/com/seewik/api/InitiativeService.java', import.meta.url), 'utf8');
const civicPack = await readFile(new URL('../../backend/src/main/resources/civic-pack-v0.2.json', import.meta.url), 'utf8');

test('desktop header controls share a stable height and emergency copy cannot wrap vertically', () => {
  assert.match(styles, /\.desktop-nav button[^}]*height: 48px/);
  assert.match(styles, /\.header-emergency[^}]*height: 48px[^}]*white-space: nowrap/);
  assert.match(styles, /\.account-button[^}]*height: 48px/);
  assert.match(styles, /\.language-switcher select[^}]*min-height: 48px/);
  assert.match(styles, /@media \(max-width: 1080px\)/);
});

test('home removes the large report title while retaining concise civic guidance', () => {
  assert.doesNotMatch(app, /<h1>\{t\('Report a problem\. Get it to the right office\.'\)\}<\/h1>/);
  assert.match(app, /hero home-intro/);
});

test('report flow provides camera, editable confirmation, and three honest filing choices', () => {
  assert.match(app, /capture="environment"/);
  assert.match(app, /Prefill report details/);
  assert.match(app, /Recipient email/);
  assert.match(app, /mailto:/);
  assert.match(app, /Copy complaint and open form/);
  assert.match(app, /Share letter/);
  assert.match(app, /Print letter/);
  assert.match(app, /Seewik did not send it/);
  assert.match(civicPack, /complaint-2\/\?dma_tab=regional/);
});

test('initiative templates and backend categories stay aligned', () => {
  for (const category of [
    'BIRTHDAY_DONATION', 'PLANTATION_DRIVE', 'AWARENESS_SESSION',
    'COMMUNITY_YOGA', 'MEDITATION_WORKSHOP', 'HEALTH_ACTIVITY', 'OTHER_CIVIC_ACTIVITY',
  ]) {
    assert.match(app, new RegExp(category));
    assert.match(backend, new RegExp(category));
  }
  assert.match(app, /Additional info \(optional\)/);
  assert.match(styles, /\.initiative-choice-grid \.card \+ \.card[^}]*margin-top: 0/);
});

test('My Initiatives uses the full available width and point rules are last on Civic Card', () => {
  assert.match(styles, /\.initiative-memberships > \.compact-list[^}]*grid-template-columns: 1fr/);
  const recognitionIndex = app.lastIndexOf('<RecognitionPanel');
  const pointsRulesIndex = app.lastIndexOf('points-rules-card');
  assert.ok(pointsRulesIndex > recognitionIndex);
});
