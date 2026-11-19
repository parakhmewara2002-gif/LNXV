# Architecture

Single Android app module (`app`) plus `decoders` (native FFmpeg audio + dav1d) and `benchmark`.
Kotlin, Jetpack Compose, Media3 ExoPlayer 1.11.0, Room, DataStore.

## Layers
- `ui/`: Compose screens (`home`, `player`, `settings`, `vault`...). ViewModels hold UI state as `StateFlow`.
- `playback/`: `engine/Media3PlayerManager` (the single owner of ExoPlayer), `effects/` (GL picture pass),
  `service/` (background playback), `pip/`, `gestures/`.
- `core/`: pure logic and storage, no Compose where avoidable so it is unit tested.
  - `model/` policies (AV1 software gate, settings search, licences list)
  - `subtitle/` format detection, encoding detection, OpenSubtitles client (`subtitle/online/`)
  - `enhancer/` picture settings, colour maths, shader generator, gate, store
  - `bookmarks/` per-video bookmark list and store
  - `design/` palette, tokens, glass components, contrast maths
  - `flags/` `FeatureFlag` (experimental features default off)
  - `security/`, `session/`: Vault encryption and PIN (not changed by Phases 1-7)
- `decoders/`: builds FFmpeg (LGPL, audio only, shared libs) and dav1d from pinned sources; see `DECODERS.md`.

## Data flow for playback
`PlayerViewModel` -> `Media3PlayerManager.prepareAndPlay()` -> ExoPlayer with a renderers factory that adds the
FFmpeg audio and gated dav1d video renderers, and a data source that routes vault URIs through decryption.
The picture enhancer is a `GlEffect` set with `setVideoEffects`; the gate turns it off for HDR, DRM, low RAM, power save.

## Storage
Room (library, playlists), DataStore (`lnxv_picture`, `lnxv_bookmarks`, online subtitle settings, user prefs).
The OpenSubtitles token is encrypted with an Android Keystore AES-GCM key; the password is never stored.

## Flavors
`sideload` (in-app updater, online subtitles) and `store` (both disabled through resource booleans).

## Conventions
New features are flag-gated or opt-in; strings in `strings.xml` plus `values-hi`; pure logic gets unit tests.
