import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { deviceLanguage, initialLanguage, localizedStatus, prabhagConfirmedMessage, translate, translationCoverage } from '../src/i18n.ts';

const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');

test('interface language follows a supported device language with English fallback', () => {
  assert.equal(deviceLanguage(['mr-IN', 'en-US']), 'mr');
  assert.equal(deviceLanguage(['hi-IN']), 'hi');
  assert.equal(deviceLanguage(['fr-FR']), 'en');
  assert.equal(initialLanguage('hi', ['mr-IN']), 'hi');
  assert.equal(initialLanguage(null, ['mr-IN']), 'mr');
});

test('versioned Marathi and Hindi catalogue entries are non-empty', () => {
  const coverage = translationCoverage();
  assert.ok(coverage.keyCount >= 150);
  assert.deepEqual(coverage.missingMarathi, []);
  assert.deepEqual(coverage.missingHindi, []);
  assert.equal(translate('mr', 'Home'), 'मुख्यपृष्ठ');
  assert.equal(translate('hi', 'Home'), 'मुखपृष्ठ');
  assert.equal(localizedStatus('mr', 'VERIFIED_FIXED'), 'दुरुस्ती पडताळली');
  assert.match(prabhagConfirmedMessage('hi', 'Prabhag 11'), /Prabhag 11/);
});

test('language and navigation accessibility foundations are present', () => {
  assert.match(source, /className="skip-link"/);
  assert.match(source, /className="language-switcher"/);
  assert.match(source, /document\.documentElement\.lang = language/);
  assert.match(source, /aria-current=/);
  assert.match(source, /role="status"/);
  assert.match(styles, /:focus-visible/);
  assert.match(styles, /min-height: 44px/);
});
