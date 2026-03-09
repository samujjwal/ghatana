/**
 * Connection Status Indicator
 *
 * @doc.type component
 * @doc.purpose Visual indicator for WebSocket connection status
 * @doc.layer product
 * @doc.pattern Atom
 *
 * Purpose:
 * Displays real-time connection status with visual feedback for
 * connected, disconnected, and reconnecting states.
 *
 * Features:
 * - Animated connection indicators
 * - Last update timestamp
 * - Reconnect button for manual retry
 * - Compact design for dashboard integration
 * - Dark mode support
 */

import { memo } from 'react';
import { Wifi, WifiOff, RefreshCw } from 'lucide-react';

export interface ConnectionStatusProps {
    /** Is currently connected */
    isConnected?: boolean;
    /** Is attempting to reconnect */
    isReconnecting?: boolean;
    /** Last update timestamp */
    lastUpdate?: Date | null;
    /** Manual reconnect handler */
    onReconnect?: () => void;
    /** Compact mode (icon only) */
    compact?: boolean;
}

/**
 * Format relative time from timestamp
 */
function formatRelativeTime(date: Date): string {
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffSeconds = Math.floor(diffMs / 1000);
    const diffMinutes = Math.floor(diffSeconds / 60);
    const diffHours = Math.floor(diffMinutes / 60);

    if (diffSeconds < 10) return 'just now';
    if (diffSeconds < 60) return `${diffSeconds}s ago`;
    if (diffMinutes < 60) return `${diffMinutes}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    return date.toLocaleDateString();
}

/**
 * Connection Status Component
 *
 * @example
 * ```tsx
 * <ConnectionStatus
 *   isConnected={true}
 *   lastUpdate={new Date()}
 *   onReconnect={() => reconnect()}
 * />
 * ```
 */
export const ConnectionStatus = memo(function ConnectionStatus({
    isConnected = false,
    isReconnecting = false,
    lastUpdate,
    onReconnect,
    compact = false,
}: ConnectionStatusProps) {
    // Status indicators
    const statusConfig = {
        connected: {
            icon: Wifi,
            color: 'text-green-600 dark:text-green-400',
            bgColor: 'bg-green-100 dark:bg-green-900/30',
            label: 'Connected',
            pulse: false,
        },
        reconnecting: {
            icon: RefreshCw,
            color: 'text-amber-600 dark:text-amber-400',
            bgColor: 'bg-amber-100 dark:bg-amber-900/30',
            label: 'Reconnecting',
            pulse: true,
        },
        disconnected: {
            icon: WifiOff,
            color: 'text-red-600 dark:text-red-400',
            bgColor: 'bg-red-100 dark:bg-red-900/30',
            label: 'Disconnected',
            pulse: false,
        },
    };

    const status = isReconnecting ? 'reconnecting' : isConnected ? 'connected' : 'disconnected';
    const config = statusConfig[status];
    const Icon = config.icon;

    if (compact) {
        return (
            <div
                className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-md ${config.bgColor}`}
                title={`${config.label}${lastUpdate ? ` • Updated ${formatRelativeTime(lastUpdate)}` : ''}`}
            >
                <Icon
                    className={`h-3.5 w-3.5 ${config.color} ${config.pulse ? 'animate-spin' : ''}`}
                />
                <span className={`text-xs font-medium ${config.color}`}>{config.label}</span>
            </div>
        );
    }

    return (
        <div
            className={`inline-flex items-center gap-2 px-3 py-2 rounded-lg border ${config.bgColor} border-slate-200 dark:border-slate-700`}
        >
            {/* Status Icon */}
            <div className="relative">
                <Icon
                    className={`h-4 w-4 ${config.color} ${config.pulse ? 'animate-spin' : ''}`}
                />
                {isConnected && !isReconnecting && (
                    <span className="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full bg-green-500 animate-pulse" />
                )}
            </div>

            {/* Status Text */}
            <div className="flex flex-col min-w-0">
                <span className={`text-sm font-medium ${config.color}`}>{config.label}</span>
                {lastUpdate && (
                    <span className="text-xs text-slate-500 dark:text-neutral-400">
                        Updated {formatRelativeTime(lastUpdate)}
                    </span>
                )}
            </div>

            {/* Reconnect Button (only show when disconnected) */}
            {!isConnected && !isReconnecting && onReconnect && (
                <button
                    onClick={onReconnect}
                    className="ml-auto px-2 py-1 text-xs font-medium text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-700 rounded transition-colors"
                    aria-label="Reconnect"
                >
                    Retry
                </button>
            )}
        </div>
    );
});
