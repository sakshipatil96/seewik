import assert from 'node:assert/strict';
import test from 'node:test';
import { automaticEscalationLanguageTransition } from '../src/escalationDraftLanguage.ts';

test('an automatic escalation draft follows English and Marathi or Hindi interfaces', () => {
  assert.deepEqual(automaticEscalationLanguageTransition('en', false, 'MR'), { nextLanguage: 'EN', shouldSwitch: true });
  assert.deepEqual(automaticEscalationLanguageTransition('mr', false, 'EN'), { nextLanguage: 'MR', shouldSwitch: true });
  assert.deepEqual(automaticEscalationLanguageTransition('hi', false, 'EN'), { nextLanguage: 'MR', shouldSwitch: true });
});

test('a manual draft-language choice is never overwritten by an interface change', () => {
  assert.deepEqual(automaticEscalationLanguageTransition('mr', true, 'EN'), { nextLanguage: 'EN', shouldSwitch: false });
  assert.deepEqual(automaticEscalationLanguageTransition('en', true, 'MR'), { nextLanguage: 'MR', shouldSwitch: false });
});
