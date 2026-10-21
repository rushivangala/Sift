# Design system audit and fixes

_Started 2026-10-19 16:29 UTC_

---

## User

Review all screens from Stages 2 to 5 against the Stage 1 design system.

Check and list mismatches for: corner radii, spacing, type sizes, icon style, accent usage, component variants, AI badge and reason pattern, 48dp touch targets, font scaling to 200%, TalkBack labels, and AA contrast.

Then check against the product promise: is every AI action explained and undoable, is on-device vs cloud always visible, and are there any upsell or dark patterns? Give a prioritized fix list, then apply the fixes.

## Assistant

_[tool: list_files]_

_[tool: list_files]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: grep]_

_[tool: run_script]_

_[tool: grep]_

_[tool: run_script]_

_[tool: run_script]_

_[tool: run_script]_

_[tool: grep]_

_[tool: read_file]_

_[tool: run_script]_

_[tool: run_script]_

_[tool: grep]_

_[tool: dc_write]_

_[tool: show_html]_

_[tool: ready_for_verification]_

Not an animation deliverable.

_[tool: ready_for_verification]_

_[tool: show_html]_

_[tool: get_webview_logs]_

I audited Stages 2–5 against Stage 1 and fixed 15 of 18 items directly in the screen files. The full prioritized list, with where each fix landed, is in **Sift Audit.dc.html**. I checked that Core Screens loads without console errors. I haven't looked at the other screen files after the edits; the background check covers only the audit page.

**Product promise**
- **Explained and undoable:** mostly. Seven AI surfaces were missing the "Why" line, confidence meter, on-device/cloud badge or Undo. All now follow the Stage 1 pattern.
- **On-device vs cloud:** two breaks, now fixed. Settings said nothing uploads even though a "Read with cloud once" option exists. That cloud button also didn't say what leaves the phone.
- **Upsells and dark patterns:** none found. The only nudge was onboarding, where full photo access was the big filled button and "Select photos only" was a small link. They're now the same size.

**Fixes, highest priority first**
1. **Trash retention:** Stage 2 said 30 days and Core said 60. I set it to 30 everywhere and recalculated the Trash countdowns.
2. **Text on photos:** badge backgrounds were too faint, about 2.9:1. I darkened them from 36% to 56% black so they pass 4.5:1 on light photos.
3. **Error color in dark themes:** the red "3 d" in Trash used the light-theme color in every theme. It now uses the theme's own error color.
4. **Touch targets:** 82 icon buttons went from 40 to 48 dp. Text buttons and chips keep their drawn size; the 48 dp tap-area rule is now written into Stage 1.
5. **TalkBack:** no file had labels before. Icon buttons and photo badges are now labelled. Decorative icons and the confidence dots are hidden, since the text next to them gets read instead.
6. **Font scaling to 200%:** 365 rows, buttons and chips had fixed heights that would clip text. They can now grow. I didn't draw a 200% version of any screen, so this is unproven visually.
7. **Smaller fixes:** 10 px text raised to 11 px. Off-scale corner radii corrected. "AI controls" renamed to "Smart features" to match States. Theme-matrix icons set to the Stage 1 weight and size.

**Open, needs your call**
- **Spacing:** 6, 10, 14 and 18 dp are used everywhere, Stage 1 included. Either allow a 2 dp sub-grid or round to 4 dp in the build.
- **Type sizes:** 13, 15, 18 and 20 px are used outside the defined scale. Either add two roles (13/18 and 15/20) or map them to the nearest existing ones.
- **Paused rows:** the paused Smart-features rows sit at 38% opacity, below AA. That's normally allowed for disabled controls, and the copy explains the state, so I left them.

