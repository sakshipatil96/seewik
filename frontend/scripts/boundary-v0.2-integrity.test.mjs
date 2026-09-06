import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const geoJsonUrl = new URL('../../data/prabhags/official-map-digitized-boundaries-v0.2.geojson', import.meta.url);
const checksumUrl = new URL('../../data/prabhags/official-map-digitized-boundaries-v0.2.sha256', import.meta.url);

function cross(a, b, c) {
  return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
}

function onSegment(a, b, point) {
  const epsilon = 1e-12;
  return Math.abs(cross(a, b, point)) <= epsilon
    && point[0] >= Math.min(a[0], b[0]) - epsilon
    && point[0] <= Math.max(a[0], b[0]) + epsilon
    && point[1] >= Math.min(a[1], b[1]) - epsilon
    && point[1] <= Math.max(a[1], b[1]) + epsilon;
}

function segmentsIntersect(a, b, c, d) {
  const abC = cross(a, b, c);
  const abD = cross(a, b, d);
  const cdA = cross(c, d, a);
  const cdB = cross(c, d, b);
  if (((abC > 0 && abD < 0) || (abC < 0 && abD > 0))
    && ((cdA > 0 && cdB < 0) || (cdA < 0 && cdB > 0))) return true;
  return onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b);
}

function segmentsProperlyCross(a, b, c, d) {
  const abC = cross(a, b, c);
  const abD = cross(a, b, d);
  const cdA = cross(c, d, a);
  const cdB = cross(c, d, b);
  return ((abC > 0 && abD < 0) || (abC < 0 && abD > 0))
    && ((cdA > 0 && cdB < 0) || (cdA < 0 && cdB > 0));
}

function properIntersectionPoint(a, b, c, d) {
  const denominator = (a[0] - b[0]) * (c[1] - d[1]) - (a[1] - b[1]) * (c[0] - d[0]);
  if (Math.abs(denominator) < 1e-18) return null;
  const t = ((a[0] - c[0]) * (c[1] - d[1]) - (a[1] - c[1]) * (c[0] - d[0])) / denominator;
  const u = ((a[0] - c[0]) * (a[1] - b[1]) - (a[1] - c[1]) * (a[0] - b[0])) / denominator;
  if (t <= 1e-9 || t >= 1 - 1e-9 || u <= 1e-9 || u >= 1 - 1e-9) return null;
  return [a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])];
}

function signedArea(ring) {
  let twiceArea = 0;
  for (let index = 0; index < ring.length - 1; index += 1) {
    twiceArea += ring[index][0] * ring[index + 1][1] - ring[index + 1][0] * ring[index][1];
  }
  return twiceArea / 2;
}

function pointInRingStrict(point, ring) {
  for (let index = 0; index < ring.length - 1; index += 1) {
    if (onSegment(ring[index], ring[index + 1], point)) return false;
  }
  let inside = false;
  for (let current = 0, previous = ring.length - 2; current < ring.length - 1; previous = current, current += 1) {
    const [x1, y1] = ring[current];
    const [x2, y2] = ring[previous];
    if ((y1 > point[1]) !== (y2 > point[1])
      && point[0] < ((x2 - x1) * (point[1] - y1)) / (y2 - y1) + x1) inside = !inside;
  }
  return inside;
}

function estimatedOverlapSquareMetres(ringA, ringB, intersections) {
  const padding = 0.0004;
  const samplesPerAxis = 96;
  const longitudes = intersections.map(([longitude]) => longitude);
  const latitudes = intersections.map(([, latitude]) => latitude);
  const west = Math.min(...longitudes) - padding;
  const east = Math.max(...longitudes) + padding;
  const south = Math.min(...latitudes) - padding;
  const north = Math.max(...latitudes) + padding;
  const longitudeStep = (east - west) / samplesPerAxis;
  const latitudeStep = (north - south) / samplesPerAxis;
  let overlapCells = 0;

  for (let x = 0; x < samplesPerAxis; x += 1) {
    const longitude = west + (x + 0.5) * longitudeStep;
    for (let y = 0; y < samplesPerAxis; y += 1) {
      const latitude = south + (y + 0.5) * latitudeStep;
      if (pointInRingStrict([longitude, latitude], ringA) && pointInRingStrict([longitude, latitude], ringB)) {
        overlapCells += 1;
      }
    }
  }

  const longitudeMetres = 111_320 * Math.cos(21.384 * Math.PI / 180);
  const latitudeMetres = 110_540;
  return overlapCells * longitudeStep * longitudeMetres * latitudeStep * latitudeMetres;
}

function assertNoSelfIntersections(ring, prabhagId) {
  const segmentCount = ring.length - 1;
  for (let first = 0; first < segmentCount; first += 1) {
    for (let second = first + 1; second < segmentCount; second += 1) {
      const adjacent = second === first + 1 || (first === 0 && second === segmentCount - 1);
      if (adjacent) continue;
      assert.equal(
        segmentsIntersect(ring[first], ring[first + 1], ring[second], ring[second + 1]),
        false,
        `${prabhagId} contains a self-intersection`,
      );
    }
  }
}

test('v0.2 source artifact matches its frozen checksum', async () => {
  const [bytes, checksumFile] = await Promise.all([readFile(geoJsonUrl), readFile(checksumUrl, 'utf8')]);
  const expected = checksumFile.trim().split(/\s+/)[0];
  assert.equal(createHash('sha256').update(bytes).digest('hex'), expected);
});

test('v0.2 contains 20 closed, finite, counter-clockwise prabhag polygons', async () => {
  const collection = JSON.parse(await readFile(geoJsonUrl, 'utf8'));
  assert.equal(collection.type, 'FeatureCollection');
  assert.equal(collection.features.length, 20);
  const ids = new Set();

  for (const feature of collection.features) {
    const id = String(feature.properties.prabhagId);
    assert.equal(ids.has(id), false, `Duplicate prabhag ${id}`);
    ids.add(id);
    assert.equal(feature.geometry.type, 'Polygon');
    assert.equal(feature.properties.datasetVersion, 'seewik-map-trace-v0.2');
    assert.equal(feature.properties.isActive, false);
    assert.equal(feature.properties.requiresCitizenConfirmation, true);

    const exterior = feature.geometry.coordinates[0];
    assert.ok(exterior.length >= 4, `${id} has too few coordinates`);
    assert.deepEqual(exterior[0], exterior.at(-1), `${id} is not closed`);
    for (const coordinate of exterior) {
      assert.equal(coordinate.length >= 2 && coordinate.every(Number.isFinite), true, `${id} has an invalid coordinate`);
    }
    assert.ok(signedArea(exterior) > 0, `${id} exterior is not counter-clockwise`);
    assertNoSelfIntersections(exterior, id);
  }
});

test('v0.2 double-trace overlaps remain within the frozen tolerance baseline', async () => {
  const collection = JSON.parse(await readFile(geoJsonUrl, 'utf8'));
  const expectedCrossingPairs = [
    'PRABHAG-01/PRABHAG-02', 'PRABHAG-01/PRABHAG-06', 'PRABHAG-01/PRABHAG-07',
    'PRABHAG-02/PRABHAG-05', 'PRABHAG-03/PRABHAG-04', 'PRABHAG-04/PRABHAG-07',
    'PRABHAG-04/PRABHAG-08', 'PRABHAG-06/PRABHAG-07', 'PRABHAG-06/PRABHAG-14',
    'PRABHAG-07/PRABHAG-11', 'PRABHAG-07/PRABHAG-14', 'PRABHAG-08/PRABHAG-09',
    'PRABHAG-09/PRABHAG-20', 'PRABHAG-10/PRABHAG-11', 'PRABHAG-10/PRABHAG-16',
    'PRABHAG-10/PRABHAG-18', 'PRABHAG-11/PRABHAG-12', 'PRABHAG-11/PRABHAG-16',
    'PRABHAG-12/PRABHAG-13', 'PRABHAG-12/PRABHAG-16', 'PRABHAG-13/PRABHAG-15',
    'PRABHAG-15/PRABHAG-16', 'PRABHAG-18/PRABHAG-19', 'PRABHAG-18/PRABHAG-20',
    'PRABHAG-19/PRABHAG-20',
  ];
  const crossingPairs = [];
  let crossingCount = 0;
  let estimatedOverlap = 0;

  for (let first = 0; first < collection.features.length; first += 1) {
    const featureA = collection.features[first];
    const ringA = featureA.geometry.coordinates[0];
    for (let second = first + 1; second < collection.features.length; second += 1) {
      const featureB = collection.features[second];
      const ringB = featureB.geometry.coordinates[0];
      const pair = `${featureA.properties.prabhagId}/${featureB.properties.prabhagId}`;
      const intersections = [];

      for (let a = 0; a < ringA.length - 1; a += 1) {
        for (let b = 0; b < ringB.length - 1; b += 1) {
          const intersection = properIntersectionPoint(ringA[a], ringA[a + 1], ringB[b], ringB[b + 1]);
          if (intersection) intersections.push(intersection);
        }
      }

      if (intersections.length) {
        crossingPairs.push(pair);
        crossingCount += intersections.length;
        estimatedOverlap += estimatedOverlapSquareMetres(ringA, ringB, intersections);
      }
    }
  }

  assert.deepEqual(crossingPairs, expectedCrossingPairs);
  assert.equal(crossingCount, 80);
  assert.ok(estimatedOverlap > 0, 'The overlap estimator must detect the frozen double-trace slivers');
  assert.ok(estimatedOverlap < 1_000, `Estimated overlap ${estimatedOverlap.toFixed(1)} m2 exceeds the 1,000 m2 limit`);
});
