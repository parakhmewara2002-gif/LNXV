# LNXV design system

Single source of truth for how the UI is built. It documents what exists in code today and marks what is only a
guideline. Code lives in `app/src/main/java/com/lnxv/player/core/design/`.

## Principles
1. Glass UI on top of content; content (the video, the thumbnails) stays the hero.
2. Every interactive element has a 48 dp touch target and a content description (or visible text).
3. Text meets WCAG AA (4.5:1) against its background in every theme. This is enforced by `ContrastTest`.
4. Motion respects the system "remove animations" setting (`MotionTokens.scaled`).
5. New features are flag-gated or opt-in; strings live in `strings.xml` with a `values-hi` translation.

## Themes (`Palette.kt`)
| Theme | Background | Notes |
|-------|------------|-------|
| AMOLED | `#000000` | no glow, true black |
| Dark | `#000000` + cyan/violet glow | glass has something to refract |
| Light | `#F2F5FA` + soft glow | text and accent darkened in Phase 5 to pass AA |

Colour roles: `textPrimary`, `textSecondary`, `textTertiary`, `accent`, `glassFill`, `glassBorder`, `glassSubtle`,
`barFill`, `placeholder`. Use `appColors` (never hard-coded colours) in new code.

## Tokens (`Tokens.kt`)
- `Space`: 2, 4, 8, 12, 16, 24, 32 dp.
- `Radius`: chip 12, card 20, sheet 28 dp.
- `Touch.minTarget`: 48 dp.
- `MotionTokens`: FAST 120, STANDARD 220, SLOW 360 ms; `scaled(ms, animatorScale)` returns 0 when animations are off.

Existing screens still contain older literal values; they move to tokens when touched, not in one risky sweep.

## Components (`GlassKit.kt`, `LiquidGlass.kt`)
Glass surfaces, dialog buttons, filter chips, `bouncyClickable`. Player side panels use `PlayerSidePanel`.

## Accessibility checklist for a new screen
- [ ] Contrast checked with `Contrast.ratio` for any new colour pair.
- [ ] `contentDescription` on icon-only buttons; list actions say what they act on.
- [ ] Works at 200% font scale without clipping.
- [ ] Works with TalkBack focus order top-to-bottom.
- [ ] Hindi string present.

## Not done yet (see ROADMAP)
Screenshot tests, adaptive tablet/foldable layouts, a full migration of old literals to tokens, and any
on-device check of TalkBack or font scaling. None of these were verified.
