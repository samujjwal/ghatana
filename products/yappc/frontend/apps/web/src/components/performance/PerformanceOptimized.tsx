/**
 * Performance Optimization Components
 * 
 * Provides virtual scrolling, React.memo optimizations, and performance
 * monitoring utilities for large datasets and expensive operations.
 * 
 * @doc.type component
 * @doc.purpose Performance optimization utilities
 * @doc.layer product
 * @doc.pattern Performance Optimization
 */

import React, {
  useState,
  useEffect,
  useMemo,
  useCallback,
  useRef,
  memo,
  useMemoizedCallback,
} from 'react';
import { Box, Typography, Spinner as CircularProgress } from '@ghatana/ui';
import { FixedSizeList as List } from 'react-window';
import { VariableSizeList } from 'react-window';
import AutoSizer from 'react-virtualized-auto-sizer';
import InfiniteLoader from 'react-window-infinite-loader';

// ============================================================================
// Types
// ============================================================================

export interface VirtualListProps<T> {
  items: T[];
  itemHeight: number | ((index: number) => number);
  height: number;
  width?: number;
  renderItem: (props: { index: number; style: React.CSSProperties; data: T }) => React.ReactNode;
  overscanCount?: number;
  getItemKey?: (index: number, data: T) => string | number;
  onScroll?: (scrollInfo: { scrollOffset: number; scrollDirection: 'forward' | 'backward' }) => void;
}

export interface InfiniteVirtualListProps<T> extends VirtualListProps<T> {
  hasNextPage: boolean;
  isNextPageLoading: boolean;
  loadNextPage: () => Promise<void>;
  minimumBatchSize?: number;
  threshold?: number;
}

export interface PerformanceMetrics {
  renderCount: number;
  renderTime: number;
  memoryUsage: number;
  componentCount: number;
  lastRenderTime: number;
}

export interface UsePerformanceMonitorOptions {
  enableMemoryTracking?: boolean;
  enableRenderTracking?: boolean;
  sampleRate?: number;
}

// ============================================================================
// Performance Monitor Hook
// ============================================================================

export const usePerformanceMonitor = (options: UsePerformanceMonitorOptions = {}) => {
  const { enableMemoryTracking = true, enableRenderTracking = true, sampleRate = 1 } = options;
  const renderCountRef = useRef(0);
  const lastRenderTimeRef = useRef(Date.now());
  const [metrics, setMetrics] = useState<PerformanceMetrics>({
    renderCount: 0,
    renderTime: 0,
    memoryUsage: 0,
    componentCount: 0,
    lastRenderTime: Date.now(),
  });

  const trackRender = useCallback(() => {
    if (!enableRenderTracking || Math.random() > sampleRate) return;

    const startTime = performance.now();
    renderCountRef.current++;

    // Track memory if available
    let memoryUsage = 0;
    if (enableMemoryTracking && 'memory' in performance) {
      const memory = (performance as unknown).memory;
      memoryUsage = memory.usedJSHeapSize;
    }

    // Count components (rough estimate)
    const componentCount = document.querySelectorAll('[data-reactroot]').length;

    const renderTime = performance.now() - startTime;
    const now = Date.now();

    setMetrics(prev => ({
      renderCount: renderCountRef.current,
      renderTime: prev.renderTime + renderTime,
      memoryUsage,
      componentCount,
      lastRenderTime: now,
    }));
  }, [enableRenderTracking, enableMemoryTracking, sampleRate]);

  useEffect(() => {
    trackRender();
  });

  return metrics;
};

// ============================================================================
// Virtualized List Component
// ============================================================================

export const VirtualizedList = memo(<T,>({
  items,
  itemHeight,
  height,
  width,
  renderItem,
  overscanCount = 5,
  getItemKey,
  onScroll,
}: VirtualListProps<T>) => {
  const theme = useTheme();
  
  const itemCount = items.length;
  const isVariableHeight = typeof itemHeight === 'function';

  const getItemData = useCallback((index: number) => items[index], [items]);
  const getItemKeyCallback = useCallback((index: number) => {
    return getItemKey ? getItemKey(index, items[index]) : index;
  }, [getItemKey, items]);

  const ListComponent = isVariableHeight ? VariableSizeList : List;

  return (
    <Box className="overflow-hidden">
      <AutoSizer>
        {({ height: autoHeight, width: autoWidth }) => (
          <ListComponent
            height={autoHeight}
            width={width || autoWidth}
            itemCount={itemCount}
            itemSize={itemHeight}
            itemData={getItemData}
            overscanCount={overscanCount}
            getItemKey={getItemKeyCallback}
            onScroll={onScroll}
          >
            {renderItem}
          </ListComponent>
        )}
      </AutoSizer>
    </Box>
  );
}) as <T>(props: VirtualListProps<T>) => JSX.Element;

VirtualizedList.displayName = 'VirtualizedList';

// ============================================================================
// Infinite Virtualized List Component
// ============================================================================

export const InfiniteVirtualizedList = memo(<T,>({
  items,
  hasNextPage,
  isNextPageLoading,
  loadNextPage,
  itemHeight,
  height,
  width,
  renderItem,
  overscanCount = 5,
  getItemKey,
  minimumBatchSize = 10,
  threshold = 5,
}: InfiniteVirtualListProps<T>) => {
  const itemCount = hasNextPage ? items.length + 1 : items.length;
  const isVariableHeight = typeof itemHeight === 'function';

  const isItemLoaded = useCallback((index: number) => {
    return !hasNextPage || index < items.length;
  }, [hasNextPage, items.length]);

  const loadMoreItems = useCallback(async () => {
    if (!isNextPageLoading) {
      await loadNextPage();
    }
  }, [isNextPageLoading, loadNextPage]);

  const getItemData = useCallback((index: number) => items[index], [items]);
  const getItemKeyCallback = useCallback((index: number) => {
    return getItemKey ? getItemKey(index, items[index]) : index;
  }, [getItemKey, items]);

  const renderRow = useCallback((props: unknown) => {
    const { index, style } = props;
    
    if (!isItemLoaded(index)) {
      return (
        <div style={style}>
          <Box
            display="flex"
            alignItems="center"
            justifyContent="center"
            height="100%"
            px={2}
          >
            <CircularProgress size={24} />
            <Typography as="p" className="text-sm" className="ml-4">
              Loading...
            </Typography>
          </Box>
        </div>
      );
    }

    return renderItem({ ...props, data: getItemData(index) });
  }, [isItemLoaded, renderItem, getItemData]);

  const ListComponent = isVariableHeight ? VariableSizeList : List;

  return (
    <Box className="overflow-hidden">
      <AutoSizer>
        {({ height: autoHeight, width: autoWidth }) => (
          <InfiniteLoader
            isItemLoaded={isItemLoaded}
            itemCount={itemCount}
            loadMoreItems={loadMoreItems}
            minimumBatchSize={minimumBatchSize}
            threshold={threshold}
          >
            {({ onItemsRendered, ref }) => (
              <ListComponent
                ref={ref}
                height={autoHeight}
                width={width || autoWidth}
                itemCount={itemCount}
                itemSize={itemHeight}
                itemData={getItemData}
                overscanCount={overscanCount}
                getItemKey={getItemKeyCallback}
                onItemsRendered={onItemsRendered}
              >
                {renderRow}
              </ListComponent>
            )}
          </InfiniteLoader>
        )}
      </AutoSizer>
    </Box>
  );
}) as <T>(props: InfiniteVirtualListProps<T>) => React.ReactElement;

InfiniteVirtualizedList.displayName = 'InfiniteVirtualizedList';

// ============================================================================
// Memoized Components
// ============================================================================

export const MemoizedBox = memo(Box, (prev, next) => {
  // Custom comparison for Box props
  const prevKeys = Object.keys(prev).sort();
  const nextKeys = Object.keys(next).sort();
  
  if (prevKeys.length !== nextKeys.length) {
    return false;
  }

  return prevKeys.every(key => {
    const prevValue = prev[key as keyof typeof prev];
    const nextValue = next[key as keyof typeof next];
    
    // Deep comparison for objects, shallow for primitives
    if (typeof prevValue === 'object' && prevValue !== null) {
      return JSON.stringify(prevValue) === JSON.stringify(nextValue);
    }
    
    return prevValue === nextValue;
  });
});

export const MemoizedTypography = memo(Typography, (prev, next) => {
  // Only re-render if text or critical props change
  return (
    prev.children === next.children &&
    prev.variant === next.variant &&
    prev.color === next.color &&
    prev.className === next.className
  );
});

// ============================================================================
// Performance Optimized Hooks
// ============================================================================

/**
 * Optimized useState with debounce for expensive updates
 */
export const useDebouncedState = <T>(
  initialValue: T,
  delay: number = 300
): [T, (value: T | ((prev: T) => T)) => void] => {
  const [state, setState] = useState(initialValue);
  const timeoutRef = useRef<NodeJS.Timeout>();

  const debouncedSetState = useCallback((value: T | ((prev: T) => T)) => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }

    timeoutRef.current = setTimeout(() => {
      setState(value);
    }, delay);
  }, [delay]);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  return [state, debouncedSetState];
};

/**
 * Optimized useEffect with cleanup for expensive operations
 */
export const useOptimizedEffect = (
  effect: () => void | (() => void),
  deps: React.DependencyList,
  options: { timeout?: number; debounce?: number } = {}
) => {
  const { timeout = 0, debounce = 0 } = options;
  const timeoutRef = useRef<NodeJS.Timeout>();
  const debounceRef = useRef<NodeJS.Timeout>();

  useEffect(() => {
    const execute = () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }

      if (timeout > 0) {
        timeoutRef.current = setTimeout(() => {
          const cleanup = effect();
          return cleanup;
        }, timeout);
      } else {
        return effect();
      }
    };

    if (debounce > 0) {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
      debounceRef.current = setTimeout(execute, debounce);
    } else {
      return execute();
    }
  }, deps);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, []);
};

/**
 * Optimized useMemo with deep comparison
 */
export const useDeepMemo = <T>(factory: () => T, deps: React.DependencyList): T => {
  const ref = useRef<T>();
  const signalRef = useRef<number>(0);

  const prevDeps = useRef<React.DependencyList>();
  
  const hasChanged = !prevDeps.current || deps.some((dep, i) => {
    const prevDep = prevDeps.current![i];
    if (typeof dep === 'object' && dep !== null) {
      return JSON.stringify(dep) !== JSON.stringify(prevDep);
    }
    return dep !== prevDep;
  });

  if (hasChanged) {
    ref.current = factory();
    signalRef.current += 1;
    prevDeps.current = deps;
  }

  return ref.current!;
};

/**
 * Optimized useCallback with deep comparison
 */
export const useDeepCallback = <T extends (...args: unknown[]) => any>(
  callback: T,
  deps: React.DependencyList
): T => {
  return useMemo(() => callback, deps);
};

// ============================================================================
// Performance Monitoring Component
// ============================================================================

interface PerformanceMonitorProps {
  children: React.ReactNode;
  showMetrics?: boolean;
  trackMemory?: boolean;
  trackRenders?: boolean;
}

export const PerformanceMonitor: React.FC<PerformanceMonitorProps> = ({
  children,
  showMetrics = false,
  trackMemory = true,
  trackRenders = true,
}) => {
  const metrics = usePerformanceMonitor({
    enableMemoryTracking: trackMemory,
    enableRenderTracking: trackRenders,
  });

  if (!showMetrics) {
    return <>{children}</>;
  }

  return (
    <Box>
      {showMetrics && (
        <Box
          className="pointer-events-none fixed top-0 right-0 p-1 font-mono z-[9999]"
          style={{ backgroundColor: 'black', color: 'lime', fontSize: '10px' }}
        >
          <div>Renders: {metrics.renderCount}</div>
          <div>Time: {metrics.renderTime.toFixed(2)}ms</div>
          <div>Memory: {(metrics.memoryUsage / 1024 / 1024).toFixed(2)}MB</div>
          <div>Components: {metrics.componentCount}</div>
        </Box>
      )}
      {children}
    </Box>
  );
};

// ============================================================================
// Lazy Loading Component
// ============================================================================

interface LazyComponentProps {
  loader: () => Promise<{ default: React.ComponentType<unknown> }>;
  fallback?: React.ReactNode;
  delay?: number;
  error?: React.ComponentType<{ error: Error; retry: () => void }>;
}

export const LazyComponent: React.FC<LazyComponentProps> = ({
  loader,
  fallback = <CircularProgress />,
  delay = 200,
  error: ErrorComponent,
}) => {
  const [Component, setComponent] = useState<React.ComponentType<unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<Error | null>(null);
  const timeoutRef = useRef<NodeJS.Timeout>();

  useEffect(() => {
    const loadComponent = async () => {
      try {
        setLoading(true);
        setLoadError(null);

        if (delay > 0) {
          await new Promise(resolve => {
            timeoutRef.current = setTimeout(resolve, delay);
          });
        }

        const module = await loader();
        setComponent(() => module.default);
      } catch (err) {
        setLoadError(err as Error);
      } finally {
        setLoading(false);
      }
    };

    loadComponent();

    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, [loader, delay]);

  const retry = useCallback(() => {
    setComponent(null);
    setLoadError(null);
  }, []);

  if (loading) {
    return <>{fallback}</>;
  }

  if (loadError) {
    if (ErrorComponent) {
      return <ErrorComponent error={loadError} retry={retry} />;
    }
    return (
      <Box p={2}>
        <Typography tone="danger">
          Failed to load component: {loadError.message}
        </Typography>
      </Box>
    );
  }

  if (Component) {
    return <Component />;
  }

  return null;
};

// ============================================================================
// Bundle Splitting Utilities
// ============================================================================

/**
 * Create a lazy-loaded component with automatic code splitting
 */
export const createLazyComponent = <T extends React.ComponentType<unknown>>(
  importFunc: () => Promise<{ default: T }>,
  options?: {
    fallback?: React.ReactNode;
    delay?: number;
  }
) => {
  return React.lazy(() => importFunc());
};

/**
 * Preload a component for better performance
 */
export const preloadComponent = <T extends React.ComponentType<unknown>>(
  importFunc: () => Promise<{ default: T }>
) => {
  importFunc();
};

/**
 * Preload multiple components in parallel
 */
export const preloadComponents = (
  importFuncs: Array<() => Promise<{ default: React.ComponentType<unknown> }>>
) => {
  return Promise.all(importFuncs.map(importFunc));
};

// ============================================================================
// Performance Utilities
// ============================================================================

/**
 * Debounce function for performance optimization
 */
export const debounce = <T extends (...args: unknown[]) => any>(
  func: T,
  wait: number
): T => {
  let timeout: NodeJS.Timeout;
  
  return ((...args: Parameters<T>) => {
    clearTimeout(timeout);
    timeout = setTimeout(() => func(...args), wait);
  }) as T;
};

/**
 * Throttle function for performance optimization
 */
export const throttle = <T extends (...args: unknown[]) => any>(
  func: T,
  limit: number
): T => {
  let inThrottle: boolean;
  
  return ((...args: Parameters<T>) => {
    if (!inThrottle) {
      func(...args);
      inThrottle = true;
      setTimeout(() => (inThrottle = false), limit);
    }
  }) as T;
};

/**
 * Memoize expensive function calls
 */
export const memoize = <T extends (...args: unknown[]) => any>(
  func: T,
  keyGenerator?: (...args: Parameters<T>) => string
): T => {
  const cache = new Map<string, ReturnType<T>>();
  
  return ((...args: Parameters<T>) => {
    const key = keyGenerator ? keyGenerator(...args) : JSON.stringify(args);
    
    if (cache.has(key)) {
      return cache.get(key);
    }
    
    const result = func(...args);
    cache.set(key, result);
    return result;
  }) as T;
};

/**
 * Check if component should update based on props
 */
export const shouldComponentUpdate = <P extends object>(
  prevProps: P,
  nextProps: P,
  keys: (keyof P)[] = Object.keys(prevProps) as (keyof P)[]
): boolean => {
  return keys.some(key => prevProps[key] !== nextProps[key]);
};

/**
 * Create a performance-optimized event handler
 */
export const useOptimizedEventHandler = <T extends (...args: unknown[]) => any>(
  handler: T,
  deps: React.DependencyList = []
): T => {
  return useCallback(handler, deps);
};
