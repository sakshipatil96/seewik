import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const component = await readFile(new URL('../src/ContributionPoster.tsx', import.meta.url), 'utf8');
const renderer = await readFile(new URL('../src/civicCardImage.ts', import.meta.url), 'utf8');

test('the poster is generated only after the citizen explicitly presses create', () => {
  assert.match(component, /onClick=\{\(\) => \{ void createPoster\(\); \}\}/);
  assert.match(component, /Create my Civic Card image/);
  assert.doesNotMatch(component, /useEffect\([^)]*createPoster/);
});

test('the local image includes a chosen name and safe high-level contribution fields only', () => {
  assert.match(renderer, /data\.displayName/);
  assert.match(renderer, /lifetimePoints/);
  assert.match(renderer, /currentMonthPoints/);
  assert.match(renderer, /contributionLabels/);
  assert.match(renderer, /Not a government document/);
  assert.doesNotMatch(renderer, /email|ownerUid|uid|coordinates|precise location|complaint text|report evidence|activity history/i);
});

test('sharing uses a device file share with download fallback and no upload or public URL', () => {
  assert.match(component, /navigator\.canShare/);
  assert.match(component, /navigator\.share/);
  assert.match(component, /downloadPoster\(result\)/);
  assert.match(renderer, /canvas\.toBlob/);
  assert.doesNotMatch(`${component}\n${renderer}`, /fetch\(|uploadBytes|publicPoster|https?:\/\//i);
  assert.match(component, /does not change your public-recognition choice/);
});
