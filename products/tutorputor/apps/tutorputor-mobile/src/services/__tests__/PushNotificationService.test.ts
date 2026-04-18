/**
 * Push Notification Service Tests
 *
 * @doc.type test
 * @doc.purpose Test push notification service
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { PushNotificationService } from '../PushNotificationService';

// Mock React Native Platform
vi.mock('react-native', () => ({
  Platform: {
    OS: 'ios',
  },
}));

describe('PushNotificationService', () => {
  let service: PushNotificationService;

  beforeEach(() => {
    service = new PushNotificationService();
  });

  describe('init', () => {
    it('should initialize successfully', async () => {
      await expect(service.init()).resolves.not.toThrow();
    });
  });

  describe('requestPermissions', () => {
    it('should request permissions', async () => {
      const granted = await service.requestPermissions();
      expect(typeof granted).toBe('boolean');
    });
  });

  describe('getPreferences', () => {
    it('should return default preferences', () => {
      const prefs = service.getPreferences();
      expect(prefs.enabled).toBe(true);
      expect(prefs.modules).toBe(true);
      expect(prefs.assessments).toBe(true);
    });
  });

  describe('updatePreferences', () => {
    it('should update preferences', async () => {
      await service.updatePreferences({ enabled: false });
      const prefs = service.getPreferences();
      expect(prefs.enabled).toBe(false);
    });
  });

  describe('handleNotification', () => {
    it('should handle notification payload', () => {
      const payload = {
        title: 'Test',
        body: 'Test notification',
        screen: 'Home',
      };
      expect(() => service.handleNotification(payload)).not.toThrow();
    });
  });
});
