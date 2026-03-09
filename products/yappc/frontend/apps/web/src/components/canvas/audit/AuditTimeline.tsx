/**
 * Audit Timeline Component
 * 
 * Displays a high-fidelity vertical timeline of project activity.
 * 
 * @doc.type component
 * @doc.purpose High-fidelity activity log visualization
 * @doc.layer product
 * @doc.pattern Vertical Timeline
 */

import React from 'react';
import { Box, Typography, Avatar, Tooltip, Stack, Divider } from '@ghatana/ui';
import { Pencil as Edit, Plus as Add, Trash2 as Delete, CheckCircle, History, User as Person, Bug as BugReport, Rocket as RocketLaunch } from 'lucide-react';

export interface AuditEvent {
    id: string;
    type: 'ARTIFACT_CREATED' | 'ARTIFACT_UPDATED' | 'ARTIFACT_DELETED' | 'TASK_COMPLETED' | 'VERSION_CREATED' | 'COMMENT_ADDED' | 'SYSTEM_ALERT';
    title: string;
    description: string;
    user: {
        name: string;
        avatar?: string;
        role: string;
    };
    timestamp: Date;
    impact: 'low' | 'medium' | 'high' | 'critical';
}

export interface AuditTimelineProps {
    events: AuditEvent[];
    loading?: boolean;
}

const EVENT_ICONS: Record<AuditEvent['type'], React.ReactNode> = {
    ARTIFACT_CREATED: <Add size={16} />,
    ARTIFACT_UPDATED: <Edit size={16} />,
    ARTIFACT_DELETED: <Delete size={16} />,
    TASK_COMPLETED: <CheckCircle size={16} />,
    VERSION_CREATED: <History size={16} />,
    COMMENT_ADDED: <Typography as="span" className="text-xs text-gray-500">💬</Typography>,
    SYSTEM_ALERT: <BugReport size={16} />,
};

const IMPACT_COLORS: Record<AuditEvent['impact'], string> = {
    low: '#9e9e9e',
    medium: '#1976d2',
    high: '#ed6c02',
    critical: '#d32f2f',
};

function formatRelativeTime(timestamp: Date): string {
    const diffMs = Date.now() - new Date(timestamp).getTime();
    const diffMinutes = Math.floor(diffMs / 60000);
    if (diffMinutes < 1) return 'just now';
    if (diffMinutes < 60) return `${diffMinutes}m ago`;
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays}d ago`;
}

/**
 * AuditTimeline Component
 */
export const AuditTimeline: React.FC<AuditTimelineProps> = ({ events, loading }) => {
    if (loading) {
        return (
            <Box className="p-6 text-center">
                <Typography as="p" className="text-sm" color="text.secondary">Loading activity...</Typography>
            </Box>
        );
    }

    if (events.length === 0) {
        return (
            <Box className="p-8 text-center opacity-[0.6]">
                <History className="mb-2 text-5xl text-gray-400 dark:text-gray-600" />
                <Typography as="p" className="text-sm" color="text.secondary">No activity recorded yet.</Typography>
            </Box>
        );
    }

    return (
        <Box className="py-2">
            {events.map((event, index) => (
                <Box
                    key={event.id}
                    className="relative pl-8 pr-4"
                    style={{
                        paddingBottom: index === events.length - 1 ? 0 : 24,
                        borderLeft: `2px solid ${IMPACT_COLORS[event.impact]}`,
                        boxShadow: '0 2px 4px rgba(0, 0, 0, 0.08)',
                    }}
                >
                    {/* Icon Circle */}
                    <Box
                        className="absolute flex items-center justify-center rounded-full left-[0px] top-[0px] w-[32px] h-[32px] border border-solid border-gray-200 dark:border-gray-700 z-[1]" >
                        {EVENT_ICONS[event.type]}
                    </Box>

                    {/* Content */}
                    <Box>
                        <Box className="flex justify-between items-start mb-1">
                            <Typography as="p" className="text-sm font-medium" fontWeight="700" className="leading-tight">
                                {event.title}
                            </Typography>
                            <Typography as="span" className="text-xs text-gray-500" color="text.disabled" className="whitespace-nowrap ml-2">
                                {formatRelativeTime(event.timestamp)}
                            </Typography>
                        </Box>

                        <Typography as="p" className="text-sm" color="text.secondary" className="mb-2 text-[0.8125rem]">
                            {event.description}
                        </Typography>

                        <Box className="flex items-center gap-2">
                            <Avatar
                                src={event.user.avatar}
                                className="text-[10px] w-[16px] h-[16px]"
                            >
                                <Person className="text-[10px]" />
                            </Avatar>
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                {event.user.name} • {event.user.role}
                            </Typography>
                        </Box>
                    </Box>
                </Box>
            ))}
        </Box>
    );
};
