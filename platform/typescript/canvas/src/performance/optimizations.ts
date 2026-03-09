/**
 * Canvas Performance Optimizations
 *
 * Comprehensive performance optimization utilities including memoization,
 * viewport culling, lazy loading, and performance monitoring.
 *
 * @doc.type utilities
 * @doc.purpose Performance optimization and monitoring
 * @doc.layer application
 */

import React, { memo, useMemo, useCallback, useRef, useEffect } from "react";
import { useAtomValue } from "jotai";

// ============================================================================
// PERFORMANCE TYPES
// ============================================================================

export interface PerformanceMetrics {
  renderTime: number;
  componentCount: number;
  visibleElements: number;
  culledElements: number;
  memoryUsage?: number;
  fps?: number;
}

export interface ViewportBounds {
  left: number;
  top: number;
  right: number;
  bottom: number;
  width: number;
  height: number;
}

export interface PerformanceConfig {
  enableViewportCulling: boolean;
  enableLazyLoading: boolean;
  enableMemoization: boolean;
  maxVisibleElements: number;
  cullingMargin: number;
  lazyLoadThreshold: number;
}

// ============================================================================
// PERFORMANCE MONITORING
// ============================================================================

/**
 * Performance monitor for tracking render performance
 */
export class PerformanceMonitor {
  private metrics: PerformanceMetrics = {
    renderTime: 0,
    componentCount: 0,
    visibleElements: 0,
    culledElements: 0,
  };

  private observers: Set<(metrics: PerformanceMetrics) => void> = new Set();
  private renderStartTime: number = 0;

  /**
   * Start performance measurement
   */
  startRender(): void {
    this.renderStartTime = performance.now();
  }

  /**
   * End performance measurement and update metrics
   */
  endRender(
    componentCount: number = 0,
    visibleElements: number = 0,
    culledElements: number = 0,
  ): void {
    this.metrics.renderTime = performance.now() - this.renderStartTime;
    this.metrics.componentCount = componentCount;
    this.metrics.visibleElements = visibleElements;
    this.metrics.culledElements = culledElements;

    // Calculate FPS if possible
    if (this.metrics.renderTime > 0) {
      this.metrics.fps = 1000 / this.metrics.renderTime;
    }

    this.notifyObservers();
  }

  /**
   * Subscribe to performance updates
   */
  subscribe(observer: (metrics: PerformanceMetrics) => void): () => void {
    this.observers.add(observer);
    return () => this.observers.delete(observer);
  }

  /**
   * Get current metrics
   */
  getMetrics(): PerformanceMetrics {
    return { ...this.metrics };
  }

  /**
   * Notify all observers
   */
  private notifyObservers(): void {
    this.observers.forEach((observer) => observer(this.getMetrics()));
  }

  /**
   * Reset metrics
   */
  reset(): void {
    this.metrics = {
      renderTime: 0,
      componentCount: 0,
      visibleElements: 0,
      culledElements: 0,
    };
  }

  /**
   * Set component count manually
   */
  setComponentCount(count: number): void {
    this.metrics.componentCount = count;
  }

  /**
   * Set visible elements count manually
   */
  setVisibleElements(count: number): void {
    this.metrics.visibleElements = count;
  }

  /**
   * Set culled elements count manually
   */
  setCulledElements(count: number): void {
    this.metrics.culledElements = count;
  }
}

// ============================================================================
// VIEWPORT CULLING
// ============================================================================

/**
 * Viewport culling utility for optimizing rendering of off-screen elements
 */
export class ViewportCuller {
  private viewportBounds: ViewportBounds;
  private cullingMargin: number;

  constructor(cullingMargin: number = 100) {
    this.cullingMargin = cullingMargin;
    this.viewportBounds = {
      left: 0,
      top: 0,
      right: window.innerWidth,
      bottom: window.innerHeight,
      width: window.innerWidth,
      height: window.innerHeight,
    };
  }

  /**
   * Update viewport bounds
   */
  updateViewportBounds(bounds: Partial<ViewportBounds>): void {
    this.viewportBounds = {
      ...this.viewportBounds,
      ...bounds,
    };
  }

  /**
   * Check if element is visible in viewport
   */
  isElementVisible(elementBounds: {
    left: number;
    top: number;
    right: number;
    bottom: number;
  }): boolean {
    // Add culling margin
    const expandedViewport = {
      left: this.viewportBounds.left - this.cullingMargin,
      top: this.viewportBounds.top - this.cullingMargin,
      right: this.viewportBounds.right + this.cullingMargin,
      bottom: this.viewportBounds.bottom + this.cullingMargin,
    };

    // Check if element intersects with expanded viewport
    return !(
      elementBounds.right < expandedViewport.left ||
      elementBounds.left > expandedViewport.right ||
      elementBounds.bottom < expandedViewport.top ||
      elementBounds.top > expandedViewport.bottom
    );
  }

  /**
   * Filter visible elements from a list
   */
  filterVisibleElements<
    T extends {
      bounds: { left: number; top: number; right: number; bottom: number };
    },
  >(elements: T[]): T[] {
    return elements.filter((element) => this.isElementVisible(element.bounds));
  }

  /**
   * Get viewport culling statistics
   */
  getCullingStats<T>(
    allElements: T[],
    visibleElements: T[],
  ): {
    total: number;
    visible: number;
    culled: number;
    cullingRatio: number;
  } {
    const total = allElements.length;
    const visible = visibleElements.length;
    const culled = total - visible;
    const cullingRatio = total > 0 ? culled / total : 0;

    return { total, visible, culled, cullingRatio };
  }
}

// ============================================================================
// LAZY LOADING
// ============================================================================

/**
 * Lazy loading utility for components and resources
 */
export class LazyLoader {
  private loadedItems: Set<string> = new Set();
  private loadingItems: Set<string> = new Set();
  private observers: Map<string, IntersectionObserver> = new Map();

  /**
   * Load an item lazily
   */
  async load<T>(
    id: string,
    loader: () => Promise<T>,
    threshold: number = 0.1,
  ): Promise<T> {
    if (this.loadedItems.has(id)) {
      throw new Error(`Item ${id} is already loaded`);
    }

    if (this.loadingItems.has(id)) {
      throw new Error(`Item ${id} is currently loading`);
    }

    this.loadingItems.add(id);

    try {
      const result = await loader();
      this.loadedItems.add(id);
      return result;
    } finally {
      this.loadingItems.delete(id);
    }
  }

  /**
   * Create intersection observer for lazy loading
   */
  createIntersectionObserver(
    id: string,
    element: HTMLElement,
    onIntersect: () => void,
    threshold: number = 0.1,
  ): void {
    if (this.observers.has(id)) {
      this.observers.get(id)?.disconnect();
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          onIntersect();
          observer.disconnect();
          this.observers.delete(id);
        }
      },
      { threshold },
    );

    observer.observe(element);
    this.observers.set(id, observer);
  }

  /**
   * Check if item is loaded
   */
  isLoaded(id: string): boolean {
    return this.loadedItems.has(id);
  }

  /**
   * Check if item is loading
   */
  isLoading(id: string): boolean {
    return this.loadingItems.has(id);
  }

  /**
   * Cleanup observers
   */
  cleanup(): void {
    this.observers.forEach((observer) => observer.disconnect());
    this.observers.clear();
  }
}

// ============================================================================
// MEMOIZATION UTILITIES
// ============================================================================

/**
 * Enhanced memo with custom comparison
 */
export function smartMemo<P extends object>(
  component: React.ComponentType<P>,
  areEqual?: (prevProps: P, nextProps: P) => boolean,
) {
  return memo(component, areEqual);
}

/**
 * Memoize expensive calculations
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function useMemoize<T extends (...args: any[]) => any>(
  fn: T,
  deps: React.DependencyList = [],
): T {
  // eslint-disable-next-line react-hooks/exhaustive-deps
  return useCallback(fn, deps) as T;
}

/**
 * Deep comparison utility for memoization
 */
export function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;

  if (typeof a !== typeof b) return false;

  if (typeof a === "object" && a !== null && b !== null) {
    const keysA = Object.keys(a as Record<string, unknown>);
    const keysB = Object.keys(b as Record<string, unknown>);

    if (keysA.length !== keysB.length) return false;

    for (const key of keysA) {
      if (
        !keysB.includes(key) ||
        !deepEqual((a as Record<string, unknown>)[key], (b as Record<string, unknown>)[key])
      ) {
        return false;
      }
    }

    return true;
  }

  return false;
}

// ============================================================================
// PERFORMANCE HOOKS
// ============================================================================

/**
 * Performance monitoring hook
 */
export const usePerformanceMonitor = () => {
  const monitorRef = useRef(new PerformanceMonitor());
  const componentCountRef = useRef(0);
  const visibleElementsRef = useRef(0);
  const culledElementsRef = useRef(0);

  useEffect(() => {
    const monitor = monitorRef.current;

    // Start render measurement
    monitor.startRender();

    // End render measurement after frame
    const frame = requestAnimationFrame(() => {
      monitor.endRender(
        componentCountRef.current,
        visibleElementsRef.current,
        culledElementsRef.current,
      );
    });

    return () => cancelAnimationFrame(frame);
  });

  return {
    monitor: monitorRef.current,
    setComponentCount: (count: number) => {
      componentCountRef.current = count;
    },
    setVisibleElements: (count: number) => {
      visibleElementsRef.current = count;
    },
    setCulledElements: (count: number) => {
      culledElementsRef.current = count;
    },
  };
};

/**
 * Viewport culling hook
 */
export const useViewportCulling = (cullingMargin: number = 100) => {
  const cullerRef = useRef(new ViewportCuller(cullingMargin));
  const viewportBoundsRef = useRef<ViewportBounds>({
    left: 0,
    top: 0,
    right: window.innerWidth,
    bottom: window.innerHeight,
    width: window.innerWidth,
    height: window.innerHeight,
  });

  useEffect(() => {
    const handleResize = () => {
      const bounds = {
        left: 0,
        top: 0,
        right: window.innerWidth,
        bottom: window.innerHeight,
        width: window.innerWidth,
        height: window.innerHeight,
      };

      cullerRef.current.updateViewportBounds(bounds);
      viewportBoundsRef.current = bounds;
    };

    window.addEventListener("resize", handleResize);
    handleResize(); // Initial call

    return () => window.removeEventListener("resize", handleResize);
  }, [cullingMargin]);

  return {
    culler: cullerRef.current,
    viewportBounds: viewportBoundsRef.current,
    isElementVisible: (bounds: {
      left: number;
      top: number;
      right: number;
      bottom: number;
    }) => cullerRef.current.isElementVisible(bounds),
    filterVisibleElements: <
      T extends {
        bounds: { left: number; top: number; right: number; bottom: number };
      },
    >(
      elements: T[],
    ) => cullerRef.current.filterVisibleElements(elements),
  };
};

/**
 * Lazy loading hook
 */
export const useLazyLoader = () => {
  const loaderRef = useRef(new LazyLoader());

  useEffect(() => {
    return () => {
      loaderRef.current.cleanup();
    };
  }, []);

  return {
    loader: loaderRef.current,
    load: <T>(id: string, loadFn: () => Promise<T>, threshold?: number) =>
      loaderRef.current.load(id, loadFn, threshold),
    createIntersectionObserver: (
      id: string,
      element: HTMLElement,
      onIntersect: () => void,
      threshold?: number,
    ) =>
      loaderRef.current.createIntersectionObserver(
        id,
        element,
        onIntersect,
        threshold,
      ),
    isLoaded: (id: string) => loaderRef.current.isLoaded(id),
    isLoading: (id: string) => loaderRef.current.isLoading(id),
  };
};

/**
 * Optimized component wrapper
 */
export const withPerformanceOptimizations = <P extends object>(
  Component: React.ComponentType<P>,
  config: Partial<PerformanceConfig> = {},
) => {
  const OptimizedComponent = smartMemo(Component, deepEqual);

  return (props: P) => {
    const { monitor } = usePerformanceMonitor();
    const { filterVisibleElements } = useViewportCulling();
    const { loader } = useLazyLoader();

    // Track component count
    monitor.setComponentCount(1);

    return React.createElement(OptimizedComponent, props);
  };
};

// ============================================================================
// PERFORMANCE UTILITIES
// ============================================================================

/**
 * Debounce utility for performance optimization
 */
export function debounce<T extends (...args: unknown[]) => unknown>(
  fn: T,
  delay: number,
): (...args: Parameters<T>) => void {
  let timeoutId: NodeJS.Timeout;

  return (...args: Parameters<T>) => {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => fn(...args), delay);
  };
}

/**
 * Throttle utility for performance optimization
 */
export function throttle<T extends (...args: unknown[]) => unknown>(
  fn: T,
  delay: number,
): (...args: Parameters<T>) => void {
  let lastCall = 0;

  return (...args: Parameters<T>) => {
    const now = Date.now();
    if (now - lastCall >= delay) {
      lastCall = now;
      fn(...args);
    }
  };
}

/**
 * Request animation frame utility
 */
export function rafThrottle<T extends (...args: unknown[]) => unknown>(
  fn: T,
): (...args: Parameters<T>) => void {
  let rafId: number | null = null;

  return (...args: Parameters<T>) => {
    if (rafId === null) {
      rafId = requestAnimationFrame(() => {
        fn(...args);
        rafId = null;
      });
    }
  };
}

/**
 * Memory usage monitoring
 */
export function getMemoryUsage(): number | null {
  if (typeof window === "undefined" || !(window as unknown as Record<string, Record<string, unknown>>).performance?.memory) {
    return null;
  }

  const memory = (window as unknown as Record<string, Record<string, unknown>>).performance.memory as Record<string, number>;
  return memory.usedJSHeapSize / memory.jsHeapSizeLimit;
}

/**
 * Performance mark and measure
 */
export function performanceMark(name: string): void {
  if (typeof window !== "undefined" && window.performance) {
    window.performance.mark(name);
  }
}

export function performanceMeasure(
  name: string,
  startMark: string,
  endMark: string,
): number {
  if (typeof window !== "undefined" && window.performance) {
    window.performance.measure(name, startMark, endMark);
    const entries = window.performance.getEntriesByName(name, "measure");
    return entries.length > 0 ? entries[entries.length - 1].duration : 0;
  }
  return 0;
}

// ============================================================================
// GLOBAL INSTANCES
// ============================================================================

export const globalPerformanceMonitor = new PerformanceMonitor();
export const globalViewportCuller = new ViewportCuller();
export const globalLazyLoader = new LazyLoader();
