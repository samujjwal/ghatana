import type { Activity } from '@/state/jotai/atoms';

/**
 * Formats a date to relative time string (e.g., "2 hours ago").
 */
function formatRelativeTime(date: Date): string {
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffSeconds = Math.floor(diffMs / 1000);
    const diffMinutes = Math.floor(diffSeconds / 60);
    const diffHours = Math.floor(diffMinutes / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffSeconds < 60) return 'just now';
    if (diffMinutes < 60) return `${diffMinutes} minute${diffMinutes !== 1 ? 's' : ''} ago`;
    if (diffHours < 24) return `${diffHours} hour${diffHours !== 1 ? 's' : ''} ago`;
    if (diffDays < 30) return `${diffDays} day${diffDays !== 1 ? 's' : ''} ago`;
    return date.toLocaleDateString();
}

/**
 * Timeline component for recent activities.
 *
 * <p><b>Purpose</b><br>
 * Displays activity history in timeline format with icons, titles,
 * descriptions, timestamps (relative), status badges, and navigation links.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <RecentActivitiesTimeline
 *     activities={recentActivities}
 *     maxItems={5}
 *     onActivityClick={handleActivityClick}
 * />
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Timeline visualization with connecting lines
 * - Status badges: success (green), failed (red), pending (amber)
 * - Relative timestamps ("2 hours ago")
 * - Quick navigation to related features
 * - Dark mode support
 * - Responsive design
 *
 * @doc.type component
 * @doc.purpose Activity history timeline
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

export interface RecentActivitiesTimelineProps {
    /** Array of recent activities */
    activities: Activity[];
    /** Maximum number of items to display (default: 5) */
    maxItems?: number;
    /** Click handler for activity navigation */
    onActivityClick?: (activity: Activity) => void;
    /** Additional CSS classes */
    className?: string;
}

/**
 * Renders a timeline of recent activities.
 */
export function RecentActivitiesTimeline({
    activities,
    maxItems = 5,
    onActivityClick,
    className = '',
}: RecentActivitiesTimelineProps) {
    const displayActivities = activities.slice(0, maxItems);

    return (
        <div className={`mb-12 ${className}`}>
            {/* Section Title */}
            <div className="flex items-center justify-between mb-6">
                <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-200">Recent Activity</h2>
                {activities.length > maxItems && (
                    <button className="text-sm text-blue-600 dark:text-indigo-400 hover:underline">
                        View all ({activities.length})
                    </button>
                )}
            </div>

            {/* Timeline */}
            <div className="space-y-6">
                {displayActivities.map((activity, index) => (
                    <div
                        key={activity.id}
                        className={`
                            relative pl-8 pb-6
                            ${index !== displayActivities.length - 1 ? 'border-l-2 border-slate-200 dark:border-neutral-600' : ''}
                        `}
                    >
                        {/* Timeline Dot */}
                        <div
                            className={`
                                absolute left-0 top-0 -ml-[9px]
                                w-4 h-4 rounded-full border-2 border-white dark:border-slate-900
                                ${getStatusDotColor(activity.status)}
                            `}
                        />

                        {/* Activity Card */}
                        <div
                            className={`
                                rounded-lg border-2 p-4
                                bg-white dark:bg-slate-900
                                ${getStatusBorderColor(activity.status)}
                                ${activity.href ? 'cursor-pointer hover:shadow-md transition-shadow' : ''}
                            `}
                            onClick={() => {
                                if (activity.href) {
                                    onActivityClick?.(activity);
                                }
                            }}
                        >
                            {/* Header: Icon + Status Badge */}
                            <div className="flex items-start justify-between mb-2">
                                <div className="flex items-center gap-3">
                                    {/* Icon */}
                                    <span className="text-2xl">{getActivityIcon(activity.type)}</span>

                                    {/* Title */}
                                    <h3 className="font-semibold text-slate-900 dark:text-neutral-200">{activity.title}</h3>
                                </div>

                                {/* Status Badge */}
                                <span
                                    className={`
                                        px-3 py-1 rounded-full text-xs font-medium
                                        ${getStatusBadgeClass(activity.status)}
                                    `}
                                >
                                    {getStatusLabel(activity.status)}
                                </span>
                            </div>

                            {/* Description */}
                            <p className="text-sm text-slate-600 dark:text-neutral-400 mb-3 ml-11">{activity.description}</p>

                            {/* Footer: Timestamp + Link */}
                            <div className="flex items-center justify-between ml-11">
                                <span className="text-xs text-slate-500 dark:text-slate-500">
                                    {formatRelativeTime(new Date(activity.timestamp))}
                                </span>

                                {activity.href && (
                                    <button className="text-xs text-blue-600 dark:text-indigo-400 hover:underline">
                                        View details →
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {/* Empty State */}
            {displayActivities.length === 0 && (
                <div className="text-center py-12 text-slate-500 dark:text-neutral-400">
                    <p className="text-lg">No recent activity</p>
                    <p className="text-sm mt-2">Your activity history will appear here as you use the platform.</p>
                </div>
            )}
        </div>
    );
}

/**
 * Returns icon for activity type.
 */
function getActivityIcon(type: Activity['type']): string {
    const icons: Record<Activity['type'], string> = {
        workflow_created: '🚀',
        simulation_run: '⚙️',
        approval_completed: '✅',
        report_generated: '📊',
        model_deployed: '🎯',
    };
    return icons[type] || '📌';
}

/**
 * Returns status dot color class.
 */
function getStatusDotColor(status: Activity['status']): string {
    const colors = {
        success: 'bg-green-500',
        failed: 'bg-red-500',
        pending: 'bg-amber-500',
    };
    return colors[status];
}

/**
 * Returns status border color class.
 */
function getStatusBorderColor(status: Activity['status']): string {
    const colors = {
        success: 'border-green-200 dark:border-green-800',
        failed: 'border-red-200 dark:border-red-800',
        pending: 'border-amber-200 dark:border-amber-800',
    };
    return colors[status];
}

/**
 * Returns status badge class.
 */
function getStatusBadgeClass(status: Activity['status']): string {
    const classes = {
        success: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
        failed: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
        pending: 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200',
    };
    return classes[status];
}

/**
 * Returns status label.
 */
function getStatusLabel(status: Activity['status']): string {
    const labels = {
        success: 'Completed',
        failed: 'Failed',
        pending: 'Pending',
    };
    return labels[status];
}
