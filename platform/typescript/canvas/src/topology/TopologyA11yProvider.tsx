/**
 * Accessibility Provider for Topology Components
 *
 * Wraps topology visualizations with WCAG 2.1 AA compliant accessibility features:
 * - Screen reader announcements
 * - Keyboard navigation
 * - Focus management
 * - High contrast mode support
 *
 * @doc.type component
 * @doc.purpose Accessibility wrapper for topology visualizations
 * @doc.layer lib
 * @doc.pattern Provider
 */

import React, {
    createContext,
    useContext,
    useCallback,
    useRef,
    useState,
    useEffect,
    type ReactNode,
} from 'react';
import type { Node, Edge } from '@xyflow/react';
import type { TopologyNodeData, TopologyEdgeData } from './types';
import {
    getNodeAriaLabel,
    getTopologySummary,
    getUpdateAnnouncement,
    useTopologyFocus,
    useTopologyKeyboardNav,
} from './accessibility';

// ============================================
// CONTEXT
// ============================================

interface TopologyA11yContextValue {
    /** Currently focused node ID */
    focusedNodeId: string | null;
    /** Set focused node */
    setFocusedNode: (nodeId: string | null, label?: string) => void;
    /** Announce to screen reader */
    announce: (message: string, priority?: 'polite' | 'assertive') => void;
    /** Whether high contrast mode is enabled */
    highContrast: boolean;
    /** Toggle high contrast mode */
    toggleHighContrast: () => void;
    /** Whether reduced motion is preferred */
    reducedMotion: boolean;
    /** Get aria label for a node */
    getNodeLabel: (node: Node<TopologyNodeData>, index: number, total: number) => string;
}

const TopologyA11yContext = createContext<TopologyA11yContextValue | null>(null);

/**
 * Hook to access topology accessibility context.
 */
export function useTopologyA11y() {
    const context = useContext(TopologyA11yContext);
    if (!context) {
        throw new Error('useTopologyA11y must be used within TopologyA11yProvider');
    }
    return context;
}

// ============================================
// PROVIDER
// ============================================

export interface TopologyA11yProviderProps {
    children: ReactNode;
    /** Nodes in the topology */
    nodes: Node<TopologyNodeData>[];
    /** Edges in the topology */
    edges: Edge<TopologyEdgeData>[];
    /** Callback when node is selected via keyboard */
    onNodeSelect?: (nodeId: string) => void;
    /** Callback when node action is triggered (Enter/Space) */
    onNodeAction?: (nodeId: string) => void;
    /** Label for the topology visualization */
    ariaLabel?: string;
    /** Description for the topology */
    ariaDescription?: string;
}

/**
 * Accessibility provider for topology visualizations.
 *
 * @example
 * ```tsx
 * function MyTopology() {
 *   return (
 *     <TopologyA11yProvider
 *       nodes={nodes}
 *       edges={edges}
 *       onNodeSelect={handleSelect}
 *       ariaLabel="Event stream topology"
 *     >
 *       <ReactFlow nodes={nodes} edges={edges} />
 *     </TopologyA11yProvider>
 *   );
 * }
 * ```
 */
export function TopologyA11yProvider({
    children,
    nodes,
    edges,
    onNodeSelect,
    onNodeAction,
    ariaLabel = 'Topology visualization',
    ariaDescription,
}: TopologyA11yProviderProps) {
    // Focus management
    const { focusedNodeId, handleFocusChange, announce, announcerRef } = useTopologyFocus();

    // High contrast mode
    const [highContrast, setHighContrast] = useState(false);

    // Reduced motion preference
    const [reducedMotion, setReducedMotion] = useState(false);

    // Check reduced motion preference
    useEffect(() => {
        const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
        setReducedMotion(mediaQuery.matches);

        const handler = (e: MediaQueryListEvent) => setReducedMotion(e.matches);
        mediaQuery.addEventListener('change', handler);
        return () => mediaQuery.removeEventListener('change', handler);
    }, []);

    // Keyboard navigation
    const { containerRef } = useTopologyKeyboardNav({
        nodes,
        focusedNodeId,
        onFocusChange: (nodeId) => {
            const node = nodes.find((n) => n.id === nodeId);
            handleFocusChange(nodeId, node?.data.label);
        },
        onNodeSelect: onNodeSelect ?? (() => { }),
        onNodeAction,
    });

    // Announce topology summary on initial load
    useEffect(() => {
        if (nodes.length > 0) {
            const summary = getTopologySummary(nodes, edges);
            announce(summary);
        }
    }, []); // Only on mount

    // Announce significant changes
    const prevNodesCount = useRef(nodes.length);
    useEffect(() => {
        const diff = nodes.length - prevNodesCount.current;
        if (diff > 0) {
            announce(getUpdateAnnouncement('node_added', `${diff} node${diff > 1 ? 's' : ''}`));
        } else if (diff < 0) {
            announce(getUpdateAnnouncement('node_removed', `${Math.abs(diff)} node${Math.abs(diff) > 1 ? 's' : ''}`));
        }
        prevNodesCount.current = nodes.length;
    }, [nodes.length, announce]);

    const contextValue: TopologyA11yContextValue = {
        focusedNodeId,
        setFocusedNode: handleFocusChange,
        announce,
        highContrast,
        toggleHighContrast: () => setHighContrast((prev) => !prev),
        reducedMotion,
        getNodeLabel: getNodeAriaLabel,
    };

    return (
        <TopologyA11yContext.Provider value={contextValue}>
            <div
                ref={containerRef}
                role="application"
                aria-label={ariaLabel}
                aria-describedby={ariaDescription ? 'topology-description' : undefined}
                aria-roledescription="Interactive topology diagram"
                tabIndex={0}
                className={highContrast ? 'high-contrast' : ''}
                data-reduced-motion={reducedMotion}
            >
                {/* Hidden description for screen readers */}
                {ariaDescription && (
                    <div id="topology-description" className="sr-only">
                        {ariaDescription}
                    </div>
                )}

                {/* Keyboard instructions (shown on focus) */}
                <div className="sr-only" aria-live="polite">
                    Use arrow keys to navigate between nodes. Press Enter or Space to select a node.
                    Press Escape to clear selection.
                </div>

                {/* Live region for announcements */}
                <div
                    ref={announcerRef}
                    role="status"
                    aria-live="polite"
                    aria-atomic="true"
                    className="sr-only"
                />

                {/* Topology summary for screen readers */}
                <div className="sr-only" role="region" aria-label="Topology summary">
                    {getTopologySummary(nodes, edges)}
                </div>

                {children}
            </div>
        </TopologyA11yContext.Provider>
    );
}

// ============================================
// ACCESSIBLE NODE WRAPPER
// ============================================

export interface AccessibleNodeWrapperProps {
    nodeId: string;
    label: string;
    description?: string;
    status?: string;
    children: ReactNode;
    onClick?: () => void;
    onKeyDown?: (e: React.KeyboardEvent) => void;
}

/**
 * Wrapper component to make topology nodes accessible.
 */
export function AccessibleNodeWrapper({
    nodeId,
    label,
    description,
    status,
    children,
    onClick,
    onKeyDown,
}: AccessibleNodeWrapperProps) {
    const { focusedNodeId, setFocusedNode, announce } = useTopologyA11y();
    const isFocused = focusedNodeId === nodeId;

    const handleFocus = useCallback(() => {
        setFocusedNode(nodeId, label);
    }, [nodeId, label, setFocusedNode]);

    const handleClick = useCallback(() => {
        setFocusedNode(nodeId, label);
        onClick?.();
    }, [nodeId, label, setFocusedNode, onClick]);

    const handleKeyDown = useCallback(
        (e: React.KeyboardEvent) => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onClick?.();
                announce(`Selected ${label}`);
            }
            onKeyDown?.(e);
        },
        [onClick, label, announce, onKeyDown]
    );

    return (
        <div
            role="button"
            tabIndex={isFocused ? 0 : -1}
            aria-label={`${label}${status ? `, status: ${status}` : ''}${description ? `, ${description}` : ''}`}
            aria-pressed={isFocused}
            onFocus={handleFocus}
            onClick={handleClick}
            onKeyDown={handleKeyDown}
            data-focused={isFocused}
            className={isFocused ? 'ring-2 ring-blue-500 ring-offset-2' : ''}
        >
            {children}
        </div>
    );
}

// ============================================
// SKIP LINK
// ============================================

export interface SkipLinkProps {
    targetId: string;
    label?: string;
}

/**
 * Skip link for keyboard users to jump past topology.
 */
export function TopologySkipLink({ targetId, label = 'Skip topology' }: SkipLinkProps) {
    return (
        <a
            href={`#${targetId}`}
            className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:p-3 focus:bg-white focus:text-blue-600 focus:border focus:border-blue-600 focus:rounded"
        >
            {label}
        </a>
    );
}

// ============================================
// HIGH CONTRAST STYLES
// ============================================

/**
 * CSS class generator for high contrast mode.
 */
export function getHighContrastClasses(enabled: boolean): string {
    if (!enabled) return '';

    return [
        '[&_.node]:border-2',
        '[&_.node]:border-black',
        '[&_.node-healthy]:bg-white',
        '[&_.node-warning]:bg-yellow-200',
        '[&_.node-error]:bg-red-200',
        '[&_.edge]:stroke-black',
        '[&_.edge]:stroke-2',
    ].join(' ');
}

export default TopologyA11yProvider;
