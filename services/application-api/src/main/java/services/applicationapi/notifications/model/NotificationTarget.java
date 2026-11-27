package services.applicationapi.notifications.model;

import java.util.Map;

/** A route key interpreted by the client, with string parameters. Never contains a URL. */
public record NotificationTarget(String key, Map<String, String> params) {
}
