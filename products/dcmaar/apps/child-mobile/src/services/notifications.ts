import notifee, { AndroidImportance, TriggerType, TimestampTrigger } from '@notifee/react-native';
import type { PushNotification } from '@/types';

class NotificationService {
  private channelId = 'guardian-alerts';

  async initialize() {
    // Create notification channel for Android
    await notifee.createChannel({
      id: this.channelId,
      name: 'Guardian Alerts',
      importance: AndroidImportance.HIGH,
    });
  }

  async displayNotification(notification: PushNotification) {
    await notifee.displayNotification({
      id: notification.id,
      title: notification.title,
      body: notification.body,
      data: notification.data,
      android: {
        channelId: this.channelId,
        smallIcon: 'ic_launcher',
        importance: AndroidImportance.HIGH,
        pressAction: {
          id: 'default',
        },
      },
      ios: {
        sound: 'default',
      },
    });
  }

  async scheduleNotification(notification: PushNotification, delaySeconds: number) {
    await notifee.createTriggerNotification(
      {
        id: notification.id,
        title: notification.title,
        body: notification.body,
        data: notification.data,
        android: {
          channelId: this.channelId,
          smallIcon: 'ic_launcher',
        },
      },
      {
        type: TriggerType.TIMESTAMP,
        timestamp: Date.now() + delaySeconds * 1000,
      }
    );
  }

  async cancelNotification(id: string) {
    await notifee.cancelNotification(id);
  }

  async cancelAllNotifications() {
    await notifee.cancelAllNotifications();
  }

  async requestPermissions() {
    const settings = await notifee.requestPermission();
    return settings.authorizationStatus >= 1; // Authorized or provisional
  }

  async checkPermissions() {
    const settings = await notifee.getNotificationSettings();
    return settings.authorizationStatus >= 1;
  }
}

export default new NotificationService();
