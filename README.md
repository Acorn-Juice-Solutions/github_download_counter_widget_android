# Download Widget

[![CI](https://github.com/Neonexus29/github_download_counter_widget_android/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Neonexus29/github_download_counter_widget_android/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)](https://developer.android.com/tools/releases/platforms)

Android home-screen widget that shows the running download total for a specific GitHub
release, per widget instance. Auto-refreshes hourly, respects the GitHub rate limit via
ETag/conditional requests, and stores the optional Personal Access Token in
`EncryptedSharedPreferences`.

Built as a portfolio piece: layered architecture, ~100% test-covered data layer, CI with
lint + Detekt + Kover, signed release workflow, Material 3 + Dynamic Colors, English +
Spanish.

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
- Explicit `NOT SET UP`, `SYNCING`, `RATE LIMIT`, `TAG NOT FOUND`, `NOT UPDATED`,
  `UPDATED` states — no silent failure modes.
- Localized in English and Spanish.

## Quickstart

Requires JDK 17 + Android SDK 34.

```bash
# Clone
git clone https://github.com/Neonexus29/github_download_counter_widget_android.git
cd github_download_counter_widget_android

# Build a debug APK (or open in Android Studio Ladybug+)
./gradlew assembleDebug

# Run the full quality gate locally
./gradlew spotlessCheck detekt testDebugUnitTest koverVerify
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
Widget (AppWidgetProvider)  ─┐
      │ onReceive(ACTION_REFRESH)
      ▼
RefreshScheduler ── enqueues ──▶ RefreshWorker (CoroutineWorker)
                                        │
                                        ▼
                          ReleaseRepository
                          │        │        │        │        │
                          ▼        ▼        ▼        ▼        ▼
                       GitHubApi  Config  Cache  ETag  SecureToken
                          │
                          ▼
                    api.github.com
```

- **`domain/`** owns the vocabulary (`Asset`, `Release`, `RefreshResult`). Zero Android APIs.
- **`data/remote/`** turns HTTP into `ApiResponse` variants. `OkHttpGitHubApi` is the only
  class in the tree that talks to the network.
- **`data/local/`** persists via `SharedPreferences` / `EncryptedSharedPreferences`.
  Storage-agnostic on purpose so tests inject plain prefs.
- **`data/repo/ReleaseRepository`** is the single choke point translating transport-level
  outcomes into UI-facing `RefreshResult`s.
- **`ui/widget/`** is a thin `AppWidgetProvider` + a pure `WidgetRenderer` + a
  `RemoteViewsService` for the asset list.
- **`worker/`** owns WorkManager plumbing. No reflection — provider dispatch is via the
  `WidgetKind` enum.
- **`di/AppContainer`** wires the graph. Manual DI, lazy, ~40 lines.

## Testing

```bash
./gradlew testDebugUnitTest           # JVM + Robolectric unit tests
./gradlew koverHtmlReport             # Coverage HTML at app/build/reports/kover/
./gradlew connectedAndroidTest        # Instrumented (requires emulator/device)
```

Coverage floor enforced in `app/build.gradle.kts`: **70% overall**, **85% in
`data.repo` + `data.remote`**. The CI job posts a coverage comment on every PR.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Security

Report vulnerabilities via the process in [`SECURITY.md`](SECURITY.md). Do not open a
public issue for security matters.

## License

MIT — see [`LICENSE`](LICENSE).
