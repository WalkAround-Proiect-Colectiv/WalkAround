package services.applicationapi.events.service.locality;

import java.text.Normalizer;
import java.util.Locale;

/** Accent-free, lowercase identifiers such as {@code "cluj-napoca"} for keys and file names. */
public final class Slugs {

    private Slugs() {
    }

    public static String slug(String value) {
        if (value == null) {
            return "";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public static String localityKey(String countryCode, String county, String name) {
        return String.join("/", slug(countryCode), slug(county), slug(name));
    }
}
