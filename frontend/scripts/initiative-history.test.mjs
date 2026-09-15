import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');
const translations = await readFile(new URL('../src/i18n.ts', import.meta.url), 'utf8');

test('sharing is limited to future published activities and uses compact title-row controls', () => {
  assert.match(source, /initiative\.status === 'PUBLISHED' && Number\.isFinite\(endAt\) && endAt > Date\.now\(\)/);
  assert.match(source, /className="initiative-title-row"/);
  assert.match(source, /className="initiative-share-icon"/);
  assert.doesNotMatch(source, /activity-share-panel/);
  assert.match(styles, /\.initiative-share-icon/);
});

test('organiser archive and permanently-delete actions remain distinct', () => {
  assert.match(source, /archived \? 'archive' : 'unarchive'/);
  assert.match(source, /deletion-eligibility/);
  assert.match(source, /method: 'DELETE'/);
  assert.match(source, /Delete this empty activity permanently\? This cannot be undone\./);
  assert.match(source, /archivedByOrganiser/);
  assert.match(source, /Current activities/);
  assert.match(source, /Past and cancelled/);
});

test('history controls are translated in Marathi and Hindi', () => {
  for (const message of ['Archive activity', 'Unarchive activity', 'Archived activities', 'Current activities', 'Past and cancelled', 'Check deletion eligibility', 'Delete permanently']) {
    const line = translations.split('\n').find((entry) => entry.includes(`'${message}'`));
    assert.ok(line?.includes('mr:') && line.includes('hi:'), `${message} must have Marathi and Hindi translations`);
  }
});
