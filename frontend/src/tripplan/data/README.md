# Emoji catalog

`emojiCatalog.json` is bundled with the frontend, so the group chat emoji palette works offline. It contains 3,972 fully qualified emoji and standalone components from the Unicode Emoji 18.0 `emoji-test.txt` file, in Unicode's recommended keyboard order. Each entry stores a glyph, its English name, and an index into the `groups` list. The picker uses these names for search and virtualizes the grid so the entire catalog is available without rendering every emoji at once.

Source: https://www.unicode.org/Public/18.0.0/emoji/emoji-test.txt. To update the catalog, download that version's `emoji-test.txt` file and run `node scripts/generate-emoji-catalog.mjs <path-to-emoji-test.txt>` from `frontend/`. If moving to a newer Unicode release, update the script's expected version and this documentation.

Unicode data is provided under the Unicode License v3; see `UNICODE-LICENSE.txt`. Which new emoji render in color depends on the user's operating system and fonts. The app can still send their Unicode text when a local font has no glyph for them.
