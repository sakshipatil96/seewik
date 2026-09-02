# Initiative meeting-point contract v0.1

Contract ID: `initiative-meeting-point-v0.1`

## Frozen Day 14 scope

- Initiative creation uses a local movable/manual pin, a citizen-confirmed public label and validated coordinates.
- Day 14 does not use Google Places, place search, a billing-enabled Maps browser key or a new remote map API.
- Seewik generates an **Open in Google Maps** universal link from the validated coordinates after the organiser publishes.
- Google Places Autocomplete is a possible later convenience layer, not part of the meeting-point source of truth.

## Organiser confirmation

- The organiser must enter a public meeting-point label, place a pin and explicitly confirm the pair before publishing.
- Changing either the label or pin invalidates the previous confirmation.
- Initiative creation never treats the organiser's current device location as the meeting point.
- **Use my location for discovery** does not appear in the creation form.
- Browser geolocation remains an explicit, separate option on the participant discovery screen only.
- The form lists unmet publishing requirements beside the publish action and preserves completed fields after validation, account-link, map or network failures.

## Stored record

New records use `initiative-v0.3` and retain the existing top-level `placeName`, `latitude` and `longitude` fields for deterministic discovery compatibility. They also store:

```json
{
  "meetingPoint": {
    "label": "Citizen-confirmed public label",
    "latitude": 21.370000,
    "longitude": 74.240000,
    "schemaVersion": "initiative-meeting-point-v0.1"
  }
}
```

- Backend validation accepts only a non-blank label of at most 200 characters, a finite latitude from -90 to 90 and a finite longitude from -180 to 180.
- Creation remains backend-owned and requires a Google-linked organiser. Firestore clients cannot create or alter Initiative records directly.
- The client keeps one opaque request ID across publication retries. The backend derives an owner-scoped deterministic Initiative ID and transactionally creates the Initiative, creation event, organiser participation and zero-point ledger entry, so a lost response or retry cannot create a second public Initiative or a partial record.
- Precise coordinates are not written to routine logs or analytics.

## Participant response

- Participant-facing responses expose the confirmed label and the generated Maps link, not separate raw coordinate fields.
- The link format is `https://www.google.com/maps/search/?api=1&query=<latitude>%2C<longitude>`.
- The participant interface shows the confirmed label and **Open in Google Maps** on nearby and My Actions Initiative cards.
- Opening Google Maps is an explicit citizen action; Seewik does not start directions or collect a participant location automatically.

## Legacy compatibility

- Existing `initiative-v0.1` and `initiative-v0.2` records remain unchanged.
- A legacy record with top-level `placeName`, `latitude` and `longitude` continues to render and receives a derived Maps link when its coordinates are valid.
- A legacy record without valid coordinates continues to show its text meeting place without an external Maps action.
- Day 14 performs no automatic geocoding, destructive rewrite or backfill.

## Deferred

- Google Places search or Places Autocomplete
- A billing-enabled Google Maps browser integration
- Background location, automatic location capture or photograph-based location inference
- Editing a published Initiative's meeting point
