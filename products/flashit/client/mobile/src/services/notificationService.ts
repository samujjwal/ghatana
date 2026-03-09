/**
 * FlashIt Mobile - Push Notification Service
 *
 * Handles push notification registration, handling, and deep linking.
 * Integrates with backend notification-service.ts via Expo Push.
 *
 * @doc.type service
 * @doc.purpose Push notification management for React Native
 * @doc.layer product
 * @doc.pattern NotificationService
 */

import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { router } from 'expo-router';

const STORAGE_KEYS = {
  PUSH_TOKEN: '@notifications_pushToken',
  PREFERENCES: '@notifications_preferences',
  LAST_NOTIFICATION: '@notifications_lastNotification',
};

/**
 * Notification preferences.
 */
export interface NotificationPreferences {
  enabled: boolean;
  sphereShares: boolean;
  momentComments: boolean;
  momentReactions: boolean;
  transcriptionComplete: boolean;
  weeklyDigest: boolean;
  quietHoursEnabled: boolean;
  quietHoursStart: string; // HH:mm format
  quietHoursEnd: string;
}

/**
 * Push notification data.
 */
export interface PushNotificationData {
  type: string;
  title: string;
  body: string;
  data?: Record<string, any>;
  actionUrl?: string;
}

/**
 * Notification handler callback.
 */
export type NotificationHandler = (notification: PushNotificationData) => void;

/**
 * Default notification preferences.
 */
const DEFAULT_PREFERENCES: NotificationPreferences = {
  enabled: true,
  sphereShares: true,
  momentComments: true,
  momentReactions: true,
  transcriptionComplete: true,
  weeklyDigest: false,
  quietHoursEnabled: false,
  quietHoursStart: '22:00',
  quietHoursEnd: '07:00',
};

/**
 * Configure notification behavior.
 */
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  }),
});

/**
 * Push Notification Service.
 */
class NotificationService {
  private pushToken: string | null = null;
  private preferences: NotificationPreferences = DEFAULT_PREFERENCES;
  private handlers: Set<NotificationHandler> = new Set();
  private foregroundSubscription: Notifications.Subscription | null = null;
  private responseSubscription: Notifications.Subscription | null = null;
  private initialized: boolean = false;

  /**
   * Initialize the notification service.
   */
  async init(): Promise<void> {
    if (this.initialized) return;

    try {
      // Load stored preferences
      const storedPrefs = await AsyncStorage.getItem(STORAGE_KEYS.PREFERENCES);
      if (storedPrefs) {
        this.preferences = { ...DEFAULT_PREFERENCES, ...JSON.parse(storedPrefs) };
      }

      // Load stored push token
      this.pushToken = await AsyncStorage.getItem(STORAGE_KEYS.PUSH_TOKEN);

      // Set up notification channels (Android)
      if (Platform.OS === 'android') {
        await this.setupAndroidChannels();
      }

      // Register for push notifications
      if (this.preferences.enabled) {
        await this.registerForPushNotifications();
      }

      // Set up notification listeners
      this.setupListeners();

      this.initialized = true;
      console.log('[Notifications] Initialized');
    } catch (error) {
      console.error('[Notifications] Init error:', error);
    }
  }

  /**
   * Set up Android notification channels.
   */
  private async setupAndroidChannels(): Promise<void> {
    await Notifications.setNotificationChannelAsync('default', {
      name: 'Default',
      importance: Notifications.AndroidImportance.HIGH,
      vibrationPattern: [0, 250, 250, 250],
      lightColor: '#6366F1',
    });

    await Notifications.setNotificationChannelAsync('moments', {
      name: 'Moments',
      description: 'Notifications about your moments',
      importance: Notifications.AndroidImportance.HIGH,
      sound: 'default',
    });

    await Notifications.setNotificationChannelAsync('social', {
      name: 'Social',
      description: 'Comments, reactions, and shares',
      importance: Notifications.AndroidImportance.DEFAULT,
    });

    await Notifications.setNotificationChannelAsync('digest', {
      name: 'Weekly Digest',
      description: 'Weekly summary notifications',
      importance: Notifications.AndroidImportance.LOW,
    });
  }

  /**
   * Register for push notifications.
   */
  async registerForPushNotifications(): Promise<string | null> {
    if (!Device.isDevice) {
      console.log('[Notifications] Not a physical device, skipping registration');
      return null;
    }

    try {
      // Check existing permissions
      const { status: existingStatus } = await Notifications.getPermissionsAsync();
      let finalStatus = existingStatus;

      // Request permissions if needed
      if (existingStatus !== 'granted') {
        const { status } = await Notifications.requestPermissionsAsync();
        finalStatus = status;
      }

      if (finalStatus !== 'granted') {
        console.log('[Notifications] Permission denied');
        return null;
      }

      // Get push token
      const tokenResult = await Notifications.getExpoPushTokenAsync({
        projectId: process.env.EXPO_PUBLIC_PROJECT_ID,
      });

      this.pushToken = tokenResult.data;
      await AsyncStorage.setItem(STORAGE_KEYS.PUSH_TOKEN, this.pushToken);

      console.log('[Notifications] Push token:', this.pushToken);
      return this.pushToken;
    } catch (error) {
      console.error('[Notifications] Registration error:', error);
      return null;
    }
  }

  /**
   * Set up notification listeners.
   */
  private setupListeners(): void {
    // Foreground notifications
    this.foregroundSubscription = Notifications.addNotificationReceivedListener(
      (notification) => {
        const data = this.parseNotification(notification);
        this.handleNotification(data, 'foreground');
      }
    );

    // Notification response (user tapped)
    this.responseSubscription = Notifications.addNotificationResponseReceivedListener(
      (response) => {
        const data = this.parseNotification(response.notification);
        this.handleNotificationTap(data);
      }
    );
  }

  /**
   * Parse notification into standard format.
   */
  private parseNotification(notification: Notifications.Notification): PushNotificationData {
    const content = notification.request.content;
    const data = content.data as Record<string, any> || {};

    return {
      type: data.type || 'default',
      title: content.title || 'Flashit',
      body: content.body || '',
      data,
      actionUrl: data.actionUrl,
    };
  }

  /**
   * Handle received notification.
   */
  private handleNotification(data: PushNotificationData, source: 'foreground' | 'background'): void {
    // Store last notification
    AsyncStorage.setItem(STORAGE_KEYS.LAST_NOTIFICATION, JSON.stringify({
      ...data,
      receivedAt: new Date().toISOString(),
      source,
    }));

    // Notify handlers
    this.handlers.forEach((handler) => {
      try {
        handler(data);
      } catch (error) {
        console.error('[Notifications] Handler error:', error);
      }
    });

    console.log('[Notifications] Received:', data.type, source);
  }

  /**
   * Handle notification tap (deep linking).
   */
  private handleNotificationTap(data: PushNotificationData): void {
    console.log('[Notifications] Tapped:', data.type);

    // Navigate based on notification type
    if (data.actionUrl) {
      this.navigateTo(data.actionUrl);
      return;
    }

    switch (data.type) {
      case 'sphere_shared':
        if (data.data?.sphereId) {
          this.navigateTo(`/spheres/${data.data.sphereId}`);
        }
        break;

      case 'moment_commented':
      case 'moment_reaction':
        if (data.data?.momentId) {
          this.navigateTo(`/moments/${data.data.momentId}`);
        }
        break;

      case 'transcription_complete':
        if (data.data?.momentId) {
          this.navigateTo(`/moments/${data.data.momentId}`);
        }
        break;

      case 'weekly_digest':
        this.navigateTo('/analytics');
        break;

      default:
        this.navigateTo('/');
        break;
    }
  }

  /**
   * Navigate to a URL using expo-router.
   */
  private navigateTo(url: string): void {
    try {
      // Remove leading slash for expo-router
      const path = url.startsWith('/') ? url : `/${url}`;
      router.push(path as any);
    } catch (error) {
      console.error('[Notifications] Navigation error:', error);
    }
  }

  /**
   * Get push token.
   */
  getPushToken(): string | null {
    return this.pushToken;
  }

  /**
   * Update notification preferences.
   */
  async updatePreferences(updates: Partial<NotificationPreferences>): Promise<void> {
    this.preferences = { ...this.preferences, ...updates };
    await AsyncStorage.setItem(STORAGE_KEYS.PREFERENCES, JSON.stringify(this.preferences));

    // Re-register or unregister based on enabled status
    if (updates.enabled !== undefined) {
      if (updates.enabled) {
        await this.registerForPushNotifications();
      }
    }
  }

  /**
   * Get notification preferences.
   */
  getPreferences(): NotificationPreferences {
    return { ...this.preferences };
  }

  /**
   * Check if notifications are enabled.
   */
  async checkPermissions(): Promise<boolean> {
    const { status } = await Notifications.getPermissionsAsync();
    return status === 'granted';
  }

  /**
   * Request notification permissions.
   */
  async requestPermissions(): Promise<boolean> {
    const { status } = await Notifications.requestPermissionsAsync();
    return status === 'granted';
  }

  /**
   * Schedule a local notification.
   */
  async scheduleLocalNotification(
    title: string,
    body: string,
    data?: Record<string, any>,
    triggerSeconds?: number
  ): Promise<string> {
    const trigger = triggerSeconds
      ? { seconds: triggerSeconds }
      : null;

    const id = await Notifications.scheduleNotificationAsync({
      content: {
        title,
        body,
        data,
        sound: 'default',
      },
      trigger,
    });

    return id;
  }

  /**
   * Cancel a scheduled notification.
   */
  async cancelNotification(id: string): Promise<void> {
    await Notifications.cancelScheduledNotificationAsync(id);
  }

  /**
   * Cancel all scheduled notifications.
   */
  async cancelAllNotifications(): Promise<void> {
    await Notifications.cancelAllScheduledNotificationsAsync();
  }

  /**
   * Get badge count.
   */
  async getBadgeCount(): Promise<number> {
    return await Notifications.getBadgeCountAsync();
  }

  /**
   * Set badge count.
   */
  async setBadgeCount(count: number): Promise<void> {
    await Notifications.setBadgeCountAsync(count);
  }

  /**
   * Clear badge.
   */
  async clearBadge(): Promise<void> {
    await Notifications.setBadgeCountAsync(0);
  }

  /**
   * Subscribe to notifications.
   */
  subscribe(handler: NotificationHandler): () => void {
    this.handlers.add(handler);
    return () => this.handlers.delete(handler);
  }

  /**
   * Check if current time is in quiet hours.
   */
  isQuietHours(): boolean {
    if (!this.preferences.quietHoursEnabled) return false;

    const now = new Date();
    const currentTime = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
    
    const start = this.preferences.quietHoursStart;
    const end = this.preferences.quietHoursEnd;

    // Handle overnight quiet hours (e.g., 22:00 - 07:00)
    if (start > end) {
      return currentTime >= start || currentTime < end;
    }

    return currentTime >= start && currentTime < end;
  }

  /**
   * Send push token to backend.
   */
  async registerTokenWithBackend(apiBaseUrl: string, authToken: string): Promise<void> {
    if (!this.pushToken) {
      console.log('[Notifications] No push token to register');
      return;
    }

    try {
      const response = await fetch(`${apiBaseUrl}/api/notifications/register`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${authToken}`,
        },
        body: JSON.stringify({
          token: this.pushToken,
          platform: Platform.OS,
          deviceId: Device.deviceName,
        }),
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      console.log('[Notifications] Token registered with backend');
    } catch (error) {
      console.error('[Notifications] Failed to register token:', error);
    }
  }

  /**
   * Clean up listeners.
   */
  cleanup(): void {
    if (this.foregroundSubscription) {
      this.foregroundSubscription.remove();
    }
    if (this.responseSubscription) {
      this.responseSubscription.remove();
    }
    this.handlers.clear();
  }
}

// Export singleton instance
export const notificationService = new NotificationService();
export default notificationService;
