# Native decoders (FFmpeg audio, dav1d AV1)

Module: `decoders/` (Android library). Java wrappers and JNI sources are vendored unmodified from androidx/media tag `1.11.0`
(commit `2bc207851df311340767e913931ca7b28cab1794`, Apache-2.0), with one patch: `Libdav1dVideoRenderer` asks
`com.lnxv.player.decoders.Av1SoftwareGate` before decoding. `ExperimentalFfmpegVideoRenderer` is intentionally not included.

## Build
`decoders/tools/build_all.sh [abi ...]` (default `arm64-v8a armeabi-v7a`; `x86_64` for emulators).
Requires NDK / CMake versions from `decoders/tools/versions.env`, plus `meson` and `ninja`.
Sources are fetched shallowly and the script **refuses to build unless HEAD equals the pinned commit SHA**.

* FFmpeg `n6.0.1`: shared libraries, `--disable-everything` + decoders `aac mp3 ac3 eac3 truehd dca vorbis opus amrnb amrwb flac alac pcm_mulaw pcm_alaw`. After configure, `config.h` must say `LGPL version 2.1 or later` with `CONFIG_GPL`, `CONFIG_NONFREE`, `CONFIG_VERSION3` all 0, otherwise the build aborts.
* dav1d `1.5.4`: static, linked into `libdav1dJNI.so`. `cpu_features v0.9.0` is used by the wrapper.
* Linked with `-Wl,-z,max-page-size=16384` (16 KB page devices).

## Replacing FFmpeg (LGPL)
`libavcodec.so`, `libavutil.so`, `libswresample.so` are separate files in `lib/<abi>/`. Build a compatible FFmpeg 6.0.x
with the same configure options (see `build_ffmpeg.sh`) and repackage the APK.

## Runtime policy
* Extension renderers are added after MediaCodec renderers, so FFmpeg/dav1d are only reached when MediaCodec cannot handle the track.
* AV1 software (`Av1SoftwarePolicy`): blocked in "Hardware only"; capped at 1920x1088 @ 30 fps (1280x736 @ 30 fps on low-RAM / no-64-bit devices). Cap values are conservative guesses, **not benchmarked**.
* Not supported by Media3's FFmpeg mapping in 1.11.0 (so not enabled): WMA, MP2, MLP.

## CI
`build-apk.yml` caches `third_party/out` keyed on `hashFiles('decoders/tools/**')`; the cache is saved right after the native build.
It fails if `libavcodec.so`, `libavutil.so`, `libswresample.so`, `libffmpegJNI.so`, `libdav1dJNI.so` are missing for either release ABI.
