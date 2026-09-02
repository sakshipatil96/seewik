# Initiative meeting-point contract v0.2

Contract ID: `initiative-meeting-point-v0.2`

Supersedes the Day 14 selection-method decision in `initiative-meeting-point-v0.1`. The stored meeting-point record remains `initiative-meeting-point-v0.1` because its public label and coordinate contract does not change.

## Approved scope amendment

- A restricted Google Autocomplete Data API browser integration is an optional convenience layer for selecting an Initiative meeting point. Seewik owns the inline input, suggestions, loading, no-results and failure interface; the Google-owned fullscreen element is not used.
- Initiative creation continues to work with the local movable pin and manual coordinates if Google is not configured, cannot load, is over quota or returns no usable result.
- Google search does not use the organiser's device location. Predictions are limited to India and restricted to the approximate Nandurbar municipal map bounds already used by the local picker.
- The integration is loaded only on the Create Initiative meeting-point step and requests only `displayName`, `formattedAddress`, and `location` after the organiser selects a prediction.
- Seewik does not store the search query, API key or complete Google Place response.
- Each search session uses an Autocomplete session token, active Seewik language plus region `IN`, a short input delay, and required Google attribution. Google may still lack translated street-level data for an individual place.

## Organiser confirmation

- A selected Google result populates the public label and coordinates and moves the local pin.
- The organiser may edit the public label or adjust the pin before confirmation.
- The organiser must explicitly confirm the final label-and-pin pair before publishing.
- Changing either the label or pin invalidates the previous confirmation.
- **Use my location for discovery** does not appear in the creation form. Browser geolocation remains an explicit, separate option on the participant discovery screen only.

## Stored record and participant response

- The backend continues accepting and validating only the citizen-confirmed label, latitude and longitude used by the `initiative-meeting-point-v0.1` stored record.
- The API key and Google Place data never enter the Initiative write request.
- Participant-facing responses expose the confirmed label and the generated Maps link, not separate raw coordinate fields.
- The link remains `https://www.google.com/maps/search/?api=1&query=<latitude>%2C<longitude>` and is derived by the backend from validated coordinates.

## Failure and legacy behavior

- Google configuration, loading, selection and quota failures show a translated message and leave the local/manual picker usable.
- Completed form fields are preserved across search and publish failures.
- Existing `initiative-v0.1`, `initiative-v0.2` and meeting-point-v0.1 records remain unchanged and readable.
- Day 14 performs no automatic geocoding, destructive rewrite or backfill.

## Credential and cost controls

- The browser key is restricted to Seewik's exact production and local HTTP referrers and to Maps JavaScript API plus Places API (New).
- The key is supplied through an ignored local/build environment value and is never printed to routine diagnostics or committed as a real credential.
- Billing alerts and API usage are reviewed before release. A billing alert is not treated as a hard spending cap.
