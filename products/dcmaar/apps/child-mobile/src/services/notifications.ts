import notifee, { AndroidImportance, TriggerType } from '@notifee/react-native';
import type { PushNotification } from '@/types';

class NotificationService {
  private readonly channelId = 'guardian-alerts';

  async initialize(): Promise<void> {
    await notifee.createChannel({
      id: this.channelId,
      name: 'Guardian Alerts',
      importance: AndroidImportance.HIGH,
    });
  }

  async displayNotification(notification: PushNotification): Promise<void> {
    await notifee.displayNotification({
      id: notification.id,
      title: notification.title,
      body: notification.body,
      data: notification.data,
      android: {
        channelId: this.channelId,
        smallIcon: 'ic_launcher',
        importance: AndroidImportance.HIGH,
        pressAction: { id: 'default' },
      },
      ios: { sound: 'default' },
    });
  }

  async scheduleNotification(notification: PushNotification, delaySeconds: number): Promise<void> {
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
      },
    );
  }

  async cancelNotification(id: string): Promise<void> {
    await notifee.cancelNotification(id);
  }

  async cancelAllNotifications(): Promise<void> {
    await notifee.cancelAllNotifications();
  }

  async requestPermissions(): Promise<boolean> {
    const settings = await notifee.requestPermission();
    return settings.authorizationStatus >= 1;
  }

  async checkPermissions(): Promise<boolean> {
    const settings = await notifee.getNotificationSettings();
    return settings.authorizationStatus >= 1;
  }
}

const notifications = new NotificationService();

export { notifications };
export default notifications;
