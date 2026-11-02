package services.applicationapi.tripplan.service.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class CityNameNormalizer {

    private CityNameNormalizer() {
    }

    public static String toLookupName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC)
                .trim()
                .replaceAll("\\s+", " ");
    }

    public static String toExactKey(String name) {
        return baseName(name)
                .toLowerCase(Locale.ROOT);
    }

    public static Set<String> toComparisonKeys(String name) {
        String base = baseName(name);
        String plain = foldSeparators(foldLatinLetters(base)).toLowerCase(Locale.ROOT);
        String expanded = foldSeparators(foldLatinLetters(expandLetterVariants(base)))
                .toLowerCase(Locale.ROOT);

        return plain.equals(expanded) ? Set.of(plain) : Set.of(plain, expanded);
    }

    private static String baseName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceFirst("\\s*/.*$", "")
                .replaceFirst("\\s*\\([^)]*\\)\\s*$", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String expandLetterVariants(String name) {
        return name
                .replace("Ä", "Ae")
                .replace("ä", "ae")
                .replace("Ö", "Oe")
                .replace("ö", "oe")
                .replace("Ü", "Ue")
                .replace("ü", "ue")
                .replace("Å", "Aa")
                .replace("å", "aa")
                .replace("Ø", "Oe")
                .replace("ø", "oe");
    }

    private static String foldSeparators(String name) {
        return name
                .replaceAll("[\\p{Pd}/]", " ")
                .replaceAll("['’ʼ.,]", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String foldLatinLetters(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFKD);
        StringBuilder result = new StringBuilder(decomposed.length());
        Character.UnicodeScript baseScript = Character.UnicodeScript.COMMON;

        for (int codePoint : decomposed.codePoints().toArray()) {
            int characterType = Character.getType(codePoint);
            boolean isMark = characterType == Character.NON_SPACING_MARK
                    || characterType == Character.COMBINING_SPACING_MARK
                    || characterType == Character.ENCLOSING_MARK;

            if (isMark) {
                if (baseScript != Character.UnicodeScript.LATIN
                        && baseScript != Character.UnicodeScript.GREEK) {
                    result.appendCodePoint(codePoint);
                }
                continue;
            }

            baseScript = Character.UnicodeScript.of(codePoint);

            switch (codePoint) {
                case 'Æ' -> result.append("AE");
                case 'æ' -> result.append("ae");
                case 'Œ' -> result.append("OE");
                case 'œ' -> result.append("oe");
                case 'ẞ' -> result.append("SS");
                case 'ß' -> result.append("ss");
                case 'Þ' -> result.append("Th");
                case 'þ' -> result.append("th");
                case 'Ø' -> result.append('O');
                case 'ø' -> result.append('o');
                case 'Ł' -> result.append('L');
                case 'ł' -> result.append('l');
                case 'Đ', 'Ð' -> result.append('D');
                case 'đ', 'ð' -> result.append('d');
                case 'ı' -> result.append('i');
                case 'Ŋ' -> result.append('N');
                case 'ŋ' -> result.append('n');
                case 'Ħ' -> result.append('H');
                case 'ħ' -> result.append('h');
                case 'Ŧ' -> result.append('T');
                case 'ŧ' -> result.append('t');
                default -> result.appendCodePoint(codePoint);
            }
        }

        return result.toString();
    }
}
