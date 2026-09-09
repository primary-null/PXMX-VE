# PXMX

Android client for Proxmox VE. Kotlin, Jetpack Compose, package `com.pxmx.app`.

## Layout

- `app/src/main/java/com/pxmx/app/data/` — API, repo, session, models
- `app/src/main/java/com/pxmx/app/ui/` — screens + ViewModels
- New screens follow an existing `*Screen.kt` + `*ViewModel.kt` pair
- Demo mode (`DemoApi`, `DemoShell`) must keep working without a cluster

## Verify

Windows:

```
.\gradlew.bat :app:testDebugUnitTest
```

UI or resource changes also:

```
.\gradlew.bat :app:assembleDebug
```

GitHub Actions runs unit tests on every PR and `main`. Debug APKs for GitHub
Releases are built **on tag** (`v*`) from that commit, never from a dirty
local tree. Do not clobber an existing tag; bump `versionName` / `versionCode`
first.

Do not commit `local.properties`, keystore files, or `app/build/`. There is
no release keystore in this repo; sideload Releases are debug APKs until one
is added locally (gitignored).

## Console

WebView of Proxmox noVNC / xterm.js. TLS is proxied through OkHttp with the
same cert pin as the API (WebView SSL-proceed breaks noVNC ES modules). Needs
a ticket/password session; API tokens often cannot open noVNC.

## Security

TLS pin, Android Keystore, VNC tickets, and cookies: conservative changes
only, with tests (`TlsHelpersTest`, `AuthAndSecurityUnitTest`,
`SecurityHardeningTest`).

## Agent routing

Implementation labor goes through the user `antigravity` skill. Android work
opens the Fold via `scrcpy-android`. Judgment follows `dual-llm`. Do not copy
those wrappers into this repo.
