import { useState, useEffect } from 'react';
import { X, AlertTriangle, CheckCircle2, Info, AlertCircle } from 'lucide-react';
import { useNavigate } from 'react-router';

/**
 * Notification System
 *
 * <p><b>Purpose</b><br>
 * Toast-style notifications for real-time alerts, incidents, and system events.
 * Supports multiple notification types with actions and auto-dismiss.
 *
 * <p><b>Features</b><br>
 * - Multiple notification types (success, error, warning, info)
 * - Auto-dismiss with configurable duration
 * - Click to navigate to related resource
 * - Stack multiple notifications
 *
 * @doc.type component
 * @doc.purpose Real-time notifications
 * @doc.layer product
 * @doc.pattern Notification
 */

export interface Notification {
    id: string;
    type: 'success' | 'error' | 'warning' | 'info';
    title: string;
    message: string;
    action?: {
        label: string;
        route: string;
    };
    duration?: number; // in milliseconds, 0 for persistent
}

interface NotificationCenterProps {
    notifications: Notification[];
    onDismiss: (id: string) => void;
}

export function NotificationCenter({ notifications, onDismiss }: NotificationCenterProps) {
    const navigate = useNavigate();

    useEffect(() => {
        notifications.forEach((notification) => {
            if (notification.duration && notification.duration > 0) {
                const timer = setTimeout(() => {
                    onDismiss(notification.id);
                }, notification.duration);
                return () => clearTimeout(timer);
            }
        });
    }, [notifications, onDismiss]);

    const getIcon = (type: Notification['type']) => {
        switch (type) {
            case 'success':
                return CheckCircle2;
            case 'error':
                return AlertCircle;
            case 'warning':
                return AlertTriangle;
            case 'info':
                return Info;
        }
    };

    const getColors = (type: Notification['type']) => {
        switch (type) {
            case 'success':
                return {
                    bg: 'bg-green-50 dark:bg-green-900/20',
                    border: 'border-green-200 dark:border-green-800',
                    icon: 'text-green-600 dark:text-green-400',
                    title: 'text-green-900 dark:text-green-100',
                };
            case 'error':
                return {
                    bg: 'bg-red-50 dark:bg-red-900/20',
                    border: 'border-red-200 dark:border-red-800',
                    icon: 'text-red-600 dark:text-red-400',
                    title: 'text-red-900 dark:text-red-100',
                };
            case 'warning':
                return {
                    bg: 'bg-amber-50 dark:bg-amber-900/20',
                    border: 'border-amber-200 dark:border-amber-800',
                    icon: 'text-amber-600 dark:text-amber-400',
                    title: 'text-amber-900 dark:text-amber-100',
                };
            case 'info':
                return {
                    bg: 'bg-blue-50 dark:bg-blue-900/20',
                    border: 'border-blue-200 dark:border-blue-800',
                    icon: 'text-blue-600 dark:text-blue-400',
                    title: 'text-blue-900 dark:text-blue-100',
                };
        }
    };

    if (notifications.length === 0) {
        return null;
    }

    return (
        <div className="fixed top-20 right-4 z-50 space-y-3 max-w-sm w-full">
            {notifications.map((notification) => {
                const Icon = getIcon(notification.type);
                const colors = getColors(notification.type);

                return (
                    <div
                        key={notification.id}
                        className={`${colors.bg} ${colors.border} border rounded-lg p-4 shadow-lg animate-slide-in`}
                    >
                        <div className="flex items-start gap-3">
                            <Icon className={`h-5 w-5 ${colors.icon} flex-shrink-0 mt-0.5`} />
                            <div className="flex-1 min-w-0">
                                <h4 className={`font-semibold ${colors.title} mb-1`}>
                                    {notification.title}
                                </h4>
                                <p className="text-sm text-slate-600 dark:text-neutral-400">
                                    {notification.message}
                                </p>
                                {notification.action && (
                                    <button
                                        onClick={() => {
                                            navigate(notification.action!.route);
                                            onDismiss(notification.id);
                                        }}
                                        className="mt-2 text-sm font-medium text-blue-600 dark:text-blue-400 hover:underline"
                                    >
                                        {notification.action.label} →
                                    </button>
                                )}
                            </div>
                            <button
                                onClick={() => onDismiss(notification.id)}
                                className="text-slate-400 hover:text-slate-600 dark:hover:text-neutral-200 transition-colors"
                            >
                                <X className="h-4 w-4" />
                            </button>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}

/**
 * Hook for managing notifications
 */
export function useNotifications() {
    const [notifications, setNotifications] = useState<Notification[]>([]);

    const addNotification = (notification: Omit<Notification, 'id'>) => {
        const id = `notification-${Date.now()}-${Math.random()}`;
        setNotifications((prev) => [
            ...prev,
            {
                ...notification,
                id,
                duration: notification.duration ?? 5000, // default 5 seconds
            },
        ]);
        return id;
    };

    const dismissNotification = (id: string) => {
        setNotifications((prev) => prev.filter((n) => n.id !== id));
    };

    const clearAll = () => {
        setNotifications([]);
    };

    return {
        notifications,
        addNotification,
        dismissNotification,
        clearAll,
    };
}
