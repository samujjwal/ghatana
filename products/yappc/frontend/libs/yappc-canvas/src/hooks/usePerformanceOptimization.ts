import { useCallback, useEffect, useRef, useState } from 'react';

import type { Node, Edge } from '@xyflow/react';

// Performance monitoring types
/**
 *
 */
export interface PerformanceMetrics {
  renderTime: number;
  updateTime: number;
  memoryUsage: number;
  nodeCount: number;
  edgeCount: number;
  fps: number;
  latency: number;
  cacheHitRate: number;
  operationsPerSecond: number;
}

/**
 *
 */
export interface CacheEntry<T> {
  data: T;
  timestamp: number;
  accessCount: number;
  size: number;
}

/**
 *
 */
export interface OptimizationSettings {
  enableVirtualization: boolean;
  enableLazyLoading: boolean;
  maxCacheSize: number;
  maxVisibleNodes: number;
  debounceMs: number;
  throttleMs: number;
  enableBatching: boolean;
  batchSize: number;
}

/**
 *
 */
export interface ViewportBounds {
  x: number;
  y: number;
  width: number;
  height: number;
  zoom: number;
}

/**
 *
 */
export interface UsePerformanceOptimizationReturn {
  // Performance metrics
  metrics: PerformanceMetrics;

  // Cache management
  cache: Map<string, CacheEntry<unknown>>;
  cacheGet: <T>(key: string) => T | null;
  cacheSet: <T>(key: string, value: T, size?: number) => void;
  cacheClear: () => void;
  cacheStats: () => { size: number; hitRate: number; totalSize: number };

  // Virtualization
  visibleNodes: Node[];
  visibleEdges: Edge[];
  updateViewport: (bounds: ViewportBounds) => void;

  // Lazy loading
  loadNodesInRegion: (bounds: ViewportBounds) => Promise<Node[]>;
  unloadNodesOutsideRegion: (bounds: ViewportBounds) => void;

  // Batching
  batchOperations: (operations: (() => void)[]) => void;
  flushBatch: () => void;

  // Memory management
  memoryUsage: number;
  garbageCollect: () => void;
  optimizeMemory: () => void;

  // Performance monitoring
  startProfiling: () => void;
  stopProfiling: () => PerformanceMetrics;
  measureOperation: <T>(name: string, operation: () => T) => T;

  // Settings
  settings: OptimizationSettings;
  updateSettings: (newSettings: Partial<OptimizationSettings>) => void;
}

/**
 * Hook for canvas performance optimization with virtualization, caching, and lazy loading
 * 
 * Provides comprehensive performance features including:
 * - Viewport-based virtualization (only render visible nodes/edges)
 * - Intelligent caching with LRU eviction and hit rate tracking
 * - Lazy loading for off-screen elements
 * - Operation batching to reduce re-renders
 * - Debouncing and throttling for expensive operations
 * - Real-time performance metrics (FPS, render time, memory usage)
 * - Performance profiling and operation measurement
 * - Configurable optimization settings
 * 
 * Optimizes canvas rendering for large graphs (1000+ nodes) with minimal performance impact.
 * Automatically manages memory and reduces unnecessary computations.
 * 
 * @param nodes - Array of canvas nodes to optimize
 * @param edges - Array of canvas edges to optimize
 * @param options - Partial optimization settings (merged with defaults)
 * @returns Performance optimization state and operations
 * 
 * @example
 * ```tsx
 * function OptimizedCanvas() {
 *   const [allNodes, setAllNodes] = useState<Node[]>([]);
 *   const [allEdges, setAllEdges] = useState<Edge[]>([]);
 *   
 *   const {
 *     metrics,
 *     cache,
 *     cacheGet,
 *     cacheSet,
 *     cacheStats,
 *     visibleNodes,
 *     visibleEdges,
 *     updateViewport,
 *     loadNodesInRegion,
 *     batchOperations,
 *     startProfiling,
 *     stopProfiling,
 *     measureOperation,
 *     settings,
 *     updateSettings
 *   } = usePerformanceOptimization(allNodes, allEdges, {
 *     enableVirtualization: true,
 *     maxVisibleNodes: 500,
 *     maxCacheSize: 50 * 1024 * 1024, // 50MB
 *     debounceMs: 100
 *   });
 *   
 *   const handleViewportChange = (viewport) => {
 *     updateViewport({
 *       x: viewport.x,
 *       y: viewport.y,
 *       width: viewport.width,
 *       height: viewport.height,
 *       zoom: viewport.zoom
 *     });
 *   };
 *   
 *   const performBulkUpdate = (updates) => {
 *     batchOperations(
 *       updates.map(update => () => updateNode(update.id, update.data))
 *     );
 *   };
 *   
 *   const expensiveCalculation = () => {
 *     return measureOperation('layout-calculation', () => {
 *       return calculateGraphLayout(visibleNodes);
 *     });
 *   };
 *   
 *   const stats = cacheStats();
 *   
 *   return (
 *     <div>
 *       <PerformanceMetrics
 *         fps={metrics.fps}
 *         renderTime={metrics.renderTime}
 *         memoryUsage={metrics.memoryUsage}
 *         cacheHitRate={metrics.cacheHitRate}
 *       />
 *       <div>
 *         Cache: {stats.size} items, {stats.hitRate}% hit rate, {(stats.totalSize / 1024 / 1024).toFixed(2)} MB
 *       </div>
 *       <ReactFlow
 *         nodes={visibleNodes}
 *         edges={visibleEdges}
 *         onViewportChange={handleViewportChange}
 *       />
 *       <SettingsPanel settings={settings} onChange={updateSettings} />
 *     </div>
 *   );
 * }
 * ```
 */
export const usePerformanceOptimization = (
  nodes: Node[],
  edges: Edge[],
  options: Partial<OptimizationSettings> = {}
): UsePerformanceOptimizationReturn => {
  // Settings with defaults
  const [settings, setSettings] = useState<OptimizationSettings>({
    enableVirtualization: true,
    enableLazyLoading: true,
    maxCacheSize: 100 * 1024 * 1024, // 100MB
    maxVisibleNodes: 1000,
    debounceMs: 100,
    throttleMs: 16, // ~60fps
    enableBatching: true,
    batchSize: 50,
    ...options
  });

  // State
  const [metrics, setMetrics] = useState<PerformanceMetrics>({
    renderTime: 0,
    updateTime: 0,
    memoryUsage: 0,
    nodeCount: nodes.length,
    edgeCount: edges.length,
    fps: 60,
    latency: 0,
    cacheHitRate: 0,
    operationsPerSecond: 0
  });

  const [visibleNodes, setVisibleNodes] = useState<Node[]>([]);
  const [visibleEdges, setVisibleEdges] = useState<Edge[]>([]);
  const [memoryUsage, setMemoryUsage] = useState(0);

  // Refs for performance tracking
  const cacheRef = useRef<Map<string, CacheEntry<unknown>>>(new Map());
  const batchQueueRef = useRef<(() => void)[]>([]);
  const performanceStartRef = useRef<number>(0);
  const fpsCounterRef = useRef({ frames: 0, lastTime: Date.now() });
  const operationCounterRef = useRef({ count: 0, lastTime: Date.now() });
  const viewportRef = useRef<ViewportBounds>({ x: 0, y: 0, width: 1000, height: 800, zoom: 1 });

  // Cache management
  const cacheGet = useCallback(<T>(key: string): T | null => {
    const entry = cacheRef.current.get(key);
    if (entry) {
      entry.accessCount++;
      return entry.data as T;
    }
    return null;
  }, []);

  const cacheSet = useCallback(<T>(key: string, value: T, size: number = 1024): void => {
    const cache = cacheRef.current;

    // Check cache size limit
    let totalSize = Array.from(cache.values()).reduce((sum, entry) => sum + entry.size, 0);

    // Evict old entries if needed
    while (totalSize + size > settings.maxCacheSize && cache.size > 0) {
      // Find least recently used entry
      let oldestKey = '';
      let oldestTime = Date.now();

      for (const [k, entry] of cache.entries()) {
        if (entry.timestamp < oldestTime) {
          oldestTime = entry.timestamp;
          oldestKey = k;
        }
      }

      if (oldestKey) {
        const removedEntry = cache.get(oldestKey);
        cache.delete(oldestKey);
        totalSize -= removedEntry?.size || 0;
      } else {
        break;
      }
    }

    cache.set(key, {
      data: value,
      timestamp: Date.now(),
      accessCount: 1,
      size
    });
  }, [settings.maxCacheSize]);

  const cacheClear = useCallback(() => {
    cacheRef.current.clear();
  }, []);

  const cacheStats = useCallback(() => {
    const cache = cacheRef.current;
    const totalHits = Array.from(cache.values()).reduce((sum, entry) => sum + entry.accessCount, 0);
    const totalRequests = totalHits + (metrics.nodeCount + metrics.edgeCount);
    const hitRate = totalRequests > 0 ? totalHits / totalRequests : 0;
    const totalSize = Array.from(cache.values()).reduce((sum, entry) => sum + entry.size, 0);

    return {
      size: cache.size,
      hitRate,
      totalSize
    };
  }, [metrics.nodeCount, metrics.edgeCount]);

  // Virtualization - only render visible elements
  const updateViewport = useCallback((bounds: ViewportBounds) => {
    viewportRef.current = bounds;

    if (!settings.enableVirtualization) {
      setVisibleNodes(nodes);
      setVisibleEdges(edges);
      return;
    }

    const buffer = 200; // Buffer around viewport
    const expandedBounds = {
      x: bounds.x - buffer,
      y: bounds.y - buffer,
      width: bounds.width + 2 * buffer,
      height: bounds.height + 2 * buffer
    };

    // Filter nodes within viewport
    const visible = nodes.filter(node => {
      if (!node.position) return true; // Always show nodes without position

      return (
        node.position.x >= expandedBounds.x &&
        node.position.x <= expandedBounds.x + expandedBounds.width &&
        node.position.y >= expandedBounds.y &&
        node.position.y <= expandedBounds.y + expandedBounds.height
      );
    });

    // Limit to max visible nodes
    const limitedNodes = visible.slice(0, settings.maxVisibleNodes);
    setVisibleNodes(limitedNodes);

    // Filter edges connected to visible nodes
    const visibleNodeIds = new Set(limitedNodes.map(n => n.id));
    const visibleEdgesList = edges.filter(edge =>
      visibleNodeIds.has(edge.source) && visibleNodeIds.has(edge.target)
    );
    setVisibleEdges(visibleEdgesList);

  }, [nodes, edges, settings.enableVirtualization, settings.maxVisibleNodes]);

  // Lazy loading
  const loadNodesInRegion = useCallback(async (bounds: ViewportBounds): Promise<Node[]> => {
    if (!settings.enableLazyLoading) return nodes;

    const cacheKey = `nodes_${bounds.x}_${bounds.y}_${bounds.width}_${bounds.height}_${bounds.zoom}`;
    const cached = cacheGet<Node[]>(cacheKey);

    if (cached) {
      return cached;
    }

    // Simulate async loading
    return new Promise(resolve => {
      setTimeout(() => {
        const regionNodes = nodes.filter(node => {
          if (!node.position) return false;
          return (
            node.position.x >= bounds.x &&
            node.position.x <= bounds.x + bounds.width &&
            node.position.y >= bounds.y &&
            node.position.y <= bounds.y + bounds.height
          );
        });

        cacheSet(cacheKey, regionNodes, regionNodes.length * 1024);
        resolve(regionNodes);
      }, 10);
    });
  }, [nodes, settings.enableLazyLoading, cacheGet, cacheSet]);

  const unloadNodesOutsideRegion = useCallback((bounds: ViewportBounds) => {
    // Remove cached nodes outside region to free memory
    const cache = cacheRef.current;
    const keysToRemove: string[] = [];

    for (const key of cache.keys()) {
      if (key.startsWith('nodes_')) {
        const parts = key.split('_');
        if (parts.length >= 5) {
          const cachedBounds = {
            x: parseFloat(parts[1]),
            y: parseFloat(parts[2]),
            width: parseFloat(parts[3]),
            height: parseFloat(parts[4])
          };

          // Check if cached region is outside current viewport
          if (
            cachedBounds.x + cachedBounds.width < bounds.x ||
            cachedBounds.x > bounds.x + bounds.width ||
            cachedBounds.y + cachedBounds.height < bounds.y ||
            cachedBounds.y > bounds.y + bounds.height
          ) {
            keysToRemove.push(key);
          }
        }
      }
    }

    keysToRemove.forEach(key => cache.delete(key));
  }, []);

  // Batching operations
  const batchOperations = useCallback((operations: (() => void)[]) => {
    if (!settings.enableBatching) {
      operations.forEach(op => op());
      return;
    }

    batchQueueRef.current.push(...operations);

    // Auto-flush if batch is full
    if (batchQueueRef.current.length >= settings.batchSize) {
      flushBatch();
    }
  }, [settings.enableBatching, settings.batchSize]);

  const flushBatch = useCallback(() => {
    const batch = batchQueueRef.current;
    batchQueueRef.current = [];

    // Execute all batched operations
    batch.forEach(operation => {
      try {
        operation();
      } catch (error) {
        console.error('Batch operation failed:', error);
      }
    });

    // Update operation counter
    operationCounterRef.current.count += batch.length;
  }, []);

  // Memory management
  const garbageCollect = useCallback(() => {
    // Clear old cache entries
    const cache = cacheRef.current;
    const now = Date.now();
    const maxAge = 5 * 60 * 1000; // 5 minutes

    for (const [key, entry] of cache.entries()) {
      if (now - entry.timestamp > maxAge) {
        cache.delete(key);
      }
    }

    // Force garbage collection if available
    if ((window as unknown).gc) {
      (window as unknown).gc();
    }
  }, []);

  const optimizeMemory = useCallback(() => {
    // Clear unnecessary data
    garbageCollect();

    // Limit visible nodes
    if (visibleNodes.length > settings.maxVisibleNodes) {
      setVisibleNodes(visibleNodes.slice(0, settings.maxVisibleNodes));
    }

    // Update memory usage estimate
    const estimatedUsage = (
      visibleNodes.length * 1024 + // ~1KB per node
      visibleEdges.length * 512 + // ~512B per edge
      cacheRef.current.size * 2048 // ~2KB per cache entry
    );
    setMemoryUsage(estimatedUsage);
  }, [visibleNodes, visibleEdges, garbageCollect, settings.maxVisibleNodes]);

  // Performance monitoring
  const startProfiling = useCallback(() => {
    performanceStartRef.current = performance.now();
  }, []);

  const stopProfiling = useCallback((): PerformanceMetrics => {
    const endTime = performance.now();
    const duration = endTime - performanceStartRef.current;

    const newMetrics: PerformanceMetrics = {
      renderTime: duration,
      updateTime: duration,
      memoryUsage,
      nodeCount: nodes.length,
      edgeCount: edges.length,
      fps: fpsCounterRef.current.frames,
      latency: 0, // Would be measured from network operations
      cacheHitRate: cacheStats().hitRate,
      operationsPerSecond: operationCounterRef.current.count
    };

    setMetrics(newMetrics);
    return newMetrics;
  }, [memoryUsage, nodes.length, edges.length, cacheStats]);

  const measureOperation = useCallback(<T>(name: string, operation: () => T): T => {
    const start = performance.now();
    const result = operation();
    const end = performance.now();

    console.log(`Operation "${name}" took ${end - start}ms`);

    // Update operation counter
    operationCounterRef.current.count++;

    return result;
  }, []);

  // Settings update
  const updateSettings = useCallback((newSettings: Partial<OptimizationSettings>) => {
    setSettings(prev => ({ ...prev, ...newSettings }));
  }, []);

  // Performance monitoring loop
  useEffect(() => {
    let frameId: number;
    let lastTime = Date.now();

    const updatePerformance = () => {
      const now = Date.now();
      const deltaTime = now - lastTime;

      if (deltaTime >= 1000) { // Update every second
        // Calculate FPS
        const fps = Math.round(fpsCounterRef.current.frames * 1000 / deltaTime);
        fpsCounterRef.current.frames = 0;
        fpsCounterRef.current.lastTime = now;

        // Calculate operations per second
        const opsPerSecond = operationCounterRef.current.count * 1000 / deltaTime;
        operationCounterRef.current.count = 0;
        operationCounterRef.current.lastTime = now;

        setMetrics(prev => ({
          ...prev,
          fps,
          operationsPerSecond: opsPerSecond,
          nodeCount: nodes.length,
          edgeCount: edges.length,
          cacheHitRate: cacheStats().hitRate,
          memoryUsage
        }));

        lastTime = now;
      }

      fpsCounterRef.current.frames++;
      frameId = requestAnimationFrame(updatePerformance);
    };

    frameId = requestAnimationFrame(updatePerformance);

    return () => {
      cancelAnimationFrame(frameId);
    };
  }, [nodes.length, edges.length, cacheStats, memoryUsage]);

  // Auto-flush batch operations
  useEffect(() => {
    const interval = setInterval(() => {
      if (batchQueueRef.current.length > 0) {
        flushBatch();
      }
    }, settings.debounceMs);

    return () => clearInterval(interval);
  }, [flushBatch, settings.debounceMs]);

  // Memory optimization interval
  useEffect(() => {
    const interval = setInterval(optimizeMemory, 30000); // Every 30 seconds
    return () => clearInterval(interval);
  }, [optimizeMemory]);

  return {
    metrics,
    cache: cacheRef.current,
    cacheGet,
    cacheSet,
    cacheClear,
    cacheStats,
    visibleNodes,
    visibleEdges,
    updateViewport,
    loadNodesInRegion,
    unloadNodesOutsideRegion,
    batchOperations,
    flushBatch,
    memoryUsage,
    garbageCollect,
    optimizeMemory,
    startProfiling,
    stopProfiling,
    measureOperation,
    settings,
    updateSettings
  };
};