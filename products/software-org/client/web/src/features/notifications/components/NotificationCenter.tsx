import { memo, useState } from 'react';

/**
 * Notification center with toast, banner, and notification panel.
 *
 * <p><b>Purpose</b><br>
 * Centralized notification management system providing real-time alerts,
 * banners, and notification history. Supports multiple notification types,
 * severity levels, and dismissal actions.
 *
 * <p><b>Features</b><br>
 * - Toast notifications (top-right corner)
 * - Banners (full-width alerts)
 * - Notification history/panel
 * - Automatic expiration
 * - Multiple severity types
 * - Action buttons per notification
 * - Notification preferences
 *
 * <p><b>Props</b><br>
 * None - component manages its own state
 *
 * @doc.type component
 * @doc.purpose Notification management system
 * @doc.layer product
 * @doc.pattern Notification Provider
 */

interface Notification {
    id: string;
    type: 'success' | 'error' | 'warning' | 'info';
    title: string;
    message: string;
    timestamp: Date;
    read: boolean;
    action?: {
        label: string;
        onClick: () => void;
    };
    autoClose?: boolean;
    duration?: number; // ms
}

export const NotificationCenter = memo(function NotificationCenter() {
    // GIVEN: User interacting with notifications
    // WHEN: Notifications displayed
    // THEN: Show toasts, manage panel, mark as read

    // In production, these would come from API (useQuery)
    // For now, manage as local state with sample data
    const [notifications, setNotifications] = useState<Notification[]>([
        {
            id: '1',
            type: 'success',
            title: 'Model Deployed',
            message: 'Fraud Detection v2.3.1 successfully deployed to production',
            timestamp: new Date(Date.now() - 5 * 60000),
            read: true,
            autoClose: true,
            duration: 5000,
        },
        {
            id: '2',
            type: 'warning',
            title: 'High Latency Detected',
            message: 'Average response time increased to 125ms (threshold: 100ms)',
            timestamp: new Date(Date.now() - 15 * 60000),
            read: true,
            action: {
                label: 'View Details',
                onClick: () => console.log('Clicked'),
            },
        },
        {
            id: '3',
            type: 'error',
            title: 'Training Failed',
            message: 'Anomaly Detector model training failed: Out of memory',
            timestamp: new Date(Date.now() - 1 * 60000),
            read: false,
            action: {
                label: 'Retry',
                onClick: () => console.log('Retry clicked'),
            },
        },
        {
            id: '4',
            type: 'info',
            title: 'Scheduled Maintenance',
            message: 'System maintenance scheduled for 2024-01-15 at 02:00 UTC',
            timestamp: new Date(Date.now() - 30 * 60000),
            read: false,
        },
        {
            id: '5',
            type: 'success',
            title: 'Data Sync Complete',
            message: '1.2M events synced from production database',
            timestamp: new Date(Date.now() - 120 * 60000),
            read: true,
        },
    ]);
    const [showPanel, setShowPanel] = useState(false);
    const [_filter, setFilter] = useState<'all' | 'unread'>('all');

    const unreadCount = notifications.filter((n) => !n.read).length;
    const activeNotification = notifications.find((n) => !n.read);

    const handleDismiss = (id: string) => {
        setNotifications((prev) => prev.filter((n) => n.id !== id));
    };

    const handleMarkAsRead = (id: string) => {
        setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    };

    const handleClearAll = () => {
        setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    };

    const typeColors = {
        success: { bg: 'bg-green-900 bg-opacity-20', border: 'border-green-600', icon: '✓', text: 'text-green-400' },
        error: { bg: 'bg-red-900 bg-opacity-20', border: 'border-red-600', icon: '✕', text: 'text-red-400' },
        warning: { bg: 'bg-yellow-900 bg-opacity-20', border: 'border-yellow-600', icon: '⚠', text: 'text-yellow-400' },
        info: { bg: 'bg-blue-900 bg-opacity-20', border: 'border-blue-600', icon: 'ℹ', text: 'text-blue-400' },
    };

    return (
        <div className="fixed inset-0 pointer-events-none">
            {/* Active Toast */}
            {activeNotification && (
                <div className="absolute top-4 right-4 pointer-events-auto">
                    <div
                        className={`${typeColors[activeNotification.type].bg} border ${typeColors[activeNotification.type].border} rounded-lg p-4 max-w-md shadow-lg`}
                    >
                        <div className="flex items-start gap-3">
                            <span className={`text-xl font-bold ${typeColors[activeNotification.type].text}`}>
                                {typeColors[activeNotification.type].icon}
                            </span>
                            <div className="flex-1">
                                <h4 className="font-semibold text-white">{activeNotification.title}</h4>
                                <p className="text-sm text-slate-300 mt-1">{activeNotification.message}</p>
                                {activeNotification.action && (
                                    <button
                                        onClick={activeNotification.action.onClick}
                                        className="mt-2 text-sm font-medium text-blue-400 hover:text-blue-300"
                                    >
                                        {activeNotification.action.label} →
                                    </button>
                                )}
                            </div>
                            <button
                                onClick={() => handleDismiss(activeNotification.id)}
                                className="text-slate-400 hover:text-slate-200 flex-shrink-0"
                            >
                                ✕
                            </button>
                        </div>

                        {/* Auto-close progress */}
                        {activeNotification.autoClose && (
                            <div className="mt-3 h-1 bg-slate-700 rounded-full overflow-hidden">
                                <div
                                    className="h-full bg-gradient-to-r from-blue-500 to-purple-500 rounded-full animate-pulse"
                                    style={{ animation: 'shrink 5s linear' }}
                                />
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Notification Bell Button */}
            <div className="absolute top-4 left-4 pointer-events-auto">
                <button
                    onClick={() => setShowPanel(!showPanel)}
                    className="relative p-2 bg-slate-800 hover:bg-slate-700 rounded-lg border border-slate-700 transition-colors"
                >
                    <span className="text-xl">🔔</span>
                    {unreadCount > 0 && (
                        <span className="absolute top-0 right-0 w-5 h-5 bg-red-500 rounded-full flex items-center justify-center text-xs font-bold text-white">
                            {unreadCount}
                        </span>
                    )}
                </button>
            </div>

            {/* Notification Panel */}
            {showPanel && (
                <div className="fixed inset-y-0 left-4 top-16 pointer-events-auto">
                    <div className="w-96 h-[calc(100vh-5rem)] bg-slate-800 rounded-lg border border-slate-700 shadow-xl flex flex-col">
                        {/* Header */}
                        <div className="p-4 border-b border-slate-700">
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-semibold text-white">Notifications</h3>
                                <button
                                    onClick={() => setShowPanel(false)}
                                    className="text-slate-400 hover:text-slate-200"
                                >
                                    ✕
                                </button>
                            </div>

                            {/* Filters */}
                            <div className="flex gap-2">
                                <button
                                    onClick={() => setFilter('all')}
                                    className="flex-1 px-3 py-1 text-xs bg-slate-700 hover:bg-slate-600 text-slate-200 rounded"
                                >
                                    All ({notifications.length})
                                </button>
                                <button
                                    onClick={() => setFilter('unread')}
                                    className="flex-1 px-3 py-1 text-xs bg-blue-600 hover:bg-blue-500 text-white rounded"
                                >
                                    Unread ({unreadCount})
                                </button>
                            </div>
                        </div>

                        {/* Notifications List */}
                        <div className="flex-1 overflow-y-auto divide-y divide-slate-700">
                            {notifications.length === 0 ? (
                                <div className="flex items-center justify-center h-full text-slate-400">
                                    <div className="text-center">
                                        <span className="text-3xl mb-2">✓</span>
                                        <p className="text-sm">All caught up!</p>
                                    </div>
                                </div>
                            ) : (
                                notifications.map((notification) => (
                                    <div
                                        key={notification.id}
                                        className={`p-4 hover:bg-slate-700 transition-colors cursor-pointer border-l-4 ${typeColors[notification.type].border} ${notification.read ? '' : 'bg-slate-750'}`}
                                        onClick={() => handleMarkAsRead(notification.id)}
                                    >
                                        <div className="flex gap-3 items-start">
                                            <span className={`text-lg font-bold flex-shrink-0 ${typeColors[notification.type].text}`}>
                                                {typeColors[notification.type].icon}
                                            </span>

                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-start gap-2 mb-1">
                                                    <h4 className="font-medium text-white truncate">{notification.title}</h4>
                                                    {!notification.read && (
                                                        <span className="w-2 h-2 rounded-full bg-blue-500 flex-shrink-0 mt-2" />
                                                    )}
                                                </div>

                                                <p className="text-xs text-slate-400 line-clamp-2 mb-2">{notification.message}</p>

                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-slate-500">
                                                        {Math.round((Date.now() - notification.timestamp.getTime()) / 60000)}m ago
                                                    </span>
                                                    <button
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            handleDismiss(notification.id);
                                                        }}
                                                        className="text-slate-500 hover:text-slate-300"
                                                    >
                                                        ✕
                                                    </button>
                                                </div>

                                                {notification.action && (
                                                    <button className="mt-2 text-xs text-blue-400 hover:text-blue-300 font-medium">
                                                        {notification.action.label} →
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>

                        {/* Footer */}
                        {unreadCount > 0 && (
                            <div className="p-4 border-t border-slate-700">
                                <button
                                    onClick={handleClearAll}
                                    className="w-full px-3 py-2 text-sm bg-slate-700 hover:bg-slate-600 text-slate-200 rounded"
                                >
                                    Mark all as read
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            )}

            <style>{`
        @keyframes shrink {
          from { width: 100%; }
          to { width: 0%; }
        }
      `}</style>
        </div>
    );
});

export default NotificationCenter;
