/**
 * Base Topology Edge Component
 *
 * A foundational edge component for topology visualizations that provides:
 * - Status-based styling
 * - Flow animation for active connections
 * - Throughput visualization
 * - Accessibility support
 *
 * Used as a base for:
 * - EventCloud stream edges (Data-Cloud)
 * - Neural Map flow edges (AEP)
 *
 * @doc.type component
 * @doc.purpose Base edge component for topology visualizations
 * @doc.layer shared
 * @doc.pattern Compound Component
 */

import React, { memo, useMemo } from 'react';
import {
    BaseEdge,
    EdgeLabelRenderer,
    getBezierPath,
    getSmoothStepPath,
    getStraightPath,
    type EdgeProps,
} from '@xyflow/react';
import type { TopologyEdgeData, TopologyNodeStatus } from './types';

// ============================================
// EDGE STYLING
// ============================================

/**
 * Status-based edge colors.
 */
const EDGE_STATUS_COLORS: Record<TopologyNodeStatus, string> = {
    healthy: '#22c55e', // green-500
    warning: '#eab308', // yellow-500
    error: '#ef4444', // red-500
    inactive: '#9ca3af', // gray-400
    processing: '#3b82f6', // blue-500
    pending: '#a855f7', // purple-500
};

/**
 * Edge path types.
 */
export type EdgePathType = 'bezier' | 'smoothstep' | 'straight';

// ============================================
// ANIMATED EDGE
// ============================================

interface AnimatedEdgePathProps {
    path: string;
    color: string;
    animated: boolean;
    throughput?: number;
}

/**
 * Animated edge path with flow visualization.
 */
const AnimatedEdgePath: React.FC<AnimatedEdgePathProps> = memo(({ path, color, animated, throughput }) => {
    // Calculate animation speed based on throughput (higher throughput = faster)
    const animationDuration = useMemo(() => {
        if (!throughput || throughput <= 0) return 2;
        // Map throughput to duration (1-4 seconds, inverse relationship)
        return Math.max(0.5, Math.min(4, 4 / Math.log10(throughput + 1)));
    }, [throughput]);

    return (
        <>
            {/* Base edge */}
            <path
                d={path}
                fill="none"
                stroke={color}
                strokeWidth={2}
                className="transition-colors duration-200"
            />

            {/* Animated flow overlay */}
            {animated && (
                <path
                    d={path}
                    fill="none"
                    stroke={color}
                    strokeWidth={4}
                    strokeOpacity={0.5}
                    strokeDasharray="10 10"
                    className="animate-flow"
                    style={{
                        animation: `flowAnimation ${animationDuration}s linear infinite`,
                    }}
                />
            )}

            {/* Glow effect for active edges */}
            {animated && (
                <path
                    d={path}
                    fill="none"
                    stroke={color}
                    strokeWidth={6}
                    strokeOpacity={0.2}
                    filter="blur(3px)"
                />
            )}

            {/* Add keyframes via style tag */}
            <style>
                {`
          @keyframes flowAnimation {
            from { stroke-dashoffset: 0; }
            to { stroke-dashoffset: -20; }
          }
        `}
            </style>
        </>
    );
});

AnimatedEdgePath.displayName = 'AnimatedEdgePath';

// ============================================
// EDGE LABEL
// ============================================

interface EdgeLabelProps {
    label?: string;
    throughput?: number;
    labelX: number;
    labelY: number;
    color: string;
}

/**
 * Edge label with optional throughput display.
 */
const EdgeLabel: React.FC<EdgeLabelProps> = memo(({ label, throughput, labelX, labelY, color }) => {
    const throughputDisplay = useMemo(() => {
        if (throughput === undefined) return null;
        if (throughput >= 1000000) return `${(throughput / 1000000).toFixed(1)}M/s`;
        if (throughput >= 1000) return `${(throughput / 1000).toFixed(1)}K/s`;
        return `${throughput.toFixed(0)}/s`;
    }, [throughput]);

    if (!label && !throughputDisplay) return null;

    return (
        <EdgeLabelRenderer>
            <div
                style={{
                    position: 'absolute',
                    transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
                    pointerEvents: 'all',
                }}
                className="px-2 py-1 rounded text-xs font-medium bg-white dark:bg-gray-800 shadow-md border border-gray-200 dark:border-gray-700"
                role="note"
                aria-label={`Edge: ${label || ''} ${throughputDisplay ? `Throughput: ${throughputDisplay}` : ''}`}
            >
                {label && <span className="text-gray-700 dark:text-gray-300">{label}</span>}
                {label && throughputDisplay && <span className="mx-1 text-gray-400">|</span>}
                {throughputDisplay && (
                    <span style={{ color }} className="font-semibold">
                        {throughputDisplay}
                    </span>
                )}
            </div>
        </EdgeLabelRenderer>
    );
});

EdgeLabel.displayName = 'EdgeLabel';

// ============================================
// BASE TOPOLOGY EDGE
// ============================================

/**
 * Props for the base topology edge component.
 * Uses EdgeProps directly with proper data typing.
 */
export interface BaseTopologyEdgeProps extends EdgeProps {
    /**
     * Path type for the edge
     */
    pathType?: EdgePathType;

    /**
     * Override the animated state
     */
    animated?: boolean;

    /**
     * Show throughput on edge
     */
    showThroughput?: boolean;

    /**
     * Edge data with TopologyEdgeData structure
     */
    data?: TopologyEdgeData;
}

/**
 * Base topology edge component.
 *
 * Provides a consistent foundation for topology edges with:
 * - Status-based coloring
 * - Flow animation
 * - Throughput display
 * - Multiple path types
 *
 * @example
 * ```tsx
 * // Use directly
 * const edgeTypes = {
 *   topology: BaseTopologyEdge,
 * };
 *
 * // Or extend for specific edge types
 * const StreamEdge: React.FC<EdgeProps> = (props) => (
 *   <BaseTopologyEdge {...props} pathType="smoothstep" showThroughput />
 * );
 * ```
 */
export const BaseTopologyEdge = memo(function BaseTopologyEdge({
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    data,
    selected,
    pathType = 'bezier',
    animated: animatedOverride,
    showThroughput = true,
}: BaseTopologyEdgeProps) {
    // Cast data to TopologyEdgeData for type-safe access
    const edgeData = data as TopologyEdgeData | undefined;

    // Determine edge properties
    const status = edgeData?.status ?? 'inactive';
    const color = EDGE_STATUS_COLORS[status];
    const animated = animatedOverride ?? edgeData?.animated ?? status === 'processing';
    const throughput = showThroughput ? edgeData?.throughput : undefined;

    // Calculate path based on type
    const [path, labelX, labelY] = useMemo(() => {
        const params = {
            sourceX,
            sourceY,
            sourcePosition,
            targetX,
            targetY,
            targetPosition,
        };

        switch (pathType) {
            case 'smoothstep':
                return getSmoothStepPath(params);
            case 'straight':
                return getStraightPath(params);
            case 'bezier':
            default:
                return getBezierPath(params);
        }
    }, [sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition, pathType]);

    return (
        <g
            role="treeitem"
            aria-selected={selected ?? false}
            aria-label={`Connection${edgeData?.label ? `: ${edgeData.label}` : ''}`}
        >
            {/* Selection highlight */}
            {selected && (
                <path
                    d={path}
                    fill="none"
                    stroke="#3b82f6"
                    strokeWidth={6}
                    strokeOpacity={0.5}
                    className="transition-opacity duration-200"
                />
            )}

            {/* Main edge with animation */}
            <AnimatedEdgePath path={path} color={color} animated={animated} throughput={throughput} />

            {/* Arrow marker at end */}
            <defs>
                <marker
                    id={`arrow-${id}`}
                    viewBox="0 -5 10 10"
                    refX={8}
                    refY={0}
                    markerWidth={6}
                    markerHeight={6}
                    orient="auto"
                    fill={color}
                >
                    <path d="M0,-5L10,0L0,5" />
                </marker>
            </defs>
            <path
                d={path}
                fill="none"
                stroke="transparent"
                strokeWidth={2}
                markerEnd={`url(#arrow-${id})`}
            />

            {/* Label */}
            <EdgeLabel
                label={edgeData?.label}
                throughput={throughput}
                labelX={labelX}
                labelY={labelY}
                color={color}
            />
        </g>
    );
});

// ============================================
// EXPORTS
// ============================================

export { EDGE_STATUS_COLORS, AnimatedEdgePath, EdgeLabel };
