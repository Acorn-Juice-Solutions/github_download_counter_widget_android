# Download Widget

[![CI](https://github.com/Acorn-Juice-Solutions/github_download_counter_widget_android/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Acorn-Juice-Solutions/github_download_counter_widget_android/actions/workflows/ci.yml)
[![Supply chain](https://github.com/Acorn-Juice-Solutions/github_download_counter_widget_android/actions/workflows/supply-chain.yml/badge.svg?branch=main)](https://github.com/Acorn-Juice-Solutions/github_download_counter_widget_android/actions/workflows/supply-chain.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)](https://developer.android.com/tools/releases/platforms)

Android home-screen widget that shows the running download total for a specific GitHub
release, per widget instance. Auto-refreshes hourly, respects the GitHub rate limit via
ETag/conditional requests, and stores the optional Personal Access Token in
`EncryptedSharedPreferences`.

Listed on [acornjuice.com](https://www.acornjuice.com/products/github-counter/) as
**GitHub Download Counter**. A small utility, maintained to the same standard as
everything else we ship: layered architecture, unit-tested data layer, CI with Spotless
(ktlint) + Detekt + JUnit + Robolectric, supply-chain checks on every push, signed
release workflow, Material 3 + Dynamic Colors, English + Spanish.

> Verified on Pixel Launcher / Android 15+. The asset list is shipped inside the
> `RemoteViews` (`RemoteCollectionItems`, API 31+) rather than through a
> `RemoteViewsService`: a remote adapter made the host apply the tree asynchronously and,
> on these launchers, silently keep the previous view — which froze the counter while the
> list kept updating. See
> [`docs/architecture.md`](docs/architecture.md#why-the-asset-list-is-inlined-and-the-bug-that-forced-it)
> for the full diagnosis; it cost three rounds of wrong fixes and is worth reading before
> touching the render path.

## Screenshots

| Standard widget | Compact widget | Both |
| :---: | :---: | :---: |
| ![Standard](docs/GitHubDownloads_Normal.png) | ![Compact](docs/GitHubDownloadWidget_LayoutSmall.png) | ![Layouts](docs/GitHubDownloadWidget_Layouts.png) |

Demo video: [`docs/GitHubWidget.mov`](docs/GitHubWidget.mov)

## Features

- Two widget flavors (full 3x2 + compact 1x1). Each instance is configured independently.
- Direct hit on `GET /repos/{owner}/{repo}/releases/tags/{tag}` — resolves old tags that
  paginate off `/releases`.
- Sends `If-None-Match` conditional requests: a `304 Not Modified` costs zero rate-limit
  budget and skips the parser entirely.
- Optional GitHub PAT (raises the rate limit from 60/h to 5000/h). Stored encrypted with
  `androidx.security-crypto` (AES-256-GCM values, AES-256-SIV keys).
- Auto-refresh every 60 min via `PeriodicWorkRequest` with `NetworkType.CONNECTED` +
  exponential backoff. Manual refresh from the widget too.
- Explicit `NOT SET UP`, `UPDATING`, `RATE LIMIT`, `TAG NOT FOUND`, `NOT UPDATED`,
  `UPDATED` states — no silent failure modes.
- Localized in English and Spanish.

## Quickstart

Requires JDK 17 + Android SDK 36.

```bash
# Clone
git clone https://github.com/Acorn-Juice-Solutions/github_download_counter_widget_android.git
cd github_download_counter_widget_android

# Build a debug APK (or open in Android Studio Ladybug+)
./gradlew assembleDebug

# Run the same quality gate CI runs
./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug
```

On Windows the wrapper is `gradlew.bat`.

## Configuration

Tap the ⚙ icon on any widget instance to open its Settings screen. Per widget:

| Field | Format | Example |
| :--- | :--- | :--- |
| **API URL** | `https://api.github.com/repos/{owner}/{repo}/releases` | `https://api.github.com/repos/JetBrains/kotlin/releases` |
| **Release tag** | The `tag_name` GitHub returned when the release was cut | `v2.1.0` |

The **GitHub Personal Access Token** field (under Advanced) is shared across all widget
instances and is never written to plain preferences.

## Architecture

See [`docs/architecture.md`](docs/architecture.md) for a diagram and per-package walkthrough.
The short version:

```
widget tap ──ACTION_REFRESH──▶ BaseDownloadWidgetProvider
                                 │ arms RefreshWatchdogWorker, then fetches (8 s cap)
                                 │
onUpdate / hourly ──▶ RefreshScheduler ──▶ RefreshWorker (CoroutineWorker)
                                 │                    │
                                 └─────────┬──────────┘
                                           ▼
                                   ReleaseRepository
                                   │       │      │      │        │
                                   ▼       ▼      ▼      ▼        ▼
                               GitHubApi Config Cache  ETag  SecureToken
                                   │
                                   ▼
                             api.github.com

every render ──▶ WidgetPublisher ──▶ AppWidgetManager.updateAppWidget
```

- **`domain/`** owns the vocabulary (`Asset`, `Release`, `RefreshResult`). Zero Android APIs.
- **`data/remote/`** turns HTTP into `ApiResponse` variants. `OkHttpGitHubApi` is the only
  class in the tree that talks to the network.
- **`data/local/`** persists via `SharedPreferences` / `EncryptedSharedPreferences`.
  Storage-agnostic on purpose so tests inject plain prefs.
- **`data/repo/ReleaseRepository`** is the single choke point translating transport-level
  outcomes into UI-facing `RefreshResult`s.
- **`ui/widget/`** is a thin `AppWidgetProvider`, a pure `WidgetRenderer` and a single
  `WidgetPublisher` choke point for every push to the launcher. The asset rows ride inside
  the `RemoteViews`; `AssetListRemoteViewsService` survives only as the pre-API-31
  fallback.
- **`worker/`** owns WorkManager plumbing: the refresh worker plus the watchdog that
  guarantees the widget can never be stranded on the loading spinner. No reflection —
  provider dispatch is via the `WidgetKind` enum.
- **`di/AppContainer`** wires the graph. Manual DI, lazy, ~40 lines.

## Testing

```bash
./gradlew testDebugUnitTest           # JVM + Robolectric unit tests
./gradlew connectedAndroidTest        # Instrumented (requires emulator/device)
```

117 unit tests covering the data + domain + rendering layers. Coverage enforcement via
Kover is checked in but currently disabled — Kover 0.9 does not yet auto-detect the
AGP 9 debug variant, so its report is consistently empty. `koverVerify` is therefore
not part of the quality gate above, and CI does not run it. Both will be re-enabled
when Kover ships AGP 9 support.

## Supply chain

`supply-chain.yml` runs on every push to `main`, on every pull request, daily at
06:31 UTC and on demand:

- **SBOM** in CycloneDX format (`download-widget.cdx.json`), generated with
  `anchore/sbom-action` from `app/gradle.lockfile` — so it reflects the resolved
  dependency tree, not just what the build files declare.
- **Vulnerability scan** with `osv-scanner` (`osv.json`).
- **Lockfile verification**: the lock has to match the declared dependencies.
- **Secret scanning across the full history**, not only the latest commit.
- **Workflow audit**, with every GitHub Action pinned to a commit SHA instead of a
  moving tag — a rewritten tag is how supply-chain worms reach a build.
- **Dependency graph** submitted to GitHub.

Scan reports are kept as build artifacts for 90 days.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Security

Report vulnerabilities via the process in [`SECURITY.md`](SECURITY.md). Do not open a
public issue for security matters. The SBOM and the scan reports described above are
attached to every run of the supply-chain workflow.

## License

MIT — see [`LICENSE`](LICENSE).
