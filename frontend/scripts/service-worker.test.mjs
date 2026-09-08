import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('service worker clones before caching and keeps the full cache write alive', async () => {
  const source = await readFile(new URL('../public/sw.js', import.meta.url), 'utf8');
  assert.match(source, /seewik-app-v3/);
  assert.match(source, /const cacheResponse=response\.clone\(\)/);
  assert.match(source, /await caches\.open\(CACHE\)[\s\S]*await cache\.put\(event\.request,cacheResponse\)/);
  assert.match(source, /event\.waitUntil\(responsePromise\.then\(\(\)=>undefined,\(\)=>undefined\)\)/);
  assert.doesNotMatch(source, /cache\.put\(event\.request,response\.clone\(\)\)/);
});
