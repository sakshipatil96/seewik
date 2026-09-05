import assert from 'node:assert/strict';
import test from 'node:test';
import { routeSnapshotHashAfterTransition } from '../src/reportRouteSnapshot.ts';

test('filing adopts the immutable route hash returned by the backend', () => {
  assert.equal(routeSnapshotHashAfterTransition(true, 'server-frozen-hash', undefined), 'server-frozen-hash');
});

test('later lifecycle transitions preserve the existing immutable route hash', () => {
  assert.equal(routeSnapshotHashAfterTransition(false, 'unrelated-response', 'server-frozen-hash'), 'server-frozen-hash');
});
