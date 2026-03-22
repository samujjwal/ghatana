/**
 * Phase 3: Performance Optimization for Generic Canvas
 * Advanced performance features including virtual scrolling, viewport culling, and memory optimization
 */

import React, { useMemo, useCallback, useRef, useEffect, useState } from 'react';

import type { BaseItem } from '../core/types';

// Performance monitoring hook
/**
 *
 */
export interface PerformanceMetrics {
    renderTime: number;
    itemCount: number;
    visibleItemCount: number;
    memoryUsage: number;
    fps: number;
    lastUpdate: number;
}

/**
 *
 */
export function usePerformanceMonitor<T extends BaseItem>(): {
    metrics: PerformanceMetrics;
    startMeasurement: (label: string) => void;
    endMeasurement: (label: string) => void;
    recordRender: (itemCount: number, visibleCount: number) => void;
} {
    const [metrics, setMetrics] = useState<PerformanceMetrics>({
        renderTime: 0,
        itemCount: 0,
        visibleItemCount: 0,
        memoryUsage: 0,
        fps: 0,
        lastUpdate: Date.now()
    });

    const measurements = useRef<Map<string, number>>(new Map());
    const frameCount = useRef(0);
    const lastFpsUpdate = useRef(Date.now());

    const startMeasurement = useCallback((label: string) => {
        measurements.current.set(label, performance.now());
    }, []);

    const endMeasurement = useCallback((label: string) => {
        const start = measurements.current.get(label);
        if (start) {
            const duration = performance.now() - start;
            if (label === 'render') {
                setMetrics(prev => ({ ...prev, renderTime: duration }));
            }
            measurements.current.delete(label);
        }
    }, []);

    const recordRender = useCallback((itemCount: number, visibleCount: number) => {
        frameCount.current++;
        const now = Date.now();

        // Update FPS every second
        if (now - lastFpsUpdate.current >= 1000) {
            const fps = frameCount.current;
            frameCount.current = 0;
            lastFpsUpdate.current = now;

            setMetrics(prev => ({
                ...prev,
                itemCount,
                visibleItemCount: visibleCount,
                fps,
                lastUpdate: now,
                memoryUsage: (performance as unknown).memory?.usedJSHeapSize || 0
            }));
        }
    }, []);

    return { metrics, startMeasurement, endMeasurement, recordRender };
}

// Virtual scrolling configuration
/**
 *
 */
export interface VirtualScrollConfig {
    itemHeight: number;
    containerHeight: number;
    overscan: number; // Number of items to render outside viewport
    estimatedItemCount?: number;
}

// Virtual scrolling hook
/**
 *
 */
export function useVirtualScrolling<T extends BaseItem>(
    items: T[],
    config: VirtualScrollConfig
) {
    const [scrollTop, setScrollTop] = useState(0);
    const containerRef = useRef<HTMLDivElement>(null);

    const { itemHeight, containerHeight, overscan } = config;
    const itemCount = items.length;

    const visibleRange = useMemo(() => {
        const startIndex = Math.max(0, Math.floor(scrollTop / itemHeight) - overscan);
        const endIndex = Math.min(
            itemCount - 1,
            Math.floor((scrollTop + containerHeight) / itemHeight) + overscan
        );
        return { startIndex, endIndex };
    }, [scrollTop, itemHeight, containerHeight, overscan, itemCount]);

    const visibleItems = useMemo(() => {
        return items.slice(visibleRange.startIndex, visibleRange.endIndex + 1);
    }, [items, visibleRange]);

    const totalHeight = itemCount * itemHeight;
    const offsetY = visibleRange.startIndex * itemHeight;

    const handleScroll = useCallback((event: React.UIEvent<HTMLDivElement>) => {
        setScrollTop(event.currentTarget.scrollTop);
    }, []);

    return {
        containerRef,
        visibleItems,
        totalHeight,
        offsetY,
        visibleRange,
        handleScroll,
        itemCount: visibleItems.length
    };
}

// Viewport culling for canvas items
/**
 *
 */
export interface ViewportBounds {
    left: number;
    top: number;
    right: number;
    bottom: number;
}

/**
 *
 */
export interface ViewportCullingConfig {
    viewport: ViewportBounds;
    padding: number; // Extra padding around viewport
}

/**
 *
 */
export function useViewportCulling<T extends BaseItem>(
    items: T[],
    config: ViewportCullingConfig,
    getItemBounds: (item: T) => ViewportBounds
) {
    const { viewport, padding } = config;

    const culledViewport = useMemo(() => ({
        left: viewport.left - padding,
        top: viewport.top - padding,
        right: viewport.right + padding,
        bottom: viewport.bottom + padding
    }), [viewport, padding]);

    const visibleItems = useMemo(() => {
        return items.filter(item => {
            const bounds = getItemBounds(item);
            return (
                bounds.right >= culledViewport.left &&
                bounds.left <= culledViewport.right &&
                bounds.bottom >= culledViewport.top &&
                bounds.top <= culledViewport.bottom
            );
        });
    }, [items, culledViewport, getItemBounds]);

    const culledItemsCount = items.length - visibleItems.length;

    return {
        visibleItems,
        culledItemsCount,
        totalItemsCount: items.length,
        cullingRatio: culledItemsCount / items.length
    };
}

// Memoization utilities for expensive operations
/**
 *
 */
export function useMemoizedItemsWithSearch<T extends BaseItem>(
    items: T[],
    searchQuery: string,
    filterFn?: (item: T) => boolean,
    sortFn?: (a: T, b: T) => number
) {
    return useMemo(() => {
        let result = items;

        // Apply filter
        if (filterFn) {
            result = result.filter(filterFn);
        }

        // Apply search
        if (searchQuery) {
            const query = searchQuery.toLowerCase();
            result = result.filter(item =>
                item.id.toLowerCase().includes(query) ||
                JSON.stringify(item.data).toLowerCase().includes(query)
            );
        }

        // Apply sort
        if (sortFn) {
            result = [...result].sort(sortFn);
        }

        return result;
    }, [items, searchQuery, filterFn, sortFn]);
}

// Debounced updates for performance
/**
 *
 */
export function useDebouncedValue<T>(value: T, delay: number): T {
    const [debouncedValue, setDebouncedValue] = useState<T>(value);

    useEffect(() => {
        const timer = setTimeout(() => setDebouncedValue(value), delay);
        return () => clearTimeout(timer);
    }, [value, delay]);

    return debouncedValue;
}

// Optimized canvas rendering hook
/**
 *
 */
export function useOptimizedCanvasRendering<T extends BaseItem>() {
    const renderCache = useRef<Map<string, React.ReactElement>>(new Map());
    const lastRenderTime = useRef<Map<string, number>>(new Map());

    const getCachedRender = useCallback((
        item: T,
        renderer: (item: T) => React.ReactElement,
        cacheTTL: number = 5000 // 5 seconds cache
    ) => {
        const cacheKey = item.id;
        const lastUpdate = item.metadata?.updatedAt ? new Date(item.metadata.updatedAt).getTime() : 0;
        const lastRender = lastRenderTime.current.get(cacheKey) || 0;
        const now = Date.now();

        // Check if cache is still valid
        if (
            renderCache.current.has(cacheKey) &&
            lastRender > lastUpdate &&
            (now - lastRender) < cacheTTL
        ) {
            return renderCache.current.get(cacheKey)!;
        }

        // Render and cache
        const rendered = renderer(item);
        renderCache.current.set(cacheKey, rendered);
        lastRenderTime.current.set(cacheKey, now);

        return rendered;
    }, []);

    const clearCache = useCallback(() => {
        renderCache.current.clear();
        lastRenderTime.current.clear();
    }, []);

    const pruneCache = useCallback((maxAge: number = 60000) => {
        const now = Date.now();
        for (const [key, timestamp] of lastRenderTime.current.entries()) {
            if (now - timestamp > maxAge) {
                renderCache.current.delete(key);
                lastRenderTime.current.delete(key);
            }
        }
    }, []);

    return { getCachedRender, clearCache, pruneCache };
}

// Batch operations for performance
/**
 *
 */
export class BatchProcessor<T> {
    private operations: Array<() => void> = [];
    private timeout: NodeJS.Timeout | null = null;
    private batchSize: number;
    private delay: number;

    /**
     *
     */
    constructor(batchSize: number = 50, delay: number = 16) { // ~60fps
        this.batchSize = batchSize;
        this.delay = delay;
    }

    /**
     *
     */
    addOperation(operation: () => void) {
        this.operations.push(operation);
        this.scheduleFlush();
    }

    /**
     *
     */
    private scheduleFlush() {
        if (this.timeout) return;

        this.timeout = setTimeout(() => {
            this.flush();
        }, this.delay);
    }

    /**
     *
     */
    private flush() {
        const batch = this.operations.splice(0, this.batchSize);

        // Process batch
        batch.forEach(operation => {
            try {
                operation();
            } catch (error) {
                console.error('Batch operation failed:', error);
            }
        });

        this.timeout = null;

        // Schedule next batch if more operations remain
        if (this.operations.length > 0) {
            this.scheduleFlush();
        }
    }

    /**
     *
     */
    clear() {
        this.operations = [];
        if (this.timeout) {
            clearTimeout(this.timeout);
            this.timeout = null;
        }
    }
}

// Performance-optimized canvas component wrapper
/**
 *
 */
export interface OptimizedCanvasProps<T extends BaseItem> {
    items: T[];
    renderItem: (item: T) => React.ReactElement;
    viewportBounds?: ViewportBounds;
    enableVirtualScrolling?: boolean;
    enableViewportCulling?: boolean;
    enableRenderCaching?: boolean;
    virtualScrollConfig?: Partial<VirtualScrollConfig>;
    children?: React.ReactNode;
}

/**
 *
 */
export function OptimizedCanvas<T extends BaseItem>({
    items,
    renderItem,
    viewportBounds,
    enableVirtualScrolling = false,
    enableViewportCulling = false,
    enableRenderCaching = true,
    virtualScrollConfig,
    children
}: OptimizedCanvasProps<T>) {
    const performanceMonitor = usePerformanceMonitor<T>();
    const { getCachedRender, pruneCache } = useOptimizedCanvasRendering<T>();

    // Virtual scrolling setup
    const virtualScrollingResult = useVirtualScrolling(
        items,
        enableVirtualScrolling && virtualScrollConfig ? {
            itemHeight: 100,
            containerHeight: 600,
            overscan: 5,
            ...virtualScrollConfig
        } : null as unknown
    );

    // Viewport culling setup
    const viewportCullingResult = useViewportCulling(
        enableVirtualScrolling ? virtualScrollingResult?.visibleItems || items : items,
        enableViewportCulling && viewportBounds ? {
            viewport: viewportBounds,
            padding: 100
        } : null as unknown,
        (item: T) => ({
            left: item.position.x - 50,
            top: item.position.y - 50,
            right: item.position.x + 50,
            bottom: item.position.y + 50
        })
    );

    const finalItems = enableViewportCulling && viewportBounds
        ? viewportCullingResult?.visibleItems || items
        : enableVirtualScrolling
            ? virtualScrollingResult?.visibleItems || items
            : items;

    // Performance monitoring
    useEffect(() => {
        performanceMonitor.recordRender(items.length, finalItems.length);
    }, [items.length, finalItems.length, performanceMonitor]);

    // Periodic cache cleanup
    useEffect(() => {
        const interval = setInterval(() => {
            pruneCache(60000); // Clean cache every minute
        }, 60000);

        return () => clearInterval(interval);
    }, [pruneCache]);

    const renderOptimizedItems = useMemo(() => {
        performanceMonitor.startMeasurement('render');

        const rendered = finalItems.map(item => (
            <div key={item.id}>
                {enableRenderCaching ? getCachedRender(item, renderItem) : renderItem(item)}
            </div>
        ));

        performanceMonitor.endMeasurement('render');
        return rendered;
    }, [finalItems, renderItem, enableRenderCaching, getCachedRender, performanceMonitor]);

    if (enableVirtualScrolling && virtualScrollingResult) {
        return (
            <div
                ref={virtualScrollingResult.containerRef}
                onScroll={virtualScrollingResult.handleScroll}
                style={{
                    height: virtualScrollConfig?.containerHeight || 600,
                    overflowY: 'auto'
                }}
            >
                <div style={{ height: virtualScrollingResult.totalHeight, position: 'relative' }}>
                    <div
                        style={{
                            transform: `translateY(${virtualScrollingResult.offsetY}px)`
                        }}
                    >
                        {renderOptimizedItems}
                    </div>
                </div>
                {children}
            </div>
        );
    }

    return (
        <div>
            {renderOptimizedItems}
            {children}
        </div>
    );
}

// Performance metrics display component
export const PerformanceDisplay: React.FC<{ metrics: PerformanceMetrics }> = ({ metrics }) => (
    <div style={{
        position: 'fixed',
        top: 10,
        right: 10,
        background: 'rgba(0,0,0,0.8)',
        color: 'white',
        padding: '10px',
        borderRadius: '4px',
        fontSize: '12px',
        fontFamily: 'monospace'
    }}>
        <div>Render: {metrics.renderTime.toFixed(2)}ms</div>
        <div>Items: {metrics.itemCount} / {metrics.visibleItemCount}</div>
        <div>FPS: {metrics.fps}</div>
        <div>Memory: {(metrics.memoryUsage / 1024 / 1024).toFixed(1)}MB</div>
    </div>
);