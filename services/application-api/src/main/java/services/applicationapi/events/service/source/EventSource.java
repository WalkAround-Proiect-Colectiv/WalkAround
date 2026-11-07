package services.applicationapi.events.service.source;

import java.util.List;
import java.util.Optional;
import services.applicationapi.events.model.Locality;

/** Where upcoming events come from. The implementation is selected with {@code app.events.provider}. */
public interface EventSource {

    /** All events the provider returns for the locality; may contain duplicates and past events. */
    List<RawEvent> search(Locality locality);

    /** Full details for one event, or empty when the provider cannot supply them. */
    Optional<RawEventDetails> details(String detailsToken);
}
