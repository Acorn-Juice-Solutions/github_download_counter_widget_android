# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.1] – 2026-09-20

Bugfix release for widget refresh, config persistence, and animation quirks discovered
on stock Pixel launchers running Android 15+. No API or behavior contract changes.

### Fixed

- Clipboard whitespace / newlines in the API URL or release tag preferences no longer
  produce `.../tags/<tag>%20` requests → GitHub 404. `WidgetConfigStore` and
  `HybridPreferenceDataStore` now trim on both read and write.
- Refresh button icon (`@android:drawable/stat_notify_sync`) was an AOSP
  `animated-rotate` drawable that spun forever regardless of the widget state.
  Replaced with a static `ic_refresh` vector.
- `MainActivity` opened from the app launcher icon (no `EXTRA_APPWIDGET_ID`) used to
  save preferences under unscoped keys that no widget read. `WidgetConfigStore.get`
  now falls back to unscoped keys when the widget-scoped key is empty.
- Changing the tag or URL in settings did not repaint the widget on some launchers,
  because `updateAppWidget` emitted from `onUpdate` was silently coalesced. Settings
  now trigger the tap-refresh flow (`ACTION_REFRESH`), which ends in
  `partiallyUpdateAppWidget` — a different launcher code path that reliably lands.
- Tapping the refresh button left the widget stuck on the "syncing" state because the
  second `updateAppWidget` (Success) was dropped by the launcher's dedup window. Split
  into two receiver invocations spaced ~3.5 s apart, with the terminal render using
  `partiallyUpdateAppWidget`.
- Fresh-install `onUpdate` used to paint a Loading state that got latched by the same
  dedup. It now runs an inline refresh so the initial paint is the real Success state.
- Reopening the settings screen after changing a value could display the previously
  cached preference text because `androidx.preference` caches `mText` against the
  XML key before we reassign to the widget-scoped key. `SettingsFragment` now
  re-reads the value from the scoped key after key reassignment.

### Changed

- `SINCRONIZANDO` / `SYNCING` badge relabeled to `ACTUALIZANDO` / `UPDATING`.
- Header timestamp format includes seconds (`HH:mm:ss`) so successive refreshes are
  visually distinguishable even when GitHub returns `304 NotModified`.

### Added

- Toast confirmation (`widget_refresh_toast_updated` / `_error`) after each tap
  refresh. Silently suppressed by Android if the user has denied notifications for
  the app.
- 10 new unit tests: 5 in the new `HybridPreferenceDataStoreTest`, 3 extra in
  `WidgetConfigStoreTest` (write-side trim, scoped-vs-unscoped fallback, blank-scoped
  fallthrough). Total: 89 → 99.

## [1.0.0] – 2026-09-19

Portfolio-grade release. Full internal rewrite. No behavior changes visible to end users
who were happy with the pre-1.0 version, but every file changed. Users upgrading in place
must re-enter their GitHub Personal Access Token — pre-1.0 stored it in plain
preferences; those keys are purged automatically on first launch of 1.0.0.

### Added

- Layered architecture (`domain`, `data.remote`, `data.local`, `data.repo`, `ui.widget`,
  `ui.settings`, `worker`, `di`, `util`).
- `EncryptedSharedPreferences`-backed token storage (`SecureTokenStore`), with
  `androidx.security-crypto`.
- Automatic hourly refresh via `PeriodicWorkRequest` (`NetworkType.CONNECTED`,
  exponential backoff).
- `If-None-Match` / ETag conditional requests. `304 Not Modified` costs zero rate-limit
  budget.
- Typed `X-RateLimit-Remaining` / `X-RateLimit-Reset` handling. On a one-shot refresh
  the app schedules a delayed retry at `resetEpochSeconds`.
- Direct `GET /repos/{owner}/{repo}/releases/tags/{tag}` endpoint (resolves tags that
  paginate off `/releases`).
- Material 3 theme + Dynamic Colors on Android 12+.
- Spanish localization (`values-es/strings.xml`).
- `UnconfiguredEmpty` widget state guiding the user to Settings on fresh install.
- `SafeLogger` that redacts `Bearer` / `Authorization` / `token=` substrings before every
  `Log.*` call.
- Full CI pipeline: Spotless (ktlint 1.4) + Detekt + JUnit + Robolectric (89 tests). A
  Kover-based coverage gate is checked in but currently disabled because Kover 0.9 does
  not yet detect the AGP 9 debug variant; the plugin will be re-enabled when upstream
  ships AGP 9 support.
- Release workflow that signs `assembleRelease` with a keystore stored in GitHub secrets
  and publishes the APK to a GitHub Release.
- Dependabot config, PR template, bug / feature issue templates.

### Changed

- Package renamed `com.example.downloadwidget` → `com.acornjuice.downloadwidget`.
- Widget refresh moved out of `AppWidgetProvider` into `RefreshWorker` +
  `ReleaseRepository`.
- `RefreshWorker` no longer instantiates provider classes via reflection — dispatch is
  through the `WidgetKind` enum.
- `AndroidManifest.xml` hardened: `allowBackup="false"`, `usesCleartextTraffic="false"`,
  explicit `data_extraction_rules.xml`, `backup_rules.xml`, `network_security_config.xml`.
- Preferences screen writes the PAT through a `PreferenceDataStore` that routes to the
  encrypted store.

### Removed

- Hardcoded `Acorn-Juice-Solutions/accuvideo-releases` URL and `v1.7.5` tag defaults.
- Unused `androidx.glance:glance` dependency.
- Stray `app/src/main/resources/androidManifest.xml` file.
- Legacy `pref_github_token[_widgetId]` plaintext keys (auto-purged on first launch).

### Security

- PAT is stored in `EncryptedSharedPreferences` (AES-256-GCM values, AES-256-SIV keys).
- `SafeLogger` redacts credential patterns from every log line.
- Manifest disables cloud backup, device transfer, and cleartext traffic.

## [0.x] – Pre-1.0

Working proof-of-concept releases. No formal changelog kept.
