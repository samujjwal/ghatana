/**
 * Topology Performance Utilities
 *
 * Production-grade performance optimizations for topology visualizations:
 * - Node virtualization for large graphs
 * - Memoization utilities
 * - Throttled updates for real-time streaming
 * - Render optimization hooks
 *
 * @doc.type utilities
 * @doc.purpose Performance optimization for topology components
 * @doc.layer lib
 * @doc.pattern Utility
 */

import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
    memo,
    type DependencyList,
} from 'react';
import type { Node, Edge, Viewport } from '@xyflow/react';
import type { TopologyNodeData, TopologyEdgeData } from './types';

// ============================================
// VIRTUALIZATION
// ============================================

export interface VirtualizationOptions {
    /** Viewport bounds */
    viewport: Viewport;
    /** Width of the visible area */
    width: number;
    /** Height of the visible area */
    height: number;
    /** Buffer zone around viewport (in pixels) */
    buffer?: number;
    /** Node dimensions for calculations */
    nodeDimensions?: { width: number; height: number };
}

export interface VirtualizationResult<T extends TopologyNodeData> {
    /** Nodes visible in viewport */
    visibleNodes: Node<T>[];
    /** Nodes outside viewport (for edge calculations) */
    hiddenNodes: Node<T>[];
    /** Number of virtualized (hidden) nodes */
    virtualizedCount: number;
    /** Whether virtualization is active */
    isVirtualized: boolean;
}

/**
 * Calculate which nodes are visible in the current viewport.
 * Nodes outside the viewport are virtualized (not rendered).
 */
export function virtualizeNodes<T extends TopologyNodeData>(
    nodes: Node<T>[],
    options: VirtualizationOptions
): VirtualizationResult<T> {
    const {
        viewport,
        width,
        height,
        buffer = 100,
        nodeDimensions = { width: 200, height: 100 },
    } = options;

    // Only virtualize if we have many nodes
    const VIRTUALIZATION_THRESHOLD = 50;
    if (nodes.length < VIRTUALIZATION_THRESHOLD) {
        return {
            visibleNodes: nodes,
            hiddenNodes: [],
            virtualizedCount: 0,
            isVirtualized: false,
        };
    }

    // Calculate viewport bounds in flow coordinates
    const { x: panX, y: panY, zoom } = viewport;
    const viewportBounds = {
        minX: -panX / zoom - buffer,
        maxX: (-panX + width) / zoom + buffer,
        minY: -panY / zoom - buffer,
        maxY: (-panY + height) / zoom + buffer,
    };

    const visibleNodes: Node<T>[] = [];
    const hiddenNodes: Node<T>[] = [];

    for (const node of nodes) {
        const nodeRight = node.position.x + nodeDimensions.width;
        const nodeBottom = node.position.y + nodeDimensions.height;

        const isVisible =
            node.position.x < viewportBounds.maxX &&
            nodeRight > viewportBounds.minX &&
            node.position.y < viewportBounds.maxY &&
            nodeBottom > viewportBounds.minY;

        if (isVisible) {
            visibleNodes.push(node);
        } else {
            hiddenNodes.push(node);
        }
    }

    return {
        visibleNodes,
        hiddenNodes,
        virtualizedCount: hiddenNodes.length,
        isVirtualized: hiddenNodes.length > 0,
    };
}

/**
 * Filter edges to only include those connected to visible nodes.
 */
export function virtualizeEdges<T extends TopologyEdgeData>(
    edges: Edge<T>[],
    visibleNodeIds: Set<string>
): Edge<T>[] {
    return edges.filter(
        (edge) => visibleNodeIds.has(edge.source) || visibleNodeIds.has(edge.target)
    );
}

/**
 * Hook for viewport-based virtualization.
 */
export function useVirtualization<T extends TopologyNodeData>(
    nodes: Node<T>[],
    edges: Edge<TopologyEdgeData>[],
    viewport: Viewport,
    dimensions: { width: number; height: number }
): {
    visibleNodes: Node<T>[];
    visibleEdges: Edge<TopologyEdgeData>[];
    stats: {
        totalNodes: number;
        visibleNodes: number;
        virtualizedNodes: number;
        isVirtualized: boolean;
    };
} {
    return useMemo(() => {
        const result = virtualizeNodes(nodes, {
            viewport,
            width: dimensions.width,
            height: dimensions.height,
        });

        const visibleNodeIds = new Set(result.visibleNodes.map((n) => n.id));
        const visibleEdges = virtualizeEdges(edges, visibleNodeIds);

        return {
            visibleNodes: result.visibleNodes,
            visibleEdges,
            stats: {
                totalNodes: nodes.length,
                visibleNodes: result.visibleNodes.length,
                virtualizedNodes: result.virtualizedCount,
                isVirtualized: result.isVirtualized,
            },
        };
    }, [nodes, edges, viewport, dimensions.width, dimensions.height]);
}

// ============================================
// THROTTLED UPDATES
// ============================================

export interface ThrottleOptions {
    /** Minimum time between updates (ms) */
    interval: number;
    /** Whether to call on leading edge */
    leading?: boolean;
    /** Whether to call on trailing edge */
    trailing?: boolean;
}

/**
 * Create a throttled version of a function.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function throttle<T extends (...args: any[]) => any>(
    fn: T,
    options: ThrottleOptions
): T & { cancel: () => void; flush: () => void } {
    const { interval, leading = true, trailing = true } = options;

    let lastCallTime: number | null = null;
    let lastArgs: Parameters<T> | null = null;
    let timeoutId: ReturnType<typeof setTimeout> | null = null;
    let result: ReturnType<T>;

    const invokeFunc = () => {
        if (lastArgs !== null) {
            result = fn(...lastArgs) as ReturnType<T>;
            lastArgs = null;
            lastCallTime = Date.now();
        }
    };

    const throttled = function (this: unknown, ...args: Parameters<T>): ReturnType<T> {
        const now = Date.now();
        lastArgs = args;

        if (lastCallTime === null) {
            // First call
            if (leading) {
                invokeFunc();
            } else {
                lastCallTime = now;
            }
        }

        const remaining = interval - (now - (lastCallTime ?? 0));

        if (remaining <= 0) {
            if (timeoutId !== null) {
                clearTimeout(timeoutId);
                timeoutId = null;
            }
            invokeFunc();
        } else if (timeoutId === null && trailing) {
            timeoutId = setTimeout(() => {
                timeoutId = null;
                invokeFunc();
            }, remaining);
        }

        return result;
    } as T & { cancel: () => void; flush: () => void };

    throttled.cancel = () => {
        if (timeoutId !== null) {
            clearTimeout(timeoutId);
            timeoutId = null;
        }
        lastCallTime = null;
        lastArgs = null;
    };

    throttled.flush = () => {
        if (timeoutId !== null) {
            clearTimeout(timeoutId);
            timeoutId = null;
            invokeFunc();
        }
    };

    return throttled;
}

/**
 * Hook for throttled state updates.
 * Useful for high-frequency metrics updates from WebSocket.
 */
export function useThrottledState<T>(
    initialValue: T,
    interval = 100
): [T, (value: T | ((prev: T) => T)) => void, T] {
    const [state, setState] = useState<T>(initialValue);
    const [throttledState, setThrottledState] = useState<T>(initialValue);
    const pendingRef = useRef<T>(initialValue);

    const throttledSetState = useMemo(
        () =>
            throttle(
                (value: T) => {
                    setThrottledState(value);
                },
                { interval, leading: true, trailing: true }
            ),
        [interval]
    );

    const updateState = useCallback(
        (value: T | ((prev: T) => T)) => {
            setState((prev) => {
                const nextValue = typeof value === 'function'
                    ? (value as (prev: T) => T)(prev)
                    : value;
                pendingRef.current = nextValue;
                throttledSetState(nextValue);
                return nextValue;
            });
        },
        [throttledSetState]
    );

    useEffect(() => {
        return () => {
            throttledSetState.cancel();
        };
    }, [throttledSetState]);

    return [throttledState, updateState, state];
}

/**
 * Hook for batching multiple rapid updates.
 */
export function useBatchedUpdates<T>(
    onBatch: (items: T[]) => void,
    batchInterval = 50
): (item: T) => void {
    const batchRef = useRef<T[]>([]);
    const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const flushBatch = useCallback(() => {
        if (batchRef.current.length > 0) {
            onBatch(batchRef.current);
            batchRef.current = [];
        }
        timeoutRef.current = null;
    }, [onBatch]);

    const addToBatch = useCallback(
        (item: T) => {
            batchRef.current.push(item);

            if (timeoutRef.current === null) {
                timeoutRef.current = setTimeout(flushBatch, batchInterval);
            }
        },
        [flushBatch, batchInterval]
    );

    useEffect(() => {
        return () => {
            if (timeoutRef.current !== null) {
                clearTimeout(timeoutRef.current);
                flushBatch();
            }
        };
    }, [flushBatch]);

    return addToBatch;
}

// ============================================
// MEMOIZATION
// ============================================

/**
 * Deep comparison for nodes (checks data changes).
 */
export function nodesEqual<T extends TopologyNodeData>(
    prev: Node<T>[],
    next: Node<T>[]
): boolean {
    if (prev.length !== next.length) return false;

    for (let i = 0; i < prev.length; i++) {
        const p = prev[i];
        const n = next[i];

        if (p.id !== n.id) return false;
        if (p.position.x !== n.position.x || p.position.y !== n.position.y) return false;

        // Check data changes
        if (p.data.status !== n.data.status) return false;
        if (p.data.label !== n.data.label) return false;

        // Check metrics changes
        const pm = p.data.metrics;
        const nm = n.data.metrics;
        if (pm?.throughput !== nm?.throughput) return false;
        if (pm?.latencyMs !== nm?.latencyMs) return false;
        if (pm?.errorRate !== nm?.errorRate) return false;
    }

    return true;
}

/**
 * Deep comparison for edges.
 */
export function edgesEqual<T extends TopologyEdgeData>(
    prev: Edge<T>[],
    next: Edge<T>[]
): boolean {
    if (prev.length !== next.length) return false;

    for (let i = 0; i < prev.length; i++) {
        const p = prev[i];
        const n = next[i];

        if (p.id !== n.id) return false;
        if (p.source !== n.source || p.target !== n.target) return false;
        if (p.data?.status !== n.data?.status) return false;
        if (p.data?.throughput !== n.data?.throughput) return false;
    }

    return true;
}

/**
 * Hook for memoized nodes with deep comparison.
 */
export function useMemoizedNodes<T extends TopologyNodeData>(
    nodes: Node<T>[]
): Node<T>[] {
    const prevRef = useRef<Node<T>[]>(nodes);

    if (!nodesEqual(prevRef.current, nodes)) {
        prevRef.current = nodes;
    }

    return prevRef.current;
}

/**
 * Hook for memoized edges with deep comparison.
 */
export function useMemoizedEdges<T extends TopologyEdgeData>(
    edges: Edge<T>[]
): Edge<T>[] {
    const prevRef = useRef<Edge<T>[]>(edges);

    if (!edgesEqual(prevRef.current, edges)) {
        prevRef.current = edges;
    }

    return prevRef.current;
}

// ============================================
// RENDER OPTIMIZATION
// ============================================

/**
 * Create a stable reference that only updates when needed.
 * Useful for avoiding unnecessary re-renders in ReactFlow.
 */
export function useStableCallback<T extends (...args: unknown[]) => unknown>(
    callback: T,
    deps: DependencyList
): T {
    const callbackRef = useRef<T>(callback);

    useEffect(() => {
        callbackRef.current = callback;
    });

    return useCallback(
        ((...args) => callbackRef.current(...args)) as T,
        // eslint-disable-next-line react-hooks/exhaustive-deps
        deps
    );
}

/**
 * HOC for memoizing topology node components.
 * Only re-renders when data actually changes.
 */
export function withNodeMemo<P extends { data: TopologyNodeData }>(
    Component: React.ComponentType<P>
): React.MemoExoticComponent<React.ComponentType<P>> {
    return memo(Component, (prevProps, nextProps) => {
        const { data: prevData } = prevProps;
        const { data: nextData } = nextProps;

        return (
            prevData.id === nextData.id &&
            prevData.label === nextData.label &&
            prevData.status === nextData.status &&
            prevData.metrics?.throughput === nextData.metrics?.throughput &&
            prevData.metrics?.latencyMs === nextData.metrics?.latencyMs &&
            prevData.metrics?.errorRate === nextData.metrics?.errorRate
        );
    });
}

/**
 * Performance monitoring hook for development.
 */
export function useRenderMetrics(componentName: string): void {
    const renderCount = useRef(0);
    const lastRenderTime = useRef(Date.now());

    useEffect(() => {
        renderCount.current += 1;
        const now = Date.now();
        const timeSinceLastRender = now - lastRenderTime.current;
        lastRenderTime.current = now;

        if (process.env.NODE_ENV === 'development') {
            console.debug(
                `[Render] ${componentName}: count=${renderCount.current}, interval=${timeSinceLastRender}ms`
            );
        }
    });
}

// ============================================
// REQUEST ANIMATION FRAME
// ============================================

/**
 * Hook for RAF-based updates (60fps max).
 */
export function useRAFUpdate<T>(
    value: T,
    onUpdate: (value: T) => void
): void {
    const rafRef = useRef<number | null>(null);
    const valueRef = useRef<T>(value);
    const pendingUpdate = useRef(false);

    valueRef.current = value;

    useEffect(() => {
        if (pendingUpdate.current) return;

        pendingUpdate.current = true;
        rafRef.current = requestAnimationFrame(() => {
            onUpdate(valueRef.current);
            pendingUpdate.current = false;
        });

        return () => {
            if (rafRef.current !== null) {
                cancelAnimationFrame(rafRef.current);
            }
        };
    }, [value, onUpdate]);
}

// ============================================
// PERFORMANCE STATS
// ============================================

export interface PerformanceStats {
    nodeCount: number;
    edgeCount: number;
    visibleNodes: number;
    renderTime: number;
    memoryUsage?: number;
}

/**
 * Hook for tracking performance statistics.
 */
export function usePerformanceStats(
    nodes: Node<TopologyNodeData>[],
    edges: Edge<TopologyEdgeData>[],
    visibleCount: number
): PerformanceStats {
    const renderStartRef = useRef(0);
    const [renderTime, setRenderTime] = useState(0);

    useEffect(() => {
        renderStartRef.current = performance.now();
    });

    useEffect(() => {
        const elapsed = performance.now() - renderStartRef.current;
        setRenderTime(elapsed);
    });

    return useMemo(
        () => ({
            nodeCount: nodes.length,
            edgeCount: edges.length,
            visibleNodes: visibleCount,
            renderTime,
            memoryUsage:
                typeof performance !== 'undefined' && 'memory' in performance
                    ? (performance as { memory: { usedJSHeapSize: number } }).memory.usedJSHeapSize
                    : undefined,
        }),
        [nodes.length, edges.length, visibleCount, renderTime]
    );
}
