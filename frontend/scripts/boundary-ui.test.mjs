import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const appSource = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
const mapSource = await readFile(new URL('../src/PrabhagBoundaryMap.tsx', import.meta.url), 'utf8');
const mapStyles = await readFile(new URL('../src/PrabhagBoundaryMap.css', import.meta.url), 'utf8');

test('boundary guide renders immediately and preserves manual and error fallbacks', () => {
  assert.match(appSource, /import PrabhagBoundaryMap from '\.\/PrabhagBoundaryMap'/);
  assert.doesNotMatch(appSource, /lazy\(\(\) => import\('\.\/PrabhagBoundaryMap'\)\)/);
  assert.doesNotMatch(appSource, /Loading approximate boundary guide/);
  assert.match(appSource, /BoundaryMapErrorBoundary/);
  assert.match(appSource, /Choose Prabhag 1–20/);
  assert.match(appSource, /disabled=\{!classificationConfirmed \|\| !prabhagSelectionMade\}/);
  assert.match(appSource, /onManualSelect=\{selectManualPrabhag\}/);
});

test('local SVG guide exposes locked provenance and accessible outline controls', () => {
  assert.match(mapSource, /official-map-digitized-boundaries-v0\.1\.geojson\?raw/);
  assert.match(mapSource, /approximate boundaries digitized from an official map image/);
  assert.match(mapSource, /REVIEW_PENDING_GEOREFERENCE/);
  assert.match(mapSource, /role="button"/);
  assert.match(mapSource, /tabIndex=\{0\}/);
  assert.match(mapSource, /event\.key !== 'Enter' && event\.key !== ' '/);
  assert.match(mapSource, /Math\.round\(position\.longitude \/ step\)/);
  assert.doesNotMatch(mapSource, /leaflet|mapbox|google\.maps|openlayers/i);
});

test('boundary guide supports narrow layouts, visible focus, and reduced motion', () => {
  assert.match(mapStyles, /\.boundary-shape:focus-visible/);
  assert.match(mapStyles, /@media \(max-width: 620px\)/);
  assert.match(mapStyles, /@media \(prefers-reduced-motion: reduce\)/);
});
