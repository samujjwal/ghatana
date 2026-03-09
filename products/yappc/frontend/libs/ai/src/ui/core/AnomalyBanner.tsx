/**
 * Anomaly Alert Banner Component
 *
 * Displays critical anomaly alerts as a prominent banner.
 * Used to draw attention to detected anomalies requiring action.
 *
 * Features:
 * - Severity-based styling
 * - Acknowledge/Dismiss actions
 * - Expandable details
 * - Auto-dismiss timer
 * - Grouped alerts
 *
 * @doc.type component
 * @doc.purpose Anomaly alert banner display
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useEffect, useCallback } from 'react';
import { Alert, AlertTitle, Box, Button, Collapse, IconButton, LinearProgress, Stack, Typography, Chip, Surface as Paper } from '@ghatana/ui';
import { X as CloseIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, Check as AcknowledgeIcon, TrendingUp as SpikeIcon, TrendingDown as DropIcon, ShowChart as TrendIcon, AlertCircle as OutlierIcon, Report as BreachIcon } from 'lucide-react';

import type { AnomalyAlert, AnomalySeverity, AnomalyType } from '@ghatana/yappc-types';

/**
 * AnomalyBanner props
 */
export interface AnomalyBannerProps {
    /** Anomaly alerts to display */
    alerts: AnomalyAlert[];
    /** Callback when alert is acknowledged */
    onAcknowledge?: (alertId: string) => void;
    /** Callback when alert is dismissed */
    onDismiss?: (alertId: string) => void;
    /** Callback when alert is resolved */
    onResolve?: (alertId: string) => void;
    /** Auto-collapse after milliseconds (0 to disable) */
    autoCollapseMs?: number;
    /** Maximum alerts to show before grouping */
    maxVisible?: number;
    /** Show only unacknowledged alerts */
    onlyUnacknowledged?: boolean;
}

/**
 * Get icon for anomaly type
 */
const getAnomalyIcon = (type: AnomalyType) => {
    switch (type) {
        case 'spike':
            return <SpikeIcon />;
        case 'drop':
            return <DropIcon />;
        case 'trend_change':
            return <TrendIcon />;
        case 'outlier':
            return <OutlierIcon />;
        case 'pattern_break':
            return <TrendIcon />;
        case 'threshold_breach':
            return <BreachIcon />;
        default:
            return <OutlierIcon />;
    }
};

/**
 * Get severity color
 */
const getSeverityColor = (severity: AnomalySeverity): 'error' | 'warning' | 'info' => {
    switch (severity) {
        case 'critical':
        case 'high':
            return 'error';
        case 'medium':
            return 'warning';
        case 'low':
            return 'info';
        default:
            return 'info';
    }
};

/**
 * Single alert item component
 */
const AlertItem: React.FC<{
    alert: AnomalyAlert;
    onAcknowledge?: (alertId: string) => void;
    onDismiss?: (alertId: string) => void;
    onResolve?: (alertId: string) => void;
    expanded?: boolean;
    onToggleExpand?: () => void;
}> = ({ alert, onAcknowledge, onDismiss, onResolve, expanded, onToggleExpand }) => {
    const severity = getSeverityColor(alert.severity);

    return (
        <Alert
            severity={severity}
            variant="filled"
            className="mb-2"
            icon={getAnomalyIcon(alert.type)}
            action={
                <Box className="flex items-center gap-1">
                    {!alert.acknowledgedAt && onAcknowledge && (
                        <IconButton
                            size="sm"
                            tone="neutral"
                            onClick={() => onAcknowledge(alert.id)}
                            title="Acknowledge"
                        >
                            <AcknowledgeIcon size={16} />
                        </IconButton>
                    )}
                    {onToggleExpand && (
                        <IconButton size="sm" tone="neutral" onClick={onToggleExpand}>
                            {expanded ? (
                                <ExpandLessIcon size={16} />
                            ) : (
                                <ExpandMoreIcon size={16} />
                            )}
                        </IconButton>
                    )}
                    {onDismiss && (
                        <IconButton
                            size="sm"
                            tone="neutral"
                            onClick={() => onDismiss(alert.id)}
                        >
                            <CloseIcon size={16} />
                        </IconButton>
                    )}
                </Box>
            }
        >
            <AlertTitle className="flex items-center gap-2">
                {alert.title}
                <Chip
                    size="sm"
                    label={alert.severity.toUpperCase()}
                    className="ml-2 text-[0.65rem] h-[18px]" />
            </AlertTitle>
            {alert.description}

            <Collapse in={expanded}>
                <Paper
                    className="mt-4 p-4 text-inherit" style={{ backgroundColor: 'rgba(0', backgroundColor: 'rgba(255' }} >
                    <Stack spacing={1}>
                        <Box className="flex justify-between">
                            <Typography as="span" className="text-xs text-gray-500">Metric:</Typography>
                            <Typography as="span" className="text-xs text-gray-500" fontWeight="bold">
                                {alert.metricName}
                            </Typography>
                        </Box>
                        <Box className="flex justify-between">
                            <Typography as="span" className="text-xs text-gray-500">Current Value:</Typography>
                            <Typography as="span" className="text-xs text-gray-500" fontWeight="bold">
                                {alert.currentValue.toFixed(2)}
                            </Typography>
                        </Box>
                        <Box className="flex justify-between">
                            <Typography as="span" className="text-xs text-gray-500">Expected Value:</Typography>
                            <Typography as="span" className="text-xs text-gray-500" fontWeight="bold">
                                {alert.expectedValue.toFixed(2)}
                            </Typography>
                        </Box>
                        <Box className="flex justify-between">
                            <Typography as="span" className="text-xs text-gray-500">Deviation:</Typography>
                            <Typography
                                as="span" className="text-xs text-gray-500"
                                fontWeight="bold"
                                color={alert.deviation > 0 ? 'inherit' : 'inherit'}
                            >
                                {alert.deviation > 0 ? '+' : ''}
                                {(alert.deviation * 100).toFixed(1)}%
                            </Typography>
                        </Box>
                        <Box className="flex justify-between">
                            <Typography as="span" className="text-xs text-gray-500">Score:</Typography>
                            <LinearProgress
                                variant="determinate"
                                value={alert.score * 100}
                                className="w-[100px] h-[8px] rounded-2xl" style={{ backgroundColor: 'rgba(255' }} />
                        </Box>
                        <Box className="flex justify-between">
                            <Typography as="span" className="text-xs text-gray-500">Detected:</Typography>
                            <Typography as="span" className="text-xs text-gray-500">
                                {alert.detectedAt.toLocaleString()}
                            </Typography>
                        </Box>
                        {alert.acknowledgedAt && (
                            <Box className="flex justify-between">
                                <Typography as="span" className="text-xs text-gray-500">Acknowledged:</Typography>
                                <Typography as="span" className="text-xs text-gray-500">
                                    {alert.acknowledgedAt.toLocaleString()}
                                </Typography>
                            </Box>
                        )}
                    </Stack>

                    {onResolve && !alert.resolvedAt && (
                        <Button
                            size="sm"
                            variant="solid"
                            tone="neutral"
                            onClick={() => onResolve(alert.id)}
                            className="mt-4" style={{ backgroundColor: 'rgba(255' }} >
                            Mark as Resolved
                        </Button>
                    )}
                </Paper>
            </Collapse>
        </Alert>
    );
};

/**
 * AnomalyBanner Component
 */
export const AnomalyBanner: React.FC<AnomalyBannerProps> = ({
    alerts = [],
    onAcknowledge,
    onDismiss,
    onResolve,
    autoCollapseMs = 0,
    maxVisible = 3,
    onlyUnacknowledged = false,
}) => {
    const [expanded, setExpanded] = useState<Set<string>>(new Set());
    const [showAll, setShowAll] = useState(false);

    // Filter alerts
    const filteredAlerts = onlyUnacknowledged
        ? alerts.filter((a) => !a.acknowledgedAt && !a.resolvedAt)
        : alerts.filter((a) => !a.resolvedAt);

    // Sort by severity and date
    const sortedAlerts = [...filteredAlerts].sort((a, b) => {
        const severityOrder = { critical: 0, high: 1, medium: 2, low: 3 };
        if (severityOrder[a.severity] !== severityOrder[b.severity]) {
            return severityOrder[a.severity] - severityOrder[b.severity];
        }
        return b.detectedAt.getTime() - a.detectedAt.getTime();
    });

    // Visible alerts
    const visibleAlerts = showAll ? sortedAlerts : sortedAlerts.slice(0, maxVisible);
    const hiddenCount = sortedAlerts.length - maxVisible;

    // Auto-collapse
    useEffect(() => {
        if (autoCollapseMs > 0 && expanded.size > 0) {
            const timer = setTimeout(() => {
                setExpanded(new Set());
            }, autoCollapseMs);
            return () => clearTimeout(timer);
        }
    }, [autoCollapseMs, expanded]);

    const toggleExpand = useCallback((alertId: string) => {
        setExpanded((prev) => {
            const next = new Set(prev);
            if (next.has(alertId)) {
                next.delete(alertId);
            } else {
                next.add(alertId);
            }
            return next;
        });
    }, []);

    if (sortedAlerts.length === 0) {
        return null;
    }

    return (
        <Box className="w-full">
            <Stack spacing={1}>
                {visibleAlerts.map((alert) => (
                    <AlertItem
                        key={alert.id}
                        alert={alert}
                        onAcknowledge={onAcknowledge}
                        onDismiss={onDismiss}
                        onResolve={onResolve}
                        expanded={expanded.has(alert.id)}
                        onToggleExpand={() => toggleExpand(alert.id)}
                    />
                ))}

                {hiddenCount > 0 && !showAll && (
                    <Button
                        size="sm"
                        onClick={() => setShowAll(true)}
                        className="self-start"
                    >
                        Show {hiddenCount} more alerts
                    </Button>
                )}

                {showAll && hiddenCount > 0 && (
                    <Button
                        size="sm"
                        onClick={() => setShowAll(false)}
                        className="self-start"
                    >
                        Show less
                    </Button>
                )}
            </Stack>
        </Box>
    );
};

export default AnomalyBanner;
