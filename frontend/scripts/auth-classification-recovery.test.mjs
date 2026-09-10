import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('classification recovers stale anonymous auth once without replacing linked users', async () => {
  const [accountService, main] = await Promise.all([
    readFile(new URL('../src/accountService.ts', import.meta.url), 'utf8'),
    readFile(new URL('../src/main.tsx', import.meta.url), 'utf8'),
  ]);

  assert.match(accountService, /if \(recreateAnonymous && user\.isAnonymous\)/);
  assert.match(accountService, /if \(!allowAnonymousRecovery \|\| !user\.isAnonymous\) throw error/);
  assert.match(accountService, /window\.localStorage\.removeItem\(SIGNED_OUT_STORAGE_KEY\)/);
  assert.match(main, /allowAnonymousRecovery: true/);
  assert.match(main, /if \(response\.status === 401\) \{\s*response = await requestClassification\(true, true\);\s*\}/);
});
