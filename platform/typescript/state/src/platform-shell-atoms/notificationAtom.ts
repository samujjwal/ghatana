/**
 * Jotai atoms for in-app notifications.
 *
 * The notification centre displays system alerts, agent HITL review requests,
 * and pipeline run completions across all Ghatana product shells.
 *
 * @doc.type atoms
 * @doc.purpose Notification state atoms
 * @doc.layer shared
 */
import { atom } from 'jotai';

export type NotificationSeverity = 'info' | 'success' | 'warning' | 'error';

export interface Notification {
  id: string;
  severity: NotificationSeverity;
  title: string;
  message?: string;
  /** Source product slug, e.g. 'aep', 'data-cloud', 'yappc'. */
  source?: string;
  /** Timestamp in ISO-8601. */
  createdAt: string;
  read: boolean;
  /** Optional deep-link path to navigate on click. */
  href?: string;
}

/** All notifications (up to 100 most recent). */
export const notificationsAtom = atom<Notification[]>([]);

/** Derived: unread notification count. */
export const unreadCountAtom = atom((get) => get(notificationsAtom).filter((n) => !n.read).length);

/** Whether the notification centre panel is open. */
export const notificationPanelOpenAtom = atom<boolean>(false);

/**
 * Write-only atom — append a new notification.
 * Keeps only the 100 most recent items.
 */
export const pushNotificationAtom = atom(
  null,
  (get, set, notification: Omit<Notification, 'id' | 'read' | 'createdAt'>) => {
    const next: Notification = {
      ...notification,
      id: crypto.randomUUID(),
      read: false,
      createdAt: new Date().toISOString(),
    };
    const current = get(notificationsAtom);
    set(notificationsAtom, [next, ...current].slice(0, 100));
  },
);

/**
 * Write-only atom — mark a notification as read.
 */
export const markReadAtom = atom(null, (get, set, id: string) => {
  set(
    notificationsAtom,
    get(notificationsAtom).map((n) => (n.id === id ? { ...n, read: true } : n)),
  );
});

/**
 * Write-only atom — mark all notifications as read.
 */
export const markAllReadAtom = atom(null, (get, set) => {
  set(
    notificationsAtom,
    get(notificationsAtom).map((n) => ({ ...n, read: true })),
  );
});
