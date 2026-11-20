package services.applicationapi.events.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative classification for providers which do not supply event categories. */
public enum EventType {
    CULTURAL, SPORTS, OTHER;

    private static final Pattern SPORTS_WORDS = Pattern.compile(
            "\\b(sport\\w*|football|fotbal|basketball|baschet|tennis|tenis|volleyball|volei|handball|handbal|marathon|maraton\\w*|cycling|ciclism|triathlon|triatlon|athletics|atletism|rugby|swimming|inot|hockey|hochei)\\b");
    private static final Pattern CULTURAL_WORDS = Pattern.compile(
            "\\b(cultur\\w*|concert\\w*|theatre|theater|teatru|teatral\\w*|museum|muzeu\\w*|exhibition|expoziti\\w*|cinema|film\\w*|opera|opereta|ballet|balet|literatur\\w*|literar\\w*|poetry|poezie|jazz|symphony|simfonic\\w*|orchestra|festival\\w*|comedy|comedie|stand[ -]?up)\\b");

    public static EventType classify(String title, String description) {
        EventType titleType = classifyText(title);
        return titleType != OTHER ? titleType : classifyText(description);
    }

    private static EventType classifyText(String value) {
        if (value == null) return OTHER;
        String text = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        if (SPORTS_WORDS.matcher(text).find()) return SPORTS;
        if (CULTURAL_WORDS.matcher(text).find()) return CULTURAL;
        return OTHER;
    }
}
