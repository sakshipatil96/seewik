export type MeetingPointBounds = { south: number; west: number; north: number; east: number };
export type MeetingPointCandidate = {
  displayName?: string;
  formattedAddress?: string;
  location?: { lat: () => number; lng: () => number };
};

export type MeetingPointSelectionResult =
  | { status: 'OK'; position: { latitude: number; longitude: number }; label: string; address: string }
  | { status: 'MISSING_LOCATION' | 'OUTSIDE_NANDURBAR' | 'MISSING_LABEL' };

export function cleanGooglePlaceLabel(value: string) {
  return value
    .replace(/^[23456789CFGHJMPQRVWX]{4,8}\+[23456789CFGHJMPQRVWX]{2,3}(?:,\s*|\s+)/i, '')
    .replace(/^[,–—-]+\s*/, '')
    .trim();
}

export function meetingPointSelectionFromPlace(
  place: MeetingPointCandidate,
  bounds: MeetingPointBounds,
): MeetingPointSelectionResult {
  const latitude = place.location?.lat();
  const longitude = place.location?.lng();
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return { status: 'MISSING_LOCATION' };
  const position = { latitude: latitude as number, longitude: longitude as number };
  if (position.latitude < bounds.south
    || position.latitude > bounds.north
    || position.longitude < bounds.west
    || position.longitude > bounds.east) {
    return { status: 'OUTSIDE_NANDURBAR' };
  }
  const address = cleanGooglePlaceLabel(place.formattedAddress?.trim() ?? '');
  const label = cleanGooglePlaceLabel(place.displayName?.trim() ?? '') || address;
  return label
    ? { status: 'OK', position, label, address: address || label }
    : { status: 'MISSING_LABEL' };
}
