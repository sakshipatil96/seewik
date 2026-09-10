import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const main = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
const picker = await readFile(new URL('../src/TemplatePicker.tsx', import.meta.url), 'utf8');
const search = await readFile(new URL('../src/GoogleMeetingPointSearch.tsx', import.meta.url), 'utf8');
const map = await readFile(new URL('../src/PrabhagBoundaryMap.tsx', import.meta.url), 'utf8');
const i18n = await readFile(new URL('../src/i18n.ts', import.meta.url), 'utf8');
const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');

test('category autofill has waiting, active, slow, complete, failure and manual states', () => {
  assert.match(main, /classificationPhase.*'idle'.*'waiting'.*'analyzing'.*'slow'.*'complete'.*'failed'.*'manual'/);
  assert.match(main, /classificationSlowTimer[\s\S]*5_000/);
  assert.match(main, /classificationTimeoutTimer[\s\S]*25_000/);
  assert.match(main, /signal: controller\.signal/);
  assert.match(main, /chooseCategoryManuallyInstead[\s\S]*classificationRequestSequence\.current \+= 1/);
});

test('location autofill is independent and every manual path invalidates late results', () => {
  assert.match(main, /reportLocationPhase.*'idle'.*'waiting'.*'analyzing'.*'slow'.*'complete'.*'failed'.*'manual'/);
  assert.match(main, /reportLocationSlowTimer[\s\S]*5_000/);
  assert.match(main, /reportLocationTimeoutTimer[\s\S]*25_000/);
  assert.match(main, /chooseLocationManuallyInstead[\s\S]*reportLocationRequestSequence\.current \+= 1/);
  assert.match(main, /requestSequence !== reportLocationRequestSequence\.current/);
  assert.doesNotMatch(main, /reportDeviceLocationAttempted/);
  assert.match(main, /'NOT_REQUESTED' \| 'PENDING' \| 'GRANTED' \| 'DECLINED_OR_UNAVAILABLE'/);
  assert.match(main, /reportAutomaticLocationSuppressed/);
  assert.match(main, /reportDeviceLocationState\.current === 'DECLINED_OR_UNAVAILABLE'/);
  assert.match(main, /reportDeviceLocationState\.current === 'PENDING'/);
});

test('all automatic target controls honor the temporary lock', () => {
  assert.match(main, /id="issue-category"[\s\S]*?disabled=\{classificationPhase === 'analyzing' \|\| classificationPhase === 'slow'\}/);
  assert.match(main, /<GoogleMeetingPointSearch[\s\S]*disabled=\{reportLocationPhase === 'analyzing' \|\| reportLocationPhase === 'slow'\}/);
  assert.match(main, /id="report-prabhag-select"[^>]*disabled=\{reportLocationPhase === 'analyzing' \|\| reportLocationPhase === 'slow'\}/);
  assert.match(picker, /disabled\?: boolean/);
  assert.match(search, /disabled\?: boolean/);
  assert.match(map, /aria-disabled=\{disabled\}/);
});

test('loading guidance is localized and accessible without a full-screen overlay', () => {
  for (const message of [
    'Reading your photo and suggesting a category…',
    'Still suggesting the issue category…',
    'Choose category manually instead',
    'Checking the photo for location…',
    'Still finding your location…',
    'Choose location manually instead',
  ]) assert.match(i18n, new RegExp(message.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  assert.match(main, /aria-busy=\{classificationPhase === 'analyzing' \|\| classificationPhase === 'slow'\}/);
  assert.match(styles, /@keyframes autofill-spin/);
  assert.match(styles, /prefers-reduced-motion[\s\S]*autofill-indicator/);
  assert.doesNotMatch(styles, /autofill[^}]*position:\s*fixed/);
});

test('automatic failures stop animating and never expose backend errors', () => {
  assert.match(main, /classificationPhase === 'failed' \? \{ animation: 'none' \}/);
  assert.match(main, /reportLocationPhase === 'failed' \? \{ animation: 'none' \}/);
  assert.match(main, /We could not suggest a category automatically\. Please choose one manually\./);
  assert.match(i18n, /Category suggestion unavailable/);
  assert.doesNotMatch(main, /setClassificationStatus\(result\.message/);
});
