import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const geojsonUrl = new URL('../../data/prabhags/official-map-digitized-boundaries-v0.1.geojson', import.meta.url);
const checksumUrl = new URL('../../data/prabhags/official-map-digitized-boundaries-v0.1.sha256', import.meta.url);
const geojsonBytes = await readFile(geojsonUrl);
const checksumRecord = await readFile(checksumUrl, 'utf8');
const collection = JSON.parse(geojsonBytes.toString('utf8'));

const coordinateKey = ([longitude, latitude]) => `${longitude},${latitude}`;
const edgeKey = (start, end) => [coordinateKey(start), coordinateKey(end)].sort().join('|');

function signedArea(ring) {
  return ring.slice(0, -1).reduce((area, point, index) => {
    const next = ring[index + 1];
    return area + point[0] * next[1] - next[0] * point[1];
  }, 0) / 2;
}

function orientation(a, b, c) {
  const cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
  if (Math.abs(cross) < 1e-12) return 0;
  return Math.sign(cross);
}

function properIntersection(a, b, c, d) {
  const abC = orientation(a, b, c);
  const abD = orientation(a, b, d);
  const cdA = orientation(c, d, a);
  const cdB = orientation(c, d, b);
  return abC !== 0 && abD !== 0 && cdA !== 0 && cdB !== 0 && abC !== abD && cdA !== cdB;
}

function ringSegments(ring) {
  return ring.slice(0, -1).map((point, index) => [point, ring[index + 1]]);
}

test('approximate boundary dataset checksum and locked metadata are unchanged', () => {
  const expectedChecksum = checksumRecord.trim().split(/\s+/)[0];
  const actualChecksum = createHash('sha256').update(geojsonBytes).digest('hex');
  assert.equal(actualChecksum, expectedChecksum);
  assert.equal(actualChecksum, 'a5d1c9870b7f8d335db87ddab4c59c94455f810e97e43eb6d396eac1b604fcc2');
  assert.equal(collection.metadata.datasetVersion, 'official-map-digitized-v0.1');
  assert.equal(collection.metadata.prabhagCount, 20);
  assert.equal(collection.metadata.geometryOfficialDigital, false);
  assert.equal(collection.metadata.requiresCitizenConfirmation, true);
});

test('all 20 polygons are closed, valid for display, and consistently labelled', () => {
  assert.equal(collection.type, 'FeatureCollection');
  assert.equal(collection.features.length, 20);
  const ids = new Set();
  const allCoordinates = [];

  for (const [index, feature] of collection.features.entries()) {
    const expectedId = `PRABHAG-${String(index + 1).padStart(2, '0')}`;
    assert.equal(feature.id, expectedId);
    assert.equal(feature.properties.prabhagId, expectedId);
    assert.equal(feature.properties.wardNumber, index + 1);
    assert.equal(feature.properties.datasetVersion, 'official-map-digitized-v0.1');
    assert.equal(feature.properties.reviewStatus, 'REVIEW_PENDING_GEOREFERENCE');
    assert.equal(feature.properties.resolutionQuality, 'APPROXIMATE_DIGITISED_OFFICIAL_MAP_IMAGE');
    assert.equal(feature.properties.isActive, false);
    assert.equal(feature.properties.requiresCitizenConfirmation, true);
    assert.equal(feature.geometry.type, 'Polygon');
    assert.equal(feature.geometry.coordinates.length, 1);
    assert.equal(ids.has(feature.id), false);
    ids.add(feature.id);

    const ring = feature.geometry.coordinates[0];
    assert.ok(ring.length >= 4);
    assert.deepEqual(ring[0], ring.at(-1));
    assert.ok(signedArea(ring) > 0, `${feature.id} must have a non-zero counter-clockwise display ring`);
    for (const coordinate of ring) {
      assert.equal(coordinate.length, 2);
      assert.ok(coordinate.every(Number.isFinite));
      allCoordinates.push(coordinate);
    }

    const segments = ringSegments(ring);
    for (let left = 0; left < segments.length; left += 1) {
      for (let right = left + 1; right < segments.length; right += 1) {
        if (right === left + 1 || (left === 0 && right === segments.length - 1)) continue;
        assert.equal(
          properIntersection(...segments[left], ...segments[right]),
          false,
          `${feature.id} must not self-intersect`,
        );
      }
    }
  }

  const longitudes = allCoordinates.map(([longitude]) => longitude);
  const latitudes = allCoordinates.map(([, latitude]) => latitude);
  const width = Math.max(...longitudes) - Math.min(...longitudes);
  const height = Math.max(...latitudes) - Math.min(...latitudes);
  assert.ok(width > 0.05 && width < 0.07, 'visible longitude coverage changed unexpectedly');
  assert.ok(height > 0.05 && height < 0.07, 'visible latitude coverage changed unexpectedly');
});

test('polygon topology has no crossings and forms one edge-connected coverage', () => {
  const edgeOwners = new Map();
  const adjacency = new Map(collection.features.map(({ id }) => [id, new Set()]));

  for (const feature of collection.features) {
    for (const [start, end] of ringSegments(feature.geometry.coordinates[0])) {
      const key = edgeKey(start, end);
      const owners = edgeOwners.get(key) ?? [];
      owners.push(feature.id);
      edgeOwners.set(key, owners);
    }
  }

  for (const owners of edgeOwners.values()) {
    assert.ok(owners.length <= 2, 'an edge cannot belong to more than two prabhags');
    if (owners.length === 2) {
      adjacency.get(owners[0]).add(owners[1]);
      adjacency.get(owners[1]).add(owners[0]);
    }
  }

  const features = collection.features;
  for (let left = 0; left < features.length; left += 1) {
    const leftSegments = ringSegments(features[left].geometry.coordinates[0]);
    for (let right = left + 1; right < features.length; right += 1) {
      const rightSegments = ringSegments(features[right].geometry.coordinates[0]);
      for (const leftSegment of leftSegments) {
        for (const rightSegment of rightSegments) {
          assert.equal(
            properIntersection(...leftSegment, ...rightSegment),
            false,
            `${features[left].id} and ${features[right].id} must not cross`,
          );
        }
      }
    }
  }

  const visited = new Set();
  const pending = [features[0].id];
  while (pending.length) {
    const current = pending.pop();
    if (visited.has(current)) continue;
    visited.add(current);
    pending.push(...adjacency.get(current));
  }
  assert.equal(visited.size, 20, 'all prabhags must form one connected visible coverage');
});
