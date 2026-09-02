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
};

type SearchState = 'LOADING_API' | 'IDLE' | 'SEARCHING' | 'NO_RESULTS' | 'SELECTING' | 'ERROR';
const requestLanguages: Record<InterfaceLanguage, string> = { en: 'en-IN', mr: 'mr-IN', hi: 'hi-IN' };

export default function GoogleMeetingPointSearch({ language, bounds, onSelect }: Props) {
  const t = (source: string) => translate(language, source);
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
      setMessage(t('Google place search is temporarily unavailable. You can still choose the meeting point using the pin below.'));
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
        if (!predictions.length) setMessage(t('No matching place was found. Try a different name or choose the pin manually below.'));
      }).catch(() => {
        if (currentRequest !== requestNumber.current) return;
        setSuggestions([]);
        setState('ERROR');
        setMessage(t('Google place search is temporarily unavailable. You can still choose the meeting point using the pin below.'));
      });
    }, 350);
    return () => window.clearTimeout(timer);
  }, [bounds, language, query]);

  async function selectPlace(prediction: GooglePlacePrediction) {
    if (!bounds) return;
    setState('SELECTING');
    setSuggestions([]);
    setMessage(t('Checking the selected meeting place…'));
    try {
      const place = prediction.toPlace();
      await place.fetchFields({ fields: ['displayName', 'formattedAddress', 'location'] });
      const selection = meetingPointSelectionFromPlace(place, bounds);
      if (selection.status !== 'OK') {
        setState('ERROR');
        setMessage(t(selection.status === 'MISSING_LOCATION'
          ? 'That result has no usable map location. Choose another result or use the pin below.'
          : selection.status === 'OUTSIDE_NANDURBAR'
            ? 'Choose a meeting place within the Nandurbar municipal area.'
            : 'That result has no usable public name. Choose another result or use the pin below.'));
        return;
      }
      selectedQuery.current = selection.label;
      setQuery(selection.label);
      setSelectedAddress(selection.address);
      setState('IDLE');
      setMessage(t('Meeting place selected. Adjust the pin or public label if needed, then confirm it.'));
      onSelect(selection);
      if (library.current) sessionToken.current = new library.current.AutocompleteSessionToken();
    } catch {
      setState('ERROR');
      setMessage(t('Google place search could not check that result. Choose another result or use the pin below.'));
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

  return <section className="google-meeting-point-search" aria-labelledby="google-place-search-title">
    <div>
      <h4 id="google-place-search-title">{t('Search for a meeting place')}</h4>
      <p>{t('Search for a public landmark, building or address in Nandurbar. Selecting a result moves the pin below.')}</p>
    </div>
    <div className="google-place-search-control">
      <input
        value={query}
        onChange={(event) => {
          selectedQuery.current = '';
          setQuery(event.target.value);
        }}
        onKeyDown={handleKeyDown}
        placeholder={t('Search place or address')}
        aria-label={t('Search for a meeting place in Nandurbar')}
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
