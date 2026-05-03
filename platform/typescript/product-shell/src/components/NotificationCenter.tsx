/**
 * NotificationCenter — notification trigger and panel.
 *
 * Renders a bell icon button that opens an overlay panel listing notifications.
 * Notification state is owned by the product; the shell renders it.
 *
 * @doc.type component
 * @doc.purpose Notification center trigger + panel for product shell
 * @doc.layer platform
 * @doc.pattern Molecule
 */
import React, { useState, useRef, useEffect } from 'react';
import { Bell, X, CheckCircle, AlertTriangle, Info, AlertCircle } from 'lucide-react';
import type { ProductNotification } from '../types';

interface NotificationCenterProps {
  notifications: readonly ProductNotification[];
  onAction?: (id: string, action: 'dismiss' | 'open') => void;
}

const LEVEL_ICON: Record<ProductNotification['level'], React.ReactElement> = {
  success: <CheckCircle className="h-4 w-4 text-green-500" aria-hidden="true" />,
  warning: <AlertTriangle className="h-4 w-4 text-amber-500" aria-hidden="true" />,
  error: <AlertCircle className="h-4 w-4 text-red-500" aria-hidden="true" />,
  info: <Info className="h-4 w-4 text-blue-500" aria-hidden="true" />,
};

/**
 * Notification center — bell trigger button + overlay panel.
 */
export function NotificationCenter({ notifications, onAction }: NotificationCenterProps): React.ReactElement {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const unreadCount = notifications.filter((n) => !n.read).length;

  // Close on outside click
  useEffect(() => {
    if (!isOpen) return;

    function handleClickOutside(event: MouseEvent): void {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  // Close on Escape
  useEffect(() => {
    if (!isOpen) return;

    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        aria-label={`Notifications${unreadCount > 0 ? `, ${unreadCount} unread` : ''}`}
        className="relative flex h-8 w-8 items-center justify-center rounded-full text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700"
      >
        <Bell className="h-4 w-4" aria-hidden="true" />
        {unreadCount > 0 && (
          <span
            aria-hidden="true"
            className="absolute right-1 top-1 flex h-2 w-2 rounded-full bg-red-500"
          />
        )}
      </button>

      {isOpen && (
        <div
          role="dialog"
          aria-label="Notifications"
          className="absolute right-0 mt-2 w-80 rounded-xl border border-gray-200 bg-white shadow-lg dark:border-gray-700 dark:bg-gray-800 z-50"
        >
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 dark:border-gray-700">
            <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Notifications</h2>
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              aria-label="Close notifications"
              className="rounded p-0.5 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>

          <div className="max-h-96 overflow-y-auto">
            {notifications.length === 0 ? (
              <div className="px-4 py-8 text-center text-sm text-gray-500 dark:text-gray-400">
                No notifications
              </div>
            ) : (
              <ul>
                {notifications.map((notification) => (
                  <li
                    key={notification.id}
                    className={[
                      'flex items-start gap-3 px-4 py-3 border-b border-gray-50 last:border-0 dark:border-gray-700/50',
                      !notification.read
                        ? 'bg-blue-50/40 dark:bg-blue-900/10'
                        : '',
                    ].join(' ')}
                  >
                    <span className="mt-0.5 shrink-0">{LEVEL_ICON[notification.level]}</span>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                        {notification.title}
                      </p>
                      {notification.message && (
                        <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400 line-clamp-2">
                          {notification.message}
                        </p>
                      )}
                      <p className="mt-1 text-xs text-gray-400 dark:text-gray-500">
                        {notification.timestamp}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => onAction?.(notification.id, 'dismiss')}
                      aria-label={`Dismiss notification: ${notification.title}`}
                      className="shrink-0 rounded p-0.5 text-gray-400 transition-colors hover:text-gray-600 dark:hover:text-gray-200"
                    >
                      <X className="h-3.5 w-3.5" aria-hidden="true" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
