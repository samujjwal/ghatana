import { ExternalLink as OpenInNew, RefreshCw as Refresh, Clock as AccessTime, Info, Hammer as Build, Bug as BugReport, CloudUpload, Shield as Security, Gauge as Speed } from 'lucide-react';
import { Card, CardContent, Typography, Box, IconButton, Tooltip, Divider, Stack, Skeleton } from '@ghatana/ui';
import React from 'react';

import { StatusBadge } from './StatusBadge';
import { wrapForTooltip } from '../../utils/accessibility';

import type { StatusType, StatusCategory } from './StatusBadge';

/**
 *
 */
export interface GateStatus {
    /** Unique identifier for the gate */
    id: string;

    /** Display name of the gate */
    name: string;

    /** Category of the gate */
    category: StatusCategory;

    /** Current status */
    status: StatusType;

    /** Timestamp of last update */
    lastUpdated: string;

    /** Optional description or message */
    message?: string;

    /** URL to view more details */
    detailsUrl?: string;

    /** URL to remediation information */
    remediationUrl?: string;

    /** Duration of the gate execution in milliseconds */
    duration?: number;

    /** Whether this gate is required for progression */
    required?: boolean;
}

/**
 *
 */
export interface GateWidgetProps {
    /** Array of gate statuses to display */
    gates: GateStatus[];

    /** Optional title for the widget */
    title?: string;

    /** Loading state */
    loading?: boolean;

    /** Whether to show compact view (single row) */
    compact?: boolean;

    /** Whether to show refresh button */
    showRefresh?: boolean;

    /** Callback when refresh is clicked */
    onRefresh?: () => void;

    /** Maximum number of gates to show before truncating */
    maxGates?: number;

    /** Custom CSS class */
    className?: string;
}

const categoryConfig = {
    build: {
        icon: Build,
        color: '#1976d2',
        label: 'Build',
    },
    test: {
        icon: BugReport,
        color: '#388e3c',
        label: 'Tests',
    },
    deploy: {
        icon: CloudUpload,
        color: '#f57c00',
        label: 'Deploy',
    },
    security: {
        icon: Security,
        color: '#d32f2f',
        label: 'Security',
    },
    quality: {
        icon: Speed,
        color: '#7b1fa2',
        label: 'Quality',
    },
    general: {
        icon: Info,
        color: '#616161',
        label: 'General',
    },
} as const;

/* Tailwind-based gate item classes replace styled() declarations */

const formatDuration = (milliseconds: number): string => {
    const seconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);

    if (hours > 0) {
        return `${hours}h ${minutes % 60}m`;
    } else if (minutes > 0) {
        return `${minutes}m ${seconds % 60}s`;
    } else {
        return `${seconds}s`;
    }
};

const formatRelativeTime = (timestamp: string): string => {
    const now = new Date();
    const date = new Date(timestamp);
    const diffMs = now.getTime() - date.getTime();

    const diffMinutes = Math.floor(diffMs / (1000 * 60));
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffDays > 0) {
        return `${diffDays}d ago`;
    } else if (diffHours > 0) {
        return `${diffHours}h ago`;
    } else if (diffMinutes > 0) {
        return `${diffMinutes}m ago`;
    } else {
        return 'Just now';
    }
};

const GateItemComponent: React.FC<{
    gate: GateStatus;
    compact?: boolean;
    /** only render relative time/duration when true to avoid duplicate matches in tests */
    showTime?: boolean;
}> = ({ gate, compact, showTime = false }) => {
    const CategoryIcon = categoryConfig[gate.category]?.icon || Info;

    if (compact) {
        return (
            <Box className="flex items-center gap-2">
                <Tooltip title={`${gate.name} - ${gate.category}`} arrow>
                    {wrapForTooltip(
                        <CategoryIcon
                            role="img"
                            aria-hidden={true}
                            className="size-4"
                            style={{ color: categoryConfig[gate.category]?.color || undefined }}
                        />,
                        // The Tooltip will sometimes add aria-label to the wrapper; provide role='img' so
                        // aria-label is valid on the wrapper element for non-interactive decorative icons.
                        { 'aria-describedby': `gate-${gate.id}-category-tooltip`, role: 'img' }
                    )}
                </Tooltip>
                <StatusBadge
                    status={gate.status}
                    category={gate.category}
                    size="sm"
                    tooltip={`${gate.name}: ${gate.message || gate.status}`}
                />
            </Box>
        );
    }

    return (
        <Box className="flex items-center justify-between p-2 rounded transition-colors duration-200 hover:bg-blue-50/50 dark:hover:bg-blue-900/10">
            <Box className="flex items-center gap-2 flex-1">
                <CategoryIcon
                    role="img"
                    aria-hidden={true}
                    className="size-[18px]"
                    style={{ color: categoryConfig[gate.category]?.color || undefined }}
                />
                <Box className="flex-1">
                    <Typography as="p" className="text-sm" fontWeight={500} noWrap>
                        {gate.name}
                    </Typography>
                    {gate.message && (
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" noWrap>
                            {gate.message}
                        </Typography>
                    )}
                </Box>
                <Box className="flex items-center gap-2">
                        <StatusBadge
                            status={gate.status}
                            category={gate.category}
                            size="sm"
                        />
                        {showTime && (
                            <Box className="flex items-center gap-1">
                                <AccessTime className="size-3.5 text-gray-400" />
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    {formatRelativeTime(gate.lastUpdated)}
                                </Typography>
                            </Box>
                        )}
                        {showTime && gate.duration && (
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                {formatDuration(gate.duration)}
                            </Typography>
                        )}
                </Box>
            </Box>
            <Box className="flex items-center gap-1">
                {gate.detailsUrl && (
                    <Tooltip title="View details" arrow>
                        {wrapForTooltip(
                            <a href={gate.detailsUrl} target="_blank" rel="noopener noreferrer" aria-label={`View details for ${gate.name}`}>
                                <IconButton
                                    size="sm"
                                    aria-label={`View details for ${gate.name}`}
                                    component="span"
                                >
                                    <OpenInNew size={16} />
                                </IconButton>
                            </a>,
                            // The wrapper may receive an aria-label from MUI Tooltip; keep aria-describedby on the
                            // wrapper but avoid assigning a role which would duplicate the inner link semantics.
                            { 'aria-describedby': `gate-${gate.id}-details-tooltip` }
                        )}
                    </Tooltip>
                )}
            </Box>
        </Box>
    );
};

const LoadingSkeleton: React.FC<{ compact?: boolean; count?: number }> = ({
    compact,
    count = 3
}) => {
    if (compact) {
        return (
            <Stack direction="row" spacing={1} alignItems="center">
                {Array.from({ length: count }).map((_, index) => (
                    <React.Fragment key={index}>
                        <Skeleton variant="circular" width={16} height={16} />
                        <Skeleton variant="rectangular" width={60} height={20} />
                    </React.Fragment>
                ))}
            </Stack>
        );
    }

    return (
        <Stack spacing={1}>
            {Array.from({ length: count }).map((_, index) => (
                <Box key={index} className="flex items-center gap-2">
                    <Skeleton variant="circular" width={18} height={18} />
                    <Skeleton variant="ghost" width="40%" />
                    <Box className="flex-1" />
                    <Skeleton variant="rectangular" width={60} height={20} />
                    <Skeleton variant="ghost" width={60} />
                    <Skeleton variant="circular" width={24} height={24} />
                </Box>
            ))}
        </Stack>
    );
};

export const GateWidget = React.forwardRef<HTMLDivElement, GateWidgetProps>(
    ({
        gates,
        title = 'Gates',
        loading = false,
        compact = false,
        showRefresh = true,
        onRefresh,
        maxGates,
        className,
        ...props
    }, ref) => {
        const displayGates = maxGates ? gates.slice(0, maxGates) : gates;
        const hasMoreGates = maxGates && gates.length > maxGates;

        // Calculate overall status
        const overallStatus: StatusType = React.useMemo(() => {
            if (gates.length === 0) return 'unknown';

            const hasError = gates.some(gate => gate.status === 'error');
            const hasWarning = gates.some(gate => gate.status === 'warning');
            const hasPending = gates.some(gate => gate.status === 'pending');
            const hasRunning = gates.some(gate => gate.status === 'running');

            if (hasError) return 'error';
            if (hasWarning) return 'warning';
            if (hasPending || hasRunning) return 'pending';

            // All gates are successful
            return 'success';
        }, [gates]);

    if (compact) {
            return (
                <Box
                    ref={ref}
                    className={['flex items-center gap-2 p-2 rounded bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700', className].filter(Boolean).join(' ')}
                    role="article"
                    {...props}
                >
                    <Typography as="span" className="text-xs text-gray-500 min-w-max" color="text.secondary">
                        {title}:
                    </Typography>
                    {loading ? (
                        <LoadingSkeleton compact count={Math.min(displayGates.length || 3, 4)} />
                    ) : (
                        <Stack direction="row" spacing={1} alignItems="center">
                            {displayGates.map((gate) => (
                                <GateItemComponent key={gate.id} gate={gate} compact />
                            ))}
                            {hasMoreGates && (
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    +{gates.length - maxGates!} more
                                </Typography>
                            )}
                        </Stack>
                    )}
                </Box>
            );
        }

        return (
            <Card ref={ref} className={['transition-all duration-200 hover:shadow-lg hover:-translate-y-px', className].filter(Boolean).join(' ')} role="article" {...props}>
                <CardContent className="p-4">
                    <Box className="flex items-center justify-between mb-2">
                        <Box className="flex items-center gap-2">
                            <Typography as="h6" component="h3">
                                {title}
                            </Typography>
                            <StatusBadge
                                status={overallStatus}
                                size="sm"
                                tooltip={`Overall status: ${gates.length} gates`}
                            />
                        </Box>
                        {showRefresh && onRefresh && (
                            <Tooltip title="Refresh gates" arrow>
                                    {wrapForTooltip(
                                        <IconButton
                                            size="sm"
                                            onClick={onRefresh}
                                            aria-label="Refresh gate status"
                                            disabled={loading}
                                        >
                                            <Refresh size={16} aria-hidden={true} />
                                        </IconButton>,
                                        { 'aria-describedby': `gate-refresh-tooltip` }
                                    )}
                            </Tooltip>
                        )}
                    </Box>

                    {loading ? (
                        <LoadingSkeleton count={displayGates.length || 3} />
                    ) : gates.length === 0 ? (
                        <Typography as="p" className="text-sm text-center py-4" color="text.secondary">
                            No gates configured
                        </Typography>
                    ) : (
                        <Stack spacing={0.5}>
                            {displayGates.map((gate, index) => (
                                <React.Fragment key={gate.id}>
                                    <GateItemComponent gate={gate} showTime={index === 0} />
                                    {index < displayGates.length - 1 && <Divider />}
                                </React.Fragment>
                            ))}
                            {hasMoreGates && (
                                <>
                                    <Divider />
                                    <Box className="text-center py-2">
                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                            +{gates.length - maxGates!} more gates
                                        </Typography>
                                    </Box>
                                </>
                            )}
                        </Stack>
                    )}
                </CardContent>
            </Card>
        );
    }
);

GateWidget.displayName = 'GateWidget';

export default GateWidget;
