/**
 * Notification Center Component
 *
 * Persistent in-app notification center driven by @ghatana/realtime events.
 * Subscribes to pipeline completion, governance alerts, and system events
 * via SSE/WebSocket and maintains a notification history.
 *
 * @doc.type component
 * @doc.purpose Persistent in-app notification center with realtime event subscription
 * @doc.layer frontend
 * @doc.pattern Observer, State Container
 */

import { useSSESubscription } from "@ghatana/realtime";
import {
  AlertTriangle,
  Bell,
  Check,
  CheckCircle2,
  Info,
  X,
  XCircle,
} from "lucide-react";
import React, { useCallback, useMemo, useRef, useState } from "react";
import { cn } from "../../lib/theme";

// ── Types ──────────────────────────────────────────────────────────────────────

export type NotificationSeverity = "info" | "success" | "warning" | "error";

export interface AppNotification {
  id: string;
  title: string;
  message: string;
  severity: NotificationSeverity;
  timestamp: number;
  read: boolean;
  source?: string;
  link?: string;
}

interface SSENotificationEvent {
  type: string;
  payload: {
    id?: string;
    title: string;
    message: string;
    severity?: NotificationSeverity;
    source?: string;
    link?: string;
  };
}

// ── Hook ───────────────────────────────────────────────────────────────────────

const MAX_NOTIFICATIONS = 50;

export function useNotificationCenter() {
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [isOpen, setIsOpen] = useState(false);

  const addNotification = useCallback((event: SSENotificationEvent) => {
    const notification: AppNotification = {
      id:
        event.payload.id ??
        `notif-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      title: event.payload.title,
      message: event.payload.message,
      severity: event.payload.severity ?? "info",
      timestamp: Date.now(),
      read: false,
      source: event.payload.source,
      link: event.payload.link,
    };

    setNotifications((prev) =>
      [notification, ...prev].slice(0, MAX_NOTIFICATIONS),
    );
  }, []);

  const markAsRead = useCallback((id: string) => {
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true } : n)),
    );
  }, []);

  const markAllAsRead = useCallback(() => {
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
  }, []);

  const dismiss = useCallback((id: string) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
  }, []);

  const unreadCount = useMemo(
    () => notifications.filter((n) => !n.read).length,
    [notifications],
  );

  // Subscribe to realtime notification events via SSE
  const onEventRef = useRef(addNotification);
  onEventRef.current = addNotification;

  useSSESubscription(() => {
    const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "";
    const url = `${baseUrl}/events/notifications`;
    const eventSource = new EventSource(url, { withCredentials: true });

    eventSource.onmessage = (event) => {
      try {
        const parsed: SSENotificationEvent = JSON.parse(event.data);
        onEventRef.current(parsed);
      } catch {
        // Malformed event — ignore
      }
    };

    eventSource.onerror = () => {
      // EventSource auto-reconnects; no action needed
    };

    return { close: () => eventSource.close() };
  }, []);

  return {
    notifications,
    isOpen,
    setIsOpen,
    unreadCount,
    markAsRead,
    markAllAsRead,
    dismiss,
    addNotification,
  };
}

// ── Severity Icon ──────────────────────────────────────────────────────────────

const severityConfig: Record<
  NotificationSeverity,
  { icon: React.ReactNode; color: string }
> = {
  info: { icon: <Info className="h-4 w-4" />, color: "text-blue-500" },
  success: {
    icon: <CheckCircle2 className="h-4 w-4" />,
    color: "text-green-500",
  },
  warning: {
    icon: <AlertTriangle className="h-4 w-4" />,
    color: "text-yellow-500",
  },
  error: { icon: <XCircle className="h-4 w-4" />, color: "text-red-500" },
};

// ── Notification Item ──────────────────────────────────────────────────────────

function NotificationItem({
  notification,
  onRead,
  onDismiss,
}: {
  notification: AppNotification;
  onRead: (id: string) => void;
  onDismiss: (id: string) => void;
}) {
  const { icon, color } = severityConfig[notification.severity];
  const timeAgo = formatTimeAgo(notification.timestamp);

  return (
    <div
      className={cn(
        "flex items-start gap-3 px-4 py-3 border-b border-[var(--color-border)] transition-colors",
        notification.read ? "opacity-60" : "bg-[var(--color-surface-hover)]",
      )}
    >
      <span className={color}>{icon}</span>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-[var(--color-text-primary)] truncate">
          {notification.title}
        </p>
        <p className="text-xs text-[var(--color-text-secondary)] mt-0.5 line-clamp-2">
          {notification.message}
        </p>
        <p className="text-xs text-[var(--color-text-muted)] mt-1">{timeAgo}</p>
      </div>
      <div className="flex items-center gap-1 shrink-0">
        {!notification.read && (
          <button
            onClick={() => onRead(notification.id)}
            className="p-1 rounded hover:bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)]"
            title="Mark as read"
          >
            <Check className="h-3.5 w-3.5" />
          </button>
        )}
        <button
          onClick={() => onDismiss(notification.id)}
          className="p-1 rounded hover:bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)]"
          title="Dismiss"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

// ── Panel ──────────────────────────────────────────────────────────────────────

export function NotificationPanel({
  notifications,
  onRead,
  onReadAll,
  onDismiss,
  onClose,
}: {
  notifications: AppNotification[];
  onRead: (id: string) => void;
  onReadAll: () => void;
  onDismiss: (id: string) => void;
  onClose: () => void;
}) {
  return (
    <div className="absolute right-0 top-full mt-2 w-96 max-h-[28rem] bg-[var(--color-surface)] border border-[var(--color-border)] rounded-lg shadow-xl z-50 flex flex-col overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--color-border)]">
        <h3 className="text-sm font-semibold text-[var(--color-text-primary)]">
          Notifications
        </h3>
        <div className="flex items-center gap-2">
          <button
            onClick={onReadAll}
            className="text-xs text-[var(--color-brand-primary)] hover:underline"
          >
            Mark all read
          </button>
          <button
            onClick={onClose}
            className="p-1 rounded hover:bg-[var(--color-surface-hover)]"
          >
            <X className="h-4 w-4 text-[var(--color-text-secondary)]" />
          </button>
        </div>
      </div>

      <div className="overflow-y-auto flex-1">
        {notifications.length === 0 ? (
          <div className="flex items-center justify-center py-12 text-sm text-[var(--color-text-muted)]">
            No notifications
          </div>
        ) : (
          notifications.map((n) => (
            <NotificationItem
              key={n.id}
              notification={n}
              onRead={onRead}
              onDismiss={onDismiss}
            />
          ))
        )}
      </div>
    </div>
  );
}

// ── Trigger Button ─────────────────────────────────────────────────────────────

export function NotificationTrigger({
  unreadCount,
  onClick,
}: {
  unreadCount: number;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className="relative p-2 rounded-lg hover:bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)]"
      title="Notifications"
    >
      <Bell className="h-5 w-5" />
      {unreadCount > 0 && (
        <span className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
          {unreadCount > 99 ? "99+" : unreadCount}
        </span>
      )}
    </button>
  );
}

// ── Helper ─────────────────────────────────────────────────────────────────────

function formatTimeAgo(timestamp: number): string {
  const seconds = Math.floor((Date.now() - timestamp) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}
