import * as Notifications from 'expo-notifications';

export async function registerForPushNotificationsAsync(): Promise<string> {
  const permissions = await Notifications.getPermissionsAsync();
  if (!permissions.granted) {
    const requested = await Notifications.requestPermissionsAsync();
    if (!requested.granted) {
      return 'Notifications permission denied';
    }
  }

  const token = await Notifications.getExpoPushTokenAsync();
  return token.data;
}