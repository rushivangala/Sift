# Photos app with AI features

_Started 2026-10-19 14:43 UTC_

---

## User

Using this design system: [PASTE STAGE 1 OUTPUT]

Design the 5 hero features as high-fidelity screens, dark and light each:
1. Home with "Ask your photos" as the main entry point. Show the idle state, typing with suggestions, and results for "receipts from May".
2. Smart cleanup: scan summary, grouped review (duplicates, near-duplicates, blurry, screenshots), confirm step, protection against accidental bulk deletion, recovery from trash.
3. Transparent AI edit: auto-edit applied, "what changed and why" panel, per-adjustment undo, visible AI-edited marker (C2PA).
4. Photos that act: receipt to expense, ticket to calendar, card to contact, with a confirmation step and low-confidence handling.
5. Storage freedom: choose storage provider, usage breakdown, smart offload with local thumbnails. No upsell patterns.

Rules: every AI element shows a short reason, a confidence indicator and an on-device or cloud badge. Low confidence asks instead of acting. Use realistic content: named people, real-looking places and dates, mixed photo types and aspect ratios. No lorem ipsum.

## Assistant

_[tool: list_files]_

_[tool: list_files]_

_[tool: read_file]_

_[tool: read_file]_

Using **Sift** (the complete Stage 1 file in this project) as the system. Building all 5 features in one canvas file, light + dark rows per feature.

_[tool: update_todos]_

_[tool: dc_write]_

_[tool: show_html]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: update_todos]_

_[tool: ready_for_verification]_

This isn't an animation deliverable, so I'm re-submitting.

_[tool: ready_for_verification]_

I've built **Sift Hero Screens.dc.html** with 16 phone screens, each in light and dark. Your prompt still had the "[PASTE STAGE 1 OUTPUT]" placeholder, so I used Sift, the full Stage 1 system in this project, not Tessera. A background check of the layout is still running, and I haven't looked at the screens myself yet.

- **01 Ask your photos (1a–1c):** the home screen while idle, typing with suggestions and an "Understood as" card, and results for "receipts from May". The results include a low-confidence "2 more might be from May" card that asks before including them.
- **02 Smart cleanup (2a–2d):** the scan summary, a near-duplicate review, a confirm step and Trash with Undo. When it's unsure which photo to keep, it asks you to pick. In the confirm step, items in shared albums are taken out of the selection, moving over 100 items needs a press-and-hold, and Trash keeps everything for 30 days.
- **03 AI edit (3a–3c):** the auto-edit as applied and a "what changed and why" panel where each change has its own Undo. A generative removal at 0.64 confidence is not applied and waits for you to preview it. The last screen explains the Content Credentials (C2PA) marker.
- **04 Photos that act (4a–4c):** a receipt becomes an expense and a ticket becomes a calendar event, each with a confirmation step. The ticket shows no end time, so that field is flagged as low confidence. The business card stays unsaved until you answer two questions about unclear fields.
- **05 Storage (5a–5c):** a choice of 7 storage providers, a usage breakdown, and smart offload that keeps thumbnails on the phone. The offload asks before moving 6 videos that changed after they were backed up. There are no paid plans anywhere.

There are two settings you can change in the file: the accent color, and an extra true-black row. The photos are color placeholders; send me real images if you want them in.

## User

Found issues — fixing…

## User

Found issues — fixing…

