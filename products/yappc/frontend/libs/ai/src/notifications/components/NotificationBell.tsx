/**
 * Notification Bell Component
 * 
 * Bell icon with unread count badge that opens notification panel.
 * 
 * @module notifications/components
 */

import React, { useState, useRef, useEffect } from 'react';
import { Bell } from 'lucide-react';
import { NotificationPanel } from './NotificationPanel';
import type { Notification } from '../hooks/useNotificationBackend';

export interface NotificationBellProps {
  notifications: Notification[];
  unreadCount: number;
  onRead: (notificationId: string) => void;
  onDismiss: (notificationId: string) => void;
  onMarkAllAsRead: () => void;
  onClearAll: () => void;
  onAction?: (notification: Notification) => void;
  className?: string;
}

/**
 * Notification Bell Component
 * 
 * Bell icon button with badge and dropdown panel.
 * 
 * @example
 * ```tsx
 * <NotificationBell
 *   notifications={notifications.notifications}
 *   unreadCount={notifications.unreadCount}
 *   onRead={(id) => notifications.markAsRead(id)}
 *   onDismiss={(id) => notifications.dismiss(id)}
 *   onMarkAllAsRead={() => notifications.markAllAsRead()}
 *   onClearAll={() => notifications.clearAll()}
 *   onAction={(notif) => navigate(notif.actionUrl)}
 * />
 * ```
 */
export const NotificationBell: React.FC<NotificationBellProps> = ({
  notifications,
  unreadCount,
  onRead,
  onDismiss,
  onMarkAllAsRead,
  onClearAll,
  onAction,
  className = '',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Close on click outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isOpen]);

  // Close on Escape key
  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      return () => document.removeEventListener('keydown', handleEscape);
    }
  }, [isOpen]);

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      {/* Bell button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
        title={`Notifications ${unreadCount > 0 ? `(${unreadCount} unread)` : ''}`}
      >
        <Bell className="w-5 h-5" />
        
        {/* Unread badge */}
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full text-[10px] font-bold bg-red-500 text-white">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}

        {/* Pulse animation for new notifications */}
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 w-[18px] h-[18px] rounded-full bg-red-500 animate-ping opacity-75" />
        )}
      </button>

      {/* Notification panel */}
      {isOpen && (
        <div className="absolute top-full right-0 mt-2 z-50">
          <NotificationPanel
            notifications={notifications}
            unreadCount={unreadCount}
            onRead={onRead}
            onDismiss={onDismiss}
            onMarkAllAsRead={onMarkAllAsRead}
            onClearAll={onClearAll}
            onAction={onAction}
          />
        </div>
      )}
    </div>
  );
};

export default NotificationBell;
