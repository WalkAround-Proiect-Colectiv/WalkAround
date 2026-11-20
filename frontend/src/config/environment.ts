const notificationDisplaySeconds = Number(process.env.EXPO_PUBLIC_NOTIFICATION_DISPLAY_SECONDS);

export const environment = {
  apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL?.replace(/\/$/, '') ?? '',
  googleWebClientId: process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID ?? '',
  googleIosClientId: process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID ?? '',
  notificationDisplayMs: Number.isInteger(notificationDisplaySeconds)
    && notificationDisplaySeconds >= 1 && notificationDisplaySeconds <= 60
    ? notificationDisplaySeconds * 1000 : 8000,
};
