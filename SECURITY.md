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

## Supported versions

| Version | Supported                        |
|---------|----------------------------------|
| 1.x     | Yes                              |
| 0.x     | No (pre-portfolio proof-of-concept) |

## What lives on the device, and how

| Data | Storage | Rationale |
| :--- | :--- | :--- |
| GitHub Personal Access Token | `EncryptedSharedPreferences`, AES-256-GCM values, AES-256-SIV keys, master key in Android Keystore | Only credential the app handles |
| Per-widget API URL, tag | Plain `SharedPreferences` | Non-sensitive by design |
| Cached asset list + `fetched_at` | Plain `SharedPreferences` | Public data |
| ETag per widget | Plain `SharedPreferences` | Opaque token, no security value |

`android:allowBackup="false"` and explicit `data_extraction_rules.xml` /
`backup_rules.xml` files prevent both cloud backup and device-to-device transfer of these
prefs.

## Log hygiene

Every `Log.*` call in the app goes through `com.acornjuice.downloadwidget.util.SafeLogger`,
which redacts `Bearer`, `Authorization:`, `token=`, `api_key=`, `PAT:` and similar
substrings before emission. If you find a call site that bypasses this, please report it —
it's a bug.
