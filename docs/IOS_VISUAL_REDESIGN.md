# LNXV iOS-inspired visual redesign pass

This pass updates the existing Jetpack Compose presentation layer without replacing the
Media3 playback engine, repositories, vault, playlists, preferences, or navigation architecture.

## Implemented in this source pass

- Home video cards and Continue Watching cards have more cinematic thumbnail proportions, softer 20dp corners, and controlled press feedback. The brand header now has a stronger LNXV wordmark and a compact "YOUR PERSONAL CINEMA"
  descriptor; the glass header has more legible tint and calmer selected-tab treatment.
- Liquid Glass surfaces now draw a restrained vertical reflection below their content and a
  fine specular top edge. The previous unused blur parameter is not presented as a real
  backdrop blur: Compose `Modifier.blur` blurs the composable itself, not what is behind it.
  Home's existing Haze integration remains the backdrop-blur path.
- Glass buttons use tighter, non-bouncy spring response; general card press feedback is
  similarly tuned for a more controlled iOS-like interaction.
- Settings section labels now use tracked uppercase typography and a subtle divider, and
  settings panels use a softer 22dp radius.
- Player controls now enter/exit with a short vertical movement plus fade; the main play
  control has a brighter cyan gradient, fine edge and refined press response. Scrubber
  thumb animation uses a controlled spring rather than a bouncy spring.

## Preserved

- Existing Media3 / ExoPlayer integration and player lifecycle.
- Existing playback actions, gestures, audio/subtitle selection, PiP, seek preview,
  playlists, favorites, library actions, settings, vault, and DataStore behavior.
- Existing shared-transition navigation and Home Haze integration.

## Verification

- Performed source-level structural checks on all edited Kotlin files: balanced braces and
  parentheses.
- Attempted `bash ./gradlew testDebugUnitTest`. The repository's `gradlew` helper reports
  that `gradle-wrapper.jar` is not bundled and requires Android Studio to bootstrap Gradle
  8.7. No Gradle installation or Android SDK is available in this execution environment.
- Therefore unit tests, Kotlin compilation, APK build, emulator rendering, and physical-device
  frame-rate verification have **not** been completed.

## Build locally

1. Open the `LNXV-main` folder in Android Studio 2024.1 or newer.
2. Allow Android Studio to download the configured Gradle 8.7 distribution and install the
   Android SDK platform/build tools requested by the project.
3. Run `app` on an Android 13+ device or emulator.
4. Run the `testDebugUnitTest` Gradle task.
5. Check Home scrolling, video card -> player transition, player control show/hide, rapid
   seek gestures, Settings scrolling, and reduced-effects mode on both a 60Hz and a
   high-refresh-rate device where available.

This is a visual implementation pass, not a claim of exact native iOS rendering. Actual
backdrop blur, refresh-rate behavior, and perceived smoothness must be validated on-device.
