import type { ConfigContext, ExpoConfig } from 'expo/config';

// The Google plugin only needs an iOS URL scheme without Firebase.
// Android uses the package name + signing certificate registered in Google Cloud.
export default ({ config }: ConfigContext): ExpoConfig => {
  const iosClientId = process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID;
  const iosUrlScheme = iosClientId?.split('.').reverse().join('.');
  return {
    ...config,
    name: config.name ?? 'WalkAround',
    slug: config.slug ?? 'walkaround',
    android: { ...config.android, package: process.env.ANDROID_APPLICATION_ID ?? 'com.walkaround.app' },
    ios: { ...config.ios, bundleIdentifier: process.env.IOS_BUNDLE_IDENTIFIER ?? 'com.walkaround.app' },
    plugins: [
      ...(config.plugins ?? []),
      ...(iosUrlScheme ? [['@react-native-google-signin/google-signin', { iosUrlScheme }] as [string, { iosUrlScheme: string }]] : []),
    ],
  };
};
