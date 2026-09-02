import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const apiConfig = await readFile(new URL('../src/apiConfig.ts', import.meta.url), 'utf8');
const viteConfig = await readFile(new URL('../vite.config.ts', import.meta.url), 'utf8');

test('localhost API calls use the Vite same-origin proxy on any local port', () => {
  assert.match(apiConfig, /window\.location\.hostname === 'localhost'/);
  assert.match(apiConfig, /window\.location\.hostname === '127\.0\.0\.1'/);
  assert.match(apiConfig, /localBrowser \? '' : DEPLOYED_API_URL/);
  assert.match(viteConfig, /['"]\/api['"]\s*:/);
  assert.match(viteConfig, /server:\s*\{ proxy: apiProxy \}/);
  assert.match(viteConfig, /preview:\s*\{ proxy: apiProxy \}/);
  assert.match(viteConfig, /removeHeader\('origin'\)/);
});
