import assert from 'node:assert/strict';
import test from 'node:test';
import { canEditReport, canResumeReport, draftRouteIsCurrent, pathForScreen, reportIdFromPath, reportIdFromReviewSearch, screenFromPath } from '../src/reportNavigation.ts';

test('all app screens have refresh-safe paths', () => {
  for (const screen of ['home', 'new-report', 'review', 'reports', 'report-detail', 'points', 'initiatives', 'new-initiative', 'awareness', 'emergency']) {
    assert.equal(screenFromPath(pathForScreen(screen)), screen);
  }
  assert.equal(screenFromPath('/unknown'), 'home');
  assert.equal(screenFromPath('/reports/report-123'), 'report-detail');
  assert.equal(reportIdFromPath('/reports/report-123'), 'report-123');
  assert.equal(reportIdFromReviewSearch('?report=draft-456'), 'draft-456');
  assert.equal(pathForScreen('report-detail', 'report 123'), '/reports/report%20123');
  assert.equal(pathForScreen('review', 'draft 456'), '/report/review?report=draft%20456');
});

test('only a DRAFT report can be edited or resumed', () => {
  assert.equal(canEditReport('DRAFT'), true);
  assert.equal(canResumeReport('DRAFT'), true);
  for (const status of ['FILED', 'OVERDUE', 'CLAIMED_FIXED', 'VERIFIED_FIXED', 'REOPENED']) {
    assert.equal(canEditReport(status), false, `${status} must be immutable`);
    assert.equal(canResumeReport(status), false, `${status} must not be resumed as a draft`);
  }
});

test('a draft becomes stale when category, prabhag, route, or pack changes', () => {
  const saved = { issueType: 'POTHOLE_ROAD_DAMAGE', prabhagId: 'PRABHAG-03', routeId: 'NMC-PW-POTHOLE-v0.2', packVersion: 'v0.2' };
  assert.equal(draftRouteIsCurrent(saved, { ...saved }), true);
  assert.equal(draftRouteIsCurrent(saved, { ...saved, issueType: 'STREETLIGHT' }), false);
  assert.equal(draftRouteIsCurrent(saved, { ...saved, prabhagId: 'PRABHAG-04' }), false);
  assert.equal(draftRouteIsCurrent(saved, { ...saved, routeId: 'different-route' }), false);
  assert.equal(draftRouteIsCurrent(saved, { ...saved, packVersion: 'v0.3' }), false);
});
