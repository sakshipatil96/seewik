import boundaryGeoJsonText from '../../data/prabhags/official-map-digitized-boundaries-v0.1.geojson?raw';
import { useEffect, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { translate, type InterfaceLanguage } from './i18n';
import GoogleMeetingPointSearch, { type GoogleMeetingPointSelection } from './GoogleMeetingPointSearch';
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

function ringPath(ring: Coordinate[]) {
  if (!mapGeometry) return '';
  return ring.map((coordinate, index) => {
    const [x, y] = mapGeometry.point(coordinate);
    return `${index === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`;
  }).join(' ') + ' Z';
}

function labelPoint(ring: Coordinate[]): Coordinate {
  if (!mapGeometry) return [0, 0];
  const points = ring.slice(0, -1).map(mapGeometry.point);
  return [
    points.reduce((total, [x]) => total + x, 0) / points.length,
    points.reduce((total, [, y]) => total + y, 0) / points.length,
  ];
}

function validPosition(latitude: number, longitude: number) {
  return Number.isFinite(latitude)
    && latitude >= -90
    && latitude <= 90
    && Number.isFinite(longitude)
    && longitude >= -180
    && longitude <= 180;
}

export default function InitiativeMeetingPointPicker({ language, position, onChange, onGooglePlaceSelect }: Props) {
  const t = (source: string) => translate(language, source);
  const [dragging, setDragging] = useState(false);
  const [manualLatitude, setManualLatitude] = useState(position?.latitude.toFixed(6) ?? '');
  const [manualLongitude, setManualLongitude] = useState(position?.longitude.toFixed(6) ?? '');
  const [manualStatus, setManualStatus] = useState('');

  useEffect(() => {
    if (!position) return;
    setManualLatitude(position.latitude.toFixed(6));
    setManualLongitude(position.longitude.toFixed(6));
  }, [position]);

  function updatePosition(next: MeetingPointPosition) {
    onChange({
      latitude: Number(next.latitude.toFixed(6)),
      longitude: Number(next.longitude.toFixed(6)),
    });
    setManualStatus('');
  }

  function positionFromPointer(event: PointerEvent<HTMLButtonElement>) {
    if (!mapGeometry) return;
    const rectangle = event.currentTarget.getBoundingClientRect();
    const x = (event.clientX - rectangle.left) / rectangle.width * VIEW_WIDTH;
    const y = (event.clientY - rectangle.top) / rectangle.height * VIEW_HEIGHT;
    updatePosition(mapGeometry.position(x, y));
  }

  function beginMove(event: PointerEvent<HTMLButtonElement>) {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);
    positionFromPointer(event);
  }

  function continueMove(event: PointerEvent<HTMLButtonElement>) {
    if (dragging) positionFromPointer(event);
  }

  function finishMove(event: PointerEvent<HTMLButtonElement>) {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    setDragging(false);
  }

  function moveWithKeyboard(event: KeyboardEvent<HTMLButtonElement>) {
    if (!mapGeometry) return;
    if (event.key === 'Enter' || event.key === ' ') {
      if (!position) {
        event.preventDefault();
        updatePosition({
          latitude: (mapGeometry.bounds.minLatitude + mapGeometry.bounds.maxLatitude) / 2,
          longitude: (mapGeometry.bounds.minLongitude + mapGeometry.bounds.maxLongitude) / 2,
        });
      }
      return;
    }
    if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) return;
    event.preventDefault();
    const current = position ?? {
      latitude: (mapGeometry.bounds.minLatitude + mapGeometry.bounds.maxLatitude) / 2,
      longitude: (mapGeometry.bounds.minLongitude + mapGeometry.bounds.maxLongitude) / 2,
    };
    const latitudeStep = mapGeometry.latitudeSpan / 80;
    const longitudeStep = mapGeometry.longitudeSpan / 80;
    updatePosition({
      latitude: Math.max(mapGeometry.bounds.minLatitude, Math.min(
        mapGeometry.bounds.maxLatitude,
        current.latitude + (event.key === 'ArrowUp' ? latitudeStep : event.key === 'ArrowDown' ? -latitudeStep : 0),
      )),
      longitude: Math.max(mapGeometry.bounds.minLongitude, Math.min(
        mapGeometry.bounds.maxLongitude,
        current.longitude + (event.key === 'ArrowRight' ? longitudeStep : event.key === 'ArrowLeft' ? -longitudeStep : 0),
      )),
    });
  }

  function applyManualCoordinates() {
    const latitude = Number(manualLatitude);
    const longitude = Number(manualLongitude);
    if (!validPosition(latitude, longitude)) {
      setManualStatus(t('Enter a valid latitude from -90 to 90 and longitude from -180 to 180.'));
      return;
    }
    updatePosition({ latitude, longitude });
    setManualStatus(t('Manual coordinates applied. Confirm the public label and meeting point below.'));
  }

  const marker = position && mapGeometry ? mapGeometry.point([position.longitude, position.latitude]) : null;
  const googleBounds = mapGeometry ? {
    south: mapGeometry.bounds.minLatitude,
    west: mapGeometry.bounds.minLongitude,
    north: mapGeometry.bounds.maxLatitude,
    east: mapGeometry.bounds.maxLongitude,
  } : null;

  return <section className="meeting-point-picker" aria-labelledby="meeting-point-map-title">
    <div className="meeting-point-heading">
      <div><h3 id="meeting-point-map-title">{t('Choose the meeting point')}</h3><p>{t('Tap or click to place the pin. Drag it or use the arrow keys to adjust it.')}</p></div>
      <span aria-hidden="true">⌖</span>
    </div>
    <GoogleMeetingPointSearch language={language} bounds={googleBounds} onSelect={onGooglePlaceSelect} />
    {collection && mapGeometry
      ? <button
          type="button"
          className={`meeting-point-map${dragging ? ' is-dragging' : ''}`}
          aria-label={t('Meeting-point map. Tap or click to place the pin. Use arrow keys to move it.')}
          onPointerDown={beginMove}
          onPointerMove={continueMove}
          onPointerUp={finishMove}
          onPointerCancel={finishMove}
          onKeyDown={moveWithKeyboard}
        >
          <svg viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`} aria-hidden="true">
            <rect className="meeting-point-map-background" width={VIEW_WIDTH} height={VIEW_HEIGHT} rx="24" />
            {collection.features.map((feature) => {
              const ring = feature.geometry.coordinates[0];
              const [x, y] = labelPoint(ring);
              return <g key={feature.properties.wardNumber}>
                <path className="meeting-point-boundary" d={ringPath(ring)} />
                <text className="meeting-point-ward-label" x={x} y={y}>{feature.properties.wardNumber}</text>
              </g>;
            })}
            {marker && <g className="meeting-point-marker">
              <circle className="meeting-point-marker-halo" cx={marker[0]} cy={marker[1]} r="22" />
              <path d={`M${marker[0]},${marker[1] + 20} C${marker[0] - 3},${marker[1] + 12} ${marker[0] - 14},${marker[1] + 2} ${marker[0] - 14},${marker[1] - 8} A14,14 0 1,1 ${marker[0] + 14},${marker[1] - 8} C${marker[0] + 14},${marker[1] + 2} ${marker[0] + 3},${marker[1] + 12} ${marker[0]},${marker[1] + 20} Z`} />
              <circle className="meeting-point-marker-centre" cx={marker[0]} cy={marker[1] - 8} r="4" />
            </g>}
          </svg>
        </button>
      : <div className="status-panel state-warning" role="status">{t('The meeting-point map is unavailable. Enter coordinates manually below.')}</div>}
    <p className="meeting-point-map-note">{t('The outlines are an approximate local orientation guide, not official navigation data. Your confirmed pin supplies the meeting-point coordinates.')}</p>
    <details className="meeting-point-manual">
      <summary>{t('Enter coordinates manually')}</summary>
      <div className="meeting-point-coordinate-grid">
        <label>{t('Latitude')}<input type="text" inputMode="decimal" value={manualLatitude} onChange={(event) => setManualLatitude(event.target.value)} placeholder="21.370000" /></label>
        <label>{t('Longitude')}<input type="text" inputMode="decimal" value={manualLongitude} onChange={(event) => setManualLongitude(event.target.value)} placeholder="74.240000" /></label>
      </div>
      <button type="button" className="secondary" onClick={applyManualCoordinates}>{t('Apply coordinates')}</button>
      {manualStatus && <small role="status" aria-live="polite">{manualStatus}</small>}
    </details>
    <div className={`meeting-point-pin-status${position ? ' is-set' : ''}`} role="status" aria-live="polite">
      {position ? `✓ ${t('Pin placed. Confirm it with the public label below.')}` : t('No meeting-point pin has been placed yet.')}
    </div>
  </section>;
}
