# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.1] – 2026-09-22

Build pipeline only. No application code changed, so the APK is byte-for-byte equivalent
to 2.0.0 and `versionCode` stays at `2` — this is not an in-place upgrade for devices.

### Fixed

- `ci.yml` used `branches: ['*']` on both `push` and `pull_request`. GitHub's glob `*`
  does not match `/`, so every branch with a prefix (`fix/...`, `feat/...`, `chore/...`)
  pushed without ever triggering CI — only `main` and other slashless branches ran.
  Restored to `'**'`, matching the other three workflows.

### Changed

- Debug APK assembly is now manual-only. `ci.yml` gained a `workflow_dispatch` trigger,
  and the `assemble` job carries `if: github.event_name == 'workflow_dispatch'`, so
  push and PR runs stop at the verify job instead of building an artifact nobody
  downloads.
- All pinned actions upgraded to their latest release across all four workflows, each
  SHA resolved through the GitHub API and re-verified against its tag:
  `actions/checkout` v4.4.0 → v7.0.1, `actions/setup-java` v4.9.1 → v6.0.1,
  `gradle/actions/setup-gradle` and `gradle/actions/dependency-submission` v3.5.0 →
  v6.3.0, `actions/upload-artifact` v4.6.2 → v7.0.1, `anchore/sbom-action` v0.24.0 →
  v0.24.2, `softprops/action-gh-release` v2.6.2 → v3.0.3.

## [2.0.0] – 2026-09-22

Supply-chain release. Adds dependency locking, SBOM generation, and vulnerability,
secret and workflow scanning on top of the 1.0.x rewrite. No application code or user
visible behavior changed.

### Added

- `supply-chain.yml`: five jobs covering lockfile integrity, CycloneDX SBOM generation
  (`anchore/sbom-action`) with an osv-scanner dependency scan, GitHub dependency graph
  submission, gitleaks secret scanning over full history, and a zizmor workflow audit.
  Runs on push, PR, a daily cron, and manual dispatch.
- Gradle dependency locking under `LockMode.STRICT` (`app/gradle.lockfile`,
  `settings-gradle.lockfile`). Before this, the version catalog pinned 18 direct
  dependencies while 88 resolved artifacts went unrecorded — an SBOM would have
  described a build that never happened.
- `relock.yml`: regenerates the lockfile on Dependabot PRs and pushes it back to the PR
  branch, since Dependabot does not understand Gradle lockfiles and every one of its PRs
  would otherwise fail CI on a stale lock.
- `.gitleaksignore` for known-safe historical matches.
- Expanded `SECURITY.md` (disclosure process, threat model, supply-chain posture) and
  README documentation.

### Security

- osv-scanner blocks the build on any package carrying a `MAL-` advisory; other
  vulnerabilities are reported to the job summary without failing.
- Release signing job disables the Gradle cache, so a branch-writable cache cannot seed
  the job that decodes the keystore.
- Both scanner binaries (osv-scanner, gitleaks) are pinned by version and verified by
  SHA256 before execution.

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
