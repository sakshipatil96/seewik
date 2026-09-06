import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const sourceUrl = new URL('./official-map-digitized-boundaries-v0.2.geojson', import.meta.url);
const outputUrl = new URL('./official-map-digitized-boundaries-v0.2.ndjson', import.meta.url);

const DATASET_VERSION = 'seewik-map-trace-v0.2';
const RESOLUTION_QUALITY = 'APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE';
const SOURCE_STATUS = 'MUNICIPAL_OFFICE_WALL_MAP_PHOTO';
const REVIEW_STATUS = 'NOT_AUTHORITY_VERIFIED';
const SOURCE_REFERENCE =
  'Nandurbar municipal-office 2025 wall-map photograph; no official machine-readable boundary geometry available';

function ringToWkt(ring) {
  return ring.map(([longitude, latitude]) => `${longitude} ${latitude}`).join(', ');
}

function geometryToWkt(geometry) {
  if (geometry.type === 'Polygon') {
    return `POLYGON(${geometry.coordinates.map((ring) => `(${ringToWkt(ring)})`).join(', ')})`;
  }

  if (geometry.type === 'MultiPolygon') {
    return `MULTIPOLYGON(${geometry.coordinates
      .map((polygon) => `(${polygon.map((ring) => `(${ringToWkt(ring)})`).join(', ')})`)
      .join(', ')})`;
  }

  throw new Error(`Unsupported geometry type: ${geometry.type}`);
}

const source = JSON.parse(await readFile(sourceUrl, 'utf8'));

if (source.type !== 'FeatureCollection' || source.features.length !== 20) {
  throw new Error('Expected a 20-feature GeoJSON FeatureCollection.');
}

const rows = source.features.map((feature) => {
  const prabhagId = String(feature.properties.prabhagId);

  return {
    prabhagId,
    prabhagName: feature.properties.prabhagName || `Prabhag ${prabhagId}`,
    geometry: geometryToWkt(feature.geometry),
    geometryType: feature.geometry.type.toUpperCase(),
    resolutionQuality: RESOLUTION_QUALITY,
    requiresCitizenConfirmation: true,
    sourceReference: SOURCE_REFERENCE,
    sourceStatus: SOURCE_STATUS,
    reviewStatus: REVIEW_STATUS,
    datasetVersion: DATASET_VERSION,
    generatorVersion: 'map-photo-trace-v0.2',
    generatorSeed: 'NOT_APPLICABLE',
    generatedAt: '2026-09-05T00:00:00Z',
    isActive: true,
  };
});

await writeFile(outputUrl, `${rows.map((row) => JSON.stringify(row)).join('\n')}\n`, 'utf8');
console.log(`Wrote ${rows.length} rows to ${fileURLToPath(outputUrl)}`);
