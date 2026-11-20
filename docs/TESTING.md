# Testing

## Automated (CI, `.github/workflows/build-apk.yml`)
- JVM unit tests: `./gradlew testSideloadDebugUnitTest`. Cover pure logic: AV1 policy, subtitle parsing, hashing,
  encoding detection, OpenSubtitles API mapping, picture maths and shader text, bookmarks, settings search, contrast.
- GLSL validation: generated shaders are written by a unit test and checked with `glslangValidator`.
- Release build (R8) of sideload and store flavors, native library presence check, benchmark module compile.
- Lint, detekt and ktlint run as non-blocking reports.

## What CI cannot tell you
Decoder speed, dropped frames, real-device UI, TalkBack, battery, thermal behaviour, GPU shader behaviour on real
drivers. Nothing in this repo has measured those. Use `DEVICE_CHECKLIST.md` and `DEVICE_TEST_PLAN.md`.

## Running locally
`./gradlew testSideloadDebugUnitTest` (needs the Android SDK; native decoders need the NDK, see `DECODERS.md`).
