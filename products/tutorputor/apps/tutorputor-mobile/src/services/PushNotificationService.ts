/**
 * Push Notification Service
 *
 * Handle push notifications for mobile app using React Native PushNotificationIOS
 * and Firebase Cloud Messaging (FCM) for Android.
 *
 * @doc.type service
 * @doc.purpose Manage push notifications for mobile app
 * @doc.layer product
 * @doc.pattern Service
 */

import { Platform } from 'react-native';

export interface NotificationPayload {
  title: string;
  body: string;
  data?: Record<string, string>;
  screen?: string;
  params?: Record<string, string>;
}

export interface NotificationPreferences {
  enabled: boolean;
  modules: boolean;
  assessments: boolean;
  aiTutor: boolean;
  reminders: boolean;
}

export class PushNotificationService {
  private deviceToken: string | null = null;
  private preferences: NotificationPreferences = {
    enabled: true,
    modules: true,
    assessments: true,
    aiTutor: true,
    reminders: true,
  };

  /**
   * Initialize push notification service
   */
  async init(): Promise<void> {
    if (Platform.OS === 'ios') {
      await this.initIOS();
    } else if (Platform.OS === 'android') {
      await this.initAndroid();
    }
  }

  /**
   * Request notification permissions
   */
  async requestPermissions(): Promise<boolean> {
    if (Platform.OS === 'ios') {
      return this.requestIOSPermissions();
    }
    return true; // Android permissions handled at app level
  }

  /**
   * Register device token with backend
   */
  async registerToken(apiBaseUrl: string, userId: string, authToken: string): Promise<void> {
    if (!this.deviceToken) {
      console.warn('[PushNotificationService] No device token available');
      return;
    }

    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/push/register`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${authToken}`,
        },
        body: JSON.stringify({
          userId,
          deviceToken: this.deviceToken,
          platform: Platform.OS,
        }),
      });

      if (!response.ok) {
        throw new Error('Failed to register device token');
      }

      console.log('[PushNotificationService] Device token registered successfully');
    } catch (error) {
      console.error('[PushNotificationService] Failed to register device token:', error);
      throw error;
    }
  }

  /**
   * Handle incoming notification
   */
  handleNotification(payload: NotificationPayload): void {
    console.log('[PushNotificationService] Received notification:', payload);

    // Route to appropriate screen based on payload
    if (payload.screen) {
      this.navigateToScreen(payload.screen, payload.params);
    }

    // Update preferences based on notification type
    this.updateNotificationContext(payload);
  }

  /**
   * Get notification preferences
   */
  getPreferences(): NotificationPreferences {
    return { ...this.preferences };
  }

  /**
   * Update notification preferences
   */
  async updatePreferences(preferences: Partial<NotificationPreferences>): Promise<void> {
    this.preferences = { ...this.preferences, ...preferences };
    
    // Persist preferences to storage
    try {
      const AsyncStorage = require('@react-native-async-storage/async-storage').default;
      await AsyncStorage.setItem('push_preferences', JSON.stringify(this.preferences));
    } catch (error) {
      console.error('[PushNotificationService] Failed to persist preferences:', error);
    }
  }

  /**
   * Load notification preferences from storage
   */
  async loadPreferences(): Promise<void> {
    try {
      const AsyncStorage = require('@react-native-async-storage/async-storage').default;
      const stored = await AsyncStorage.getItem('push_preferences');
      if (stored) {
        this.preferences = JSON.parse(stored);
      }
    } catch (error) {
      console.error('[PushNotificationService] Failed to load preferences:', error);
    }
  }

  private async initIOS(): Promise<void> {
    try {
      const PushNotificationIOS = require('@react-native-community/push-notification-ios').default;
      
      PushNotificationIOS.addEventListener('register', (token: string) => {
        this.deviceToken = token;
        console.log('[PushNotificationService] iOS device token registered:', token);
      });

      PushNotificationIOS.addEventListener('notification', (notification: NotificationPayload) => {
        this.handleNotification(notification);
      });

      await this.requestIOSPermissions();
    } catch (error) {
      console.error('[PushNotificationService] Failed to initialize iOS notifications:', error);
    }
  }

  private async initAndroid(): Promise<void> {
    try {
      // FCM initialization would go here
      // For now, placeholder implementation
      console.log('[PushNotificationService] Android FCM initialization placeholder');
    } catch (error) {
      console.error('[PushNotificationService] Failed to initialize Android notifications:', error);
    }
  }

  private async requestIOSPermissions(): Promise<boolean> {
    try {
      const PushNotificationIOS = require('@react-native-community/push-notification-ios').default;
      const result = await PushNotificationIOS.requestPermissions();
      return result === 'authorized' || result === 'granted';
    } catch (error) {
      console.error('[PushNotificationService] Failed to request iOS permissions:', error);
      return false;
    }
  }

  private navigateToScreen(screen: string, params?: Record<string, string>): void {
    // This would integrate with React Navigation
    // For now, log the navigation intent
    console.log('[PushNotificationService] Navigate to screen:', screen, params);
  }

  private updateNotificationContext(payload: NotificationPayload): void {
    // Update app state based on notification context
    // This could trigger data refreshes, badge updates, etc.
    console.log('[PushNotificationService] Update notification context:', payload);
  }
}

// Singleton instance
export const pushNotificationService = new PushNotificationService();
