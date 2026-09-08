import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { routeSnapshotHashAfterTransition } from '../src/reportRouteSnapshot.ts';

const app = readFileSync(new URL('../src/main.tsx', import.meta.url), 'utf8');
const service = readFileSync(new URL('../../backend/src/main/java/com/seewik/api/ReportLifecycleService.java', import.meta.url), 'utf8');

test('filing adopts the immutable route hash returned by the backend', () => {
  assert.equal(routeSnapshotHashAfterTransition(true, 'server-frozen-hash', undefined), 'server-frozen-hash');
});

test('later lifecycle transitions preserve the existing immutable route hash', () => {
  assert.equal(routeSnapshotHashAfterTransition(false, 'unrelated-response', 'server-frozen-hash'), 'server-frozen-hash');
});

test('boundary dataset version is persisted, freeze-validated and included in the hashed snapshot', () => {
  assert.match(app, /boundaryDatasetVersion,/);
  assert.match(app, /boundaryDatasetVersion: data\.boundaryDatasetVersion/);
  assert.match(service, /BOUNDARY_DATASET_VERSION_MISSING/);
  assert.match(service, /BOUNDARY_DATASET_VERSION_MISMATCH/);
  assert.match(service, /snapshot\.put\("boundaryDatasetVersion", recordedBoundaryDatasetVersion\)/);
  assert.match(service, /routeSnapshotHash = hashJson\(routeSnapshot\)/);
});
