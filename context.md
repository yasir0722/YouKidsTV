# YouKids: Product and Implementation Context

## App summary

YouKids is an Android TV video-library prototype built with the AndroidX
Leanback UI framework. It currently demonstrates the complete television
browsing journey:

1. A TV launcher entry opens a multi-row catalogue.
2. Selecting a card opens a details screen with artwork, metadata, and
   related videos.
3. Selecting "Watch trailer" opens a full-screen player with transport
   controls.

The current catalogue is not YouKids content. It is a local, hard-coded list
of five Android TV sample videos that is repeated and shuffled across six
placeholder categories. Card and backdrop images are downloaded with Glide;
video playback uses Leanback's `MediaPlayerAdapter`.

## Current technical baseline

| Area | Current implementation |
| --- | --- |
| Platform | Android TV only; Leanback is required and touch input is optional |
| Language | Kotlin |
| Module structure | One `app` module; no data, domain, or UI feature modules |
| UI | Leanback `BrowseSupportFragment`, `DetailsSupportFragment`, and `VideoSupportFragment` |
| Navigation | Activities and intent extras: browse -> details -> playback |
| Content source | `MovieList`, an in-memory hard-coded sample catalogue |
| Images | Glide loading remote card and backdrop images |
| Playback | `MediaPlayerAdapter` through `PlaybackTransportControlGlue` |
| SDK configuration | Compile/target SDK 37, minimum SDK 36 |
| Testing | No unit, instrumented, or UI tests are present |

## Existing user-facing behavior

- The home screen shows six named placeholder category rows and one
  "PREFERENCES" row.
- Search and personal settings only show placeholder messages.
- The details screen exposes Watch trailer, Rent, and Buy actions. Only Watch
  trailer launches a player; the other actions are placeholders.
- The error screen is a demonstration flow that always waits three seconds and
  then shows a generic error.
- Catalogue items are shuffled in-place when rows or related content are
  constructed, so ordering is neither stable nor user-specific.

## Known baseline gaps to address before feature work

- The app is a starter/sample rather than a YouKids product: labels,
  content, categories, artwork, and monetization copy are placeholders.
- Related-item navigation stores its intent extra using the localized
  `movie` string rather than the `DetailsActivity.MOVIE` key the destination
  reads. Opening a related item can therefore return to the home screen
  instead of its details page.
- There is no content API, persistence layer, offline behaviour, loading
  state, recoverable network-error state, analytics, or test coverage.
- `Movie` is sent through intents with Java serialization. Replace this with
  an ID-based route after a repository exists, rather than passing the full
  content object.
- The minimum SDK 36 excludes most existing Android TV devices. Confirm the
  intended device fleet before changing it; the final floor must match the
  Android TV and Google Play distribution requirements.

## Product direction

Turn the prototype into a safe, maintainable Android TV experience for
children and families. The first usable release should let a child browse a
curated catalogue, find a title, view age-appropriate details, and reliably
watch an approved video. Parent-only actions and all external services must
be designed explicitly rather than inferred from this prototype.

## Delivery plan

### Phase 0 — Define release decisions

1. Define the initial audience, markets, supported Android TV devices, and
   minimum supported API level.
2. Choose the content authority and integration method: a managed backend,
   CMS, or a versioned curated feed. Define its availability and ownership.
3. Define the catalogue schema: stable ID, title, synopsis, age rating,
   duration, category, artwork, playback URL, captions, language, and
   availability window.
4. Decide whether launch scope includes user profiles, parental controls,
   authentication, downloads, subscriptions, purchases, casting, and ads.
5. Establish child-safety, privacy, consent, content-review, and telemetry
   requirements before collecting data or integrating third-party SDKs.

**Exit criteria:** a short approved product requirements document, API/content
contract, supported-device matrix, and privacy/safety requirements exist.

### Phase 1 — Stabilize the prototype

1. Correct related-item navigation to use the same non-localized intent key
   as browse-to-details navigation.
2. Replace generic naming and placeholder copy with temporary YouKids
   branding, keeping all visible text in string resources.
3. Replace mutable global shuffling with deterministic, immutable catalogue
   data so rows and related recommendations are reproducible.
4. Make intent input validation and playback failure handling explicit:
   missing/invalid media must show a recoverable screen rather than crash.
5. Create an emulator/device smoke-test checklist for launch, browse,
   details, related navigation, playback controls, and back navigation.
6. Add automated tests for the catalogue mapper, navigation input, and
   playback route selection.

**Exit criteria:** the demo is reliable on a supported Android TV emulator
and a physical device, with no placeholder flow required to complete the
core journey.

### Phase 2 — Establish production architecture and content

1. Introduce a repository interface and use-case layer. Keep UI models
   separate from network/database DTOs.
2. Implement the approved remote catalogue source with timeouts, structured
   errors, and a local cache. Use stable content IDs for navigation.
3. Model screen states explicitly: loading, populated, empty, offline cached,
   and recoverable error.
4. Build category and title detail screens from the repository, including
   age rating, duration, captions, and availability.
5. Implement real TV search across locally available catalogue data, then
   extend it to server search only if required by the selected backend.
6. Add tests for repository success, empty, cache, and error paths.

**Exit criteria:** an editor can publish approved content to the selected
source and it appears in the app without an app release; the core experience
remains usable from cache when appropriate.

### Phase 3 — Build a child-safe playback experience

1. Migrate from the sample platform adapter to the playback stack chosen for
   supported media formats and DRM requirements (normally Media3/ExoPlayer).
2. Support adaptive streams, caption/subtitle selection, audio languages,
   playback error recovery, and lifecycle-correct release of player
   resources.
3. Persist progress and resume position by profile and content ID when
   profiles are in scope.
4. Filter recommendations and autoplay behaviour through age-rating and
   content-approval rules.
5. Add instrumented playback and remote-control navigation tests.

**Exit criteria:** approved streams play reliably, controls work with a TV
remote, captions work where supplied, and only permitted follow-on content
is offered.

### Phase 4 — Add family features only after the core is stable

1. Add child profiles and a parent-gated settings area if included in the
   Phase 0 scope.
2. Implement parental controls, profile PIN/parent verification, viewing
   history, favourites/watchlist, and content preferences as approved.
3. Add authentication and entitlement/subscription flows only after privacy,
   account recovery, purchase, and support paths are specified.
4. Replace the current Rent and Buy placeholders only when a full,
   policy-compliant commerce experience has been designed.

**Exit criteria:** every family-facing feature has an approved UX, data
retention policy, failure state, accessibility coverage, and automated tests.

### Phase 5 — Release readiness and operations

1. Add CI checks for formatting/linting, unit tests, instrumented smoke
   tests, and release builds.
2. Set up non-sensitive crash reporting and operational metrics consistent
   with the approved privacy policy.
3. Prepare Android TV store assets, privacy disclosures, support contacts,
   release notes, and a rollback process.
4. Test performance and remote navigation on each supported TV/device class
   and network condition.
5. Define catalogue incident handling: unavailable content, expired rights,
   invalid artwork, and playback-provider outages.

**Exit criteria:** a signed release candidate passes device, accessibility,
privacy, security, and content-safety checklists, and the operating team can
monitor and roll back the release.

## Recommended first implementation slice

Start with Phase 1 and the first two Phase 2 tasks:

1. Fix related-item navigation and replace demo strings/data with a stable
   local YouKids fixture.
2. Introduce a `VideoRepository` interface backed initially by that fixture.
3. Navigate by content ID and load each screen through the repository.
4. Add automated tests for the browse-to-details, related-to-details, and
   details-to-playback routes.
5. Replace the fixture with the chosen catalogue integration once Phase 0's
   contract is approved.

This creates a safe migration path from the sample app to production content
without coupling the UI to a backend or shipping unstable demo behavior.

## Decisions still required

- What is the launch catalogue source and who approves children’s content?
- Which countries, languages, age bands, and Android TV devices are in the
  first release?
- Are profiles and parental controls required for the first release?
- Is viewing possible without sign-in, and is a subscription or purchase
  model in scope?
- What media formats, captions, DRM, and offline requirements must playback
  support?
- Which privacy, consent, and analytics policies apply to children and
  parents?
