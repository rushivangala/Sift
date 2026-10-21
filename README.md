# Sift

A photo library that organizes itself, privately. Native Android app (Kotlin, Jetpack Compose) built from the
Sift design system for **Android 17 on Pixel 10 Pro** (1280 × 2856 px, ~412 dp, 120 Hz LTPO OLED).

- On-device by default; the app has **no INTERNET permission**. Cloud is opt-in, always labeled, and never the filled button.
- Every AI element shows a reason, a confidence meter and an on-device/cloud badge. High (≥ 0.90) may act, always with Undo; Medium suggests; Low asks.
- Bring-your-own storage, no upsell. Photos stay plain files.

## What's in the app

| Area | Screens |
| --- | --- |
| Design system | Color roles for light, dark and true black; accent pipeline with Material You wallpaper, 6 curated accents and "match my photo" (tone clamp, chroma cap, AA verify, tone stepping); Hanken Grotesk + IBM Plex Mono type scale; shape, spacing, spring motion with reduce-motion fallback; Material Symbols Rounded with live wght/FILL/GRAD axes |
| Components | Bottom nav (pill morph), Ask bar, photo grid cell with corner badges and selection morph, selection bar, AI result card, confidence meter, origin badge, bottom sheet, dialog, toast with Undo, toggle, chips, radio rows, storage meter, hold-to-confirm |
| Library | Home with "Ask your photos", day-grouped grid (real MediaStore or sample library), multi-select, share, move sheet, trash via the system consent dialog, partial-access state, empty state |
| Ask | Typeahead, "understood as" card, receipts-from-May results with a low-confidence question, on-device metadata search for your real library, cloud consent dialog |
| Smart cleanup | Scan summary, grouped near-duplicate review, confirm with bulk-delete protection (1.5 s hold over 100 items), Trash with 30-day recovery |
| Viewer | Pager, info sheet with real EXIF, favorite, share, edit, "Use its color"; auto-edit card, "what changed and why" with per-adjustment undo, Content Credentials (C2PA) screen |
| Photos that act | Receipt → expense, ticket → calendar (real calendar insert), card → contact with low-confidence questions (real contacts insert) |
| Storage | Usage by location and type (real StatFs and MediaStore sizes), smart offload, choose where originals live |
| Albums | Real folders as albums, trip album with reason and Undo, Locked folder behind BiometricPrompt |
| Settings | Theme (System / Light / Dark + true black), accent source picker with the pipeline log, privacy toggles (hide previews in recents is live), Smart features master switch with per-feature toggles |

The **sample library** (Settings → Library, or "Explore with the sample library" on first launch) fills every flow with the
designed content: Lisbon, October 2026, with color placeholders for photos, exactly as in the designs.

### Not in this build

- On-device ML models (duplicate/blur detection, OCR, faces). With your real library, Ask searches dates, places, albums and
  file names, and Cleanup only offers the screenshot rule. The AI flows are fully navigable in the sample library.
- NAS / Dropbox / Drive connectors, Google Photos import, map, editor tools, family sharing.

## Build

Requires JDK 17 and the Android SDK.

```bash
./gradlew testDebugUnitTest assembleRelease           # API 36
./gradlew assembleRelease -Psift.sdk=37               # Android 17 (API 37) once that platform is installed
```

The APK is at `app/build/outputs/apk/release/app-release.apk` (signed with the debug key so it installs directly).

## CI

`.github/workflows/android.yml` builds against API 37 when the runner can install it (otherwise 36), runs the unit tests
(including a WCAG AA check of every accent source in every theme), lint, and a Compose UI suite on an emulator set to the
Pixel 10 Pro's 1280 × 2856 resolution. Each push to `main` publishes a GitHub Release with the APKs and screenshots.

## Licenses

Hanken Grotesk and IBM Plex Mono: SIL Open Font License 1.1 (see `licenses/`). Material Symbols: Apache License 2.0.
