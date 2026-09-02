import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { translate } from '../src/i18n.ts';
import { meetingPointSelectionFromPlace } from '../src/meetingPointSearchSelection.ts';

const app = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
const picker = await readFile(new URL('../src/InitiativeMeetingPointPicker.tsx', import.meta.url), 'utf8');
const placeSearch = await readFile(new URL('../src/GoogleMeetingPointSearch.tsx', import.meta.url), 'utf8');
const mapsLoader = await readFile(new URL('../src/googleMapsPlaces.ts', import.meta.url), 'utf8');
const pickerStyles = await readFile(new URL('../src/InitiativeMeetingPointPicker.css', import.meta.url), 'utf8');
const backend = await readFile(new URL('../../backend/src/main/java/com/seewik/api/InitiativeService.java', import.meta.url), 'utf8');
const contract = await readFile(new URL('../../data/contracts/day14-initiative-meeting-point-contract-v0.2.md', import.meta.url), 'utf8');

test('creation uses a separate confirmed pin instead of device discovery location', () => {
  assert.match(app, /initiativeMeetingPoint/);
  assert.match(app, /initiativeMeetingPointConfirmed/);
  assert.match(app, /<InitiativeMeetingPointPicker/);
  assert.match(app, /setInitiativeMeetingPointConfirmed\(false\)/);
  assert.match(app, /Confirm the meeting-point label and pin before publishing/);
  assert.doesNotMatch(app, /Use my location for discovery/);
  assert.doesNotMatch(app, /locateForInitiatives\('CREATE'\)/);
  assert.match(app, /clientRequestId: initiativeCreateRequestId\.current/);
  assert.match(app, /initiativeCreateRequestId\.current = crypto\.randomUUID\(\)/);
});

test('local picker remains the accessible fallback when optional Google place search is unavailable', () => {
  assert.match(picker, /official-map-digitized-boundaries-v0\.1\.geojson\?raw/);
  assert.match(picker, /onPointerDown=\{beginMove\}/);
  assert.match(picker, /onPointerMove=\{continueMove\}/);
  assert.match(picker, /ArrowUp/);
  assert.match(picker, /Enter coordinates manually/);
  assert.match(picker, /latitude >= -90/);
  assert.match(picker, /longitude >= -180/);
  assert.doesNotMatch(picker, /navigator\.geolocation|mapbox|leaflet/i);
  assert.match(pickerStyles, /touch-action: none/);
  assert.match(pickerStyles, /:focus-visible/);
  assert.match(pickerStyles, /@media \(max-width: 520px\)/);
});

test('Google place search is controlled, restricted, localized, minimal, and never logs its browser key', () => {
  assert.match(picker, /<GoogleMeetingPointSearch/);
  assert.match(mapsLoader, /VITE_GOOGLE_MAPS_API_KEY/);
  assert.match(mapsLoader, /importLibrary\('places'\)/);
  assert.doesNotMatch(mapsLoader, /console\.|localStorage|sessionStorage/);
  assert.match(placeSearch, /AutocompleteSuggestion\.fetchAutocompleteSuggestions/);
  assert.match(placeSearch, /includedRegionCodes: \['in'\]/);
  assert.match(placeSearch, /locationRestriction: bounds/);
  assert.match(placeSearch, /language: requestLanguages\[language\]/);
  assert.match(placeSearch, /region: 'in'/);
  assert.match(placeSearch, /No matching place was found/);
  assert.match(placeSearch, /role="listbox"/);
  assert.match(placeSearch, /Powered by Google/);
  assert.doesNotMatch(placeSearch, /PlaceAutocompleteElement/);
  assert.match(placeSearch, /fields: \['displayName', 'formattedAddress', 'location'\]/);
  assert.match(placeSearch, /meetingPointSelectionFromPlace/);
  assert.match(placeSearch, /Google place search is temporarily unavailable/);
  assert.doesNotMatch(placeSearch, /navigator\.geolocation/);
});

test('the production build receives the restricted browser key from GitHub Actions', async () => {
  const workflow = await readFile(new URL('../../.github/workflows/deploy.yml', import.meta.url), 'utf8');

  assert.match(workflow, /VITE_GOOGLE_MAPS_API_KEY: \$\{\{ secrets\.VITE_GOOGLE_MAPS_API_KEY \}\}/);
  assert.match(workflow, /test -n "\$VITE_GOOGLE_MAPS_API_KEY"/);
  assert.doesNotMatch(workflow, /VITE_GOOGLE_MAPS_API_KEY:\s*AIza/);
});

test('Google place selections accept only usable results inside the municipal bounds', () => {
  const bounds = { south: 21.35, west: 74.21, north: 21.41, east: 74.28 };
  const selected = meetingPointSelectionFromPlace({
    displayName: 'Civil Hospital',
    formattedAddress: 'Civil Hospital, Nandurbar',
    location: { lat: () => 21.37, lng: () => 74.24 },
  }, bounds);
  assert.deepEqual(selected, {
    status: 'OK',
    position: { latitude: 21.37, longitude: 74.24 },
    label: 'Civil Hospital',
    address: 'Civil Hospital, Nandurbar',
  });
  assert.equal(meetingPointSelectionFromPlace({
    displayName: 'Outside result',
    location: { lat: () => 19.07, lng: () => 72.87 },
  }, bounds).status, 'OUTSIDE_NANDURBAR');
  assert.equal(meetingPointSelectionFromPlace({ displayName: 'Missing coordinates' }, bounds).status, 'MISSING_LOCATION');
  assert.equal(meetingPointSelectionFromPlace({
    location: { lat: () => 21.37, lng: () => 74.24 },
  }, bounds).status, 'MISSING_LABEL');
  assert.deepEqual(meetingPointSelectionFromPlace({
    displayName: '96CV+HHR Bus stand',
    formattedAddress: '96CV+HHR, Bus stand, Nandurbar',
    location: { lat: () => 21.37, lng: () => 74.24 },
  }, bounds), {
    status: 'OK',
    position: { latitude: 21.37, longitude: 74.24 },
    label: 'Bus stand',
    address: 'Bus stand, Nandurbar',
  });
});

test('a Google selection moves the creation pin, fills the label, and invalidates confirmation', () => {
  assert.match(app, /onGooglePlaceSelect=\{\(\{ position, label \}\) =>/);
  assert.match(app, /setInitiativePlaceName\(label\.slice\(0, 200\)\)/);
  assert.match(app, /setInitiativeMeetingPointConfirmed\(false\)/);
});

test('backend stores a versioned meeting point and exposes only its label and generated Maps link', () => {
  assert.match(backend, /MEETING_POINT_SCHEMA_VERSION = "initiative-meeting-point-v0\.1"/);
  assert.match(backend, /initiative\.put\("meetingPoint", meetingPoint\)/);
  assert.match(backend, /https:\/\/www\.google\.com\/maps\/search\/\?api=1&query=/);
  assert.match(backend, /readMeetingPoint/);
  assert.match(backend, /legacyMeetingPoint/);
  assert.match(app, /Open in Google Maps/);
  assert.doesNotMatch(app, /initiative\.latitude|initiative\.longitude/);
});

test('meeting-point copy is available in Marathi and Hindi', () => {
  for (const key of [
    'Choose the meeting point',
    'Search for a meeting place',
    'Search for a public place or address',
    'Google place search is temporarily unavailable. You can still choose the meeting point using the pin below.',
    'Choose a meeting place within the Nandurbar municipal area.',
    'Meeting place selected. Adjust the pin or public label if needed, then confirm it.',
    'Public meeting-point label',
    'Confirm meeting point',
    'Open in Google Maps',
    'Before you can publish',
    'Place the meeting-point pin.',
  ]) {
    assert.notEqual(translate('mr', key), key, `Missing Marathi: ${key}`);
    assert.notEqual(translate('hi', key), key, `Missing Hindi: ${key}`);
  }
});

test('amended contract makes Places optional and preserves deterministic stored data and legacy compatibility', () => {
  assert.match(contract, /optional convenience layer/);
  assert.match(contract, /displayName.*,.*formattedAddress.*,.*location/);
  assert.match(contract, /does not store the search query, API key or complete Google Place response/);
  assert.match(contract, /no automatic geocoding, destructive rewrite or backfill/);
  assert.match(contract, /Changing either the label or pin invalidates the previous confirmation/);
  assert.match(contract, /Participant-facing responses expose the confirmed label and the generated Maps link, not separate raw coordinate fields/);
});
