# Android Photos App Design System

_Started 2026-10-18 10:38 UTC_

---

## User

Design Stage 1 of an Android photos app for Pixel 10 Pro (1280x2856, 120Hz OLED), Android 17+, Material 3 Expressive, Jetpack Compose.

Product: a photo library that organizes itself, privately. Main competitor: Google Photos. Differentiators: on-device AI by default (cloud opt-in, always labeled), bring-your-own storage with no lock-in, transparent AI (every action shows why and can be undone), photos that act (tickets, receipts and cards become calendar events, expenses and contacts). Personality: calm, precise, trustworthy.

Deliver:
1. Three name options with a logo direction each. Pick one and justify it in two sentences.
2. Design system: color roles for light, dark and true black; accent system with three sources (Material You wallpaper color, 6 curated accents, "match my photo" sampling); type family and scale; spacing; shape scale; elevation; motion rules; icon style.
3. Components: bottom navigation, Ask bar, photo grid cell, selection state, AI result card with reason, confidence indicator and on-device/cloud badge, bottom sheet, dialog, toast, toggle, chip, storage meter.
4. Contrast check: confirm AA for every accent in both themes, and flag any that fail with a fix.

Render the system visually. Photos provide the color; UI stays quiet.

## Assistant

_[tool: list_files]_

## User

Continue

## Assistant

## Assistant

_[tool: run_script]_

_[tool: dc_write]_

_[tool: ready_for_verification]_

This page isn't an animation, so the warning doesn't apply.

_[tool: ready_for_verification]_

I've built the Stage 1 system as one page, `Sift Design System.dc.html`, with sections 01–06 matching your four deliverables.

**Name:** I picked Sift over Hearth and Plate. It describes what the product does (a library that sorts itself) as a short, calm verb that also works inside the app ("Sifted 214 photos on device"). Its mark, a 3 × 3 grid with tiles still settling into place, stays neutral next to any photo and doubles as the "organizing" animation. A trademark search is still needed.

**Accents:** All three sources (wallpaper color, the six curated accents, "match my photo") go through the same steps: each color is pinned to a fixed lightness, its saturation is capped, and contrast is checked before it reaches the UI. Picking a source and color on the page restyles every component in light, dark and true black.

**Contrast:**
- **Passing:** all six accents pass AA in all three themes, which is 54 text pairs. The neutral text colors and control outlines pass too. Ratios are calculated live from the colors on the page, so they stay correct when colors change.
- **Flagged:** the raw base colors fail as text in places:
  - Ochre on light: 2.39:1.
  - Moss, Clay and Tide on light: 3.7–3.8:1.
  - Slate and Plum on dark: about 3.6:1.
- **Fix:** base colors are inputs only and never ship as text or icon colors. Each one is replaced by a darker version for light themes and a lighter one for dark, listed per row in the table.

**Rules I added that you should check:**
- Confidence levels: at 0.90 or higher the app may act, always with Undo. From 0.70 to 0.89 it only suggests. Below 0.70 it stays silent unless asked.
- The cloud badge is always outlined, and "Use cloud" is never the filled button, so the private choice is the primary action.
- True black uses 1 dp hairlines instead of shadows, since shadows vanish on #000.

The photos are gradient stand-ins, not real images. There are two adjustable settings: the default accent and the corner radius of grid cells.

