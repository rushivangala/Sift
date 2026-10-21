# Library states design system

_Started 2026-10-19 16:06 UTC_

---

## User

Design these states, dark and light, reusing existing components:
- Empty library
- First sync in progress (with partial results usable)
- Offline
- Low storage
- Failed upload with retry
- Partial media permission
- AI processing
- AI wrong or low confidence, with correction flow
- Very large library (100k+ photos): scrubber, loading and search behavior
- Global AI off: how every AI surface looks and behaves

For each, show the screen and write one line on what the user can do next. Tone: calm, no blame, no pushy upsells.

## Assistant

_[tool: list_files]_

_[tool: list_files]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: grep]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: run_script]_

_[tool: ready_for_verification]_

This isn't an animation, so I'm re-running verification.

_[tool: ready_for_verification]_

I built all ten states in light and dark in `Sift States.dc.html`, with 15 screens per theme. The verifier is still checking it, so I haven't looked at the render myself yet.

Each state has a "Next" line stating what the user can do. States with extra rules also have a few bullets.

- **Layout:** Four states have two screens: AI processing, wrong or low confidence, very large library, and global AI off. Global AI off has three.
- **Global AI off:** It adds a table covering every AI surface when AI is off.
- **Reuse:** The screens reuse the existing components, apart from two small additions: a status banner card and a search coverage meter. They're listed in the last section.
- **Tone:** The copy states what is safe and what to do next. It has no blame and no upsells.

The design-system project the brief referenced was empty. I took the palette and components from `Sift Core Screens.dc.html` and `Sift Design System.dc.html`.

