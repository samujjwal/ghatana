/**
 * useSmartNotifications Hook Tests
 */

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSmartNotifications, useNotificationToast } from '../useSmartNotifications';
import type { Notification } from '../../services/ai/NotificationService';

describe('useSmartNotifications', () => {
  it('should return initial state', () => {
    const { result } = renderHook(() => useSmartNotifications());

    expect(result.current.notifications).toEqual([]);
    expect(result.current.consolidated).toEqual([]);
    expect(result.current.unreadCount).toBe(0);
    expect(result.current.isLoading).toBe(false);
  });

  it('should add notification', () => {
    const { result } = renderHook(() => useSmartNotifications());

    act(() => {
      result.current.addNotification({
        type: 'info',
        priority: 'normal',
        title: 'Test',
        message: 'Test message',
      });
    });

    expect(result.current.notifications.length).toBe(1);
    expect(result.current.unreadCount).toBe(1);
  });

  it('should mark notification as read', () => {
    const { result } = renderHook(() => useSmartNotifications());

    act(() => {
      result.current.addNotification({
        type: 'info',
        priority: 'normal',
        title: 'Test',
        message: 'Test message',
      });
    });

    expect(result.current.unreadCount).toBe(1);

    act(() => {
      result.current.markAsRead(result.current.notifications[0].id);
    });

    expect(result.current.unreadCount).toBe(0);
  });

  it('should mark all notifications as read', () => {
    const { result } = renderHook(() => useSmartNotifications());

    act(() => {
      result.current.addNotification({
        type: 'info',
        priority: 'normal',
        title: 'Test',
        message: 'Test message',
      });
      result.current.addNotification({
        type: 'error',
        priority: 'high',
        title: 'Error',
        message: 'Error message',
      });
    });

    expect(result.current.unreadCount).toBe(2);

    act(() => {
      result.current.markAllAsRead();
    });

    expect(result.current.unreadCount).toBe(0);
  });

  it('should remove notification', () => {
    const { result } = renderHook(() => useSmartNotifications());

    act(() => {
      result.current.addNotification({
        type: 'info',
        priority: 'normal',
        title: 'Test',
        message: 'Test message',
      });
    });

    expect(result.current.notifications.length).toBe(1);

    act(() => {
      result.current.removeNotification(result.current.notifications[0].id);
    });

    expect(result.current.notifications.length).toBe(0);
  });

  it('should calculate stats', () => {
    const { result } = renderHook(() => useSmartNotifications());

    act(() => {
      result.current.addNotification({
        type: 'info',
        priority: 'normal',
        title: 'Test',
        message: 'Test message',
      });
      result.current.addNotification({
        type: 'error',
        priority: 'high',
        title: 'Error',
        message: 'Error message',
      });
    });

    expect(result.current.stats.total).toBe(2);
    expect(result.current.stats.unread).toBe(2);
    expect(result.current.stats.byType.info).toBe(1);
    expect(result.current.stats.byType.error).toBe(1);
  });
});

describe('useNotificationToast', () => {
  it('should provide toast functions', () => {
    const { result } = renderHook(() => useNotificationToast());

    expect(result.current.show).toBeDefined();
    expect(result.current.success).toBeDefined();
    expect(result.current.error).toBeDefined();
    expect(result.current.warning).toBeDefined();
    expect(result.current.info).toBeDefined();
  });

  it('should show success toast', () => {
    const { result } = renderHook(() => useNotificationToast());

    act(() => {
      result.current.success('Success message');
    });

    // Would verify notification was added to useSmartNotifications
  });

  it('should show error toast', () => {
    const { result } = renderHook(() => useNotificationToast());

    act(() => {
      result.current.error('Error message');
    });

    // Would verify notification was added with error type
  });

  it('should show warning toast', () => {
    const { result } = renderHook(() => useNotificationToast());

    act(() => {
      result.current.warning('Warning message');
    });

    // Would verify notification was added with warning type
  });

  it('should show info toast', () => {
    const { result } = renderHook(() => useNotificationToast());

    act(() => {
      result.current.info('Info message');
    });

    // Would verify notification was added with info type
  });
});
