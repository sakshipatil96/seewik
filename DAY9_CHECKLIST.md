# Day 9 Checklist

Day 9 absorbs the former Set 3 work and incorporates the approximate prabhag geometry digitized from an official Nagar Parishad map image.

## Set 1 — Lock language and boundary requirements

- [x] Confirm English, Marathi and Hindi interface support.
- [x] Follow a supported device language on first use; otherwise use English.
- [x] Keep a visible language control on every screen.
- [x] Persist the choice locally and allow it to change at any time.
- [x] Preserve citizen-entered text and mixed-language evidence unchanged.
- [x] Keep complaint drafting limited to Marathi and English.
- [x] Keep deterministic authority and route facts untranslated unless Civic Pack supplies local wording.
- [x] Use 20 geographic prabhags, never 40 seat positions, as map regions.
- [x] Classify the new geometry as approximate digitization from an official source image, not official GIS geometry.
- [x] Keep the digitized dataset inactive for routing pending georeference review.
- [x] Lock map privacy, confirmation, manual fallback and replacement rules.
- [x] Freeze `data/contracts/day9-language-boundary-contract-v0.1.md`.

## Set 2 — Language and accessibility foundations

- [x] Add device-language detection and English fallback.
- [x] Add an always-visible English/Marathi/Hindi switcher.
- [x] Persist and restore the citizen's choice.
- [x] Set the document language when the choice changes.
- [x] Add a versioned translation catalogue for primary citizen flows.
- [x] Translate navigation, homepage, report, report review, My Reports, report details, points and Initiative screens.
- [x] Translate common loading, validation, success and fallback states, with safe English fallback for unrecognized upstream errors.
- [x] Keep complaint facts and Initiative text exactly as entered.
- [x] Keep authority and route facts unchanged.
- [x] Make the Marathi/English-only drafting choice clear in Hindi UI.
- [x] Localize display dates while preserving stored timestamps.
- [x] Add a skip link and visible keyboard focus.
- [x] Mark active desktop and mobile navigation destinations.
- [x] Keep controls at least approximately 44×44 pixels.
- [x] Ensure every form control has an accessible label.
- [x] Announce asynchronous status changes.
- [x] Verify language persistence, fallback, date formatting and catalogue completeness.
- [x] Verify direct-link refresh and browser Back/Forward remain unchanged.
- [x] Record that Marathi/Hindi product-language review remains pending because no independent language reviewer has approved `interface-copy-v0.1` yet.

## Set 3 — Approximate prabhag boundary UI

- [x] Validate the 20-feature GeoJSON, checksum, topology and visible coverage.
- [x] Render the local versioned geometry without a third-party map provider.
- [x] Label it “approximate boundaries digitized from an official map image.”
- [x] Display the dataset version and georeference-review status.
- [x] Show all 20 outlines and highlight only a deterministic/manual candidate.
- [x] Keep automatic candidates confirmation-required.
- [x] Keep manual Prabhag 1–20 selection next to the map.
- [x] Provide a complete non-map selection experience.
- [x] Never guess a nearest polygon.
- [x] Preserve outside-area rejection and automatic-resolution degraded fallback.
- [x] Keep any citizen marker temporary, approximate and free of numeric coordinates.
- [x] Test mobile layout, keyboard use, 200% zoom-equivalent reflow and all three languages.
- [x] Lazy-load the map.

## Set 4 — Code splitting and performance

- [ ] Preserve the approximately 807.28 kB minified / 239.78 kB gzip baseline.
- [ ] Add route-level lazy loading.
- [ ] Split reporting and Initiative features.
- [ ] Avoid loading Firebase-heavy screens on the homepage where practical.
- [ ] Load boundary code only on the report screen.
- [ ] Prevent overlapping Initiative refresh requests.
- [ ] Stop polling after leaving Initiate.
- [ ] Preserve direct-link refresh and browser history.
- [ ] Measure minified, gzip and transferred JavaScript.
- [ ] Measure desktop/mobile LCP, INP and CLS with comparable conditions.
- [ ] Document remaining warnings.

## Set 5 — Track B image evaluation

- [x] Keep previously viewed images as pilot-only evidence.
- [x] Use only untouched images in a scored set.
- [x] Keep raw photographs and private identifiers outside Git.
- [x] Record private case ID, Nandurbar provenance, permission, privacy review and expected label.
- [x] Crop or redact unnecessary faces, plates, house numbers and addresses.
- [x] Create `classification-image-cases-v0.1-draft` metadata and integrity tests.
- [x] Support JPEG, PNG and WebP up to 5 MB.
- [x] Keep Track B separate from the frozen 60-case Track A evaluation.
- [x] Freeze images, labels, schema, prompt, model and scoring before calls.
- [x] Exclude unresolved labels from accuracy scoring.
- [x] Obtain a second label review where possible; document if unavailable.
- [x] Run image-only classification twice without text hints or silent retries.
- [x] Preserve raw responses and separate transport, timeout, model and schema failures.
- [x] Report counts, accuracy, confusion pairs, clarification behaviour, stability and latency.
- [x] Make no universal image-accuracy claim.

## Set 6 — Design-only notes

- [ ] Write the Initiative verification design note.
- [ ] Describe verifier roles, evidence, privacy and anti-fraud constraints.
- [ ] Explain why organiser/participant self-attestation is insufficient for rewards.
- [ ] Keep the design explicitly unimplemented and all Initiative rewards at zero.
- [ ] Do not create alert policies without a recipient.
- [ ] Preserve the survey single-reviewer limitation.

## Set 7 — Final verification, documentation and deployment

- [ ] Create `DAY9_BUILD_LOG.md`.
- [ ] Update `CHANGELOG.md` and the parent project guide.
- [ ] Run affected backend and frontend gates.
- [ ] Run repository-content and whitespace checks.
- [ ] Deploy only from green `main`.
- [ ] Verify production routes, languages, boundary fallbacks and browser console.
- [ ] Preserve representative production screenshots in all three languages.
- [ ] Record app, local, remote, checked and deployed commit identifiers separately.

Survey scoring remains deferred while responses are still being collected. It is not a Day 9 blocker.

## Day 9 closure and carry-over

- Day 9 closes after completed Sets 1, 2, 3 and 5.
- Set 4 performance work moves to Day 11 after the attendance UI is stable.
- Set 6's verification-design concern is split into Day 11 self-attestation and a later hardened QR/geolocation phase; Initiative rewards remain zero.
- Set 7 final verification moves behind the architectural login change as Day 10 Set 7.
- Day 10 introduces recoverable Google-linked profiles while preserving the existing Firebase UID whenever account linking succeeds.
- Day 11 adds self-attested attendance before the carried performance work.
