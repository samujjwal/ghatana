/**
 * Topology Accessibility Utilities
 *
 * WCAG 2.1 AA compliant accessibility utilities for topology visualizations.
 * Provides keyboard navigation, screen reader support, and focus management.
 *
 * @doc.type utilities
 * @doc.purpose Accessibility utilities for topology components
 * @doc.layer lib
 * @doc.pattern Utility
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import type { Node, Edge } from '@xyflow/react';
import type { TopologyNodeData, TopologyEdgeData } from './types';

// ============================================
// ARIA LABELS
// ============================================

/**
 * Generate accessible label for a topology node.
 */
export function getNodeAriaLabel(
    node: Node<TopologyNodeData>,
    index: number,
    totalNodes: number
): string {
    const { data } = node;
    const statusLabel = data.status
        ? `, status: ${data.status}`
        : '';
    const metricsLabel = data.metrics
        ? `, throughput: ${data.metrics.throughput} per second, latency: ${data.metrics.latencyMs}ms`
        : '';
    const descLabel = data.description
        ? `, ${data.description}`
        : '';

    return `${data.label}, ${data.nodeType} node${statusLabel}${metricsLabel}${descLabel}. Node ${index + 1} of ${totalNodes}.`;
}

/**
 * Generate accessible label for a topology edge.
 */
export function getEdgeAriaLabel(
    edge: Edge<TopologyEdgeData>,
    sourceLabel: string,
    targetLabel: string
): string {
    const { data } = edge;
    const statusLabel = data?.status
        ? `, ${data.status}`
        : '';
    const throughputLabel = data?.throughput !== undefined
        ? `, ${data.throughput} messages per second`
        : '';

    return `Connection from ${sourceLabel} to ${targetLabel}${statusLabel}${throughputLabel}`;
}

/**
 * Generate live region announcement for topology updates.
 */
export function getUpdateAnnouncement(
    updateType: 'node_added' | 'node_removed' | 'status_change' | 'metrics_update',
    nodeLabel?: string,
    details?: string
): string {
    switch (updateType) {
        case 'node_added':
            return `New node added: ${nodeLabel}${details ? `. ${details}` : ''}`;
        case 'node_removed':
            return `Node removed: ${nodeLabel}`;
        case 'status_change':
            return `Status changed for ${nodeLabel}: ${details}`;
        case 'metrics_update':
            return `Metrics updated for ${nodeLabel}: ${details}`;
        default:
            return 'Topology updated';
    }
}

// ============================================
// KEYBOARD NAVIGATION
// ============================================

export interface KeyboardNavigationOptions {
    /** Nodes in the topology */
    nodes: Node<TopologyNodeData>[];
    /** Currently focused node ID */
    focusedNodeId: string | null;
    /** Callback when focus changes */
    onFocusChange: (nodeId: string | null) => void;
    /** Callback when node is selected */
    onNodeSelect: (nodeId: string) => void;
    /** Callback when node action is triggered */
    onNodeAction?: (nodeId: string) => void;
}

/**
 * Hook for keyboard navigation in topology.
 *
 * Supports:
 * - Arrow keys for navigation between nodes
 * - Enter/Space for selection
 * - Escape to clear selection
 * - Tab for focus management
 */
export function useTopologyKeyboardNav(options: KeyboardNavigationOptions) {
    const { nodes, focusedNodeId, onFocusChange, onNodeSelect, onNodeAction } = options;
    const containerRef = useRef<HTMLDivElement>(null);

    // Sort nodes by position for logical navigation
    const sortedNodeIds = nodes
        .sort((a, b) => {
            // Sort by Y position first, then X
            if (Math.abs(a.position.y - b.position.y) > 50) {
                return a.position.y - b.position.y;
            }
            return a.position.x - b.position.x;
        })
        .map((n) => n.id);

    const handleKeyDown = useCallback(
        (event: KeyboardEvent) => {
            if (nodes.length === 0) return;

            const currentIndex = focusedNodeId
                ? sortedNodeIds.indexOf(focusedNodeId)
                : -1;

            switch (event.key) {
                case 'ArrowRight':
                case 'ArrowDown': {
                    event.preventDefault();
                    const nextIndex = (currentIndex + 1) % sortedNodeIds.length;
                    onFocusChange(sortedNodeIds[nextIndex]);
                    break;
                }

                case 'ArrowLeft':
                case 'ArrowUp': {
                    event.preventDefault();
                    const prevIndex = currentIndex <= 0
                        ? sortedNodeIds.length - 1
                        : currentIndex - 1;
                    onFocusChange(sortedNodeIds[prevIndex]);
                    break;
                }

                case 'Home': {
                    event.preventDefault();
                    onFocusChange(sortedNodeIds[0]);
                    break;
                }

                case 'End': {
                    event.preventDefault();
                    onFocusChange(sortedNodeIds[sortedNodeIds.length - 1]);
                    break;
                }

                case 'Enter':
                case ' ': {
                    event.preventDefault();
                    if (focusedNodeId) {
                        onNodeSelect(focusedNodeId);
                    }
                    break;
                }

                case 'Escape': {
                    event.preventDefault();
                    onFocusChange(null);
                    break;
                }

                case 'a': {
                    // Context action (e.g., open node menu)
                    if (event.ctrlKey || event.metaKey) return;
                    if (focusedNodeId && onNodeAction) {
                        event.preventDefault();
                        onNodeAction(focusedNodeId);
                    }
                    break;
                }
            }
        },
        [nodes, focusedNodeId, sortedNodeIds, onFocusChange, onNodeSelect, onNodeAction]
    );

    useEffect(() => {
        const container = containerRef.current;
        if (!container) return;

        container.addEventListener('keydown', handleKeyDown);
        return () => container.removeEventListener('keydown', handleKeyDown);
    }, [handleKeyDown]);

    return { containerRef };
}

// ============================================
// FOCUS MANAGEMENT
// ============================================

/**
 * Hook for managing focus within topology visualization.
 */
export function useTopologyFocus() {
    const [focusedNodeId, setFocusedNodeId] = useState<string | null>(null);
    const [isNavigating, setIsNavigating] = useState(false);
    const announcerRef = useRef<HTMLDivElement>(null);

    // Announce changes to screen readers
    const announce = useCallback((message: string, priority: 'polite' | 'assertive' = 'polite') => {
        if (announcerRef.current) {
            announcerRef.current.setAttribute('aria-live', priority);
            announcerRef.current.textContent = message;

            // Clear after announcement
            setTimeout(() => {
                if (announcerRef.current) {
                    announcerRef.current.textContent = '';
                }
            }, 1000);
        }
    }, []);

    // Handle focus change
    const handleFocusChange = useCallback((nodeId: string | null, nodeLabel?: string) => {
        setFocusedNodeId(nodeId);
        setIsNavigating(true);

        if (nodeId && nodeLabel) {
            announce(`Focused on ${nodeLabel}`);
        }

        // Reset navigation flag after a short delay
        setTimeout(() => setIsNavigating(false), 100);
    }, [announce]);

    return {
        focusedNodeId,
        isNavigating,
        handleFocusChange,
        announce,
        announcerRef,
    };
}

// ============================================
// SCREEN READER UTILITIES
// ============================================

/**
 * Component for screen reader announcements (live region).
 */
export function ScreenReaderAnnouncer({
    ref,
    label = 'Status updates',
}: {
    ref: React.RefObject<HTMLDivElement>;
    label?: string;
}) {
    return (
        <div
            ref={ref}
            role="status"
            aria-label={label}
            aria-live="polite"
            aria-atomic="true"
            className="sr-only"
        />
    );
}

/**
 * Generate summary text for screen readers about topology state.
 */
export function getTopologySummary(
    nodes: Node<TopologyNodeData>[],
    edges: Edge<TopologyEdgeData>[]
): string {
    const nodesByType: Record<string, number> = {};
    const nodesByStatus: Record<string, number> = {};

    nodes.forEach((node) => {
        const type = node.data.nodeType;
        nodesByType[type] = (nodesByType[type] || 0) + 1;

        if (node.data.status) {
            nodesByStatus[node.data.status] = (nodesByStatus[node.data.status] || 0) + 1;
        }
    });

    const typesSummary = Object.entries(nodesByType)
        .map(([type, count]) => `${count} ${type}${count > 1 ? 's' : ''}`)
        .join(', ');

    const statusSummary = Object.entries(nodesByStatus)
        .map(([status, count]) => `${count} ${status}`)
        .join(', ');

    const connectionsSummary = edges.length === 1
        ? '1 connection'
        : `${edges.length} connections`;

    let summary = `Topology with ${nodes.length} nodes: ${typesSummary}. ${connectionsSummary}.`;

    if (statusSummary) {
        summary += ` Status: ${statusSummary}.`;
    }

    return summary;
}

// ============================================
// FOCUS TRAP FOR MODALS
// ============================================

/**
 * Hook for trapping focus within a topology detail panel.
 */
export function useFocusTrap(isActive: boolean) {
    const trapRef = useRef<HTMLDivElement>(null);
    const previousFocus = useRef<HTMLElement | null>(null);

    useEffect(() => {
        if (!isActive || !trapRef.current) return;

        // Save current focus
        previousFocus.current = document.activeElement as HTMLElement;

        // Get all focusable elements
        const focusableElements = trapRef.current.querySelectorAll<HTMLElement>(
            'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );

        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        // Focus first element
        firstElement?.focus();

        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key !== 'Tab') return;

            if (e.shiftKey) {
                // Shift + Tab
                if (document.activeElement === firstElement) {
                    e.preventDefault();
                    lastElement?.focus();
                }
            } else {
                // Tab
                if (document.activeElement === lastElement) {
                    e.preventDefault();
                    firstElement?.focus();
                }
            }
        };

        document.addEventListener('keydown', handleKeyDown);

        return () => {
            document.removeEventListener('keydown', handleKeyDown);
            // Restore focus
            previousFocus.current?.focus();
        };
    }, [isActive]);

    return trapRef;
}

// ============================================
// COLOR CONTRAST UTILITIES
// ============================================

/**
 * Check if a color combination meets WCAG AA contrast ratio.
 * Requires 4.5:1 for normal text, 3:1 for large text.
 */
export function checkContrastRatio(
    foreground: string,
    background: string,
    isLargeText = false
): { ratio: number; passes: boolean } {
    const getLuminance = (hex: string): number => {
        const rgb = parseInt(hex.slice(1), 16);
        const r = ((rgb >> 16) & 0xff) / 255;
        const g = ((rgb >> 8) & 0xff) / 255;
        const b = (rgb & 0xff) / 255;

        const [R, G, B] = [r, g, b].map((c) =>
            c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
        );

        return 0.2126 * R + 0.7152 * G + 0.0722 * B;
    };

    const l1 = getLuminance(foreground);
    const l2 = getLuminance(background);

    const ratio = (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    const threshold = isLargeText ? 3 : 4.5;

    return {
        ratio: Math.round(ratio * 100) / 100,
        passes: ratio >= threshold,
    };
}

/**
 * Get accessible status colors that meet WCAG AA.
 */
export const accessibleStatusColors = {
    healthy: {
        bg: '#dcfce7', // green-100
        text: '#166534', // green-800
        border: '#22c55e', // green-500
    },
    warning: {
        bg: '#fef9c3', // yellow-100
        text: '#854d0e', // yellow-800
        border: '#eab308', // yellow-500
    },
    error: {
        bg: '#fee2e2', // red-100
        text: '#991b1b', // red-800
        border: '#ef4444', // red-500
    },
    inactive: {
        bg: '#f3f4f6', // gray-100
        text: '#374151', // gray-700
        border: '#9ca3af', // gray-400
    },
};
