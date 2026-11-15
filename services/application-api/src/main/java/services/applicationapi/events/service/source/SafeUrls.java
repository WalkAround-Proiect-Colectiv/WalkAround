package services.applicationapi.events.service.source;

import java.util.regex.Pattern;
import services.applicationapi.events.model.EventVenue;

/**
 * Hygiene and safety helpers for third-party event data: allows only {@code https://} links, strips HTML
 * from descriptions, bounds numeric ratings/review counts, and caps strings to database column limits.
 */
public final class SafeUrls {

    public static final int MAX_TITLE_LENGTH = 300;
    public static final int MAX_WHEN_LENGTH = 200;
    public static final int MAX_VENUE_NAME_LENGTH = 300;
    public static final int MAX_ADDRESS_LENGTH = 500;
    public static final int MAX_DESCRIPTION_LENGTH = 4000;
    public static final int MAX_SOURCE_LENGTH = 100;

    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SafeUrls() {
    }

    /** Returns the trimmed {@code https://} URL, or {@code null} for any other scheme or malformed string. */
    public static String httpsOrNull(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.strip();
        if (!trimmed.startsWith("https://") || trimmed.length() <= "https://".length()) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                return null;
            }
        }
        return trimmed;
    }

    /** Strips HTML tags, collapses runs of whitespace, and caps at {@link #MAX_DESCRIPTION_LENGTH}. */
    public static String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String withoutTags = HTML_TAGS.matcher(description).replaceAll(" ");
        String collapsed = WHITESPACE.matcher(withoutTags).replaceAll(" ").strip();
        if (collapsed.isEmpty()) {
            return null;
        }
        return cap(collapsed, MAX_DESCRIPTION_LENGTH);
    }

    /** Trims a string, returns {@code null} when blank, and caps its length to {@code maxLength}. */
    public static String cap(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    /** Validates venue fields: rating within {@code [0, 5]}, review count {@code >= 0}, and {@code https://} URL. */
    public static EventVenue sanitizeVenue(String name, Double rating, Integer reviews, String link) {
        String cleanName = cap(name, MAX_VENUE_NAME_LENGTH);
        Double cleanRating = rating != null && Double.isFinite(rating) && rating >= 0.0 && rating <= 5.0
                ? rating
                : null;
        Integer cleanReviews = reviews != null && reviews >= 0
                ? reviews
                : null;
        String cleanUrl = httpsOrNull(link);
        if (cleanName == null && cleanRating == null && cleanReviews == null && cleanUrl == null) {
            return null;
        }
        return new EventVenue(cleanName, cleanRating, cleanReviews, cleanUrl);
    }
}
