# Comprehensive design system audit

_Started 2026-10-19 16:16 UTC_

---

## User

Take these screens: home with Ask bar, photo viewer, cleanup review, editor, settings.
Render each in:
- Light and dark
- True black
- Two curated accents
- Material You dynamic color from a warm wallpaper and a cool wallpaper
- "Match my photo" on a sunset photo and a green forest photo

For every combination, report contrast ratios for text, buttons and selection states against AA. List any failures and the fix.

## Assistant

_[tool: list_files]_

_[tool: list_files]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: grep]_

_[tool: grep]_

_[tool: grep]_

_[tool: dc_write]_

_[tool: ready_for_verification]_

This page isn't an animation, so I'm skipping that check.

_[tool: ready_for_verification]_

I built `Sift Theme Matrix.dc.html`. It shows all 5 screens in 18 themes, with a contrast report at the top. I haven't seen the final audit numbers yet, so I can't tell you how many pairs fail. The failures table in the file lists each one with the fix.

- **Screens:** home with Ask bar, photo viewer, cleanup review, editor and settings.
- **Themes:** six sources (Slate and Clay as the curated accents, warm and cool wallpapers, a sunset photo and a forest photo), each in Light, Dark and True black. I chose Clay as the second curated accent and picked the warm and cool wallpaper and sunset and forest seed colors myself. Swap any of them if you want different ones.
- **Contrast:** 18 pairs per theme. They cover text, filled buttons, outlines, toasts and selection controls, held to 4.5:1 for text and 3:1 for outlines and controls. I computed them from the hex values used in the screens.
- **Report:** the failures table has each failing pair, its ratio as shipped, the fix and its ratio after the fix. There is also a full per-theme grid of ratios after the fixes.

The screens are rendered with the fixes applied. The fixes step the accent shades until each pair passes:
- The primary accent now has to clear 4.5:1 on every surface it can sit on (base, card, Ask bar, dialog), not only the base surface.
- The toast's Undo color is stepped against the inverse surface.
- The selection badge color is nudged until it clears 3:1 against the selected photo's background.

**Not covered:**
- **Selected photo background:** it sits at about 1.1:1 against the surface in every theme. The check badge and the inset on the photo carry the selection, so I didn't count it as a failure.
- **White text over photos:** it depends on the image, so it isn't in the audit.
- **Dividers and inactive slider tracks:** I treated them as decorative and didn't test them.

For the wallpaper themes I tint the neutral surfaces toward the wallpaper's hue, which is my reading of Material You. The curated and photo themes keep the fixed warm neutrals.

