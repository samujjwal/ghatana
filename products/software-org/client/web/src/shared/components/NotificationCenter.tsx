import React, { useState } from 'react';

/**
 * NotificationCenter - Alert and notification management component.
 *
 * <p><b>Purpose</b><br>
 * Centralized notification hub for system alerts, user actions, and updates.
 * Supports categorization, dismissal, and priority levels.
 *
 * <p><b>Features</b><br>
 * - Real-time notification updates
 * - Category filtering (alerts, actions, updates)
 * - Priority levels (critical, high, normal, low)
 * - Batch dismiss functionality
 * - Mark as read/unread
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <NotificationCenter
 *   count={5}
 *   onDismiss={(id) => console.log(id)}
 * />
 * ```
 *
 * @doc.type component
 * @doc.purpose Notification management and alerts
 * @doc.layer product
 * @doc.pattern Molecule
 */

interface Notification {
    id: string;
    title: string;
    message: string;
    category: 'alert' | 'action' | 'update';
    priority: 'critical' | 'high' | 'normal' | 'low';
    read: boolean;
    timestamp: Date;
}

interface NotificationCenterProps {
    count?: number;
    onDismiss?: (id: string) => void;
    onMarkAsRead?: (id: string) => void;
}

const priorityColors = {
    critical: 'bg-red-100 dark:bg-red-900 text-red-900 dark:text-red-100',
    high: 'bg-orange-100 dark:bg-orange-900 text-orange-900 dark:text-orange-100',
    normal: 'bg-blue-100 dark:bg-blue-900 text-blue-900 dark:text-blue-100',
    low: 'bg-slate-100 dark:bg-neutral-700 text-slate-900 dark:text-neutral-200',
};

const priorityBadgeColors = {
    critical: 'bg-red-500',
    high: 'bg-orange-500',
    normal: 'bg-blue-500',
    low: 'bg-slate-500',
};

export const NotificationCenter = React.memo(function NotificationCenter({
    onDismiss,
    onMarkAsRead,
}: NotificationCenterProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [selectedFilter, setSelectedFilter] = useState<'all' | 'alert' | 'action' | 'update'>('all');
    const [notifications, setNotifications] = useState<Notification[]>([
        {
            id: '1',
            title: 'P0 Incident Detected',
            message: 'Database performance degradation detected in production',
            category: 'alert',
            priority: 'critical',
            read: false,
            timestamp: new Date(Date.now() - 5 * 60000),
        },
        {
            id: '2',
            title: 'Workflow Completed',
            message: 'Data quality check workflow finished successfully',
            category: 'update',
            priority: 'normal',
            read: true,
            timestamp: new Date(Date.now() - 15 * 60000),
        },
        {
            id: '3',
            title: 'Action Required',
            message: 'Review and approve new model deployment',
            category: 'action',
            priority: 'high',
            read: false,
            timestamp: new Date(Date.now() - 30 * 60000),
        },
    ]);

    const unreadCount = notifications.filter((n) => !n.read).length;
    const filtered = selectedFilter === 'all'
        ? notifications
        : notifications.filter((n) => n.category === selectedFilter);

    const handleDismiss = (id: string) => {
        setNotifications((prev) => prev.filter((n) => n.id !== id));
        onDismiss?.(id);
    };

    const handleMarkAsRead = (id: string) => {
        setNotifications((prev) =>
            prev.map((n) => (n.id === id ? { ...n, read: true } : n))
        );
        onMarkAsRead?.(id);
    };

    const handleClearAll = () => {
        setNotifications([]);
    };

    return (
        <div className="relative">
            {/* Bell Icon Button */}
            <button
                onClick={() => setIsOpen(!isOpen)}
                className="relative p-2 text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                aria-label={`Notifications (${unreadCount} unread)`}
            >
                🔔
                {unreadCount > 0 && (
                    <span className="absolute top-1 right-1 inline-flex items-center justify-center w-5 h-5 text-xs font-bold text-white bg-red-500 rounded-full">
                        {unreadCount}
                    </span>
                )}
            </button>

            {/* Notification Panel */}
            {isOpen && (
                <div className="absolute right-0 top-full mt-2 w-96 bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg shadow-xl z-40">
                    {/* Header */}
                    <div className="border-b border-slate-200 dark:border-neutral-600 px-4 py-3">
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                Notifications ({unreadCount})
                            </h3>
                            {notifications.length > 0 && (
                                <button
                                    onClick={handleClearAll}
                                    className="text-xs text-slate-500 dark:text-neutral-400 hover:text-slate-700 dark:hover:text-slate-200"
                                >
                                    Clear all
                                </button>
                            )}
                        </div>

                        {/* Filter Tabs */}
                        <div className="flex gap-2 overflow-x-auto pb-2">
                            {(['all', 'alert', 'action', 'update'] as const).map((filter) => (
                                <button
                                    key={filter}
                                    onClick={() => setSelectedFilter(filter)}
                                    className={`px-3 py-1 text-sm rounded-full whitespace-nowrap transition-colors ${selectedFilter === filter
                                            ? 'bg-blue-500 text-white'
                                            : 'bg-slate-100 dark:bg-neutral-700 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-600'
                                        }`}
                                >
                                    {filter.charAt(0).toUpperCase() + filter.slice(1)}
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Notification List */}
                    <div className="max-h-96 overflow-y-auto">
                        {filtered.length === 0 ? (
                            <div className="px-4 py-8 text-center text-slate-500 dark:text-neutral-400">
                                <p>No notifications</p>
                            </div>
                        ) : (
                            filtered.map((notification) => (
                                <div
                                    key={notification.id}
                                    className={`border-b border-slate-100 dark:border-neutral-600 p-4 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors cursor-pointer ${!notification.read ? 'bg-blue-50 dark:bg-blue-900/10' : ''
                                        }`}
                                    onClick={() => handleMarkAsRead(notification.id)}
                                >
                                    <div className="flex gap-3">
                                        {/* Priority Badge */}
                                        <div
                                            className={`w-2 h-2 rounded-full mt-1 flex-shrink-0 ${priorityBadgeColors[notification.priority]
                                                }`}
                                        />

                                        {/* Content */}
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-start justify-between gap-2">
                                                <div>
                                                    <p className="font-medium text-slate-900 dark:text-neutral-100 text-sm">
                                                        {notification.title}
                                                    </p>
                                                    <p className="text-slate-600 dark:text-neutral-400 text-sm mt-1">
                                                        {notification.message}
                                                    </p>
                                                </div>
                                                <button
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        handleDismiss(notification.id);
                                                    }}
                                                    className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 flex-shrink-0"
                                                    aria-label="Dismiss notification"
                                                >
                                                    ✕
                                                </button>
                                            </div>

                                            {/* Category & Time */}
                                            <div className="flex items-center gap-2 mt-2">
                                                <span
                                                    className={`inline-block px-2 py-1 text-xs font-medium rounded ${priorityColors[notification.priority]
                                                        }`}
                                                >
                                                    {notification.priority}
                                                </span>
                                                <span className="text-xs text-slate-500 dark:text-neutral-400">
                                                    {Math.round((Date.now() - notification.timestamp.getTime()) / 60000)}m ago
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>

                    {/* Footer */}
                    {notifications.length > 0 && (
                        <div className="border-t border-slate-200 dark:border-neutral-600 px-4 py-3">
                            <button className="w-full text-center text-sm text-blue-600 dark:text-indigo-400 hover:text-blue-700 dark:hover:text-blue-300 font-medium">
                                View all notifications
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
});

export default NotificationCenter;
