import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import {
  googlePlaceSearchConfigured,
  loadGooglePlaces,
  type GooglePlacePrediction,
  type GooglePlacesLibrary,
} from './googleMapsPlaces';
import { translate, type InterfaceLanguage } from './i18n';
import {
  meetingPointSelectionFromPlace,
  type MeetingPointBounds,
} from './meetingPointSearchSelection';

export type GoogleMeetingPointSelection = {
  position: { latitude: number; longitude: number };
  label: string;
  address: string;
};

type Props = {
  language: InterfaceLanguage;
  bounds: MeetingPointBounds | null;
  onSelect: (selection: GoogleMeetingPointSelection) => void;
  mode?: 'meeting-point' | 'report-location';
  value?: string;
  onQueryChange?: (value: string) => void;
};

type SearchState = 'LOADING_API' | 'IDLE' | 'SEARCHING' | 'NO_RESULTS' | 'SELECTING' | 'ERROR';
const requestLanguages: Record<InterfaceLanguage, string> = { en: 'en-IN', mr: 'mr-IN', hi: 'hi-IN' };

export default function GoogleMeetingPointSearch({ language, bounds, onSelect, mode = 'meeting-point', value, onQueryChange }: Props) {
  const t = (source: string) => translate(language, source);
  const isReportLocation = mode === 'report-location';
  const contextualText = (meetingPointText: string, reportLocationText: string) => t(isReportLocation ? reportLocationText : meetingPointText);
  const library = useRef<GooglePlacesLibrary | null>(null);
  const sessionToken = useRef<unknown>(null);
  const requestNumber = useRef(0);
  const selectedQuery = useRef('');
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<GooglePlacePrediction[]>([]);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [state, setState] = useState<SearchState>(googlePlaceSearchConfigured() ? 'LOADING_API' : 'ERROR');
  const [message, setMessage] = useState(
    googlePlaceSearchConfigured() ? '' : t('Google place search is not configured. Use the pin below.'),
  );

  useEffect(() => {
    if (value === undefined || value === query) return;
    selectedQuery.current = value;
    setQuery(value);
  }, [value]);
  const [selectedAddress, setSelectedAddress] = useState('');

  useEffect(() => {
    let active = true;
    if (!bounds || !googlePlaceSearchConfigured()) return;
    setState('LOADING_API');
    loadGooglePlaces().then((places) => {
      if (!active) return;
      library.current = places;
      sessionToken.current = new places.AutocompleteSessionToken();
      setState('IDLE');
    }).catch(() => {
      if (!active) return;
      setState('ERROR');
      setMessage(contextualText(
        'Google place search is temporarily unavailable. You can still choose the meeting point using the pin below.',
        'Google location search is temporarily unavailable. Choose your Prabhag manually below.',
      ));
    });
    return () => { active = false; };
  }, [bounds]);

  useEffect(() => {
    const places = library.current;
    const input = query.trim();
    if (input && input === selectedQuery.current) {
      setSuggestions([]);
      setActiveIndex(-1);
      return;
    }
    if (!places || !bounds || input.length < 3) {
      setSuggestions([]);
      setActiveIndex(-1);
      return;
    }
    const currentRequest = ++requestNumber.current;
    setState('SEARCHING');
    setMessage('');
    const timer = window.setTimeout(() => {
      places.AutocompleteSuggestion.fetchAutocompleteSuggestions({
        input,
        includedRegionCodes: ['in'],
        language: requestLanguages[language],
        region: 'in',
        locationRestriction: bounds,
        sessionToken: sessionToken.current,
      }).then(({ suggestions: nextSuggestions }) => {
        if (currentRequest !== requestNumber.current) return;
        const predictions = nextSuggestions.flatMap((item) => item.placePrediction ? [item.placePrediction] : []);
        setSuggestions(predictions);
        setActiveIndex(predictions.length ? 0 : -1);
        setState(predictions.length ? 'IDLE' : 'NO_RESULTS');
        if (!predictions.length) setMessage(contextualText(
          'No matching place was found. Try a different name or choose the pin manually below.',
          'No matching location was found. Try a different address or landmark.',
        ));
      }).catch(() => {
        if (currentRequest !== requestNumber.current) return;
        setSuggestions([]);
        setState('ERROR');
        setMessage(contextualText(
          'Google place search is temporarily unavailable. You can still choose the meeting point using the pin below.',
          'Google location search is temporarily unavailable. Choose your Prabhag manually below.',
        ));
      });
    }, 350);
    return () => window.clearTimeout(timer);
  }, [bounds, language, query]);

  async function selectPlace(prediction: GooglePlacePrediction) {
    if (!bounds) return;
    setState('SELECTING');
    setSuggestions([]);
    setMessage(contextualText('Checking the selected meeting place…', 'Checking the selected location…'));
    try {
      const place = prediction.toPlace();
      await place.fetchFields({ fields: ['displayName', 'formattedAddress', 'location'] });
      const selection = meetingPointSelectionFromPlace(place, bounds);
      if (selection.status !== 'OK') {
        setState('ERROR');
        setMessage(contextualText(
          selection.status === 'MISSING_LOCATION'
            ? 'That result has no usable map location. Choose another result or use the pin below.'
            : selection.status === 'OUTSIDE_NANDURBAR'
              ? 'Choose a meeting place within the Nandurbar municipal area.'
              : 'That result has no usable public name. Choose another result or use the pin below.',
          selection.status === 'MISSING_LOCATION'
            ? 'That result has no usable map location. Choose another result or select your Prabhag manually.'
            : selection.status === 'OUTSIDE_NANDURBAR'
              ? 'Choose a location within the Nandurbar municipal area.'
              : 'That result has no usable public name. Choose another result or select your Prabhag manually.',
        ));
        return;
      }
      const selectedValue = isReportLocation ? selection.address || selection.label : selection.label;
      selectedQuery.current = selectedValue;
      setQuery(selectedValue);
      onQueryChange?.(selectedValue);
      setSelectedAddress(selection.address);
      setState('IDLE');
      setMessage(contextualText(
        'Meeting place selected. Adjust the pin or public label if needed, then confirm it.',
        'Location selected. Confirm or correct the suggested Prabhag below.',
      ));
      onSelect(selection);
      if (library.current) sessionToken.current = new library.current.AutocompleteSessionToken();
    } catch {
      setState('ERROR');
      setMessage(contextualText(
        'Google place search could not check that result. Choose another result or use the pin below.',
        'Google location search could not check that result. Choose another result or select your Prabhag manually.',
      ));
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (!suggestions.length) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActiveIndex((index) => (index + 1) % suggestions.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex((index) => (index <= 0 ? suggestions.length - 1 : index - 1));
    } else if (event.key === 'Enter' && activeIndex >= 0) {
      event.preventDefault();
      void selectPlace(suggestions[activeIndex]);
    } else if (event.key === 'Escape') {
      setSuggestions([]);
      setActiveIndex(-1);
    }
  }

  const titleId = isReportLocation ? 'google-report-location-search-title' : 'google-place-search-title';
  return <section className={`google-meeting-point-search ${isReportLocation ? 'report-location-search' : ''}`} aria-labelledby={titleId}>
    <div>
      <h4 id={titleId}>{contextualText('Search for a meeting place', 'Address or landmark')}</h4>
      <p>{contextualText(
        'Search for a public landmark, building or address in Nandurbar. Selecting a result moves the pin below.',
        'Search within Nandurbar. Selecting a result suggests a Prabhag; you confirm or correct it.',
      )}</p>
    </div>
    <div className="google-place-search-control">
      <input
        value={query}
        onChange={(event) => {
          selectedQuery.current = '';
          setQuery(event.target.value);
          onQueryChange?.(event.target.value);
        }}
        onKeyDown={handleKeyDown}
        placeholder={contextualText('Search place or address', 'Search location or address')}
        aria-label={contextualText('Search for a meeting place in Nandurbar', 'Search for the report location in Nandurbar')}
        aria-expanded={suggestions.length > 0}
        aria-controls="google-place-suggestions"
        aria-activedescendant={activeIndex >= 0 ? `google-place-option-${activeIndex}` : undefined}
        role="combobox"
        autoComplete="off"
      />
      {suggestions.length > 0 && <div id="google-place-suggestions" className="google-place-suggestions" role="listbox">
        {suggestions.map((prediction, index) => <button
          id={`google-place-option-${index}`}
          key={prediction.placeId}
          type="button"
          role="option"
          aria-selected={index === activeIndex}
          className={index === activeIndex ? 'active' : ''}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => void selectPlace(prediction)}
        ><strong>{prediction.mainText?.toString() || prediction.text.toString()}</strong>{prediction.secondaryText && <small>{prediction.secondaryText.toString()}</small>}</button>)}
        <img className="google-attribution" src="https://maps.gstatic.com/mapfiles/api-3/images/powered-by-google-on-white3.png" alt="Powered by Google" />
      </div>}
    </div>
    {(state === 'LOADING_API' || state === 'SEARCHING') && <small role="status" aria-live="polite">{t(state === 'LOADING_API' ? 'Loading Google place search…' : 'Searching places…')}</small>}
    {selectedAddress && <div className="google-place-selection"><span>{t('Selected address')}</span><b>{selectedAddress}</b></div>}
    {message && <small className={state === 'ERROR' || state === 'NO_RESULTS' ? 'google-place-search-error' : ''} role="status" aria-live="polite">{message}</small>}
    <div className="google-place-search-divider"><span>{t('or choose the pin manually')}</span></div>
  </section>;
}
