import boundaryGeoJsonText from '../../data/prabhags/official-map-digitized-boundaries-v0.1.geojson?raw';
import { useEffect, useRef, useState } from 'react';
import { translate, type InterfaceLanguage } from './i18n';
import GoogleMeetingPointSearch, { type GoogleMeetingPointSelection } from './GoogleMeetingPointSearch';
import { googlePlaceSearchConfigured, loadGoogleMaps } from './googleMapsPlaces';
import './InitiativeMeetingPointPicker.css';

export type MeetingPointPosition = { latitude: number; longitude: number };

type Coordinate = [number, number];
type BoundaryFeature = {
  properties: { wardNumber: number };
  geometry: { type: 'Polygon'; coordinates: Coordinate[][] };
};
type BoundaryCollection = { type: 'FeatureCollection'; features: BoundaryFeature[] };

type Props = {
  language: InterfaceLanguage;
  position: MeetingPointPosition | null;
  onChange: (position: MeetingPointPosition) => void;
  onGooglePlaceSelect: (selection: GoogleMeetingPointSelection) => void;
};

const VIEW_WIDTH = 720;
const VIEW_HEIGHT = 540;
const PADDING = 28;

type GoogleLatLng = { lat: () => number; lng: () => number };
type GoogleMapEvent = { latLng?: GoogleLatLng };
type GoogleMapInstance = {
  addListener: (event: string, handler: (value: GoogleMapEvent) => void) => void;
  panTo: (position: { lat: number; lng: number }) => void;
};
type GoogleMarkerInstance = {
  position: { lat: number; lng: number };
  addListener: (event: string, handler: (value: GoogleMapEvent) => void) => void;
};
type GoogleMapsLibrary = {
  Map: new (element: HTMLElement, options: Record<string, unknown>) => GoogleMapInstance;
};
type GoogleMarkerLibrary = {
  AdvancedMarkerElement: new (options: Record<string, unknown>) => GoogleMarkerInstance;
};

function collectionFromSource(): BoundaryCollection | null {
  try {
    const value = JSON.parse(boundaryGeoJsonText) as BoundaryCollection;
    return value.type === 'FeatureCollection'
      && Array.isArray(value.features)
      && value.features.length === 20
      && value.features.every((feature) => feature.geometry?.type === 'Polygon')
      ? value
      : null;
  } catch {
    return null;
  }
}

const collection = collectionFromSource();

function geometry(features: BoundaryFeature[]) {
  const coordinates = features.flatMap((feature) => feature.geometry.coordinates.flat());
  const longitudes = coordinates.map(([longitude]) => longitude);
  const latitudes = coordinates.map(([, latitude]) => latitude);
  const bounds = {
    minLongitude: Math.min(...longitudes),
    maxLongitude: Math.max(...longitudes),
    minLatitude: Math.min(...latitudes),
    maxLatitude: Math.max(...latitudes),
  };
  const longitudeSpan = bounds.maxLongitude - bounds.minLongitude;
  const latitudeSpan = bounds.maxLatitude - bounds.minLatitude;
  const scale = Math.min(
    (VIEW_WIDTH - 2 * PADDING) / longitudeSpan,
    (VIEW_HEIGHT - 2 * PADDING) / latitudeSpan,
  );
  const offsetX = (VIEW_WIDTH - longitudeSpan * scale) / 2;
  const offsetY = (VIEW_HEIGHT - latitudeSpan * scale) / 2;
  return {
    bounds,
    longitudeSpan,
    latitudeSpan,
    point([longitude, latitude]: Coordinate): Coordinate {
      return [
        offsetX + (longitude - bounds.minLongitude) * scale,
        offsetY + (bounds.maxLatitude - latitude) * scale,
      ];
    },
    position(x: number, y: number): MeetingPointPosition {
      const longitude = bounds.minLongitude + (x - offsetX) / scale;
      const latitude = bounds.maxLatitude - (y - offsetY) / scale;
      return {
        latitude: Math.max(bounds.minLatitude, Math.min(bounds.maxLatitude, latitude)),
        longitude: Math.max(bounds.minLongitude, Math.min(bounds.maxLongitude, longitude)),
      };
    },
  };
}

const mapGeometry = collection ? geometry(collection.features) : null;

export default function InitiativeMeetingPointPicker({ language, position, onChange, onGooglePlaceSelect }: Props) {
  const t = (source: string) => translate(language, source);
  const [googleMapState, setGoogleMapState] = useState<'LOADING' | 'READY' | 'ERROR'>(
    googlePlaceSearchConfigured() ? 'LOADING' : 'ERROR',
  );
  const googleMapElement = useRef<HTMLDivElement | null>(null);
  const googleMap = useRef<GoogleMapInstance | null>(null);
  const googleMarker = useRef<GoogleMarkerInstance | null>(null);
  const onChangeRef = useRef(onChange);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    let active = true;
    if (!googleMapElement.current || !googlePlaceSearchConfigured()) return;
    Promise.all([
      loadGoogleMaps().then((root) => root.importLibrary('maps') as Promise<GoogleMapsLibrary>),
      loadGoogleMaps().then((root) => root.importLibrary('marker') as Promise<GoogleMarkerLibrary>),
    ]).then(([mapsLibrary, markerLibrary]) => {
      if (!active || !googleMapElement.current) return;
      const centre = position
        ? { lat: position.latitude, lng: position.longitude }
        : { lat: 21.3707, lng: 74.2403 };
      const map = new mapsLibrary.Map(googleMapElement.current, {
        center: centre,
        zoom: position ? 17 : 13,
        mapId: 'DEMO_MAP_ID',
        streetViewControl: false,
        mapTypeControl: false,
        fullscreenControl: true,
        restriction: mapGeometry ? {
          latLngBounds: {
            south: mapGeometry.bounds.minLatitude,
            west: mapGeometry.bounds.minLongitude,
            north: mapGeometry.bounds.maxLatitude,
            east: mapGeometry.bounds.maxLongitude,
          },
          strictBounds: false,
        } : undefined,
      });
      const marker = new markerLibrary.AdvancedMarkerElement({
        map,
        position: centre,
        gmpDraggable: true,
        title: t('Meeting point'),
      });
      map.addListener('click', (event) => {
        if (!event.latLng) return;
        const next = { latitude: event.latLng.lat(), longitude: event.latLng.lng() };
        marker.position = { lat: next.latitude, lng: next.longitude };
        onChangeRef.current(next);
      });
      marker.addListener('dragend', (event) => {
        if (!event.latLng) return;
        onChangeRef.current({ latitude: event.latLng.lat(), longitude: event.latLng.lng() });
      });
      googleMap.current = map;
      googleMarker.current = marker;
      setGoogleMapState('READY');
    }).catch(() => {
      if (active) setGoogleMapState('ERROR');
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!position || !googleMap.current || !googleMarker.current) return;
    const next = { lat: position.latitude, lng: position.longitude };
    googleMarker.current.position = next;
    googleMap.current.panTo(next);
  }, [position]);

  const googleBounds = mapGeometry ? {
    south: mapGeometry.bounds.minLatitude,
    west: mapGeometry.bounds.minLongitude,
    north: mapGeometry.bounds.maxLatitude,
    east: mapGeometry.bounds.maxLongitude,
  } : null;

  return <section className="meeting-point-picker" aria-labelledby="meeting-point-map-title">
    <div className="meeting-point-heading">
      <div><h3 id="meeting-point-map-title">{t('Choose the meeting point')}</h3><p>{t('Search above, tap the map or drag the pin to choose the exact meeting point.')}</p></div>
      <span aria-hidden="true">⌖</span>
    </div>
    <GoogleMeetingPointSearch language={language} bounds={googleBounds} onSelect={onGooglePlaceSelect} />
    <div className="nandurbar-map-context"><strong>{t('Nandurbar municipal area')}</strong><span>{t('The live map is centred on Nandurbar. Search above or move the pin, then confirm the public label below.')}</span></div>
    {googlePlaceSearchConfigured()
      ? <div ref={googleMapElement} className="meeting-point-google-map" aria-label={t('Interactive Google map for the meeting point')} />
      : <div className="status-panel state-warning" role="status">{t('The Google map is unavailable. Search for a public place above and try again.')}</div>}
    {googleMapState === 'LOADING' && <small role="status">{t('Loading Google map…')}</small>}
    {googleMapState === 'ERROR' && googlePlaceSearchConfigured() && <div className="status-panel state-warning" role="status">{t('The Google map is temporarily unavailable. You can still use the selected search result.')}</div>}
    <div className={`meeting-point-pin-status${position ? ' is-set' : ''}`} role="status" aria-live="polite">
      {position ? `✓ ${t('Pin placed. Confirm it with the public label below.')}` : t('No meeting-point pin has been placed yet.')}
    </div>
    {position && <a className="meeting-point-google-preview" href={`https://www.google.com/maps/search/?api=1&query=${position.latitude.toFixed(6)}%2C${position.longitude.toFixed(6)}`} target="_blank" rel="noreferrer">⌖ {t('Check this pin in Google Maps')}</a>}
  </section>;
}
