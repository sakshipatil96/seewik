import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { translate } from '../src/i18n.ts';

const app = readFileSync(new URL('../src/main.tsx', import.meta.url), 'utf8');

const priorityCopy = [
  "Let's find how to report this issue",
  'Add a photo or short description before finding the official route.',
  'Wait for the photo analysis to finish, or add a short description.',
  'CIVIC RESPONSIBILITY ROUTER',
  'Here is the responsible authority',
  'Responsible authority',
  'Selected issue',
  'Trying AI wording…',
  'Try AI wording again',
  'Prepare filing',
  'Complaint language',
  'Your complaint is prepared in Marathi or English. We save this choice so your manual edits are preserved.',
  'AI wording is ready',
  'Your current wording has not changed. Review this suggestion and choose whether to use it.',
  'Suggested subject',
  'Suggested complaint body',
  'Use AI wording',
  'Keep my wording',
  'Use this letter for the office route; print, save, or share, then submit it in person.',
  'Your device location could not be used. Search for an address or select your Prabhag manually.',
];

test('the 20 highest-visibility Day 17 report and filing strings have Marathi and Hindi copy', () => {
  assert.equal(priorityCopy.length, 20);
  for (const key of priorityCopy) {
    assert.notEqual(translate('mr', key), key, `missing Marathi copy for: ${key}`);
    assert.notEqual(translate('hi', key), key, `missing Hindi copy for: ${key}`);
  }
});

test('device-location failure gives the required recovery message without removing manual paths', () => {
  const message = 'Your device location could not be used. Search for an address or select your Prabhag manually.';
  assert.match(app, new RegExp(message.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  assert.match(app, /setReportLocationStatus\(/);
  assert.match(app, /<GoogleMeetingPointSearch/);
  assert.match(app, /onChange=\{\(event\) => selectManualPrabhag\(event\.target\.value\)\}/);
});
