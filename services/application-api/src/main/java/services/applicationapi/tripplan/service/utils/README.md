# City-name comparison

[`CityNameNormalizer`](CityNameNormalizer.java) is shared by the local airport lookup and flight destination validation. Keep its three public operations distinct:

- `toLookupName` preserves a user's Unicode city spelling, normalizes to NFC, and collapses surrounding/internal whitespace. `CitySuggestion.lookupName` uses this for subsequent requests.
- `toExactKey` derives a lowercase base name after Unicode NFKC normalization, whitespace cleanup, and removal of trailing `(qualifier)` or `/qualifier`. It does **not** remove diacritics; the local airport service tries this key first.
- `toComparisonKeys` performs broader comparison: it folds many Latin and Greek marks, maps some special letters, handles selected expanded spellings, and normalizes separators and punctuation. It returns one or two possible keys.

These operations compare spelling variants of a name. They are not a translation service: for example, two unrelated names that denote the same city in different languages are not guaranteed to match. Broad folding can also collide across distinct municipalities, which is why the local airport service rejects ambiguous fallback matches. When adding name rules, check both false negatives and collisions in the calling services.
