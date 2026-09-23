# Contributing

Thanks for the interest. This is a small portfolio project, but PRs are welcome and are
treated seriously.

## Environment

- JDK 17 (Temurin recommended)
- Android SDK 36
- Android Studio Ladybug (2024.2) or newer

Clone and run:

```bash
git clone https://github.com/Acorn-Juice-Solutions/github_download_counter_widget_android.git
cd github_download_counter_widget_android
./gradlew help
```

## Quality gate (must pass locally before opening a PR)

```bash
./gradlew spotlessApply spotlessCheck detekt testDebugUnitTest
```

- **Spotless** enforces ktlint 1.4 formatting on `*.kt` / `*.kts`. `spotlessApply` fixes
  most findings automatically.
- **Detekt** runs on `src/main` and `src/test` with the config at
  `config/detekt/detekt.yml`.
- **`testDebugUnitTest`** runs the JVM + Robolectric suite (117 tests).
- Coverage reporting via Kover is temporarily disabled — see the plugin comment in
  `app/build.gradle.kts`. It will come back on when Kover ships AGP 9 support.

The CI pipeline runs the same commands plus `assembleDebug`; the release pipeline signs
`assembleRelease` from a keystore in secrets.

## Style rules the linter does not enforce

- **No hardcoded strings in Kotlin or XML.** Every user-visible string is in
  `res/values/strings.xml` with a `values-es/strings.xml` counterpart.
- **No plaintext token access.** Reads/writes go through `SecureTokenStore`.
- **Layer isolation.** `domain.*` cannot import from `data.*` / `ui.*` / `worker.*`.
  `data.*` cannot import from `ui.*` / `worker.*`. Enforcement is by discipline today; a
  Detekt custom rule is on the roadmap.
- **`SafeLogger` for anything that might contain a credential.** `android.util.Log.*`
  direct calls are OK for non-sensitive messages only.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) — one of:

```
feat: add rate-limit aware backoff
fix: preserve cache when refresh returns 500
refactor(data): extract asset parsing into ReleaseParser
docs: link architecture.md from README
ci: cache Gradle across jobs
deps: bump okhttp to 4.13.0
test(repo): cover the ETag invalidation path
```

## Release process (maintainers)

Prerequisites (one-off): create a keystore, encode base64, add these repo secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Then:

```bash
# 1. Bump version in app/build.gradle.kts (versionCode + versionName)
# 2. Move [Unreleased] entries to [X.Y.Z] – <today> in CHANGELOG.md
git commit -am "release: X.Y.Z"
git tag vX.Y.Z
git push origin main
git push origin vX.Y.Z
```

The tag goes up on its own rather than with `--tags`, which would fire every local tag
that is not yet upstream.

The `Release` workflow then, in order:

1. Generates a CycloneDX SBOM from the tagged tree, in a job of its own. The generator is
   third-party code and the job below holds the decoded signing keystore, so the two never
   share a workspace, a runner or a secret scope.
2. Refuses to continue if the tag does not match `versionName` — `v2.0.2` requires
   `versionName = "2.0.2"`. Prerelease tags such as `v2.0.2-rc1` are rejected by this
   check as it stands.
3. Builds and signs the APK, publishes it as `download-widget-vX.Y.Z.apk` with the SBOM
   attached, and attests both the build and the SBOM through Sigstore.

Everything that can fail does so before anything is published, so a failed release leaves
no release rather than half of one: delete the tag, fix, tag again.

## Reporting a bug

Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md). **Redact your PAT
and any personal repository URL** before pasting logs — the log path is quite good at
scrubbing credentials, but nothing beats not sending them in the first place.
