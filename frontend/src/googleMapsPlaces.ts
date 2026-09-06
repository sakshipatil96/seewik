export type GoogleMapsRoot = {
  importLibrary: (library: string) => Promise<unknown>;
};

type GoogleMapsWindow = Window & {
  google?: { maps?: GoogleMapsRoot };
  __seewikGoogleMapsReady?: () => void;
};

export type GooglePlaceLocation = {
  lat: () => number;
  lng: () => number;
};

export type GooglePlace = {
  displayName?: string;
  formattedAddress?: string;
  location?: GooglePlaceLocation;
  fetchFields: (options: { fields: string[] }) => Promise<void>;
};

export type GooglePlacePrediction = {
  placeId: string;
  text: { toString: () => string };
  mainText?: { toString: () => string };
  secondaryText?: { toString: () => string };
  toPlace: () => GooglePlace;
};

export type GoogleAutocompleteSuggestion = {
  placePrediction?: GooglePlacePrediction;
};

export type GooglePlacesLibrary = {
  AutocompleteSessionToken: new () => unknown;
  AutocompleteSuggestion: {
    fetchAutocompleteSuggestions: (request: {
      input: string;
      includedRegionCodes: string[];
      language: string;
      region: string;
      locationRestriction: { south: number; west: number; north: number; east: number };
      sessionToken: unknown;
    }) => Promise<{ suggestions: GoogleAutocompleteSuggestion[] }>;
  };
};

type GoogleGeocodingLibrary = {
  Geocoder: new () => {
    geocode: (request: { location: { lat: number; lng: number } }) => Promise<{
      results: Array<{ formatted_address?: string }>;
    }>;
  };
};

const CALLBACK_NAME = '__seewikGoogleMapsReady';
const SCRIPT_ID = 'seewik-google-maps-script';
let mapsPromise: Promise<GoogleMapsRoot> | null = null;

function mapsWindow() {
  return window as GoogleMapsWindow;
}

export function googlePlaceSearchConfigured() {
  return Boolean(import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim());
}

async function loadMapsRoot(): Promise<GoogleMapsRoot> {
  const existing = mapsWindow().google?.maps;
  if (existing?.importLibrary) return existing;
  if (!googlePlaceSearchConfigured()) throw new Error('GOOGLE_PLACES_NOT_CONFIGURED');
  if (mapsPromise) return mapsPromise;

  mapsPromise = new Promise<GoogleMapsRoot>((resolve, reject) => {
    const finish = () => {
      const maps = mapsWindow().google?.maps;
      delete mapsWindow()[CALLBACK_NAME];
      if (maps?.importLibrary) resolve(maps);
      else reject(new Error('GOOGLE_PLACES_LOAD_FAILED'));
    };

    mapsWindow()[CALLBACK_NAME] = finish;
    const existingScript = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null;
    if (existingScript) {
      existingScript.remove();
    }

    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.async = true;
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(import.meta.env.VITE_GOOGLE_MAPS_API_KEY.trim())}&v=weekly&loading=async&callback=${CALLBACK_NAME}`;
    script.addEventListener('error', () => {
      delete mapsWindow()[CALLBACK_NAME];
      script.remove();
      mapsPromise = null;
      reject(new Error('GOOGLE_PLACES_LOAD_FAILED'));
    }, { once: true });
    document.head.append(script);
  });

  return mapsPromise;
}

export async function loadGooglePlaces(): Promise<GooglePlacesLibrary> {
  const maps = await loadMapsRoot();
  return await maps.importLibrary('places') as GooglePlacesLibrary;
}

export async function loadGoogleMaps(): Promise<GoogleMapsRoot> {
  return await loadMapsRoot();
}

export async function reverseGeocodeGoogleLocation(latitude: number, longitude: number) {
  const maps = await loadMapsRoot();
  const geocoding = await maps.importLibrary('geocoding') as GoogleGeocodingLibrary;
  const response = await new geocoding.Geocoder().geocode({ location: { lat: latitude, lng: longitude } });
  return response.results[0]?.formatted_address?.trim() ?? '';
}
