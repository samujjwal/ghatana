/**
 * @ghatana/yappc-ide - Performance Optimized Virtual Scroll Component
 * 
 * High-performance virtual scrolling with lazy loading, memory management,
 * and optimization for large datasets.
 * 
 * @doc.type component
 * @doc.purpose Performance-optimized virtual scrolling for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';

// Extend Performance interface for memory API
declare global {
  interface Performance {
    memory?: {
      usedJSHeapSize: number;
      totalJSHeapSize: number;
      jsHeapSizeLimit: number;
    };
  }
}

/**
 * Virtual scroll item configuration
 */
interface VirtualItem {
  id: string;
  index: number;
  height?: number;
  data?: unknown;
  lazy?: boolean;
  priority?: 'high' | 'medium' | 'low';
}

/**
 * Virtual scroll configuration
 */
interface VirtualScrollConfig {
  itemHeight: number | ((index: number) => number);
  containerHeight: number;
  overscan?: number;
  threshold?: number;
  enableLazyLoading?: boolean;
  enableDynamicHeight?: boolean;
  enableMemoryOptimization?: boolean;
  cacheSize?: number;
  preloadCount?: number;
}

/**
 * Performance metrics
 */
export interface PerformanceMetrics {
  renderTime: number;
  itemCount: number;
  visibleCount: number;
  cachedCount: number;
  memoryUsage: number;
  fps: number;
}

/**
 * Performance optimized virtual scroll props
 */
export interface PerformanceOptimizedVirtualScrollProps {
  items: VirtualItem[];
  config: VirtualScrollConfig;
  renderItem: (item: VirtualItem, index: number) => React.ReactNode;
  onLoadMore?: () => Promise<void>;
  onPerformanceUpdate?: (metrics: PerformanceMetrics) => void;
  className?: string;
  enableDebugMode?: boolean;
}

/**
 * Memory cache for virtual items
 */
class ItemCache {
  private cache = new Map<string, { item: VirtualItem; timestamp: number }>();
  private maxSize: number;
  private ttl: number;

  constructor(maxSize = 1000, ttl = 300000) { // 5 minutes TTL
    this.maxSize = maxSize;
    this.ttl = ttl;
  }

  get(id: string): VirtualItem | null {
    const entry = this.cache.get(id);
    if (!entry) return null;

    if (Date.now() - entry.timestamp > this.ttl) {
      this.cache.delete(id);
      return null;
    }

    return entry.item;
  }

  set(id: string, item: VirtualItem): void {
    if (this.cache.size >= this.maxSize) {
      // Remove oldest entry
      const firstKey = this.cache.keys().next().value;
      if (firstKey) this.cache.delete(firstKey);
    }

    this.cache.set(id, { item, timestamp: Date.now() });
  }

  clear(): void {
    this.cache.clear();
  }

  size(): number {
    return this.cache.size;
  }
}

/**
 * Performance monitor
 */
class PerformanceMonitor {
  private frameCount = 0;
  private lastTime = performance.now();
  private fps = 0;

  update(): number {
    this.frameCount++;
    const now = performance.now();

    if (now - this.lastTime >= 1000) {
      this.fps = Math.round((this.frameCount * 1000) / (now - this.lastTime));
      this.frameCount = 0;
      this.lastTime = now;
    }

    return this.fps;
  }

  getFPS(): number {
    return this.fps;
  }
}

/**
 * Performance optimized virtual scroll component
 */
export const PerformanceOptimizedVirtualScroll: React.FC<PerformanceOptimizedVirtualScrollProps> = ({
  items,
  config,
  renderItem,
  onLoadMore,
  onPerformanceUpdate,
  className = '',
  enableDebugMode = false,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scrollTop, setScrollTop] = useState(0);
  const [isScrolling, setIsScrolling] = useState(false);

  // Performance optimization instances
  const itemCache = useMemo(() => new ItemCache(config.cacheSize), [config.cacheSize]);
  const performanceMonitor = useMemo(() => new PerformanceMonitor(), []);
  const scrollTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const renderStartTimeRef = useRef<number>(0);

  // Calculate item positions and heights
  const itemPositions = useMemo(() => {
    const positions: Array<{ top: number; height: number; bottom: number }> = [];
    let currentTop = 0;

    for (let i = 0; i < items.length; i++) {
      const height = typeof config.itemHeight === 'function'
        ? config.itemHeight(i)
        : config.itemHeight;

      positions.push({
        top: currentTop,
        height,
        bottom: currentTop + height,
      });

      currentTop += height;
    }

    return positions;
  }, [items, config.itemHeight]);

  // Calculate visible range
  const visibleRange = useMemo(() => {
    const { containerHeight, overscan = 5 } = config;

    let startIndex = 0;
    let endIndex = items.length - 1;

    // Binary search for start index
    let left = 0;
    let right = itemPositions.length - 1;

    while (left <= right) {
      const mid = Math.floor((left + right) / 2);
      if (itemPositions[mid].bottom <= scrollTop) {
        left = mid + 1;
      } else if (itemPositions[mid].top >= scrollTop) {
        right = mid - 1;
      } else {
        left = mid;
        break;
      }
    }

    startIndex = Math.max(0, left - overscan);

    // Find end index
    const visibleBottom = scrollTop + containerHeight;
    left = startIndex;
    right = itemPositions.length - 1;

    while (left <= right) {
      const mid = Math.floor((left + right) / 2);
      if (itemPositions[mid].top >= visibleBottom) {
        right = mid - 1;
      } else if (itemPositions[mid].bottom <= visibleBottom) {
        left = mid + 1;
      } else {
        left = mid;
        break;
      }
    }

    endIndex = Math.min(items.length - 1, left + overscan);

    return { startIndex, endIndex };
  }, [itemPositions, scrollTop, config]);

  // Handle scroll events with throttling
  const handleScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const newScrollTop = e.currentTarget.scrollTop;
    setScrollTop(newScrollTop);

    setIsScrolling(true);

    // Clear existing timeout
    if (scrollTimeoutRef.current) {
      clearTimeout(scrollTimeoutRef.current);
    }

    // Set scrolling to false after scroll ends
    scrollTimeoutRef.current = setTimeout(() => {
      setIsScrolling(false);
    }, 150);
  }, []);

  // Setup intersection observer for lazy loading
  useEffect(() => {
    if (!config.enableLazyLoading || !onLoadMore) return;

    // Simple intersection detection without external hook
    const handleScroll = () => {
      if (!containerRef.current) return;

      const { scrollTop, scrollHeight, clientHeight } = containerRef.current;
      const threshold = 100; // Load more when 100px from bottom

      if (scrollTop + clientHeight >= scrollHeight - threshold) {
        onLoadMore();
      }
    };

    const container = containerRef.current;
    if (container) {
      container.addEventListener('scroll', handleScroll);
    }

    return () => {
      if (container) {
        container.removeEventListener('scroll', handleScroll);
      }
    };
  }, [config.enableLazyLoading, onLoadMore]);

  // Performance monitoring
  useEffect(() => {
    if (!onPerformanceUpdate) return;

    renderStartTimeRef.current = performance.now();

    const metrics: PerformanceMetrics = {
      renderTime: performance.now() - (renderStartTimeRef.current || 0),
      itemCount: items.length,
      visibleCount: visibleRange.endIndex - visibleRange.startIndex + 1,
      cachedCount: itemCache.size(),
      memoryUsage: performance.memory?.usedJSHeapSize || 0,
      fps: performanceMonitor.update(),
    };

    onPerformanceUpdate(metrics);
  }, [items.length, visibleRange, itemCache, performanceMonitor, onPerformanceUpdate]);

  // Render visible items
  const visibleItems = useMemo(() => {
    const renderItems = [];

    for (let i = visibleRange.startIndex; i <= visibleRange.endIndex; i++) {
      const item = items[i];
      if (!item) continue;

      // Check cache first
      let cachedItem = itemCache.get(item.id);
      if (!cachedItem) {
        cachedItem = item;
        if (config.enableMemoryOptimization) {
          itemCache.set(item.id, item);
        }
      }

      const position = itemPositions[i];
      const isLastItem = i === items.length - 1;

      renderItems.push(
        <div
          key={item.id}
          style={{
            position: 'absolute',
            top: position.top,
            left: 0,
            right: 0,
            height: position.height,
          }}
          data-index={i}
          data-last-item={isLastItem ? 'true' : undefined}
        >
          {renderItem(cachedItem, i)}
        </div>
      );
    }

    return renderItems;
  }, [visibleRange, items, itemPositions, renderItem, itemCache, config.enableMemoryOptimization]);

  // Calculate total height
  const totalHeight = useMemo(() => {
    if (itemPositions.length === 0) return 0;
    return itemPositions[itemPositions.length - 1].bottom;
  }, [itemPositions]);

  // Cleanup
  useEffect(() => {
    return () => {
      if (scrollTimeoutRef.current) {
        clearTimeout(scrollTimeoutRef.current);
      }
      if (config.enableMemoryOptimization) {
        itemCache.clear();
      }
    };
  }, [config.enableMemoryOptimization, itemCache]);

  return (
    <div
      ref={containerRef}
      className={`relative overflow-auto ${className}`}
      style={{ height: config.containerHeight }}
      onScroll={handleScroll}
    >
      {/* Spacer for total height */}
      <div style={{ height: totalHeight, position: 'relative' }}>
        {/* Visible items */}
        {visibleItems}
      </div>

      {/* Debug overlay */}
      {enableDebugMode && (
        <div className="fixed top-4 left-4 bg-black/80 text-white p-3 rounded text-xs font-mono z-50">
          <div>Items: {items.length}</div>
          <div>Visible: {visibleRange.endIndex - visibleRange.startIndex + 1}</div>
          <div>Cached: {itemCache.size()}</div>
          <div>FPS: {performanceMonitor.getFPS()}</div>
          <div>Scrolling: {isScrolling ? 'Yes' : 'No'}</div>
          <div>Memory: {Math.round((performance.memory?.usedJSHeapSize || 0) / 1024 / 1024)}MB</div>
        </div>
      )}

      {/* Loading indicator for lazy loading */}
      {config.enableLazyLoading && onLoadMore && (
        <div
          className="absolute bottom-0 left-0 right-0 p-4 text-center text-gray-500 dark:text-gray-400"
          style={{ top: totalHeight }}
        >
          <div className="inline-flex items-center gap-2">
            <div className="w-4 h-4 border-2 border-gray-300 border-t-blue-500 rounded-full animate-spin" />
            Loading more items...
          </div>
        </div>
      )}
    </div>
  );
};

/**
 * Hook for performance optimized virtual scrolling
 */
export const usePerformanceOptimizedVirtualScroll = (
  config: VirtualScrollConfig
) => {
  const [metrics, setMetrics] = useState<PerformanceMetrics | null>(null);
  const [isOptimized, setIsOptimized] = useState(false);

  const updateMetrics = useCallback((newMetrics: PerformanceMetrics) => {
    setMetrics(newMetrics);

    // Auto-optimization based on performance
    if (newMetrics.fps < 30) {
      setIsOptimized(true);
      // Reduce overscan, increase cache size, etc.
    } else if (newMetrics.fps > 55) {
      setIsOptimized(false);
    }
  }, []);

  const optimizedConfig = useMemo(() => {
    if (!isOptimized) return config;

    return {
      ...config,
      overscan: Math.max(1, Math.floor((config.overscan || 5) / 2)),
      cacheSize: (config.cacheSize || 1000) * 2,
    };
  }, [config, isOptimized]);

  return {
    metrics,
    updateMetrics,
    config: optimizedConfig,
    isOptimized,
  };
};

export default PerformanceOptimizedVirtualScroll;
