import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');

test('changing photo or text clears evidence-derived complaint state', () => {
  const reset = source.match(/function resetEvidenceDerivedState\(\) \{(?<body>[\s\S]*?)\n  \}/)?.groups?.body ?? '';
  assert.match(reset, /setClassification\(null\)/);
  assert.match(reset, /setClassificationConfirmed\(false\)/);
  assert.match(reset, /setComplaintFacts\(''\)/);
  assert.match(reset, /setRouteResult\(null\)/);
  assert.equal(source.match(/resetEvidenceDerivedState\(\);/g)?.length, 2);
});

test('a new classification replaces facts with the current evidence result', () => {
  assert.match(source, /setComplaintFacts\(evidenceText\.trim\(\) \|\| result\.description \|\| ''\)/);
});
