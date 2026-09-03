import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { deviceLanguage, initialLanguage, localizedMonthLabel, localizedStatus, prabhagConfirmedMessage, translate, translationCoverage } from '../src/i18n.ts';

const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');
const awareness = JSON.parse(await readFile(new URL('../src/content/civic-awareness-v0.1.json', import.meta.url), 'utf8'));

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
  assert.equal(localizedMonthLabel('hi', 'August 2026'), 'अगस्त 2026');
  assert.equal(localizedMonthLabel('mr', 'August 2026'), 'ऑगस्ट २०२६');
});

test('Day 12 awareness facts and actions have Marathi and Hindi text', () => {
  const content = awareness.topics.flatMap((topic) => [
    topic.heading,
    topic.summary,
    ...topic.limitations,
    ...topic.sections.flatMap((section) => [section.text, ...(section.links ?? [])]),
  ]);
  for (const key of content) {
    assert.notEqual(translate('mr', key), key, `Missing Marathi: ${key}`);
    assert.notEqual(translate('hi', key), key, `Missing Hindi: ${key}`);
  }
});

test('language and navigation accessibility foundations are present', () => {
  assert.match(source, /className="skip-link"/);
  assert.match(source, /className="language-switcher"/);
  assert.match(source, /document\.documentElement\.lang = language/);
  assert.match(source, /aria-current=/);
  assert.match(source, /role="status"/);
  assert.match(styles, /:focus-visible/);
  assert.match(styles, /min-height: 44px/);
  assert.match(styles, /\.brand-button \{[^}]*white-space: nowrap;/);
  const baseNavigation = styles.slice(0, styles.indexOf('@media (max-width: 1080px)'));
  const compactNavigation = styles.slice(styles.indexOf('@media (max-width: 1080px)'), styles.indexOf('@media (max-width: 760px)'));
  assert.match(baseNavigation, /\.mobile-nav \{[^}]*position: fixed;[^}]*display: grid;/);
  assert.doesNotMatch(baseNavigation, /\.mobile-nav \{ display: none; \}/);
  assert.match(baseNavigation, /calc\(92px \+ env\(safe-area-inset-bottom\)\)/);
  assert.match(compactNavigation, /\.desktop-nav \{ display: none; \}/);
});
