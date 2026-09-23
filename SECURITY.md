# Security policy

## Reporting a vulnerability

Email **iosune@acornjuice.com** with the details. Please **do not** open a public GitHub
issue for anything that could affect users in the wild.

Include:

- A minimal reproduction (Android version, app version, steps).
- What you observed and what you expected.
- Whether you consider the finding remotely exploitable.

Acknowledgement within **72 hours**. A first assessment (accepted / clarifying questions /
declined) within **7 days**. Patch timeline depends on severity — coordinated disclosure
before the patch is available is welcome.

## If it is already being exploited

If you have reason to believe the issue is being exploited in the wild, put
`[ACTIVE EXPLOITATION]` in the subject line. Those reports jump the queue.

From that point two clocks run, and they are not the same one:

- **What we owe you.** Acknowledgement within 72 hours, first assessment within 7 days,
  as above.
- **What we owe the authorities.** For the products we place on the EU market, the Cyber
  Resilience Act (Regulation (EU) 2024/2847, Art. 14) gives us 24 hours from becoming
  aware of an actively exploited vulnerability to file an early warning with the
  coordinating CSIRT and ENISA through the Single Reporting Platform, 72 hours for the
  full notification, and 14 days for the final report once a fix or mitigation is
  available. Those obligations have applied since 11 September 2026, and we run the same
  process here.

The second clock starts when we read your report. That is why the subject line matters.

## Scope

In scope:

- The app and both widget flavors, including the configuration screen.
- Token storage, log emission and anything that could leak the Personal Access Token.
- The build and release pipeline in `.github/workflows/`, including dependency handling.

Out of scope:

- GitHub's own API, its rate limits and anything that requires a compromised GitHub
  account to begin with.
- Findings that need a rooted device, an unlocked bootloader or physical access to an
  unlocked phone.
- Reports produced by an automated scanner with no demonstrated impact on this app.

If you are unsure which side of the line a finding falls on, send it anyway and say so.

## Safe harbour

We will not pursue legal action over security research carried out in good faith under
this policy: testing against your own device and your own GitHub account, no access to
other people's data, no service disruption, and giving us a reasonable window to fix the
issue before going public.

## Supply chain

Every push, every pull request and once a day, `supply-chain.yml` produces a CycloneDX
SBOM (`download-widget.cdx.json`) and an `osv-scanner` report (`osv.json`), verifies the
lockfile against the declared dependencies, scans the full history for secrets, and
checks that every GitHub Action stays pinned to a commit SHA. Reports are kept as build
artifacts for 90 days.

That 90-day window does not apply to releases. Every tagged release carries its own
CycloneDX SBOM as an asset, plus a build-provenance attestation and an SBOM attestation
signed through Sigstore. Both are bound to the APK's digest, so a swapped binary or an
edited inventory stops verifying:

```bash
gh attestation verify download-widget-vX.Y.Z.apk \
  --repo Acorn-Juice-Solutions/github_download_counter_widget_android
```

## Supported versions

| Version | Supported               |
|---------|-------------------------|
| 1.x     | Yes                     |
| 0.x     | No (early prototype)    |

## What lives on the device, and how

| Data | Storage | Rationale |
| :--- | :--- | :--- |
| GitHub Personal Access Token | `EncryptedSharedPreferences`, AES-256-GCM values, AES-256-SIV keys, master key in Android Keystore | Only credential the app handles |
| Per-widget API URL, tag | Plain `SharedPreferences` | Non-sensitive by design |
| Cached asset list + `fetched_at` | Plain `SharedPreferences` | Public data |
| ETag per widget | Plain `SharedPreferences` | Opaque token, no security value |
| "Refresh in flight since" stamp per widget | Plain `SharedPreferences` | A timestamp; lets the watchdog spot a refresh the OS killed mid-flight |

`android:allowBackup="false"` and explicit `data_extraction_rules.xml` /
`backup_rules.xml` files prevent both cloud backup and device-to-device transfer of these
prefs.

## Log hygiene

Every `Log.*` call in the app goes through `com.acornjuice.downloadwidget.util.SafeLogger`,
which redacts `Bearer`, `Authorization:`, `token=`, `api_key=`, `PAT:` and similar
substrings before emission. If you find a call site that bypasses this, please report it —
it's a bug.
