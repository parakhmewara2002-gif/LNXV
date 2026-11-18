Vendored sources in `src/main/java/androidx/media3/decoder/{ffmpeg,av1}` and `src/main/jni/{ffmpegjni,dav1djni}` come from
https://github.com/androidx/media tag 1.11.0 (commit 2bc207851df311340767e913931ca7b28cab1794), Apache License 2.0 (`LICENSE-androidx-media.txt`).
Local change: `Libdav1dVideoRenderer.java` imports `com.lnxv.player.decoders.Av1SoftwareGate` and refuses to decode when the gate denies.
`Av1SoftwareGate.java` is LNXV code. FFmpeg (LGPL-2.1+), dav1d (BSD-2-Clause) and cpu_features (Apache-2.0) are fetched at build time, not stored here.
