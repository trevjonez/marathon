# Overhaul the HTML report: React 18 + Vite + TypeScript, richer data model, resilient artifact handling

## Summary

This reworks the Marathon HTML report end to end — frontend, the Kotlin
reporter data model, the on-disk layout, and the build pipeline that produces
the bundle. The old report (React 16 + webpack, IE11-era, D3 v3 + moment.js
loaded from CDNs, logcat-only log view rendered off-screen) is replaced with a
React 18 + Vite + TypeScript + Tailwind app that still runs entirely from
`file://` — no dev server, no CDN, no network at view time.

The work grew out of running Marathon at scale against a large Android suite
(hundreds of tests, ~10 emulators, heavy retries) and repeatedly hitting the
report's limits: no way to see which device/OS a failure came from, no
per-attempt history, an unusable log viewer, and a timeline stuck on unpinned
CDN scripts. Every change here is backed by that real-world usage.

I'm opening this as one PR for context, but it's structured as focused commits
and I'm happy to split it into a stacked series (frontend / reporter data model /
build infra / artifact-resilience fixes) if that's easier to review.

## Highlights

**Frontend rewrite (`html-report/`)**
- React 16 + webpack + SCSS → React 18 + Vite 5 + TypeScript 5 + Tailwind 3 + Radix UI.
- Output is a fixed-name IIFE bundle (`app.min.js` / `app.min.css`). IIFE — not
  ES modules — because Chromium blocks `<script type="module">` from a `null`
  origin (`file://`). This is the single most important constraint in the whole
  frontend and is documented at the Vite config.
- Enhanced search / filter / sort on the pool page, all persisted to the URL
  hash so a filtered view is a shareable link.
- Log viewer replaced: pluggable parsers (Android logcat, iOS oslog, generic),
  level/tag/pid filters, regex search, wrapped long lines, window-virtualized
  rendering for large logs. Detects format automatically; user can override.
- Device details surfaced per test and per attempt: serial, model,
  manufacturer, API level, features.
- Full retry/attempt history: each attempt shows its own device, timing,
  screenshot, video, logcat, and stacktrace.

**Timeline**
- Rewritten from imperative D3/SVG to declarative React/HTML. Drops the
  `container.innerHTML = ''` re-render pattern (which was clamping page scroll on
  every filter change), gains real `<button>` semantics on bars (keyboard nav +
  focus rings + a11y), and stable layout across re-renders.
- Dark mode.
- Removed the iframe (a carryover from Marathon's Composer/Juno predecessor) —
  the chart renders inline from a `window.timeline` payload on the index page.
- Dim-not-hide filters for OS major / manufacturer, URL-retained for sharing.
- Bars link to the test detail page.

**Reporter data model (`core`, `report/html-report`)**
- Per-page globals (`window.mainData`, `window.pool`, `window.test`,
  `window.logs`, `window.timeline`, `window.reportGeneratedAt`) instead of the
  old template-string substitution.
- Attempt history flattened from `PoolSummary.retries`, each attempt carrying
  its own device + artifacts.
- Log bodies inlined into the payload (capped) so log pages render under
  `file://` where `fetch()` is blocked; the client prefers a network fetch of
  the full log when the report is served over HTTP.

**Build pipeline**
- The frontend bundle is now produced by Gradle via
  [`com.github.node-gradle.node`](https://github.com/node-gradle/gradle-node-plugin)
  (7.1.0), which provisions Node 24 into the module's build dir. No host Node
  install required, and nothing built is committed to git — the compiled bundle
  is a generated resource packaged into the module jar by `processResources`.
- A Kotlin-driven fixture generator (`:core:generateHtmlReportFixture`) runs the
  real `HtmlSummaryReporter` + `TimelineSummaryProvider` to emit a portable,
  static preview into `core/build/fixtures/`. The preview validates production
  code paths rather than a throwaway TS reimplementation, and doubles as the
  target for Playwright smoke checks.

## Artifact-resilience fixes

Running at scale surfaced several ways a captured artifact can be present on
disk but unusable, each of which the old report happily linked to and rendered
as a broken tile:

- **Zero-frame screenshot GIFs.** `ScreenCapturer` opens the GIF eagerly; a test
  that captures no frames leaves a 1-byte file (just the `0x3B` trailer). Now
  deleted on cleanup so the reporter's existing `exists()` gate omits it.
- **`moov`-less videos.** `adb screenrecord` killed before finalizing writes an
  mp4 with `ftyp` + `mdat` but no `moov` — browsers refuse to decode it. The
  reporter now scans the header and omits such files from the emitted refs.
- **`#` in artifact filenames.** `Test.toTestName()`'s `Class#method` separator
  was surviving `escape()` into filenames; Chromium's `file://` loader returns
  200-with-0-bytes on URL-encoded `#` paths for `<video>`/`<img>`. `#` now
  collapses to `-` in the escape allow-list. (No downstream parses filenames for
  the class/method split — allure/junit/html reconstruct it from the `Test`
  model.)
- **Self-contained `html/` subtree.** Artifact directories (`logs`,
  `device-logs`, `video`, `screenshot`) moved under `html/` so an archive tool
  that grabs only the `html/` tree gets every asset the report links to, instead
  of `../../../../` refs escaping the archived root and 404ing. **This is a
  breaking change to the on-disk output layout** (see below).
- Video element uses `<source type="video/mp4">` + `preload="none"` to sidestep
  two Chromium `file://` quirks (range-request 0-byte reads on `preload=metadata`;
  incremental type-sniffing stalling on a bare `src`).

## Breaking changes

1. **On-disk artifact layout.** `FileType.LOG` / `DEVICE_LOG` / `VIDEO` /
   `SCREENSHOT*` now write under `html/` (e.g. `<output>/html/screenshot/…`
   rather than `<output>/screenshot/…`). `TEST_RESULT`, `DEVICE_INFO`,
   `XCTESTRUN`, `BILL`, `TRACING` are unchanged (the report doesn't link them).
   Allure output (`<output>/allure-results/`) is unaffected — it records
   absolute paths and its one relative use targets `TRACING`, which didn't move.
   Downstream tooling that hardcoded `<output>/logs/…` or `<output>/screenshot/…`
   must switch to the `html/`-prefixed paths.
2. **Toolchain.** JVM target raised to 21; Gradle wrapper on 9.6.1;
   `foojay-resolver-convention` provisions the JDK toolchain. `kotlinx-coroutines`
   bumped to 1.11.0.
3. **Node 24** required to build the frontend — but only when building; the
   node-gradle plugin downloads it, so no host install and no impact on
   consumers who don't touch the JS source.

## Also included

- **`buildSrc/Versions.kt` → `gradle/libs.versions.toml` version catalog**, with
  small `libs` lookup helpers for buildSrc precompiled scripts, and the project
  version moved to root `gradle.properties`. Sizable mechanical diff; happy to
  split into its own PR if you'd rather review the report work in isolation.
- Gradle 9 migration fixes across modules (`task<T>` → `tasks.register<T>`,
  `withConvention` → `java.srcDirs`, etc.) — required for the wrapper bump.
- **`.github/workflows/fork-release.yml`** — a CLI-only release workflow gated to
  non-upstream repos (`github.repository != 'MarathonLabs/marathon'`), so it's a
  no-op on the canonical repo. Included deliberately: it gives external
  contributors a one-command way to cut a CLI zip from a fork for real-world
  testing (exactly the loop that produced this PR), and a versioned-release path
  is something the project will want regardless. Trivial to drop if unwanted.
- Default version bumped to `0.11.0` to reflect the report generation being a
  new minor. Maintainer's call — adjust or revert to taste.

## Merge strategy

**Recommend squash merge.** The branch history includes a screenrecord
shutdown-race experiment (wait-for-process-exit + file-size-stability before
pulling) that was later reverted — real-world data showed the truncated mp4s
came from hard-killed `screenrecord` processes that never received the SIGINT,
so no amount of waiting in the pull path could recover them (the reporter's
`moov`-atom filter is the honest mitigation). The commits are on the branch for
narrative honesty; a squash collapses that dead end out of `main`.

## Testing

- `:core:generateHtmlReportFixture` emits a full static report; verified under
  both `file://` and an HTTP host.
- Existing `FileManagerTest` / `ScreenRecorderTestRunListenerTest` updated and
  green; new unit tests for the logcat parser.
- Exercised against a real large-scale Android suite over many runs: retries,
  failures, skips, incompletes, screenshots, videos, multi-device pools, and the
  degenerate artifact cases listed above.

## Screenshots

_(attach in the GUI — suggested set:)_
- Index: totals + pool cards + timeline (light & dark)
- Pool page: filter/sort bar + virtualized test list
- Test page: attempt history with per-attempt device + screenshot + video
- Log viewer: level/tag filters + search
- Timeline: OS/manufacturer dim filters, dark mode

## Notes for reviewers

- `file://` compatibility is a hard requirement and constrains several choices
  (IIFE bundle, inlined log bodies, `<source>` tag, no fetch at view time). The
  reasoning is in code comments at each site.
- The frontend lives at repo root under `html-report/`; the Kotlin side that
  consumes the bundle is `report/html-report` + `core`'s `HtmlSummaryReporter`.
- Commit history is intentionally granular — reviewing commit-by-commit reads as
  a narrative (overhaul → data-model → each real-world fix).
