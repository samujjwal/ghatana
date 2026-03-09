import notifee, { AndroidImportance, TriggerType } from '@notifee/react-native';
import notifications from '@/services/notifications';

jest.mock('@notifee/react-native', () => {
  const actual = jest.requireActual('@notifee/react-native');
  return {
    __esModule: true,
    default: {
      createChannel: jest.fn(),
      displayNotification: jest.fn(),
      createTriggerNotification: jest.fn(),
      cancelNotification: jest.fn(),
      cancelAllNotifications: jest.fn(),
      requestPermission: jest.fn().mockResolvedValue({ authorizationStatus: 1 }),
      getNotificationSettings: jest.fn().mockResolvedValue({ authorizationStatus: 1 }),
    },
    AndroidImportance: actual.AndroidImportance,
    TriggerType: actual.TriggerType,
  };
});

describe('NotificationService', () => {
  const sampleNotification = {
    id: 'notif-1',
    title: 'Test',
    body: 'Body',
    data: { screen: 'Dashboard' },
    timestamp: new Date(),
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('initializes channel', async () => {
    await notifications.initialize();
    expect(notifee.createChannel).toHaveBeenCalledWith({
      id: 'guardian-alerts',
      name: 'Guardian Alerts',
      importance: AndroidImportance.HIGH,
    });
  });

  it('displays notification', async () => {
    await notifications.displayNotification(sampleNotification);
    expect(notifee.displayNotification).toHaveBeenCalledWith(
      expect.objectContaining({
        id: sampleNotification.id,
        title: sampleNotification.title,
        body: sampleNotification.body,
      })
    );
  });

  it('schedules notification with trigger timestamp', async () => {
    await notifications.scheduleNotification(sampleNotification, 30);
    expect(notifee.createTriggerNotification).toHaveBeenCalledWith(
      expect.objectContaining({ id: sampleNotification.id }),
      expect.objectContaining({ type: TriggerType.TIMESTAMP })
    );
  });

  it('cancels single notification', async () => {
    await notifications.cancelNotification('notif-1');
    expect(notifee.cancelNotification).toHaveBeenCalledWith('notif-1');
  });

  it('cancels all notifications', async () => {
    await notifications.cancelAllNotifications();
    expect(notifee.cancelAllNotifications).toHaveBeenCalled();
  });

  it('requests permissions', async () => {
    const granted = await notifications.requestPermissions();
    expect(granted).toBe(true);
    expect(notifee.requestPermission).toHaveBeenCalled();
  });

  it('checks permissions', async () => {
    const granted = await notifications.checkPermissions();
    expect(granted).toBe(true);
    expect(notifee.getNotificationSettings).toHaveBeenCalled();
  });
});
