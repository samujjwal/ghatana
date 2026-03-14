/**
 * Notification Center Component
 *
 * Component for viewing and managing real-time alerts, updates, and collaboration
 * notifications across the organization.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Grid,
    Card,
    Box,
    Chip,
    Tabs,
    Tab,
    Button,
    Typography,
    Stack,
} from '@ghatana/design-system';

/**
 * Notification summary metrics
 */
export interface NotificationMetrics {
    totalNotifications: number;
    unreadCount: number;
    urgentCount: number;
    todayCount: number;
}

/**
 * Notification item
 */
export interface Notification {
    id: string;
    title: string;
    message: string;
    type: 'alert' | 'update' | 'collaboration' | 'system' | 'approval';
    priority: 'urgent' | 'high' | 'normal' | 'low';
    category: 'task' | 'project' | 'meeting' | 'document' | 'mention' | 'approval' | 'system';
    timestamp: string;
    read: boolean;
    actionable: boolean;
    source: string;
    relatedUsers?: string[];
}

/**
 * Alert notification
 */
export interface AlertNotification {
    id: string;
    title: string;
    severity: 'critical' | 'warning' | 'info';
    message: string;
    timestamp: string;
    source: string;
    acknowledged: boolean;
    actionRequired: boolean;
}

/**
 * Collaboration activity
 */
export interface CollaborationActivity {
    id: string;
    activityType: 'comment' | 'mention' | 'share' | 'invite' | 'update';
    title: string;
    description: string;
    user: string;
    timestamp: string;
    itemType: 'document' | 'task' | 'project' | 'meeting';
    itemName: string;
    read: boolean;
}

/**
 * Approval request
 */
export interface ApprovalRequest {
    id: string;
    title: string;
    requestType: 'budget' | 'resource' | 'decision' | 'document' | 'access';
    requester: string;
    timestamp: string;
    deadline: string;
    priority: 'urgent' | 'high' | 'normal';
    status: 'pending' | 'approved' | 'rejected' | 'expired';
    description: string;
}

/**
 * Notification Center Props
 */
export interface NotificationCenterProps {
    /** Notification metrics */
    metrics: NotificationMetrics;
    /** All notifications */
    notifications: Notification[];
    /** Alert notifications */
    alerts: AlertNotification[];
    /** Collaboration activities */
    collaborationActivities: CollaborationActivity[];
    /** Approval requests */
    approvalRequests: ApprovalRequest[];
    /** Callback when notification is clicked */
    onNotificationClick?: (notificationId: string) => void;
    /** Callback when alert is clicked */
    onAlertClick?: (alertId: string) => void;
    /** Callback when collaboration activity is clicked */
    onCollaborationClick?: (activityId: string) => void;
    /** Callback when approval is clicked */
    onApprovalClick?: (approvalId: string) => void;
    /** Callback when mark all read is clicked */
    onMarkAllRead?: () => void;
    /** Callback when clear all is clicked */
    onClearAll?: () => void;
}

/**
 * Notification Center Component
 *
 * Provides comprehensive notification management with:
 * - Notification summary metrics
 * - All notifications feed with filtering
 * - Alert notifications with severity levels
 * - Collaboration activity stream
 * - Approval requests tracking
 * - Tab-based navigation (All, Alerts, Collaboration, Approvals)
 *
 * Reuses @ghatana/design-system components:
 * - Card (notification cards, alert cards, activity cards)
 * - Chip (type, priority, category, status indicators)
 * - Button (action buttons)
 *
 * @example
 * ```tsx
 * <NotificationCenter
 *   metrics={notificationMetrics}
 *   notifications={allNotifications}
 *   alerts={alertNotifications}
 *   collaborationActivities={activities}
 *   approvalRequests={approvals}
 *   onNotificationClick={(id) => handleNotification(id)}
 *   onMarkAllRead={() => markAllAsRead()}
 * />
 * ```
 */
export const NotificationCenter: React.FC<NotificationCenterProps> = ({
    metrics,
    notifications,
    alerts,
    collaborationActivities,
    approvalRequests,
    onNotificationClick,
    onAlertClick,
    onCollaborationClick,
    onApprovalClick,
    onMarkAllRead,
    onClearAll,
}) => {
    const [selectedTab, setSelectedTab] = useState<'all' | 'alerts' | 'collaboration' | 'approvals'>('all');
    const [notificationFilter, setNotificationFilter] = useState<'all' | 'unread' | 'urgent' | 'actionable'>('all');
    const [priorityFilter, setPriorityFilter] = useState<'all' | 'urgent' | 'high' | 'normal' | 'low'>('all');

    // Get type color
    const getTypeColor = (type: string): 'error' | 'warning' | 'default' => {
        switch (type) {
            case 'alert':
            case 'approval':
                return 'error';
            case 'update':
            case 'collaboration':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Get priority color
    const getPriorityColor = (priority: string): 'error' | 'warning' | 'default' => {
        switch (priority) {
            case 'urgent':
            case 'critical':
                return 'error';
            case 'high':
            case 'warning':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Get category color
    const getCategoryColor = (category: string): 'error' | 'warning' | 'default' => {
        switch (category) {
            case 'approval':
            case 'mention':
                return 'error';
            case 'task':
            case 'meeting':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'approved':
                return 'success';
            case 'pending':
                return 'warning';
            case 'rejected':
            case 'expired':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get activity type icon
    const getActivityIcon = (type: string): string => {
        switch (type) {
            case 'comment':
                return '💬';
            case 'mention':
                return '@';
            case 'share':
                return '↗';
            case 'invite':
                return '📨';
            case 'update':
                return '🔄';
            default:
                return '📢';
        }
    };

    // Format relative time
    const formatRelativeTime = (timestamp: string): string => {
        const date = new Date(timestamp);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffHours < 24) return `${diffHours}h ago`;
        if (diffDays < 7) return `${diffDays}d ago`;
        return date.toLocaleDateString();
    };

    // Format date
    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
    };

    // Filter notifications
    let filteredNotifications = notifications;
    if (notificationFilter === 'unread') {
        filteredNotifications = notifications.filter((n) => !n.read);
    } else if (notificationFilter === 'urgent') {
        filteredNotifications = notifications.filter((n) => n.priority === 'urgent');
    } else if (notificationFilter === 'actionable') {
        filteredNotifications = notifications.filter((n) => n.actionable);
    }

    if (priorityFilter !== 'all') {
        filteredNotifications = filteredNotifications.filter((n) => n.priority === priorityFilter);
    }

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Notification Center
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Alerts, updates, and collaboration notifications
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    {onMarkAllRead && (
                        <Button variant="secondary" size="md" onClick={onMarkAllRead} disabled={metrics.unreadCount === 0}>
                            Mark All Read
                        </Button>
                    )}
                    {onClearAll && (
                        <Button variant="secondary" size="md" onClick={onClearAll}>
                            Clear All
                        </Button>
                    )}
                </Stack>
            </Box>

            {/* Metrics Summary */}
            <Grid columns={4} gap={4}>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Total Notifications
                        </Typography>
                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100 mt-1">
                            {metrics.totalNotifications}
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Unread
                        </Typography>
                        <Typography variant="h5" className={`mt-1 ${metrics.unreadCount > 0 ? 'text-orange-600' : 'text-slate-900 dark:text-neutral-100'}`}>
                            {metrics.unreadCount}
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Urgent
                        </Typography>
                        <Typography variant="h5" className={`mt-1 ${metrics.urgentCount > 0 ? 'text-red-600' : 'text-slate-900 dark:text-neutral-100'}`}>
                            {metrics.urgentCount}
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Today
                        </Typography>
                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100 mt-1">
                            {metrics.todayCount}
                        </Typography>
                    </Box>
                </Card>
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`All (${notifications.length})`} value="all" />
                    <Tab label={`Alerts (${alerts.length})`} value="alerts" />
                    <Tab label={`Collaboration (${collaborationActivities.length})`} value="collaboration" />
                    <Tab label={`Approvals (${approvalRequests.length})`} value="approvals" />
                </Tabs>

                {/* All Notifications Tab */}
                {selectedTab === 'all' && (
                    <Box className="p-4">
                        {/* Notification Filters */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${notifications.length})`} color={notificationFilter === 'all' ? 'error' : 'default'} onClick={() => setNotificationFilter('all')} />
                            <Chip
                                label={`Unread (${notifications.filter((n) => !n.read).length})`}
                                color={notificationFilter === 'unread' ? 'warning' : 'default'}
                                onClick={() => setNotificationFilter('unread')}
                            />
                            <Chip
                                label={`Urgent (${notifications.filter((n) => n.priority === 'urgent').length})`}
                                color={notificationFilter === 'urgent' ? 'error' : 'default'}
                                onClick={() => setNotificationFilter('urgent')}
                            />
                            <Chip
                                label={`Actionable (${notifications.filter((n) => n.actionable).length})`}
                                color={notificationFilter === 'actionable' ? 'warning' : 'default'}
                                onClick={() => setNotificationFilter('actionable')}
                            />
                        </Stack>

                        {/* Priority Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 self-center">
                                Priority:
                            </Typography>
                            <Chip label="All" color={priorityFilter === 'all' ? 'error' : 'default'} onClick={() => setPriorityFilter('all')} size="small" />
                            <Chip label="Urgent" color={priorityFilter === 'urgent' ? 'error' : 'default'} onClick={() => setPriorityFilter('urgent')} size="small" />
                            <Chip label="High" color={priorityFilter === 'high' ? 'warning' : 'default'} onClick={() => setPriorityFilter('high')} size="small" />
                            <Chip label="Normal" color={priorityFilter === 'normal' ? 'default' : 'default'} onClick={() => setPriorityFilter('normal')} size="small" />
                            <Chip label="Low" color={priorityFilter === 'low' ? 'default' : 'default'} onClick={() => setPriorityFilter('low')} size="small" />
                        </Stack>

                        {/* Notification List */}
                        <Stack spacing={2}>
                            {filteredNotifications.map((notification) => (
                                <Card
                                    key={notification.id}
                                    className={`cursor-pointer hover:shadow-md transition-shadow ${!notification.read ? 'border-l-4 border-l-blue-500' : ''}`}
                                    onClick={() => onNotificationClick?.(notification.id)}
                                >
                                    <Box className="p-4">
                                        {/* Notification Header */}
                                        <Box className="flex items-start justify-between mb-2">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className={`${!notification.read ? 'font-bold' : ''} text-slate-900 dark:text-neutral-100`}>
                                                        {notification.title}
                                                    </Typography>
                                                    <Chip label={notification.type} color={getTypeColor(notification.type)} size="small" />
                                                    <Chip label={notification.priority} color={getPriorityColor(notification.priority)} size="small" />
                                                    {notification.category && <Chip label={notification.category} color={getCategoryColor(notification.category)} size="small" />}
                                                    {!notification.read && <Chip label="NEW" color="error" size="small" />}
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {notification.message}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Notification Footer */}
                                        <Box className="flex items-center justify-between mt-3">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                {notification.source} • {formatRelativeTime(notification.timestamp)}
                                            </Typography>
                                            {notification.actionable && (
                                                <Chip label="Action Required" color="warning" size="small" />
                                            )}
                                        </Box>

                                        {/* Related Users */}
                                        {notification.relatedUsers && notification.relatedUsers.length > 0 && (
                                            <Box className="mt-2 pt-2 border-t border-slate-200 dark:border-neutral-700">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Related: {notification.relatedUsers.join(', ')}
                                                </Typography>
                                            </Box>
                                        )}
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Alerts Tab */}
                {selectedTab === 'alerts' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            System Alerts
                        </Typography>

                        <Stack spacing={3}>
                            {alerts.map((alert) => (
                                <Card
                                    key={alert.id}
                                    className={`cursor-pointer hover:shadow-md transition-shadow border-l-4 ${alert.severity === 'critical' ? 'border-l-red-600' : alert.severity === 'warning' ? 'border-l-orange-600' : 'border-l-blue-600'
                                        }`}
                                    onClick={() => onAlertClick?.(alert.id)}
                                >
                                    <Box className="p-4">
                                        {/* Alert Header */}
                                        <Box className="flex items-start justify-between mb-2">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {alert.title}
                                                    </Typography>
                                                    <Chip label={alert.severity} color={getPriorityColor(alert.severity)} size="small" />
                                                    {alert.actionRequired && <Chip label="Action Required" color="error" size="small" />}
                                                    {!alert.acknowledged && <Chip label="Unacknowledged" color="warning" size="small" />}
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {alert.message}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Alert Footer */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                {alert.source} • {formatDate(alert.timestamp)}
                                            </Typography>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Collaboration Tab */}
                {selectedTab === 'collaboration' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Collaboration Activity
                        </Typography>

                        <Stack spacing={2}>
                            {collaborationActivities.map((activity) => (
                                <Card
                                    key={activity.id}
                                    className={`cursor-pointer hover:shadow-md transition-shadow ${!activity.read ? 'border-l-4 border-l-blue-500' : ''}`}
                                    onClick={() => onCollaborationClick?.(activity.id)}
                                >
                                    <Box className="p-4">
                                        {/* Activity Header */}
                                        <Box className="flex items-start gap-3">
                                            <Typography variant="h4" className="text-slate-600 dark:text-neutral-400">
                                                {getActivityIcon(activity.activityType)}
                                            </Typography>
                                            <Box className="flex-1">
                                                <Typography variant="h6" className={`${!activity.read ? 'font-bold' : ''} text-slate-900 dark:text-neutral-100 mb-1`}>
                                                    {activity.title}
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-2">
                                                    {activity.description}
                                                </Typography>
                                                <Box className="flex items-center gap-2">
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        {activity.user}
                                                    </Typography>
                                                    <Chip label={activity.itemType} size="small" />
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        {activity.itemName}
                                                    </Typography>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        • {formatRelativeTime(activity.timestamp)}
                                                    </Typography>
                                                </Box>
                                            </Box>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Approvals Tab */}
                {selectedTab === 'approvals' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Pending Approvals
                        </Typography>

                        <Stack spacing={3}>
                            {approvalRequests.map((approval) => (
                                <Card key={approval.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onApprovalClick?.(approval.id)}>
                                    <Box className="p-4">
                                        {/* Approval Header */}
                                        <Box className="flex items-start justify-between mb-2">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {approval.title}
                                                    </Typography>
                                                    <Chip label={approval.requestType} color={getCategoryColor(approval.requestType)} size="small" />
                                                    <Chip label={approval.priority} color={getPriorityColor(approval.priority)} size="small" />
                                                    <Chip label={approval.status} color={getStatusColor(approval.status)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {approval.description}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Approval Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={3} gap={3}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Requester
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {approval.requester}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Submitted
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {formatRelativeTime(approval.timestamp)}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Deadline
                                                    </Typography>
                                                    <Typography variant="body2" className={`${new Date(approval.deadline) < new Date() ? 'text-red-600' : 'text-slate-900 dark:text-neutral-100'}`}>
                                                        {formatDate(approval.deadline)}
                                                    </Typography>
                                                </Box>
                                            </Grid>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockNotificationCenterData = {
    metrics: {
        totalNotifications: 48,
        unreadCount: 12,
        urgentCount: 3,
        todayCount: 18,
    } as NotificationMetrics,

    notifications: [
        {
            id: 'notif-1',
            title: 'Budget Approval Required',
            message: 'Platform Engineering team requesting $50K for infrastructure upgrade',
            type: 'approval',
            priority: 'urgent',
            category: 'approval',
            timestamp: '2025-12-11T09:30:00Z',
            read: false,
            actionable: true,
            source: 'Finance System',
            relatedUsers: ['John Smith', 'Sarah Johnson'],
        },
        {
            id: 'notif-2',
            title: 'Sprint Planning Meeting',
            message: 'Sprint 24 planning meeting scheduled for tomorrow at 10 AM',
            type: 'update',
            priority: 'high',
            category: 'meeting',
            timestamp: '2025-12-11T08:15:00Z',
            read: false,
            actionable: true,
            source: 'Calendar',
            relatedUsers: ['Team Members'],
        },
        {
            id: 'notif-3',
            title: 'Document Review Complete',
            message: 'Technical specification document has been reviewed and approved',
            type: 'collaboration',
            priority: 'normal',
            category: 'document',
            timestamp: '2025-12-10T16:45:00Z',
            read: true,
            actionable: false,
            source: 'Document System',
        },
    ] as Notification[],

    alerts: [
        {
            id: 'alert-1',
            title: 'System Performance Degradation',
            severity: 'critical',
            message: 'API response times have increased by 300% in the last hour. Immediate action required.',
            timestamp: '2025-12-11T10:00:00Z',
            source: 'Monitoring System',
            acknowledged: false,
            actionRequired: true,
        },
        {
            id: 'alert-2',
            title: 'Budget Threshold Warning',
            severity: 'warning',
            message: 'Q4 budget utilization has reached 85%. Review spending to avoid overrun.',
            timestamp: '2025-12-11T07:30:00Z',
            source: 'Finance System',
            acknowledged: true,
            actionRequired: false,
        },
        {
            id: 'alert-3',
            title: 'Security Update Available',
            severity: 'info',
            message: 'Critical security patches available for deployment. Recommended installation within 48 hours.',
            timestamp: '2025-12-10T14:20:00Z',
            source: 'Security System',
            acknowledged: true,
            actionRequired: true,
        },
    ] as AlertNotification[],

    collaborationActivities: [
        {
            id: 'collab-1',
            activityType: 'mention',
            title: 'You were mentioned in a comment',
            description: 'Sarah Johnson mentioned you in "Q4 Product Roadmap" discussion',
            user: 'Sarah Johnson',
            timestamp: '2025-12-11T09:45:00Z',
            itemType: 'document',
            itemName: 'Q4 Product Roadmap',
            read: false,
        },
        {
            id: 'collab-2',
            activityType: 'comment',
            title: 'New comment on your task',
            description: 'Mike Chen commented on "API Performance Optimization" task',
            user: 'Mike Chen',
            timestamp: '2025-12-11T08:30:00Z',
            itemType: 'task',
            itemName: 'API Performance Optimization',
            read: false,
        },
        {
            id: 'collab-3',
            activityType: 'share',
            title: 'Document shared with you',
            description: 'Emily Davis shared "Architecture Review Notes" with you',
            user: 'Emily Davis',
            timestamp: '2025-12-10T17:15:00Z',
            itemType: 'document',
            itemName: 'Architecture Review Notes',
            read: true,
        },
    ] as CollaborationActivity[],

    approvalRequests: [
        {
            id: 'approval-1',
            title: 'Infrastructure Budget Request',
            requestType: 'budget',
            requester: 'John Smith',
            timestamp: '2025-12-11T09:00:00Z',
            deadline: '2025-12-13T17:00:00Z',
            priority: 'urgent',
            status: 'pending',
            description: 'Request for $50,000 budget allocation for infrastructure upgrade and scaling',
        },
        {
            id: 'approval-2',
            title: 'New Hire Access Request',
            requestType: 'access',
            requester: 'Sarah Johnson',
            timestamp: '2025-12-10T14:30:00Z',
            deadline: '2025-12-12T17:00:00Z',
            priority: 'high',
            status: 'pending',
            description: 'Access permissions for 3 new team members to production systems',
        },
        {
            id: 'approval-3',
            title: 'Strategic Initiative Proposal',
            requestType: 'decision',
            requester: 'Mike Chen',
            timestamp: '2025-12-09T11:20:00Z',
            deadline: '2025-12-15T17:00:00Z',
            priority: 'normal',
            status: 'approved',
            description: 'Proposal for AI-powered analytics platform implementation',
        },
    ] as ApprovalRequest[],
};
