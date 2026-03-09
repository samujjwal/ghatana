/**
 * Base Topology Node Component
 *
 * A foundational node component for topology visualizations that provides:
 * - Status-based styling (healthy, warning, error, etc.)
 * - Metrics display
 * - Accessibility support (WCAG AA)
 * - Connection handles
 *
 * Used as a base for:
 * - EventCloud stream nodes (Data-Cloud)
 * - Neural Map agent nodes (AEP)
 *
 * @doc.type component
 * @doc.purpose Base node component for topology visualizations
 * @doc.layer shared
 * @doc.pattern Compound Component
 */

import React, { memo, useMemo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type {
    TopologyNodeData,
    TopologyNodeStatus,
    TopologyMetrics,
    TopologyNode,
} from './types';

// ============================================
// STATUS STYLING
// ============================================

/**
 * Status-based color configuration.
 */
const STATUS_COLORS: Record<TopologyNodeStatus, { bg: string; border: string; text: string; dot: string }> = {
    healthy: {
        bg: 'bg-green-50 dark:bg-green-950',
        border: 'border-green-500',
        text: 'text-green-700 dark:text-green-300',
        dot: 'bg-green-500',
    },
    warning: {
        bg: 'bg-yellow-50 dark:bg-yellow-950',
        border: 'border-yellow-500',
        text: 'text-yellow-700 dark:text-yellow-300',
        dot: 'bg-yellow-500',
    },
    error: {
        bg: 'bg-red-50 dark:bg-red-950',
        border: 'border-red-500',
        text: 'text-red-700 dark:text-red-300',
        dot: 'bg-red-500',
    },
    inactive: {
        bg: 'bg-gray-50 dark:bg-gray-800',
        border: 'border-gray-400',
        text: 'text-gray-600 dark:text-gray-400',
        dot: 'bg-gray-400',
    },
    processing: {
        bg: 'bg-blue-50 dark:bg-blue-950',
        border: 'border-blue-500',
        text: 'text-blue-700 dark:text-blue-300',
        dot: 'bg-blue-500 animate-pulse',
    },
    pending: {
        bg: 'bg-purple-50 dark:bg-purple-950',
        border: 'border-purple-500',
        text: 'text-purple-700 dark:text-purple-300',
        dot: 'bg-purple-500',
    },
};

/**
 * Status labels for accessibility.
 */
const STATUS_LABELS: Record<TopologyNodeStatus, string> = {
    healthy: 'Healthy',
    warning: 'Warning',
    error: 'Error',
    inactive: 'Inactive',
    processing: 'Processing',
    pending: 'Pending',
};

// ============================================
// METRICS DISPLAY
// ============================================

/**
 * Format metrics for display.
 */
function formatMetricValue(value: number, type: 'throughput' | 'latency' | 'percent' | 'count'): string {
    switch (type) {
        case 'throughput':
            if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M/s`;
            if (value >= 1000) return `${(value / 1000).toFixed(1)}K/s`;
            return `${value.toFixed(0)}/s`;
        case 'latency':
            if (value >= 1000) return `${(value / 1000).toFixed(2)}s`;
            return `${value.toFixed(0)}ms`;
        case 'percent':
            return `${value.toFixed(1)}%`;
        case 'count':
            if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`;
            if (value >= 1000) return `${(value / 1000).toFixed(1)}K`;
            return value.toFixed(0);
        default:
            return String(value);
    }
}

/**
 * Inline metrics display component.
 */
interface MetricsDisplayProps {
    metrics: TopologyMetrics;
    compact?: boolean;
}

const MetricsDisplay: React.FC<MetricsDisplayProps> = memo(({ metrics, compact }) => {
    const displayMetrics = useMemo(() => {
        const items: Array<{ label: string; value: string; ariaLabel: string }> = [];

        if (metrics.throughput !== undefined) {
            items.push({
                label: 'TPT',
                value: formatMetricValue(metrics.throughput, 'throughput'),
                ariaLabel: `Throughput: ${formatMetricValue(metrics.throughput, 'throughput')}`,
            });
        }

        if (metrics.latencyMs !== undefined) {
            items.push({
                label: 'LAT',
                value: formatMetricValue(metrics.latencyMs, 'latency'),
                ariaLabel: `Latency: ${formatMetricValue(metrics.latencyMs, 'latency')}`,
            });
        }

        if (metrics.errorRate !== undefined) {
            items.push({
                label: 'ERR',
                value: formatMetricValue(metrics.errorRate * 100, 'percent'),
                ariaLabel: `Error rate: ${formatMetricValue(metrics.errorRate * 100, 'percent')}`,
            });
        }

        if (metrics.queueDepth !== undefined) {
            items.push({
                label: 'Q',
                value: formatMetricValue(metrics.queueDepth, 'count'),
                ariaLabel: `Queue depth: ${metrics.queueDepth}`,
            });
        }

        return items;
    }, [metrics]);

    if (displayMetrics.length === 0) {
        return null;
    }

    return (
        <div
            className={`flex gap-2 text-xs ${compact ? 'flex-row' : 'flex-col'}`}
            role="list"
            aria-label="Node metrics"
        >
            {displayMetrics.map((metric) => (
                <div
                    key={metric.label}
                    className="flex items-center gap-1 text-gray-600 dark:text-gray-400"
                    role="listitem"
                    aria-label={metric.ariaLabel}
                >
                    <span className="font-medium">{metric.label}:</span>
                    <span>{metric.value}</span>
                </div>
            ))}
        </div>
    );
});

MetricsDisplay.displayName = 'MetricsDisplay';

// ============================================
// STATUS INDICATOR
// ============================================

interface StatusIndicatorProps {
    status: TopologyNodeStatus;
    size?: 'sm' | 'md' | 'lg';
}

const StatusIndicator: React.FC<StatusIndicatorProps> = memo(({ status, size = 'md' }) => {
    const colors = STATUS_COLORS[status];
    const sizeClasses = {
        sm: 'w-2 h-2',
        md: 'w-3 h-3',
        lg: 'w-4 h-4',
    };

    return (
        <span
            className={`rounded-full ${sizeClasses[size]} ${colors.dot}`}
            role="status"
            aria-label={`Status: ${STATUS_LABELS[status]}`}
            title={STATUS_LABELS[status]}
        />
    );
});

StatusIndicator.displayName = 'StatusIndicator';

// ============================================
// BASE TOPOLOGY NODE
// ============================================

/**
 * Props for the base topology node component.
 * Uses Omit to properly override data with TopologyNodeData.
 */
export interface BaseTopologyNodeProps extends Omit<NodeProps, 'data'> {
    /**
     * Whether to show metrics inline
     */
    showMetrics?: boolean;

    /**
     * Compact display mode
     */
    compact?: boolean;

    /**
     * Custom icon component
     */
    icon?: React.ReactNode;

    /**
     * Custom header content
     */
    header?: React.ReactNode;

    /**
     * Custom body content
     */
    body?: React.ReactNode;

    /**
     * Custom footer content
     */
    footer?: React.ReactNode;

    /**
     * Handle positions
     */
    handles?: {
        source?: Position[];
        target?: Position[];
    };

    /**
     * Additional className
     */
    className?: string;

    /**
     * Node data with TopologyNodeData structure
     */
    data: TopologyNodeData;
}

/**
 * Base topology node component.
 *
 * Provides a consistent foundation for topology nodes with:
 * - Status-based styling
 * - Metrics display
 * - Accessibility
 * - Customizable handles
 *
 * @example
 * ```tsx
 * // Use directly
 * <BaseTopologyNode {...nodeProps} showMetrics />
 *
 * // Or extend for specific node types
 * const AgentNode: React.FC<NodeProps> = (props) => (
 *   <BaseTopologyNode
 *     {...props}
 *     icon={<AgentIcon />}
 *     body={<AgentDetails agent={props.data} />}
 *   />
 * );
 * ```
 */
export const BaseTopologyNode = memo(function BaseTopologyNode({
    data,
    selected,
    showMetrics = true,
    compact = false,
    icon,
    header,
    body,
    footer,
    handles = { source: [Position.Right], target: [Position.Left] },
    className = '',
}: BaseTopologyNodeProps) {
    // Use data directly since it's required
    const status = data.status ?? 'inactive';
    const colors = STATUS_COLORS[status];
    const label = data.label ?? 'Unknown';

    return (
        <div
            className={`
        relative rounded-lg border-2 shadow-md
        transition-all duration-200 ease-in-out
        ${colors.bg} ${colors.border}
        ${selected ? 'ring-2 ring-blue-500 ring-offset-2' : ''}
        ${compact ? 'p-2 min-w-[120px]' : 'p-3 min-w-[180px]'}
        ${className}
      `}
            role="treeitem"
            aria-selected={selected ?? false}
            aria-label={`${label}, ${STATUS_LABELS[status]}`}
            tabIndex={0}
        >
            {/* Target Handles */}
            {handles.target?.map((position, index) => (
                <Handle
                    key={`target-${position}-${index}`}
                    type="target"
                    position={position}
                    id={`target-${position}-${index}`}
                    className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white dark:!border-gray-800"
                    aria-label={`Input connection ${index + 1}`}
                />
            ))}

            {/* Header */}
            <div className="flex items-center gap-2 mb-1">
                {icon && <div className="flex-shrink-0">{icon}</div>}
                <StatusIndicator status={status} size={compact ? 'sm' : 'md'} />
                {header ?? (
                    <h3
                        className={`font-semibold truncate ${compact ? 'text-sm' : 'text-base'} ${colors.text}`}
                        title={label}
                    >
                        {label}
                    </h3>
                )}
            </div>

            {/* Description (if not compact) */}
            {!compact && data.description && (
                <p className="text-xs text-gray-500 dark:text-gray-400 mb-2 line-clamp-2">{data.description}</p>
            )}

            {/* Body */}
            {body}

            {/* Metrics */}
            {showMetrics && data.metrics && <MetricsDisplay metrics={data.metrics} compact={compact} />}

            {/* Footer */}
            {footer}

            {/* Source Handles */}
            {handles.source?.map((position, index) => (
                <Handle
                    key={`source-${position}-${index}`}
                    type="source"
                    position={position}
                    id={`source-${position}-${index}`}
                    className="!w-3 !h-3 !bg-blue-500 !border-2 !border-white dark:!border-gray-800"
                    aria-label={`Output connection ${index + 1}`}
                />
            ))}
        </div>
    );
});

// ============================================
// EXPORTS
// ============================================

export { MetricsDisplay, StatusIndicator, STATUS_COLORS, STATUS_LABELS, formatMetricValue };
