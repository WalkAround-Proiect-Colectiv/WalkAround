import { Stack } from 'expo-router';
import { SessionProvider } from '../login/components/SessionProvider';
import ReminderBanner from '../events/components/ReminderBanner';

export default function RootLayout() {
  return <SessionProvider><Stack screenOptions={{ headerShown: false }} /><ReminderBanner /></SessionProvider>;
}
