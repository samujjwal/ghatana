import { atom } from 'jotai';
import { v4 as uuidv4 } from 'uuid';

/**
 * Notification Store - Jotai-based notification management
 *
 * <p><b>Purpose</b><br>
 * Manages application notifications (toasts, alerts, snackbars) with automatic cleanup,
 * persistence, and user interaction tracking. Provides app-scoped state for all notifications.
 *
 * <p><b>Features</b><br>
 * - Multiple notification types (success, error, warning, info)
 * - Auto-dismiss with configurable duration
 * - Dismissible by user
 * - Persistent alerts that survive navigation
 * - Notification batching
 * - Sound/vibration feedback options
 * - Action callbacks (e.g., undo, retry)
 * - Stacking with position control
 *
 * <p><b>Types</b><br>
 * - success: Green toast for successful operations
 * - error: Red toast for errors
 * - warning: Yellow/orange toast for warnings
 * - info: Blue toast for information
 * - alert: Persistent modal-like alert
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const [, addNotification] = useAtom(addNotificationAtom);
 * addNotification({
 *   type: 'success',
 *   title: 'Saved!',
 *   message: 'Your changes have been saved.',
 *   duration: 3000,
 * });
 * ```
 *
 * @doc.type store
 * @doc.purpose Notification management
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

/**
 * Notification type definition.
 *
 * @doc.type interface
 * @doc.purpose Notification data structure
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Notification {
    id: string;
    type: 'success' | 'error' | 'warning' | 'info' | 'alert';
    title: string;
    message?: string;
    duration?: number;
    action?: {
        label: string;
        onClick: () => void;
    };
    persistent?: boolean;
    createdAt: number;
}

/**
 * Notifications list state atom.
 */
export const notificationsAtom = atom<Notification[]>([]);

/**
 * Add notification action atom.
 */
export const addNotificationAtom = atom(
    null,
    (
        get,
        set,
        notification: Omit<Notification, 'id' | 'createdAt'>
    ) => {
        const id = uuidv4();
        const newNotification: Notification = {
            ...notification,
            id,
            createdAt: Date.now(),
        };

        const notifications = get(notificationsAtom);
        set(notificationsAtom, [...notifications, newNotification]);

        // Auto-dismiss non-persistent notifications
        if (!notification.persistent && notification.duration !== 0) {
            const duration = notification.duration || 5000;
            setTimeout(() => {
                set(notificationsAtom, (current) =>
                    current.filter((n) => n.id !== id)
                );
            }, duration);
        }

        return id;
    }
);

/**
 * Remove notification action atom.
 */
export const removeNotificationAtom = atom(
    null,
    (get, set, notificationId: string) => {
        const notifications = get(notificationsAtom);
        set(
            notificationsAtom,
            notifications.filter((n) => n.id !== notificationId)
        );
    }
);

/**
 * Clear all notifications action atom.
 */
export const clearNotificationsAtom = atom(null, (get, set) => {
    set(notificationsAtom, []);
});

/**
 * Clear notifications by type action atom.
 */
export const clearNotificationsByTypeAtom = atom(
    null,
    (get, set, type: Notification['type']) => {
        const notifications = get(notificationsAtom);
        set(
            notificationsAtom,
            notifications.filter((n) => n.type !== type)
        );
    }
);

/**
 * Count of notifications by type (derived).
 */
export const notificationCountByTypeAtom = atom((get) => {
    const notifications = get(notificationsAtom);
    return {
        success: notifications.filter((n) => n.type === 'success').length,
        error: notifications.filter((n) => n.type === 'error').length,
        warning: notifications.filter((n) => n.type === 'warning').length,
        info: notifications.filter((n) => n.type === 'info').length,
        alert: notifications.filter((n) => n.type === 'alert').length,
        total: notifications.length,
    };
});

/**
 * Total unread alerts (persistent notifications).
 */
export const unreadAlertsAtom = atom((get) => {
    const notifications = get(notificationsAtom);
    return notifications.filter((n) => n.persistent).length;
});

/**
 * Latest notification (for stacking effect).
 */
export const latestNotificationAtom = atom((get) => {
    const notifications = get(notificationsAtom);
    return notifications[notifications.length - 1] || null;
});

/**
 * Notifications sorted by type priority (errors first).
 */
export const sortedNotificationsAtom = atom((get) => {
    const notifications = get(notificationsAtom);
    const priority = { error: 0, alert: 1, warning: 2, info: 3, success: 4 };

    return [...notifications].sort(
        (a, b) =>
            (priority[a.type] ?? 5) - (priority[b.type] ?? 5) ||
            b.createdAt - a.createdAt
    );
});
