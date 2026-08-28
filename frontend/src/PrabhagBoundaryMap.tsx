import boundaryGeoJsonText from '../../data/prabhags/official-map-digitized-boundaries-v0.1.geojson?raw';
import type { KeyboardEvent } from 'react';
import { translate, type InterfaceLanguage } from './i18n';
import './PrabhagBoundaryMap.css';

type Position = { latitude: number; longitude: number };
type Coordinate = [number, number];
type BoundaryFeature = {
  id: string;
  properties: {
    prabhagId: string;
    wardNumber: number;
    datasetVersion: string;
    reviewStatus: string;
  };
  geometry: { type: 'Polygon'; coordinates: Coordinate[][] };
};
type BoundaryCollection = {
  type: 'FeatureCollection';
  metadata: { datasetVersion: string; prabhagCount: number };
  features: BoundaryFeature[];
};

type PrabhagBoundaryMapProps = {
  language: InterfaceLanguage;
  highlightedPrabhagId?: string;
  selectionKind?: 'AUTOMATIC_CANDIDATE' | 'CONFIRMED' | 'MANUAL';
  currentPosition?: Position | null;
  onManualSelect: (prabhagId: string) => void;
};

const VIEW_WIDTH = 720;
const VIEW_HEIGHT = 540;
const PADDING = 28;

function validCollection(value: unknown): value is BoundaryCollection {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as BoundaryCollection;
  return candidate.type === 'FeatureCollection'
    && candidate.metadata?.datasetVersion === 'official-map-digitized-v0.1'
    && candidate.metadata?.prabhagCount === 20
    && Array.isArray(candidate.features)
    && candidate.features.length === 20
    && candidate.features.every((feature) => feature.geometry?.type === 'Polygon');
}

function parseCollection() {
  try {
    const value: unknown = JSON.parse(boundaryGeoJsonText);
    return validCollection(value) ? value : null;
  } catch {
    return null;
  }
}

const collection = parseCollection();

function boundsFor(features: BoundaryFeature[]) {
  const coordinates = features.flatMap((feature) => feature.geometry.coordinates.flat());
  const longitudes = coordinates.map(([longitude]) => longitude);
  const latitudes = coordinates.map(([, latitude]) => latitude);
  return {
    minLongitude: Math.min(...longitudes),
    maxLongitude: Math.max(...longitudes),
    minLatitude: Math.min(...latitudes),
    maxLatitude: Math.max(...latitudes),
  };
}

function projection(features: BoundaryFeature[]) {
  const bounds = boundsFor(features);
  const longitudeSpan = bounds.maxLongitude - bounds.minLongitude;
  const latitudeSpan = bounds.maxLatitude - bounds.minLatitude;
  const contentWidth = VIEW_WIDTH - 2 * PADDING;
  const contentHeight = VIEW_HEIGHT - 2 * PADDING;
  const scale = Math.min(contentWidth / longitudeSpan, contentHeight / latitudeSpan);
  const renderedWidth = longitudeSpan * scale;
  const renderedHeight = latitudeSpan * scale;
  const offsetX = (VIEW_WIDTH - renderedWidth) / 2;
  const offsetY = (VIEW_HEIGHT - renderedHeight) / 2;

  return {
    bounds,
    point([longitude, latitude]: Coordinate): Coordinate {
      return [
        offsetX + (longitude - bounds.minLongitude) * scale,
        offsetY + (bounds.maxLatitude - latitude) * scale,
      ];
    },
  };
}

function ringPath(ring: Coordinate[], point: (coordinate: Coordinate) => Coordinate) {
  return ring.map((coordinate, index) => {
    const [x, y] = point(coordinate);
    return `${index === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`;
  }).join(' ') + ' Z';
}

function labelPoint(ring: Coordinate[], point: (coordinate: Coordinate) => Coordinate) {
  const openRing = ring.slice(0, -1).map(point);
  return [
    openRing.reduce((sum, [x]) => sum + x, 0) / openRing.length,
    openRing.reduce((sum, [, y]) => sum + y, 0) / openRing.length,
  ] as Coordinate;
}

function approximateMarker(position: Position, point: (coordinate: Coordinate) => Coordinate, bounds: ReturnType<typeof boundsFor>) {
  const step = 0.002;
  const longitude = Math.round(position.longitude / step) * step;
  const latitude = Math.round(position.latitude / step) * step;
  if (longitude < bounds.minLongitude || longitude > bounds.maxLongitude || latitude < bounds.minLatitude || latitude > bounds.maxLatitude) return null;
  return point([longitude, latitude]);
}

export default function PrabhagBoundaryMap({
  language,
  highlightedPrabhagId,
  selectionKind,
  currentPosition,
  onManualSelect,
}: PrabhagBoundaryMapProps) {
  const t = (source: string) => translate(language, source);

  if (!collection) {
    return <div className="boundary-map-unavailable" role="status">{t('The boundary guide is unavailable. Choose Prabhag 1–20 manually.')}</div>;
  }

  const mapProjection = projection(collection.features);
  const marker = currentPosition ? approximateMarker(currentPosition, mapProjection.point, mapProjection.bounds) : null;
  const selectionLabel = selectionKind === 'AUTOMATIC_CANDIDATE'
    ? t('Suggested by the resolver — confirmation required')
    : selectionKind === 'MANUAL'
      ? t('Selected manually')
      : selectionKind === 'CONFIRMED'
        ? t('Confirmed selection')
        : '';

  function chooseWithKeyboard(event: KeyboardEvent<SVGPathElement>, prabhagId: string) {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    onManualSelect(prabhagId);
  }

  return <section className="boundary-map-panel" aria-labelledby="boundary-map-title">
    <div className="boundary-map-heading">
      <div>
        <h3 id="boundary-map-title">{t('Approximate prabhag boundary guide')}</h3>
        <p>{t('approximate boundaries digitized from an official map image')}</p>
      </div>
      <span className="boundary-map-count">20</span>
    </div>
    <dl className="boundary-map-metadata">
      <div><dt>{t('Dataset')}</dt><dd>{collection.metadata.datasetVersion}</dd></div>
      <div><dt>{t('Georeference review')}</dt><dd>REVIEW_PENDING_GEOREFERENCE</dd></div>
    </dl>
    <p className="boundary-map-caveat">{t('This is a visual orientation aid, not official digital GIS geometry. It never changes automatic routing and every selection still requires citizen confirmation.')}</p>
    <div className="boundary-map-frame">
      <svg
        className="boundary-map"
        viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
        role="group"
        aria-label={t('Boundary guide with all 20 prabhag outlines. Use Tab to reach an outline, then Enter or Space to select it manually.')}
      >
        <rect className="boundary-map-background" width={VIEW_WIDTH} height={VIEW_HEIGHT} rx="24" />
        {collection.features.map((feature) => {
          const highlighted = feature.properties.prabhagId === highlightedPrabhagId;
          const ring = feature.geometry.coordinates[0];
          const [labelX, labelY] = labelPoint(ring, mapProjection.point);
          const prabhagLabel = `${t('Prabhag')} ${feature.properties.wardNumber}`;
          return <g key={feature.id}>
            <path
              className={`boundary-shape${highlighted ? ' is-highlighted' : ''}`}
              d={ringPath(ring, mapProjection.point)}
              role="button"
              tabIndex={0}
              aria-label={`${t('Select')} ${prabhagLabel}${highlighted && selectionLabel ? `. ${selectionLabel}` : ''}`}
              aria-pressed={selectionKind !== 'AUTOMATIC_CANDIDATE' && highlighted}
              onClick={() => onManualSelect(feature.properties.prabhagId)}
              onKeyDown={(event) => chooseWithKeyboard(event, feature.properties.prabhagId)}
            />
            <text className={`boundary-label${highlighted ? ' is-highlighted' : ''}`} x={labelX} y={labelY} aria-hidden="true">
              {feature.properties.wardNumber}
            </text>
          </g>;
        })}
        {marker && <g className="boundary-location-marker" aria-hidden="true">
          <circle className="boundary-location-halo" cx={marker[0]} cy={marker[1]} r="15" />
          <circle className="boundary-location-dot" cx={marker[0]} cy={marker[1]} r="6" />
        </g>}
      </svg>
    </div>
    <div className="boundary-map-legend" aria-hidden="true">
      <span><i className="boundary-legend-outline" />{t('All approximate outlines')}</span>
      <span><i className="boundary-legend-highlight" />{t('Current suggestion or selection')}</span>
      {marker && <span><i className="boundary-legend-marker" />{t('Approximate temporary location')}</span>}
    </div>
    {highlightedPrabhagId && selectionLabel && <p className="boundary-map-selection" role="status" aria-live="polite">
      {t('Prabhag')} {Number(highlightedPrabhagId.slice(-2))} · {selectionLabel}
    </p>}
    <p className="boundary-map-privacy">{t('The temporary location marker is deliberately approximate. Numeric coordinates are not shown, and this visual guide does not store them.')}</p>
  </section>;
}
