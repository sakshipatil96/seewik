import boundaryGeoJsonText from '../../data/prabhags/official-map-digitized-boundaries-v0.2.geojson?raw';

export type PrabhagCoordinate = [number, number];
export type PrabhagBoundaryFeature = {
  id: string;
  properties: {
    prabhagId: string;
    prabhagName: string;
    wardNumber: number;
    datasetVersion: string;
    resolutionQuality: string;
    requiresCitizenConfirmation: true;
  };
  geometry: { type: 'Polygon'; coordinates: PrabhagCoordinate[][] };
};
export type PrabhagBoundaryCollection = {
  type: 'FeatureCollection';
  name: string;
  features: PrabhagBoundaryFeature[];
};

export const PRABHAG_DATASET_VERSION = 'seewik-map-trace-v0.2';
export const PRABHAG_RESOLUTION_QUALITY = 'APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE';

function parseCollection(): PrabhagBoundaryCollection | null {
  try {
    const value = JSON.parse(boundaryGeoJsonText) as {
      type?: string;
      name?: string;
      features?: Array<Omit<PrabhagBoundaryFeature, 'id'> & { id?: string }>;
    };
    if (value.type !== 'FeatureCollection' || !Array.isArray(value.features) || value.features.length !== 20) return null;
    const ids = new Set<string>();
    const features = value.features.map((feature, index) => {
      const expectedId = `PRABHAG-${String(index + 1).padStart(2, '0')}`;
      const properties = feature.properties;
      if (feature.geometry?.type !== 'Polygon'
        || !Array.isArray(feature.geometry.coordinates)
        || properties?.prabhagId !== expectedId
        || properties?.wardNumber !== index + 1
        || properties?.datasetVersion !== PRABHAG_DATASET_VERSION
        || properties?.resolutionQuality !== 'APPROXIMATE_DIGITISED_OFFICIAL_MAP_IMAGE'
        || properties?.requiresCitizenConfirmation !== true
        || ids.has(expectedId)) throw new Error('INVALID_PRABHAG_BOUNDARY_DATA');
      ids.add(expectedId);
      return {
        ...feature,
        id: expectedId,
        properties: { ...properties, resolutionQuality: PRABHAG_RESOLUTION_QUALITY },
      } as PrabhagBoundaryFeature;
    });
    return { type: 'FeatureCollection', name: value.name ?? 'Nandurbar prabhag boundary guide', features };
  } catch {
    return null;
  }
}

export const prabhagBoundaryCollection = parseCollection();

export function prabhagBounds(features = prabhagBoundaryCollection?.features) {
  if (!features?.length) return null;
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

export const PRABHAG_BOUNDS = prabhagBounds();
