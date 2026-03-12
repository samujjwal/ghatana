/**
 * NotificationCenter — bell icon with unread badge and a dismissible panel.
 *
 * Severity colours:
 *   info → blue   |   warning → amber   |   error → red   |   success → green
 *
 * @doc.type component
 * @doc.purpose In-app notification viewer for the platform shell navigation bar
 * @doc.layer shared
 * @doc.pattern PresentationalComponent
 */
import React from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import {
  notificationsAtom,
  unreadCountAtom,
  notificationPanelOpenAtom,
  markReadAtom,
  markAllReadAtom,
  type Notification,
  type NotificationSeverity,
} from '../atoms/notificationAtom';

/* ─── severity styling ─────────────────────────────────────────────────────── */

const SEVERITY_BORDER: Record<NotificationSeverity, string> = {
  info: 'border-blue-500',
  success: 'border-green-500',
  warning: 'border-amber-500',
  error: 'border-red-500',
};

const SEVERITY_DOT: Record<NotificationSeverity, string> = {
  info: 'bg-blue-500',
  success: 'bg-green-500',
  warning: 'bg-amber-500',
  error: 'bg-red-500',
};

/* ─── sub-components ───────────────────────────────────────────────────────── */

function NotificationItem({ item }: { item: Notification }) {
  const markRead = useSetAtom(markReadAtom);

  return (
    <div
      className={[
        'flex items-start gap-3 p-3 border-l-4 rounded-r',
        SEVERITY_BORDER[item.severity],
        item.read ? 'opacity-60' : 'bg-white dark:bg-gray-800',
      ].join(' ')}
    >
      <span
        className={['mt-1.5 h-2 w-2 rounded-full flex-shrink-0', SEVERITY_DOT[item.severity]].join(' ')}
        aria-hidden
      />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
          {item.title}
        </p>
        {item.message && (
          <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5 line-clamp-2">
            {item.message}
          </p>
        )}
        <time className="text-[10px] text-gray-400 dark:text-gray-500">
          {new Date(item.createdAt).toLocaleTimeString()}
        </time>
      </div>
      {!item.read && (
        <button
          className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline flex-shrink-0"
          onClick={() => markRead(item.id)}
          aria-label={`Mark "${item.title}" as read`}
        >
          Dismiss
        </button>
      )}
    </div>
  );
}

/* ─── main component ───────────────────────────────────────────────────────── */

export interface NotificationCenterProps {
  /** Maximum panel height; defaults to 80 vh. */
  maxHeight?: string;
}

/**
 * Bell icon button for the nav bar.
 *
 * Shows an unread badge.  Clicking opens a slide-down panel listing all
 * notifications, newest first.
 */
export function NotificationCenter({ maxHeight = '80vh' }: NotificationCenterProps) {
  const notifications = useAtomValue(notificationsAtom);
  const unread = useAtomValue(unreadCountAtom);
  const [open, setOpen] = useAtom(notificationPanelOpenAtom);
  const markAll = useSetAtom(markAllReadAtom);

  return (
    <div className="relative">
      {/* Bell button */}
      <button
        aria-label={unread > 0 ? `${unread} unread notifications` : 'Notifications'}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="relative p-2 rounded-md text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-500"
      >
        {/* Bell icon (inline SVG, no dependency) */}
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="h-5 w-5"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.8}
          aria-hidden
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6 6 0 10-12 0v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>
        {unread > 0 && (
          <span
            className="absolute top-1 right-1 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white"
            aria-hidden
          >
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {/* Panel */}
      {open && (
        <div
          role="dialog"
          aria-label="Notifications"
          className="absolute right-0 z-50 mt-2 w-80 sm:w-96 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 shadow-xl overflow-hidden"
        >
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-2 border-b border-gray-200 dark:border-gray-700">
            <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-200">
              Notifications
              {unread > 0 && (
                <span className="ml-2 text-xs text-gray-400">({unread} unread)</span>
              )}
            </h2>
            {unread > 0 && (
              <button
                onClick={() => markAll()}
                className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline"
              >
                Mark all read
              </button>
            )}
          </div>

          {/* List */}
          <div
            className="overflow-y-auto divide-y divide-gray-100 dark:divide-gray-800"
            style={{ maxHeight }}
          >
            {notifications.length === 0 ? (
              <p className="py-8 text-center text-sm text-gray-400">
                No notifications
              </p>
            ) : (
              notifications.map((n) => <NotificationItem key={n.id} item={n} />)
            )}
          </div>
        </div>
      )}
    </div>
  );
}
