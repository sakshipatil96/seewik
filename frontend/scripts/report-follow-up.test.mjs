import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const app = readFileSync(new URL('../src/main.tsx', import.meta.url), 'utf8');
const copy = readFileSync(new URL('../src/i18n.ts', import.meta.url), 'utf8');
const service = readFileSync(new URL('../../backend/src/main/java/com/seewik/api/ReportFollowUpService.java', import.meta.url), 'utf8');

test('follow-up timing is backend-owned and recurrence-aware', () => {
  assert.match(service, /Duration\.ofDays\(7\)/);
  assert.match(service, /Duration\.ofDays\(3\)/);
  assert.match(service, /VERIFIED_FIXED/);
  assert.match(service, /cycleNumber/);
  assert.doesNotMatch(app, /cycleNumber\}.*email/i);
});

test('all three escalation routes are explicit and never auto-send', () => {
  for (const channel of ['NMC_FOLLOW_UP', 'DISTRICT_JOINT_COMMISSIONER', 'DMA_DESK_6']) {
    assert.match(app, new RegExp(channel));
  }
  assert.match(app, /Seewik has not sent/);
  assert.match(app, /I sent this follow-up/);
  assert.match(app, /routeSnapshotHash/);
});

test('new follow-up interface copy has Marathi and Hindi translations', () => {
  for (const key of ['Was this issue resolved?', 'No, it is still unresolved', 'Choose the next follow-up route', 'I sent this follow-up']) {
    const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    assert.match(copy, new RegExp(`'${escaped}': \\{ mr: '.+', hi: '.+' \\}`));
  }
});
