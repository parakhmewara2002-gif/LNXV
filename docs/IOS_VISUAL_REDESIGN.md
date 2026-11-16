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


## Remaining iOS/glass UI pass (LNXV_REMAINING_IOS_UI.md) - NOT DEVICE-TESTED, CI not yet run

Changed (all behavior and callbacks preserved; crash reporter untouched; no material-icons-extended):
- `GlassKit.kt`: `GlassSearchField` (search icon + clear), `GlassTextField`, `GlassDialogButton` (+ `DestructiveRed`), `GlassSegmented`, `GlassStepper`, `rememberTapHaptic`; haptics on switch toggle and tab change.
- `Motion.kt`: long-press (`bouncyClickable(onLongClick)`) gives a LongPress haptic (selection mode).
- `HomeScreen.kt`: GlassSearchField, GlassTextField in all dialogs, GlassDialogButton actions (destructive in red), icon + label selection bar, "Refresh library" empty-state action.
- `HomeComponents.kt`: cards share 22dp radius / 16dp thumbnails / hairline border / 12dp padding, capsule resolution + duration badges (also on grid cards), new `EmptyStateView` (large faint icon, title, optional subtitle and `GlassButton`).
- `LiquidGlass.kt`: `GlassBadge` is now a capsule.
- `SettingsScreen.kt`: iOS grouped sections with hairline dividers, `GlassSegmented` theme chooser, `GlassStepper` capsules, value + chevron rows, whole-row switch toggle, restyled update card (`GlassProgress`, primary `GlassButton`, secondary text action), Storage/Developer actions restyled.
- `VaultScreen.kt`: glass PIN fields, glass header pills, `GlassPanel` encrypt banner, bouncy glass keypad with haptics.
- `PlayerExtras.kt`: Resume and Sleep-timer dialogs use `GlassDialog`; `ImmersiveDialogEffect()` is still called inside the dialog content so system bars stay hidden.
- `LNXVNavigationHost.kt`: iOS push/pop (300 ms FastOutSlowIn, old screen shifts 30% and dims); routes to/from the player fade + scale; no animation when effects are reduced.
- Splash: `windowSplashScreenAnimatedIcon` added (black background was already set; minSdk 23 compat path kept).
- `Type.kt`: tighter letter spacing, SemiBold/Bold titles.

Not done / left as is: a few plain `TextButton`s remain (playlist detail rows, recent-stream list, vault "Cancel", "Use biometrics", "Forgot PIN?"). Fully custom fonts were not added (not requested).
