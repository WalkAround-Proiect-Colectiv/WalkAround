package services.applicationapi.events.dto;

/**
 * Indicates whether extra provider details were merged into the event details response.
 *
 * <ul>
 *   <li>{@link #COMPLETE}: full provider details are present.</li>
 *   <li>{@link #BASIC}: list or snapshot data is served by design (for example in recorded mode without a fixture).</li>
 *   <li>{@link #UNAVAILABLE}: the provider details request failed, so basic data is served as a fallback.</li>
 * </ul>
 */
public enum EnrichmentStatus {
    COMPLETE,
    BASIC,
    UNAVAILABLE
}
