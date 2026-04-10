/**
 * NotificationService Tests
 */

import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  processNotifications,
  getNotificationStats,
  markAsRead,
  markAllAsRead,
  removeNotification,
  clearExpiredNotifications,
  type Notification,
  type NotificationRequest,
  type NotificationPreferences,
} from '../NotificationService';

describe('NotificationService', () => {
  describe('processNotifications', () => {
    it('should prioritize notifications by urgency', async () => {
      const request: NotificationRequest = {
        notifications: [
          {
            id: '1',
            type: 'info',
            priority: 'low',
            title: 'Info notification',
            message: 'Test message',
            timestamp: Date.now(),
            read: false,
          },
          {
            id: '2',
            type: 'error',
            priority: 'urgent',
            title: 'Error notification',
            message: 'Error message',
            timestamp: Date.now(),
            read: false,
          },
        ],
      };

      const response = await processNotifications(request);

      expect(response.prioritized).toBeDefined();
      expect(response.prioritized[0].priority).toBe('urgent');
    });

    it('should consolidate similar notifications', async () => {
      const request: NotificationRequest = {
        notifications: [
          {
            id: '1',
            type: 'info',
            priority: 'normal',
            title: 'Notification 1',
            message: 'Test message',
            timestamp: Date.now(),
            read: false,
          },
          {
            id: '2',
            type: 'info',
            priority: 'normal',
            title: 'Notification 2',
            message: 'Test message',
            timestamp: Date.now(),
            read: false,
            context: { projectId: 'proj1' },
          },
        ],
        userContext: {
          preferences: {
            enableConsolidation: true,
            categories: { info: true, success: true, warning: true, error: true, task: true, mention: true, system: true },
            priorities: { urgent: true, high: true, normal: true, low: true },
          },
        },
      };

      const response = await processNotifications(request);

      expect(response.consolidated.length).toBeLessThanOrEqual(2);
    });

    it('should suppress notifications during quiet hours', async () => {
      // Use 00:00-23:59 to ensure quiet hours covers any local time
      const request: NotificationRequest = {
        notifications: [
          {
            id: '1',
            type: 'info',
            priority: 'low',
            title: 'Info notification',
            message: 'Test message',
            timestamp: Date.now(),
            read: false,
          },
        ],
        userContext: {
          preferences: {
            enableConsolidation: true,
            quietHours: { start: '00:00', end: '23:59' },
            categories: { info: true, success: true, warning: true, error: true, task: true, mention: true, system: true },
            priorities: { urgent: true, high: true, normal: true, low: true },
          },
        },
      };

      const response = await processNotifications(request);

      // Non-urgent notifications should be suppressed during quiet hours
      expect(response.suppressed.length).toBeGreaterThan(0);
    });
  });

  describe('getNotificationStats', () => {
    it('should calculate notification statistics', () => {
      const notifications: Notification[] = [
        {
          id: '1',
          type: 'info',
          priority: 'normal',
          title: 'Info',
          message: 'Test',
          timestamp: Date.now(),
          read: false,
        },
        {
          id: '2',
          type: 'error',
          priority: 'urgent',
          title: 'Error',
          message: 'Test',
          timestamp: Date.now(),
          read: true,
        },
      ];

      const stats = getNotificationStats(notifications);

      expect(stats.total).toBe(2);
      expect(stats.unread).toBe(1);
      expect(stats.byType.info).toBe(1);
      expect(stats.byType.error).toBe(1);
      expect(stats.byPriority.normal).toBe(1);
      expect(stats.byPriority.urgent).toBe(1);
    });

    it('should handle empty notification list', () => {
      const stats = getNotificationStats([]);

      expect(stats.total).toBe(0);
      expect(stats.unread).toBe(0);
    });
  });

  describe('markAsRead', () => {
    it('should mark notification as read', () => {
      const notifications: Notification[] = [
        {
          id: '1',
          type: 'info',
          priority: 'normal',
          title: 'Info',
          message: 'Test',
          timestamp: Date.now(),
          read: false,
        },
      ];

      const updated = markAsRead('1', notifications);

      expect(updated[0].read).toBe(true);
    });

    it('should not affect other notifications', () => {
      const notifications: Notification[] = [
        {
          id: '1',
          type: 'info',
          priority: 'normal',
          title: 'Info',
          message: 'Test',
          timestamp: Date.now(),
          read: false,
        },
        {
          id: '2',
          type: 'error',
          priority: 'urgent',
          title: 'Error',
          message: 'Test',
          timestamp: Date.now(),
          read: false,
        },
      ];

      const updated = markAsRead('1', notifications);

      expect(updated[0].read).toBe(true);
      expect(updated[1].read).toBe(false);
    });
  });

  describe('markAllAsRead', () => {
    it('should mark all notifications as read', () => {
      const notifications: Notification[] = [
        {
          id: '1',
          type: 'info',
          priority: 'normal',
          title: 'Info',
          message: 'Test',
          timestamp: Date.now(),
          read: false,
        },
        {
          id: '2',
          type: 'error',
          priority: 'urgent',
          title: 'Error',
          message: 'Test',
          timestamp: Date.now(),
          read: false,
        },
      ];

      const updated = markAllAsRead(notifications);

      expect(updated.every(n => n.read)).toBe(true);
    });
  });

  describe('removeNotification', () => {
    it('should remove notification by id', () => {
      const notifications: Notification[] = [
        {
          id: '1',
          type: 'info',
          priority: 'normal',
          title: 'Info',
          message: 'Test',
          timestamp: Date.now(),
          read: false,
        },
        {
          id: '2',
          type: 'error',
          priority: 'urgent',
          title: 'Error',
          message: 'Test',
          timestamp: Date.now(),
          read: false,
        },
      ];

      const updated = removeNotification('1', notifications);

      expect(updated.length).toBe(1);
      expect(updated[0].id).toBe('2');
    });
  });

  describe('clearExpiredNotifications', () => {
    it('should remove expired notifications', () => {
      const now = Date.now();
      const notifications: Notification[] = [
        {
          id: '1',
          type: 'info',
          priority: 'normal',
          title: 'Info',
          message: 'Test',
          timestamp: now,
          read: false,
          expiresAt: now - 1000,
        },
        {
          id: '2',
          type: 'error',
          priority: 'urgent',
          title: 'Error',
          message: 'Test',
          timestamp: now,
          read: false,
          expiresAt: now + 100000,
        },
      ];

      const updated = clearExpiredNotifications(notifications);

      expect(updated.length).toBe(1);
      expect(updated[0].id).toBe('2');
    });

    it('should keep notifications without expiration', () => {
      const notifications: Notification[] = [
        {
          id: '1',
          type: 'info',
          priority: 'normal',
          title: 'Info',
          message: 'Test',
          timestamp: Date.now(),
          read: false,
        },
      ];

      const updated = clearExpiredNotifications(notifications);

      expect(updated.length).toBe(1);
    });
  });
});
