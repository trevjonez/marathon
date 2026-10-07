# HTML Report Overhaul — Working Plan

Living document. Update as work progresses. Cross-session state lives here.

Related files:
- Kotlin reporter: `core/src/main/kotlin/com/malinskiy/marathon/report/html/HtmlSummaryReporter.kt`
- Kotlin DTOs: `report/html-report/src/main/kotlin/com/malinskiy/marathon/report/*.kt`
- Bundled JS/CSS output (checked-in): `report/html-report/src/main/resources/html-report/{app.min.js,app.min.css,index.html,log-container.html,log-entry.html}`
- JS source (rewrite target): `html-report/`
- Timeline reporter: `core/src/main/kotlin/com/malinskiy/marathon/report/timeline/TimelineReporter.kt`
- Timeline assets: `report/execution-timeline/src/main/resources/timeline/{index.html,chart.js,chart.css,iframeResizer.contentWindow.min.js}`
- Reporter wiring: `core/src/main/kotlin/com/malinskiy/marathon/analytics/TrackerFactory.kt`
- Data source of truth: `core/src/main/kotlin/com/malinskiy/marathon/analytics/internal/sub/{ExecutionReport,PoolSummary,Summary}.kt`

## Goals

1. Modernize the HTML report (React 16 → 18 + TS, Vite, drop dead deps).
2. Preserve `file://` compatibility. No CDN loads.
3. Preserve on-disk layout (per-pool / per-test / per-log HTML files).
4. Greatly enhanced search / filter / sort.
5. Expose device details (serial, model, manufacturer, OS version incl `api34`, features) on every test row and detail page.
6. Expose retry / attempt history — per-attempt device, timing, screenshot, video, logcat, stacktrace.
7. URL-hash-encoded UI state for deep-linkable filter / sort / open-panel views.
8. Drop IE11. Modern evergreen browsers only.
9. Migrate JS to TypeScript.

## Constraints (locked)

- Must work under `file://` — no fetch cross-origin assumptions, no runtime CDN.
- Keep the four-page emission layout:
  - `html/index.html`
  - `html/pools/<pool>.html`
  - `html/pools/<pool>/<device>/<test>.html`
  - `html/pools/<pool>/<device>/logs/<test>.html`
- Timeline stays as `html/timeline/*` (iframe-embedded from home).
- Backward-compat is NOT required for `window.*` JSON payload keys — nothing external reads them (see hanging Q2). Confirm before renames.

## Current data flow

```
Test runners → Track events
   ↓
ExecutionReportGenerator (core/analytics/internal/sub)
   ↓
ExecutionReport { device*Events, testEvents } → lazy .summary → Summary{ List<PoolSummary> }
   ↓
TrackerFactory.createExecutionReportGenerator wires reporters:
   • TimelineReporter          → html/timeline/*
   • HtmlSummaryReporter       → html/*
   • (plus JUnit, RawJson, TestJson, Allure, DeviceInfo, Stdout, Billing)
```

`HtmlSummaryReporter.generate` reads one `index.html` template, string-replaces `${relative_path}`, `${data_json}`, `${log}`, `${date}`; emits one HTML file per view. Data reaches JS via `window.mainData` / `window.pool` / `window.test` / `window.logs`. `App.js` sniffs which global is set, mounts matching component.

`PoolSummary.retries: Map<TestResult, List<TestEvent>>` already threads prior attempts through; currently unused by the HTML reporter.

## Rot inventory (why overhaul)

### JS side (`html-report/`)
- React 16.8. `ReactDOM.render` (removed in 18). `componentWillMount` deprecated.
- Webpack 5 + `webpack-cli` 4 mismatch. No source maps. No content hashing.
- Two build pipelines (postcss-cli for SCSS + webpack for JS). SCSS pipeline uses deprecated `precss`, `postcss-sass-colors`.
- Compiled `app.min.js` + `app.min.css` checked into `report/html-report/src/main/resources/html-report/`. No automated regen.
- Dead / stale deps: `elasticlunr` (unmaintained since 2017), `randomcolor` (dormant since 2020), `react-loading` (unmaintained), `iframe-resizer-react` v1 (v5 is GPL-licensed), `prop-types`, `react-player` (~200 KB for what `<video>` does natively for local files).
- No tests, no TypeScript, no lint, browserslist targets IE11.

### Kotlin side
- `HtmlDevice.@SerializedName("isTable")` — typo. Should be `is_tablet`.
- `HtmlFullTest.id` default `"$packageName$className$name"` — no separators, collision-prone.
- `HtmlSummaryReporter.receive{Screenshot,Video,Log}Path` hard-code `../../../..` — coupled to layout.
- `safePathLength()` truncates test ids to 128 chars silently — collision risk on long parameterized tests.
- `PoolSummary.flaky` hard-coded 0, but `HtmlIndex.totalFlaky` displayed.
- `PoolSummary.retries` computed but never surfaced in HTML.
- Duration math uses needless `Double` conversion.
- Stacktrace bolted onto page via `${log}` template hack in bottom of body — should be a data field rendered by React.
- No test coverage on reporter.

### Timeline (`report/execution-timeline/`)
- D3 v3 (2016), loaded from `d3js.org` CDN.
- moment.js loaded from `momentjs.com` CDN (offline reports break; lib in maintenance mode).
- 567-line imperative D3 chart, hard to test.
- Uses `iframeResizer.contentWindow.min.js` v3; paired with `iframe-resizer-react` v1 on the other side (v5 is GPL split).

### UX / functional gaps
- No dark mode.
- No URL-encoded state → no deep-linkable filter views.
- No sort / column / grouping controls on test list.
- Stacktrace displayed at page bottom (via `${log}`), not attached to the failing test.
- All videos rendered inline at once → heavy pages when many failures.
- No per-device grouping / filter (color pill only).
- Log viewer parses only android logcat via `arr[3].split("/")` (crashes on other formats); lines longer than viewport spill off-screen; no filter, no search, no virtualization, no wrap toggle; empty-log state shows an infinite red bubble spinner.
- Search: elasticlunr full-text or `field:value`; no boolean, no regex, no combined filter chips.
- No keyboard nav, no a11y roles.
- Timeline iframe fixed 1200 px width.

## Target architecture

### Frontend

- **Vite 5 + React 18 + TypeScript 5**.
- **Styling**: Tailwind 3 + Radix UI primitives (dropdowns, dialogs, tabs, lightbox). `dark:` prefix for theming.
- **Routing**: `react-router-dom` v6 `HashRouter` inside each emitted HTML file. Cross-page nav still uses on-disk `href`s to the neighbor HTML files. In-page state (search / filters / sort / open panel / active attempt) encoded in URL hash for deep-linkability.
- **Search**: `minisearch` (elasticlunr successor, actively maintained, ~6 KB) supporting boolean, prefix, fuzzy.
- **Virtualization**: `@tanstack/react-virtual` on the pool test list and logcat viewer (10k+ rows target).
- **Icons / utils**: `clsx` (replaces `classnames`). Drop `randomcolor` — deterministic hash-to-hue helper (stable device colors across runs).
- **Video**: `<video controls>` unless Q5 says otherwise. Lazy-mount one at a time.
- **Iframe sizing**: 20-line `postMessage` + `ResizeObserver` handshake, drop `iframe-resizer` dep both sides. Also carries theme + click-through.
- **Dark mode**: `prefers-color-scheme` + toggle persisted to `localStorage`.
- **A11y**: keyboard shortcuts (`/` focus search, `j/k` navigate, `esc` clear), focus rings, ARIA roles on interactive lists.

### Backend (Kotlin)

- Extend DTOs in `report/html-report` with device details + per-attempt history.
- Populate from `PoolSummary.retries` in `HtmlSummaryReporter`.
- Bump `report_schema_version` to 2 on the top-level index payload.

## Data model changes

Snake_case JSON keys (via `@SerializedName`) — Kotlin side unchanged case.

### `HtmlDevice` (extend)
- Fix `isTable` → JSON key `is_tablet`.
- Add: `model_name`, `manufacturer`, `os_version` (raw string, e.g. `"34"` for Android, `"17.0"` for iOS), `os_major` (`Int?`), `network_state` (String), `features` (`List<String>` — SCREENSHOT, VIDEO, …).
- Keep: `serial`, `api_level` (already there — same as `os_major`? decide, don't duplicate).

### `HtmlAttempt` (new)
Full artifact set per attempt, since attempts can run on different devices.
```kotlin
data class HtmlAttempt(
    @SerializedName("attempt_index") val attemptIndex: Int,   // 0 = first run
    @SerializedName("final") val final: Boolean,               // last attempt counted in summary
    @SerializedName("status") val status: Status,
    @SerializedName("start_time_ms") val startTimeMs: Long,
    @SerializedName("end_time_ms") val endTimeMs: Long,
    @SerializedName("duration_millis") val durationMillis: Long,
    @SerializedName("batch_id") val batchId: String,
    @SerializedName("device") val device: HtmlDevice,
    @SerializedName("stacktrace") val stacktrace: String?,
    @SerializedName("screenshot") val screenshot: String,
    @SerializedName("videos") val videos: List<String>,
    @SerializedName("log_file") val logFile: String,
)
```

### `HtmlFullTest` (extend)
- Add: `attempts: List<HtmlAttempt>`, `attempt_count: Int`, `is_flaky: Boolean`, `distinct_devices: List<HtmlDevice>` (dedup by serial).
- Keep top-level `status`/`stacktrace`/`screenshot`/`videos`/`log_file`/`deviceId` as **final attempt** — back-compat and cheap first-paint.
- Add: `start_time_ms`, `end_time_ms`, `batch_id`, `device: HtmlDevice`.

### `HtmlShortTest` (pool list rows) (extend)
- Add: `start_time_ms`, `end_time_ms`, `batch_id`, `attempt_count`, `is_flaky`, `devices: List<String>` (serials across attempts), `os_versions: List<String>`, `device: HtmlDevice` (final attempt device).

### `HtmlPoolSummary` (extend)
- Add: `flaky_count`, `start_time_ms`, `end_time_ms`.

### `HtmlIndex` (extend)
- Add: `generated_at_ms`, `report_schema_version: Int = 2`.
- Fix `totalFlaky` computation (currently pulls `pool.flaky` which is hard-coded 0 upstream).

### `HtmlTestLogDetails` (restructure)
Support per-attempt log switching:
```kotlin
data class HtmlTestLogDetails(
    @SerializedName("pool_id") val poolId: String,
    @SerializedName("test_id") val testId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("attempts") val attempts: List<HtmlLogAttempt>,
)
data class HtmlLogAttempt(
    @SerializedName("attempt_index") val attemptIndex: Int,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device") val device: HtmlDevice,
    @SerializedName("status") val status: Status,
    @SerializedName("log_path") val logPath: String,
)
```
URL hash controls active attempt: `logs/<test>.html#/attempt/2`.

## Reporter changes

`HtmlSummaryReporter`:
- Populate new DTO fields.
- Build attempt list per test from `PoolSummary.retries[finalResult]` (prior `TestEvent`s) + the final `TestResult`. Sort by `startTime` ascending. Mark last as `final=true`.
- Compute `is_flaky` per test: `attempts.size > 1 && attempts.any { it.status == Passed } && attempts.any { it.status == Failed }`. Also propagate to pool `flaky_count` and index `totalFlaky`.
- Per-attempt artifact resolution: extend `receiveScreenshotPath` / `receiveVideoPaths` / `receiveLogPath` to accept an explicit `TestResult` (currently uses only the final one). Files are already keyed on `(pool, device, test, batchId)` by `FileManager.createFile`, so per-attempt paths resolve correctly.
- Drop the `${log}` template hack from `index.html` template + `generateLogcatHtml` + `cssClassForLogcatLine`. Rendering moves client-side, driven by `attempts[].stacktrace`. Delete `log-container.html` / `log-entry.html` resource files (or leave orphaned, but prefer deletion for cleanliness).
- Add generation timestamp + schema version to index payload.
- Tests: add unit tests exercising the new attempt-flattening + flaky detection.

## UI feature matrix

### HomePage (`window.mainData`)
- Existing pool cards + counts.
- Add: total flaky, total retried, total distinct devices, total attempts.
- Timeline iframe (existing).

### PoolPage (`window.pool`)
Rewrite:
- **Filter chips** (multi-select, combinable, URL-encoded):
  - status (passed / failed / ignored)
  - device (list of serials in this pool)
  - os_major (list, e.g. `api 33`, `api 34`, `iOS 17`)
  - manufacturer
  - has-retries
  - flaky-only
  - has-screenshot / has-video / has-log
  - duration range (slider)
- **Free-text search** via minisearch on package / class / method / stacktrace excerpt.
- **Sort**: name / duration / start time / status / attempt count / device.
- **Grouping toggle**: none / by device / by OS major / by class / by status / by batch.
- **Row view toggle**: dense list vs card.
- **Row rendering**:
  - status label, test name, class, package
  - device badge with color from deterministic hash-of-serial
  - OS chip (`api 34`, `iOS 17.0`, etc.)
  - duration
  - retry pip count (with per-attempt status dots on hover)
  - flaky badge if applicable
- **Virtualized** (target: usable at 10k rows).
- **Counts** under each filter chip update live.

### TestPage (`window.test`)
Rewrite:
- Header: test name, class, package, final status, total duration, batch id.
- **Attempt strip** at top: one card per attempt, colored by status, chronological.
  - Click / keyboard to expand.
  - Failed attempts default-expanded, passed default-collapsed.
  - Expanded panel per attempt:
    - device: serial, model, manufacturer, OS `api34`, features
    - start time (absolute + relative), duration, batch id
    - stacktrace with copy button
    - screenshot with lightbox
    - inline `<video>` (lazy)
    - log link (opens logs page pinned to that attempt)
- Distinct devices summary at the top for quick scan.

### LogsPage (`window.logs`)
Full rewrite. Current implementation is broken beyond logcat: assumes fixed column split (`arr[3].split("/")`), lines wider than viewport spill off-screen, no filter, no search, no virtualization, ~110 lines total.

Unified log viewer requirements:
- **Attempt selector dropdown** (from `attempts` list). URL hash `#/attempt/N` (deep-link).
- **Client-side pluggable parser** — no Kotlin-side log format changes. Log files stay raw text (what vendors already emit).
  - Detector runs once on load. Heuristic order:
    1. Android logcat (`MM-DD HH:MM:SS.SSS PID TID LEVEL TAG: MSG`)
    2. iOS `os_log` / xcodebuild output
    3. Generic fallback (no per-column parse; whole line is `message`, timestamp/level unknown)
  - Detected format shown in header; user can force override via dropdown.
  - Each parser returns `LogEntry { timestampMs?, level?, tag?, pid?, tid?, message, raw }`.
- **Row rendering**:
  - Columns configurable per format; empty columns collapse when parser has no data for them (generic mode → single wide `message` column).
  - Long lines wrap by default (fix the current off-screen bug). Toggle: wrap / truncate / horizontal scroll.
  - Ellipsis-truncate with hover reveal + copy-line button.
  - Monospace font, per-level color band on the row (verbose/debug/info/warn/error/assert).
  - Sticky column headers.
- **Filtering** (all combinable, URL-encoded so filters persist in shared links):
  - level multi-select
  - tag multi-select (populated from parsed entries)
  - PID multi-select (when parser has PID)
  - free-text search on message (substring + optional regex toggle)
  - time range brackets (drag on a mini-timeline strip above the table)
- **Search & navigation**:
  - `/` focuses search box (keyboard shortcut)
  - `n` / `N` jump next / previous match
  - Match highlighting inline in row
  - Hit-count badge
- **Timestamp jump**: click a timestamp to anchor URL hash to that line (`#/line/1234`); loading a hash-anchored URL scrolls into view.
- **Virtualized rows** via `@tanstack/react-virtual` — target usable at 100k+ lines.
- **Follow-tail toggle** (bottom of list stays pinned as new lines load — not currently applicable since logs are static, but leave the affordance for future streaming).
- **Copy actions**: copy line, copy visible-filtered subset, copy selection range.
- **Empty-log** state: friendly placeholder instead of the current infinite red loading spinner (`react-loading` type=bubbles) that shows when the log is empty/404.
- **Dark mode** compatible color band per level.

Parser module structure (TS):
```
src/logs/
  parsers/
    logcat.ts       // android
    oslog.ts        // ios
    generic.ts      // fallback
    index.ts        // detect() + registry
  types.ts          // LogEntry, LogParser interface
```

No new Kotlin work for logs beyond what's already in Phase 1 (per-attempt `HtmlLogAttempt.logPath`). The log file itself stays raw text; UI does all parsing.

### Cross-cutting
- Dark mode toggle in header.
- Keyboard shortcuts.
- ARIA roles on lists / dialogs.
- Focus rings.

## Build integration

- Vite build produces `app.min.js` + `app.min.css` under `html-report/dist/`.
- Gradle task (via `node-gradle` plugin) `bundleHtmlReport`:
  - Depends on `npm ci && npm run build`.
  - Copies output into `report/html-report/src/main/resources/html-report/`.
  - Input-fingerprints `html-report/src/**`, `html-report/package.json`, `html-report/package-lock.json`, `html-report/vite.config.ts`, `html-report/tsconfig.json`.
- Wire `bundleHtmlReport` into `:report:html-report:processResources`.
- **Committed bundle**: keep, with CI parity check via `git diff --exit-code` after rebuild. (Pending Q1 confirmation.)

## Phases

### Phase 1 — Kotlin DTOs + reporter (foundation)
- Extend / add DTOs in `report/html-report/src/main/kotlin/…/report/`.
- Update `HtmlSummaryReporter` to populate them, flatten attempts, compute flaky.
- Fix `HtmlDevice` typo.
- Fix `HtmlIndex.totalFlaky` computation.
- Drop `${log}` template hack.
- Add reporter unit tests (attempts / flaky / device details).
- **No frontend work yet** — the old bundle still consumes the extra fields as extras / ignores them. Ships independently.

### Phase 2 — Frontend rewrite
- Bootstrap Vite + TS + React 18 in `html-report/`.
- Router + URL-state.
- Search / filter / sort / group.
- Attempt strip on TestPage.
- Per-attempt log viewer.
- Dark mode + a11y.
- Wire Gradle build integration.

### Phase 3 — Timeline modernization (in scope this pass)

Current state:
- `TimelineReporter` emits `html/timeline/index.html` with `${dataset}` string-replaced by JSON.
- HTML pulls `d3.v3.min.js` from `d3js.org` **and** `moment.js` from `momentjs.com` (both CDN loads — offline `file://` reports currently break).
- `chart.js` is 567 lines of imperative D3 v3 + moment.
- `iframeResizer.contentWindow.min.js` v3 shipped inside timeline resources.
- `HomePage` in main app embeds via `iframe-resizer-react` v1 (v5 is GPL split).

Data pipeline (unchanged):
- `TimelineSummaryProvider.generate(ExecutionReport): TimelineExecutionResult`
- `TimelineExecutionResult { passedTests, failedTests, ignoredTests, executionStats, measures: List<Measure> }`
- `Measure { measure /* device serial */, executionStats, data: List<Data> }`
- `Data { testName, metricType, startDate, endDate, expectedValue, variance }`
- `MetricType { FAILURE, PASSED, IGNORED, INCOMPLETE, ASSUMPTION_FAILURE, DEVICE_PROVIDER_INIT, DEVICE_PREPARE }`

Kotlin work:
- Extend `Measure` with device details (`model`, `manufacturer`, `os_version`, `os_major`) so tooltips can show `api34` etc. — matches main report enrichment.
- Extend `Data` with `batch_id` and (optionally) `attempt_index` so timeline can visualize retries as separate bars stacked per device row.
- Add `report_schema_version` at the top of `TimelineExecutionResult`.
- Fix the copy-paste `logger = MarathonLogging.logger(TimelineSummaryProvider::class.java.simpleName)` in `TimelineReporter.kt:38` (should be `TimelineReporter`).

Frontend work:
- Fold the timeline into the same Vite build as the main React app. Emit a second bundled entry (`timeline.min.js` / `timeline.min.css`) served from `report/execution-timeline/src/main/resources/timeline/`.
- Rewrite `chart.js` in TypeScript. Use `d3-scale` + `d3-axis` + `d3-selection` + `d3-time` as ES modules (self-hosted, tree-shaken; no `d3js.org` CDN, no monolithic bundle). D3 v7.
- Drop moment.js. Use `Intl.DateTimeFormat` for absolute times, small helper for `HH:mm:ss.SSS` durations.
- Drop `iframeResizer.contentWindow.min.js`. Replace with a ~20-line `postMessage` handshake:
  - child posts `{type: 'resize', height}` on `ResizeObserver` fires
  - parent listens, sets iframe `height` inline
- Drop `iframe-resizer-react` dep from main app; use plain `<iframe>` + a `useIframeResizer` hook.
- Responsive chart width (currently hard-coded 1200 px in `chart.js`). Use container width via `ResizeObserver`.
- Hover tooltips show device details (model + `api34`), test name, duration, status, batch id, attempt index.
- Click a bar → deep-link to that test in the parent frame via `postMessage({type: 'openTest', poolId, deviceId, filename})`. Parent navigates.
- Dark mode: parent posts current theme on load / theme change; child styles accordingly.

### Phase 4 — Documentation & polish
- Update `docs/report.md` (if exists) or add one.
- Screenshot the new report for README.
- Removal of dead files (`layout/log-container.html`, `layout/log-entry.html`, etc.) if they end up orphaned.

## Hanging questions

Answer inline as we hit them. Mark with ✅ when resolved.

1. ✅ **Bundle in git**: committed, with CI parity check via `git diff --exit-code` after rebuild.
2. ✅ **No external consumers.** Grepped entire repo (kt / js / ts / md / yml / json / gradle) for `window.mainData|pool|test|logs` and Html DTO names — only `HtmlSummaryReporter.kt` (writer) and `html-report/src/**` (reader) reference them. No plugin, CLI, sample, vendor, or docs pins the shape. **Zero existing tests** on the HTML report path (no `HtmlSummaryReporter*Test.kt`, no timeline `.kt` tests, no JS tests). Rename freely; add reporter tests in Phase 1 to establish a regression net.
3. ✅ **Tailwind 3 + Radix UI primitives.** Utility-first with design tokens; `dark:` prefix for theming; Radix for a11y-correct dropdowns/dialogs/tabs/lightbox.
4. ✅ **Timeline overhauled this pass.** Fixes offline-`file://` break (CDN loads for d3 + momentjs), aligns with data plumbing changes, kills `iframe-resizer` dep both sides.
5. ✅ **`<video controls>` native.** Drop `react-player` (~200 KB). All artifacts local; browsers handle mp4/webm natively. Lazy-mount one at a time on TestPage.
6. ✅ **iframe-resizer replaced** with hand-rolled `postMessage` + `ResizeObserver` handshake (both sides). Dep dropped in main app and timeline resources. Also carries theme + click-through events.
7. ✅ **Unified log viewer + pluggable parser.** Client-side only, no Kotlin log path changes. Parsers: android logcat, iOS os_log, generic fallback. Format auto-detected + user-overridable. Long lines wrap, virtualized, level/tag/PID/free-text filter, timestamp jump, regex toggle, hit count, copy actions. See `LogsPage` section for detail.
8. ✅ **Delete all orphans** during frontend rewrite. Targets: `html-report/layout/{index,log-container,log-entry}.html`, `html-report/webpack.config.{dev,prod}.js`, `html-report/.babelrc`, `html-report/postcss.config.js`, `html-report/styles/*.scss`, `html-report/src/**/*.js` (replaced by TS equivalents), stale `HtmlSummaryReporter.generateLogcatHtml` + `cssClassForLogcatLine` Kotlin helpers, `${log}` template hook in index.html. Also drop resource files `report/html-report/src/main/resources/html-report/{log-container,log-entry}.html` since the `${log}` template hack goes away.
9. ✅ **No back-compat.** Static per-run report; nothing pins the JSON shape across runs. Rename freely, restructure freely. Bump `report_schema_version` to 2 on emit as a courtesy marker.
10. ✅ **Hand-rolled** on Radix Dialog. Screenshots are single-image; no need for gallery lib. Reuses the Radix dep already pulled in for other primitives.
11. ✅ **Node 24 LTS** (LTS since 2025-10-28, supported through 2028-04). Pin via `.nvmrc` and `engines` in `package.json`. Vite 5/6 compatible.
12. ✅ **Vitest + React Testing Library + jsdom.** Native ESM, drop-in Jest API, near-zero config with Vite.

## Progress log

- 2026-07-23: Plan drafted. No code changes yet.
- 2026-07-23: All hanging questions resolved.
  - Q1: bundle stays in git + CI parity check.
  - Q2: no external consumers; zero existing tests on HTML report path — rename freely, add reporter tests in Phase 1.
  - Q3: Tailwind 3 + Radix UI.
  - Q4: timeline overhaul in scope this pass — folded into Phase 3 with Kotlin + Vite plan.
  - Q5: drop `react-player`, use `<video controls>`.
  - Q6: hand-rolled `postMessage` + `ResizeObserver`, drop `iframe-resizer` both sides.
  - Q7: unified client-side pluggable log viewer with android / iOS / generic parsers, virtualized, filter+search+regex+timestamp jump, wrap toggle — no Kotlin log path changes.
  - Q8: delete all orphan files during rewrite.
  - Q9: no JSON back-compat needed; static per-run report; bump schema version.
  - Q10: hand-rolled lightbox on Radix Dialog.
  - Q11: Node 24 LTS.
  - Q12: Vitest + React Testing Library + jsdom.
- 2026-07-23: **Fixture generator moved to Kotlin.** Real reporter code now generates the preview. `core/src/test/kotlin/…/report/html/HtmlReportFixtureGenerator.kt` synthesizes an `ExecutionReport` (250 tests × 20 devices per platform, retries/flakes/fails/skips), writes fake logcat / os_log text to the paths marathon uses at runtime, then invokes real `HtmlSummaryReporter` + `TimelineReporter`. Output lands under `core/build/fixtures/{android,ios}/html/`. Gradle task `:core:generateHtmlReportFixture` (group `html-report`) drives it and depends on `:report:html-report:syncHtmlReportBundles` so the JS/CSS on the classpath is fresh. TS-side fixture builder + all `src/dev/*` helpers deleted; Playwright scripts now consume the Kotlin-emitted tree. Bug caught by the round-trip: PoolPage row href dropped the pool segment.
- 2026-07-23: **`log_body` inlining in reporter.** Kotlin `HtmlSummaryReporter.readInlineLogBody` reads log file bytes at emit time and embeds them into `HtmlLogAttempt.log_body` (1 MiB cap per attempt with truncation marker). LogsPage now prefers the inline body over `fetch(log_path)`, so `file://`-hosted reports work on Chromium (which blocks fetch under a `null` origin). — Original fixture generator plan.
- 2026-07-23: **Fixture generator (initial TS version, since replaced).** `npm run fixtures` produces `dist/fixtures/{android,ios}/…` — full report tree per platform (2 pools × 10 devices × 125 tests = 250 tests / 20 devices), inlined payloads (log_body inline so `file://` needs no fetch), SVG-data-URL screenshots. Deterministic via seeded PRNG. Sample video wired to `sample.mp4` per test dir; drop `html-report/dev-assets/sample.mp4` into place to enable playback. **Also**: `HtmlLogAttempt.log_body` added (TS side); `LogsPage` prefers inline body over `fetch(log_path)` — fixes `file://` blocked-fetch on Chromium.
- 2026-07-23: **Phase 2 complete.**
  - Frontend: Vite 5 + React 18 + TS 5 + Tailwind 3 + Radix, scaffolded under `html-report/`.
  - Pages: `HomePage`, `PoolPage` (filter chips/search/sort/group/virtualized rows/URL hash state), `TestPage` (attempt strip, device details panel, screenshot lightbox on Radix Dialog, `<video>` playback, stacktrace with copy), `LogsPage` (pluggable parsers logcat/os_log/generic, virtualized, level+tag+PID+free-text+regex filters, attempt selector, wrap toggle).
  - Timeline: standalone Vite entry — D3 v7 modules, no CDN, no moment.js, `postMessage` + `ResizeObserver` handshake with parent frame for size + theme + click-through.
  - Gradle: `bundleHtmlReport` task drives `npm ci` + `vite build` + copy into module resources.
  - CI: bundle parity job (`html-report-bundle-parity`) rebuilds + `git diff --exit-code`.
  - Action versions swept (checkout/setup-java/setup-node/junit-report/gh-release/appleboy-telegram); dead `little-core-labs/get-git-tag` replaced with inline shell; obsolete S3 sync fork replaced with `aws-actions/configure-aws-credentials` + native `aws s3 sync`; runners bumped to `ubuntu-24.04`.
- 2026-07-23: **Gradle 9 migration.** Kotlin 1.9.24→2.1.20, coroutines 1.8.1→1.9.0, Dokka 1.9.10→2.0.0, Detekt 1.23.6→1.23.7; dropped junit-platform-gradle-plugin; added foojay-resolver 1.0.0; jvmTarget 11→21; toolchain wiring in `ProjectExtensions`; Gradle 9-removed APIs replaced (`withConvention`, `task<T>`, `by tasks.registering`); vendor-apple base kts null-safety fix.
- 2026-07-23: **Phase 1 complete.**
  - DTOs extended / added: `HtmlDevice` (fixed `isTable`→`is_tablet`; added model_name, manufacturer, os_version, os_major, network_state, features, api_level), `HtmlAttempt` (new), `HtmlFullTest` (attempts, attempt_count, is_flaky, distinct_devices, device, start/end/batch), `HtmlShortTest` (attempts hints, device, timing, batch), `HtmlPoolSummary` (flaky_count, start/end), `HtmlIndex` (generated_at_ms, report_schema_version=2, fixed totalFlaky), `HtmlTestLogDetails` + new `HtmlLogAttempt` (per-attempt log switching).
  - `HtmlSummaryReporter` rewritten: flattens attempts from `PoolSummary.retries`, computes flaky, per-attempt artifact paths, drops `${log}` template hack + `generateLogcatHtml` + `cssClassForLogcatLine`.
  - Template: dropped `${log}` hook, dropped `log-container.html` / `log-entry.html` resources, retitled `Composer`→`Marathon Report`, replaced Juno branding footer with generic `Generated by Marathon at ${date}`.
  - `TimelineReporter` logger name fix (was pointing at `TimelineSummaryProvider`).
  - Reporter tests added (`HtmlSummaryReporterTest`, 8 cases): schema version, snake_case device fields, retry history, flaky rollup, distinct devices across attempts, single-attempt-not-flaky guard, per-attempt log page shape, verify `${log}` template hook is gone.
  - All `:core:test` green after rerun.
