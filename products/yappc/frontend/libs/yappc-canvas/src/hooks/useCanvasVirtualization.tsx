/**
 * @file Canvas Virtualization Hook
 * Efficiently renders large canvas scenes by only rendering visible elements
 *
 * @doc.type hook
 * @doc.purpose Optimize canvas performance for 1000+ elements
 * @doc.layer presentation
 * @doc.pattern PerformanceOptimization
 *
 * @example
 * ```typescript
 * const { visibleElements, containerRef, scrollPosition } = useCanvasVirtualization({
 *   elements: canvasElements,
 *   viewportWidth: 1920,
 *   viewportHeight: 1080,
 *   overscan: 100
 * });
 * ```
 */

import { useState, useEffect, useRef, useCallback, useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

export interface VirtualElement {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  zIndex?: number;
  type: string;
  data: unknown;
}

export interface ViewportBounds {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export interface VirtualizationConfig {
  elements: VirtualElement[];
  viewportWidth: number;
  viewportHeight: number;
  overscan?: number; // pixels to render outside viewport
  scale?: number; // zoom scale
  scrollX?: number;
  scrollY?: number;
}

export interface VirtualizationResult {
  visibleElements: VirtualElement[];
  hiddenElements: VirtualElement[];
  containerRef: React.RefObject<HTMLDivElement>;
  viewportBounds: ViewportBounds;
  totalElements: number;
  visibleCount: number;
  scrollPosition: { x: number; y: number };
  setScrollPosition: (x: number, y: number) => void;
  isVirtualizing: boolean;
}

// ============================================================================
// Spatial Index for Fast Queries
// ============================================================================

/**
 *
 */
class SpatialIndex {
  private grid: Map<string, VirtualElement[]> = new Map();
  private cellSize: number = 200;

  /**
   *
   */
  constructor(cellSize: number = 200) {
    this.cellSize = cellSize;
  }

  /**
   *
   */
  clear(): void {
    this.grid.clear();
  }

  /**
   *
   */
  insert(element: VirtualElement): void {
    const cellKey = this.getCellKey(element.x, element.y);
    if (!this.grid.has(cellKey)) {
      this.grid.set(cellKey, []);
    }
    this.grid.get(cellKey)!.push(element);
  }

  /**
   *
   */
  queryRange(bounds: ViewportBounds): VirtualElement[] {
    const results: VirtualElement[] = [];
    const seen = new Set<string>();

    // Calculate grid cells that intersect with bounds
    const startCol = Math.floor(bounds.left / this.cellSize);
    const endCol = Math.floor(bounds.right / this.cellSize);
    const startRow = Math.floor(bounds.top / this.cellSize);
    const endRow = Math.floor(bounds.bottom / this.cellSize);

    for (let col = startCol; col <= endCol; col++) {
      for (let row = startRow; row <= endRow; row++) {
        const cellKey = `${col},${row}`;
        const cell = this.grid.get(cellKey);
        if (cell) {
          for (const element of cell) {
            if (!seen.has(element.id)) {
              seen.add(element.id);
              // Precise check for actual intersection
              if (this.intersectsBounds(element, bounds)) {
                results.push(element);
              }
            }
          }
        }
      }
    }

    return results;
  }

  /**
   *
   */
  private getCellKey(x: number, y: number): string {
    const col = Math.floor(x / this.cellSize);
    const row = Math.floor(y / this.cellSize);
    return `${col},${row}`;
  }

  /**
   *
   */
  private intersectsBounds(
    element: VirtualElement,
    bounds: ViewportBounds
  ): boolean {
    return (
      element.x < bounds.right &&
      element.x + element.width > bounds.left &&
      element.y < bounds.bottom &&
      element.y + element.height > bounds.top
    );
  }
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Canvas Virtualization Hook
 * @doc.purpose Optimize rendering performance for large canvas scenes
 *
 * Features:
 * - Spatial indexing for O(1) visibility queries
 * - Overscan rendering to prevent pop-in
 * - Efficient scroll position tracking
 * - Automatic re-rendering on scroll/resize
 */
export function useCanvasVirtualization(
  config: VirtualizationConfig
): VirtualizationResult {
  const {
    elements,
    viewportWidth,
    viewportHeight,
    overscan = 100,
    scale = 1,
    scrollX: initialScrollX = 0,
    scrollY: initialScrollY = 0,
  } = config;

  const containerRef = useRef<HTMLDivElement>(null);
  const spatialIndexRef = useRef<SpatialIndex>(new SpatialIndex(200));
  const [scrollPosition, setScrollPosition] = useState({
    x: initialScrollX,
    y: initialScrollY,
  });
  const [isVirtualizing, setIsVirtualizing] = useState(true);

  // Build spatial index when elements change
  useEffect(() => {
    const index = spatialIndexRef.current;
    index.clear();

    // Batch insert for better performance
    const batchSize = 100;
    for (let i = 0; i < elements.length; i += batchSize) {
      const batch = elements.slice(i, i + batchSize);
      batch.forEach((element) => index.insert(element));
    }
  }, [elements]);

  // Calculate viewport bounds with overscan
  const viewportBounds = useMemo<ViewportBounds>(() => {
    const scaledOverscan = overscan / scale;
    return {
      left: scrollPosition.x - scaledOverscan,
      top: scrollPosition.y - scaledOverscan,
      right: scrollPosition.x + viewportWidth / scale + scaledOverscan,
      bottom: scrollPosition.y + viewportHeight / scale + scaledOverscan,
    };
  }, [scrollPosition, viewportWidth, viewportHeight, overscan, scale]);

  // Query visible elements
  const visibleElements = useMemo(() => {
    if (!isVirtualizing) return elements;

    // Use spatial index for fast query
    return spatialIndexRef.current.queryRange(viewportBounds);
  }, [elements, viewportBounds, isVirtualizing]);

  // Calculate hidden elements for debugging
  const hiddenElements = useMemo(() => {
    if (!isVirtualizing) return [];

    const visibleIds = new Set(visibleElements.map((e) => e.id));
    return elements.filter((e) => !visibleIds.has(e.id));
  }, [elements, visibleElements, isVirtualizing]);

  // Handle scroll events
  const handleScroll = useCallback(() => {
    if (!containerRef.current) return;

    const { scrollLeft, scrollTop } = containerRef.current;
    setScrollPosition({ x: scrollLeft, y: scrollTop });
  }, []);

  // Attach scroll listener
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    container.addEventListener('scroll', handleScroll, { passive: true });
    return () => container.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  // Toggle virtualization (useful for debugging)
  const toggleVirtualization = useCallback(() => {
    setIsVirtualizing((prev) => !prev);
  }, []);

  return {
    visibleElements,
    hiddenElements,
    containerRef,
    viewportBounds,
    totalElements: elements.length,
    visibleCount: visibleElements.length,
    scrollPosition,
    setScrollPosition,
    isVirtualizing,
  };
}

// ============================================================================
// Component: VirtualizedCanvas
// ============================================================================

import React from 'react';

interface VirtualizedCanvasProps {
  elements: VirtualElement[];
  renderElement: (element: VirtualElement) => React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
  viewportWidth: number;
  viewportHeight: number;
  overscan?: number;
  showDebugInfo?: boolean;
}

/**
 * Virtualized Canvas Component
 * @doc.purpose Render large canvas scenes efficiently
 *
 * @example
 * ```tsx
 * <VirtualizedCanvas
 *   elements={canvasElements}
 *   renderElement={(el) => <CanvasElement key={el.id} data={el} />}
 *   viewportWidth={1920}
 *   viewportHeight={1080}
 * />
 * ```
 */
export const VirtualizedCanvas: React.FC<VirtualizedCanvasProps> = ({
  elements,
  renderElement,
  className,
  style,
  viewportWidth,
  viewportHeight,
  overscan = 100,
  showDebugInfo = false,
}) => {
  const {
    visibleElements,
    containerRef,
    totalElements,
    visibleCount,
    scrollPosition,
  } = useCanvasVirtualization({
    elements,
    viewportWidth,
    viewportHeight,
    overscan,
  });

  return (
    <div
      ref={containerRef}
      className={className}
      style={{
        position: 'relative',
        overflow: 'auto',
        width: viewportWidth,
        height: viewportHeight,
        ...style,
      }}
    >
      {/* Render only visible elements */}
      {visibleElements.map(renderElement)}

      {/* Debug overlay */}
      {showDebugInfo && (
        <div
          style={{
            position: 'absolute',
            top: 10,
            left: 10,
            background: 'rgba(0,0,0,0.7)',
            color: 'white',
            padding: '8px 12px',
            borderRadius: '4px',
            fontSize: '12px',
            fontFamily: 'monospace',
            zIndex: 1000,
          }}
        >
          <div>
            Elements: {visibleCount} / {totalElements}
          </div>
          <div>
            Scroll: {Math.round(scrollPosition.x)},{' '}
            {Math.round(scrollPosition.y)}
          </div>
          <div>
            Rendering: {((visibleCount / totalElements) * 100).toFixed(1)}%
          </div>
        </div>
      )}
    </div>
  );
};

// ============================================================================
// Performance Monitoring
// ============================================================================

interface PerformanceMetrics {
  frameTime: number;
  elementCount: number;
  visibleCount: number;
  queryTime: number;
}

/**
 * Hook to monitor canvas performance
 * @doc.purpose Track virtualization performance metrics
 */
export function useCanvasPerformance(): {
  metrics: PerformanceMetrics;
  startMeasurement: () => void;
  endMeasurement: () => void;
} {
  const [metrics, setMetrics] = useState<PerformanceMetrics>({
    frameTime: 0,
    elementCount: 0,
    visibleCount: 0,
    queryTime: 0,
  });

  const startTimeRef = useRef<number>(0);
  const queryStartRef = useRef<number>(0);

  const startMeasurement = useCallback(() => {
    startTimeRef.current = performance.now();
  }, []);

  const endMeasurement = useCallback(() => {
    const frameTime = performance.now() - startTimeRef.current;
    setMetrics((prev) => ({
      ...prev,
      frameTime,
    }));
  }, []);

  const startQuery = useCallback(() => {
    queryStartRef.current = performance.now();
  }, []);

  const endQuery = useCallback(
    (elementCount: number, visibleCount: number) => {
      const queryTime = performance.now() - queryStartRef.current;
      setMetrics({
        frameTime: metrics.frameTime,
        elementCount,
        visibleCount,
        queryTime,
      });
    },
    [metrics.frameTime]
  );

  return {
    metrics,
    startMeasurement,
    endMeasurement,
  };
}

export default useCanvasVirtualization;
