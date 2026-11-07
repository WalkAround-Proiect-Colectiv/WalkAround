package services.applicationapi.events.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketLink(String source, String url, String linkType) {

    public TicketLink(String source, String url) {
        this(source, url, null);
    }
}
