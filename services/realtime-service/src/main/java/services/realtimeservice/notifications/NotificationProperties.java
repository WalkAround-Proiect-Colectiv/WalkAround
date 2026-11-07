package services.realtimeservice.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for per-user notifications. The signing secret, issuer and audience must match the
 * Application API, which issues the sign-in tokens.
 */
@ConfigurationProperties("app.notifications")
public record NotificationProperties(String signingSecret, String issuer, String audience, String reminderTopic) {
}
