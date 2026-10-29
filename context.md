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

The catalogue is being connected to the Laravel backend in
`/Users/techqesb/Dev/telegram-video-library`. The app reads the public
read-only feed for the configured Telegram `link` topic. YouTube entries open
in a WebView embed, direct MP4 URLs use Leanback playback, and Telegram uploads
can use a short-lived backend playback URL.

## Current technical baseline

| Area | Current implementation |
| --- | --- |
| Platform | Android TV only; Leanback is required and touch input is optional |
| Language | Kotlin |
| Module structure | One `app` module; no data, domain, or UI feature modules |
| UI | Leanback `BrowseSupportFragment`, `DetailsSupportFragment`, and `VideoSupportFragment` |
| Navigation | Activities and intent extras: browse -> details -> playback |
| Content source | `VideoLibraryRepository`, public backend catalogue endpoint |
| Images | Glide loading remote card and backdrop images |
| Playback | YouTube iframe in WebView; MP4/Telegram via `MediaPlayerAdapter`; stored language, speed, quality, and subtitle preferences |
| SDK configuration | Compile/target SDK 37, minimum SDK 23 |
| Testing | JUnit unit tests cover catalogue ordering and related-item selection; TV UI and installer flows still need device smoke tests |

## Existing user-facing behavior

- The home screen shows videos returned from the backend's curated `link`
  topic feed.
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
- There is no local cache or offline behaviour. API loading and error handling
  are being added; analytics and end-to-end TV tests remain future work.
- `Movie` is sent through intents with Java serialization. Replace this with
  an ID-based route after a repository exists, rather than passing the full
  content object.
- The minimum SDK is now 23 to support older Android TV devices. Confirm the
  intended device fleet before release; the final floor must match the
  Android TV and Google Play distribution requirements.

## Product direction

Turn the prototype into a safe, maintainable Android TV experience for
children and families. The first usable release should let a child browse a
curated catalogue, find a title, view age-appropriate details, and reliably
watch an approved video. Parent-only actions and all external services must
be designed explicitly rather than inferred from this prototype.

## Selected content integration

- The backend reads one configured Telegram channel/forum supergroup.
- Only posts in its `link` topic are shown in the public YouKids catalogue.
- Link-topic posts may contain one HTTPS YouTube URL or direct HTTPS `.mp4`
  URL. Use the message text for the title and optional description.
- Public access means anyone who can reach the API can view and play published
  items from that topic. Other Telegram topic categories stay outside the
  public feed.
- Do not embed Telegram credentials or backend secrets in the Android app.
- YouTube playback uses the embedded YouTube player in WebView as requested;
  embedding may fail for videos whose owners or YouTube restrict playback.

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

1. **Done:** related-item navigation now uses the clicked item and the
   canonical intent key, matching browse-to-details navigation.
2. **In progress:** replaced the generic browse title and watch/rent/buy demo
   actions with YouKids/video wording.
3. **Done:** removed in-place catalogue shuffling; IDs, category row content,
   and related recommendations now follow deterministic catalogue order.
4. Make intent input validation and playback failure handling explicit:
   missing/invalid media must show a recoverable screen rather than crash.
5. Create an emulator/device smoke-test checklist for launch, browse,
   details, related navigation, playback controls, and back navigation.
6. **In progress:** added unit tests for related-item exclusion and stable
   catalogue ordering. Still add coverage for navigation input and playback
   route selection.

**Exit criteria:** the demo is reliable on a supported Android TV emulator
and a physical device, with no placeholder flow required to complete the
core journey.

### Phase 2 — Connect Telegram topic catalogue to YouKids TV

1. **In progress:** sync one external HTTPS YouTube or MP4 link from each
   supported message in the configured Telegram `link` topic.
2. **In progress:** expose only published `link`-topic catalogue entries via
   the backend's public read-only API; keep other routes authenticated.
3. **In progress:** replace the hard-coded Android TV sample list with API
   loading, explicit error/empty states, and stable video IDs.
4. Play YouTube through the selected WebView embed and MP4 inside the TV
   player; retain signed backend playback for Telegram uploads.
5. Verify D-pad browsing, details, playback, unavailable-video behavior, and
   back navigation on an Android TV emulator.
6. Test Telegram URL parsing, topic filtering, public API exposure, and
   backend playback URL scoping.

**Exit criteria:** a link posted in the Telegram `link` topic appears in
YouKids TV after sync, other topics do not appear in the public feed, and the
matching YouTube or MP4 playback path works on the target TV emulator.

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

## Integration workflow

Run Telegram sync after posting or editing a supported link-topic message.
Configure `youkidsApiBaseUrl` in Gradle to the backend `/api/v1` URL. The
default `https://afterlight.yasiraz.my/api/v1` points to the deployed backend;
override it with `http://10.0.2.2:8080/api/v1` when using the local Docker API
from an Android emulator.

From the TV app's Settings card, choose **Sync videos from Telegram** after
adding or editing a link-topic post. The app asks Afterlight to run a
rate-limited server-side sync, then reloads the catalogue on the home screen.

For tag browsing, begin a Telegram link-topic message with `title = Bing`,
`title = Upin Ipin`, `title = Mechamato`, or another label, then put one or
more video links in that message. After the backend is migrated and synced,
**Explore all videos** presents the tags and opens a filtered list. Tag values
may contain comma-, pipe-, or newline-separated labels; each label is shown as
its own selectable tag. **Watch on YouTube** remains one browse tab whose
content is arranged as up to three compact, horizontally scrollable card rows
instead of three tabs or one long horizontal row. Focus follows the remote and
scrolls the active row toward the selected card. Each row includes edge
padding so the first and last cards can be brought fully into view.

The app shuffles the catalogue when it loads. This keeps the main rows,
filtered tag lists, related videos, and the in-player alternative-video list
from always presenting the same order. Search has been removed from the TV
interface; the Leanback search affordance is hidden and there is no search
dialog or search-results flow.

During playback, pressing Down once shows the playback controls. Pressing Down
again opens a focusable horizontal strip of small thumbnail cards outside the
controls, allowing the viewer to move between alternative videos with
Left/Right and open one with OK. Back hides the strip before leaving playback.
The same suggestion strip opens automatically when the current video reaches
the end, for both YouTube and direct video playback.

## Review required before further implementation

Investigation on 2026-10-17 found the following issues. These are review TODOs
only; no implementation has been approved from this list yet.

### YouTube grid: first and last cards cannot be reached

**Observed behavior:** the three-row **Watch on YouTube** section displays
cards in the middle of each row, but the far-left and far-right cards cannot
reliably receive remote focus or be brought fully onscreen.

**Likely cause:** the previous custom three-row card view was nested inside
Leanback's own horizontally focused `ListRow`. Each internal row used a
`HorizontalScrollView`, while the outer Leanback `HorizontalGridView` also
handled D-pad focus. The internal scroll command ran only after a card gained
focus; the outer Leanback row could intercept Left/Right before focus reached
an offscreen card. Edge padding therefore could not make unreachable cards
reachable.

**Implementation status:** the nested `ListRow`/grid arrangement has been
replaced with a custom Leanback `YouTubeGridRow` and `YouTubeGridRowPresenter`.
The row itself owns one `HorizontalGridView` configured for three visual rows,
so the grid is no longer nested inside another horizontally focused row.

**Device verification completed on the Android TV emulator:**

1. One **Watch on YouTube** header displays three visual rows.
2. Cards render in the emulator and D-pad focus enters the grid.
3. Repeated Right reached the horizontal right edge; repeated Left returned
   to the horizontal left edge.
4. Down moved to the next visual row and Up returned to the previous row.

The grid partition now has JVM unit coverage for at-most-three-row layout,
preserved ordering, and first/last video retention. Device verification was
performed manually because the repository has no Android instrumentation test
setup.

### Telegram sync: some expected videos do not appear

**Observed behavior:** the TV Settings sync action calls
`POST /api/v1/public/videos/refresh`. The endpoint responded with HTTP 202 and
`Telegram synchronization queued.` on 2026-10-17. After the queue had time to
settle, the public catalogue remained unchanged at 37 videos.

**Live catalogue evidence:** `GET /api/v1/public/videos?per_page=100&page=1`
returned 37 published entries, all with `media.provider = youtube`,
`media.type = external_url`, and an empty `tag`. There were no direct MP4 or
Telegram-upload entries in the public response.

**Important distinction:** the web sync's `scanned: 170` count covers all
Telegram messages seen by the backend, not just the 37 records eligible for
the public TV catalogue. The public feed intentionally includes only published
videos assigned to the configured primary channel's `link` topic. External
links must be HTTPS YouTube or direct `.mp4` URLs. Telegram-uploaded videos
and messages in other topics may be synchronized into the private library but
will not appear in YouKids.

**Investigation result:** the public Android endpoint remains asynchronous:
`POST /api/v1/public/videos/refresh` returns HTTP 202 with only
`Telegram synchronization queued.` and no job ID or result counts. However,
the authenticated Afterlight web application exposes a synchronous flow.
Using its **Sync videos from Telegram** action calls
`POST /api/v1/sync/telegram` and returns HTTP 200 with:
`scanned`, `synchronized`, `created`, `updated`, `skipped`, and `deleted`.
The observed response was `scanned: 170`, `synchronized: 170`, `created: 0`,
`updated: 170`, `skipped: 0`, and `deleted: 0`.

**Backend inspection result:**

The backend source is now available at
`/Users/techqesb/Dev/telegram-video-library`. Its authenticated web action
calls `POST /api/v1/sync/telegram`, which invokes
`TelegramSyncService::synchronize()` synchronously and returns
`scanned`, `synchronized`, `created`, `updated`, `skipped`, and `deleted`.
The Android public endpoint now runs the same synchronous service and returns
the result fields directly. This is intentionally synchronous for the small
private deployment; the existing one-request-per-minute throttle remains in
place.

The parser and sync service do support multiple supported links in one
Telegram message. Each link receives a deterministic source ID composed from
the Telegram message ID and a URL hash.

**Remaining TODOs:**

1. Compare every expected Telegram post against the configured primary
   channel, `link` topic, publication status, and supported URL format.
2. Add an end-to-end test: post a supported YouTube link and a supported MP4
   in the configured topic, request sync, then assert the expected catalogue
   count and video IDs are present in the public API and TV UI.

Playback preferences are saved on the TV and default to Malay audio where a
track is available, 0.75× speed, preferred 1080p YouTube quality, and Malay
subtitles. If a Malay subtitle track is unavailable, YouKids leaves captions
off. Direct MP4/Telegram playback selects matching audio/subtitle tracks when
the file exposes them. YouTube's embedded API does not expose audio-track
selection. YouKids also starts a 30-minute screen timer for each app session.
The timer can be changed in Settings to 5, 10, 15, 30, 45, or 60 minutes. The
remaining session time is shared across browsing and multiple videos, and the
current countdown is reset immediately to the newly selected duration. The
always-visible bar also shows the TV's current local time. When the timer
reaches 5, 3, or 1 minute remaining, a centered circular warning shows the
number for five seconds. When the timer expires, the current screen pauses until
the viewer selects the correct answer to a three-choice math question with an
answer greater than 20. The first wrong answer resets the question; a second
wrong answer exits the app. A correct answer offers 5, 10, or 15 extra minutes.
YouTube may also limit requested speeds to
its supported rates and may ignore quality preferences based on the video,
device, or connection.

## APK publishing and TV updates

Build the debug APK with `:app:assembleDebug`; the artifact is
`app/build/outputs/apk/debug/app-debug.apk`. Publish it from Afterlight
Settings with the exact APK version name and a version code greater than the
currently published release. The updater validates the package, version,
file size, and SHA-256 checksum before handing the APK to Android's installer.

The latest published release is YouKids `2.9` (version code `20`), SHA-256
`f832f33206889700a4d31592aeb2e054ddc72f09f40d8fae2ae2c9417b5668d8`.

This personal sideload build uses Android Studio's `~/.android/debug.keystore`.
Preserve that signing key for future updates or Android will not accept them
over the installed app. The first updater-enabled APK must be downloaded and
installed manually. Later updates can be checked from YouKids Settings, but
Android requires allowing installs from YouKids and confirming each update in
the system installer.

## Decisions still required

- Who approves and age-rates links before placing them in the public `link`
  topic?
- Which countries, languages, age bands, and Android TV devices are in the
  first release?
- Are profiles and parental controls required for the first release?
- Is viewing possible without sign-in, and is a subscription or purchase
  model in scope?
- What captions, DRM, and offline requirements must playback support?
- Which privacy, consent, and analytics policies apply to children and
  parents?
