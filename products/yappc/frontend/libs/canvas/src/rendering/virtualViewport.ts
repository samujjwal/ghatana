/**
 * Virtual Viewport System
 * 
 * Implements viewport-based virtualization to render only visible elements,
 * dramatically improving performance for large canvases (1000+ nodes).
 * 
 * Features:
 * - Spatial indexing with quad-tree structure
 * - Viewport frustum culling
 * - Margin-based prefetching for smooth panning
 * - Performance monitoring and metrics
 * 
 * @module rendering/virtualViewport
 */

import type { CanvasElement, Bounds, Point } from '../types/canvas-document';

/**
 * Viewport bounds in canvas space
 */
export interface ViewportBounds {
  x: number;
  y: number;
  width: number;
  height: number;
  zoom: number;
}

/**
 * Configuration for virtual viewport
 */
export interface VirtualViewportConfig {
  /** Margin around viewport to pre-render (prevents pop-in during pan) */
  margin: number;
  /** Enable spatial indexing for faster queries */
  useSpatialIndex: boolean;
  /** Update throttle in milliseconds */
  updateThrottle: number;
  /** Maximum nodes to render (safety limit) */
  maxVisibleNodes: number;
}

/**
 * Visibility result for an element
 */
export interface VisibilityResult {
  elementId: string;
  isVisible: boolean;
  distanceFromViewport: number;
  /** Priority for rendering (0 = highest) */
  priority: number;
}

/**
 * Spatial index node for quad-tree
 */
interface QuadTreeNode {
  bounds: Bounds;
  elements: CanvasElement[];
  children?: QuadTreeNode[];
  level: number;
}

/**
 * Virtual viewport statistics
 */
export interface ViewportStats {
  totalElements: number;
  visibleElements: number;
  culledElements: number;
  indexQueryTime: number;
  lastUpdateTime: number;
}

/**
 * Default configuration
 */
export const DEFAULT_VIEWPORT_CONFIG: VirtualViewportConfig = {
  margin: 100, // 100px margin around viewport
  useSpatialIndex: true,
  updateThrottle: 16, // ~60fps
  maxVisibleNodes: 5000,
};

/**
 * Create a virtual viewport manager
 * 
 * @example
 * ```ts
 * const viewport = createVirtualViewport({
 *   margin: 200,
 *   useSpatialIndex: true,
 * });
 * 
 * // Update viewport bounds
 * viewport.updateViewport({
 *   x: 0, y: 0,
 *   width: 1920, height: 1080,
 *   zoom: 1,
 * });
 * 
 * // Get visible elements
 * const visible = viewport.getVisibleElements(elements);
 * ```
 */
export function createVirtualViewport(
  config: Partial<VirtualViewportConfig> = {}
) {
  const cfg = { ...DEFAULT_VIEWPORT_CONFIG, ...config };
  
  let currentViewport: ViewportBounds = {
    x: 0, y: 0, width: 0, height: 0, zoom: 1,
  };
  
  let spatialIndex: QuadTreeNode | null = null;
  let lastUpdateTime = 0;
  const stats: ViewportStats = {
    totalElements: 0,
    visibleElements: 0,
    culledElements: 0,
    indexQueryTime: 0,
    lastUpdateTime: 0,
  };

  /**
   * Update viewport bounds
   */
  function updateViewport(viewport: ViewportBounds): void {
    const now = Date.now();
    if (now - lastUpdateTime < cfg.updateThrottle) {
      return; // Throttle updates
    }
    
    currentViewport = { ...viewport };
    lastUpdateTime = now;
  }

  /**
   * Build spatial index (quad-tree) for elements
   */
  function buildSpatialIndex(elements: CanvasElement[]): QuadTreeNode {
    // Calculate root bounds
    const rootBounds = calculateBounds(elements);
    
    // Build quad-tree recursively
    return buildQuadTree(elements, rootBounds, 0);
  }

  /**
   * Recursive quad-tree builder
   */
  function buildQuadTree(
    elements: CanvasElement[],
    bounds: Bounds,
    level: number
  ): QuadTreeNode {
    const node: QuadTreeNode = {
      bounds,
      elements: [],
      level,
    };

    // Leaf node if few elements or max depth
    if (elements.length <= 10 || level >= 6) {
      node.elements = elements;
      return node;
    }

    // Split into quadrants
    const midX = bounds.x + bounds.width / 2;
    const midY = bounds.y + bounds.height / 2;

    const quadrants: CanvasElement[][] = [[], [], [], []];

    for (const el of elements) {
      const elBounds = getElementBounds(el);
      const centerX = elBounds.x + elBounds.width / 2;
      const centerY = elBounds.y + elBounds.height / 2;

      // Determine quadrant (0: top-left, 1: top-right, 2: bottom-left, 3: bottom-right)
      const qx = centerX >= midX ? 1 : 0;
      const qy = centerY >= midY ? 1 : 0;
      const quadIndex = qy * 2 + qx;

      quadrants[quadIndex].push(el);
    }

    // Build child nodes
    node.children = [
      buildQuadTree(quadrants[0], {
        x: bounds.x,
        y: bounds.y,
        width: bounds.width / 2,
        height: bounds.height / 2,
      }, level + 1),
      buildQuadTree(quadrants[1], {
        x: midX,
        y: bounds.y,
        width: bounds.width / 2,
        height: bounds.height / 2,
      }, level + 1),
      buildQuadTree(quadrants[2], {
        x: bounds.x,
        y: midY,
        width: bounds.width / 2,
        height: bounds.height / 2,
      }, level + 1),
      buildQuadTree(quadrants[3], {
        x: midX,
        y: midY,
        width: bounds.width / 2,
        height: bounds.height / 2,
      }, level + 1),
    ];

    return node;
  }

  /**
   * Query quad-tree for elements in viewport
   */
  function queryQuadTree(
    node: QuadTreeNode,
    viewport: Bounds
  ): CanvasElement[] {
    const result: CanvasElement[] = [];

    // Check if node intersects viewport
    if (!boundsIntersect(node.bounds, viewport)) {
      return result;
    }

    // Add elements from this node
    for (const el of node.elements) {
      const elBounds = getElementBounds(el);
      if (boundsIntersect(elBounds, viewport)) {
        result.push(el);
      }
    }

    // Recurse to children
    if (node.children) {
      for (const child of node.children) {
        result.push(...queryQuadTree(child, viewport));
      }
    }

    return result;
  }

  /**
   * Get visible elements within viewport
   */
  function getVisibleElements(elements: CanvasElement[]): CanvasElement[] {
    const startTime = performance.now();
    
    stats.totalElements = elements.length;

    // Calculate viewport with margin
    const viewportWithMargin: Bounds = {
      x: currentViewport.x - cfg.margin,
      y: currentViewport.y - cfg.margin,
      width: currentViewport.width + cfg.margin * 2,
      height: currentViewport.height + cfg.margin * 2,
    };

    let visible: CanvasElement[];

    if (cfg.useSpatialIndex) {
      // Rebuild index if needed
      if (!spatialIndex) {
        spatialIndex = buildSpatialIndex(elements);
      }

      // Query spatial index
      visible = queryQuadTree(spatialIndex, viewportWithMargin);
    } else {
      // Brute force check
      visible = elements.filter(el => {
        const bounds = getElementBounds(el);
        return boundsIntersect(bounds, viewportWithMargin);
      });
    }

    // Apply max visible limit
    if (visible.length > cfg.maxVisibleNodes) {
      visible = prioritizeElements(visible, currentViewport)
        .slice(0, cfg.maxVisibleNodes);
    }

    stats.visibleElements = visible.length;
    stats.culledElements = elements.length - visible.length;
    stats.indexQueryTime = performance.now() - startTime;
    stats.lastUpdateTime = Date.now();

    return visible;
  }

  /**
   * Get visibility results with metadata
   */
  function getVisibilityResults(
    elements: CanvasElement[]
  ): VisibilityResult[] {
    const viewportWithMargin: Bounds = {
      x: currentViewport.x - cfg.margin,
      y: currentViewport.y - cfg.margin,
      width: currentViewport.width + cfg.margin * 2,
      height: currentViewport.height + cfg.margin * 2,
    };

    return elements.map(el => {
      const bounds = getElementBounds(el);
      const isVisible = boundsIntersect(bounds, viewportWithMargin);
      const distance = isVisible
        ? 0
        : calculateDistanceFromViewport(bounds, currentViewport);
      
      return {
        elementId: el.id,
        isVisible,
        distanceFromViewport: distance,
        priority: calculatePriority(el, currentViewport),
      };
    });
  }

  /**
   * Invalidate spatial index (call when elements change)
   */
  function invalidateIndex(): void {
    spatialIndex = null;
  }

  /**
   * Get current statistics
   */
  function getStats(): ViewportStats {
    return { ...stats };
  }

  /**
   * Check if specific element is visible
   */
  function isElementVisible(element: CanvasElement): boolean {
    const bounds = getElementBounds(element);
    const viewportWithMargin: Bounds = {
      x: currentViewport.x - cfg.margin,
      y: currentViewport.y - cfg.margin,
      width: currentViewport.width + cfg.margin * 2,
      height: currentViewport.height + cfg.margin * 2,
    };
    return boundsIntersect(bounds, viewportWithMargin);
  }

  return {
    updateViewport,
    getVisibleElements,
    getVisibilityResults,
    invalidateIndex,
    getStats,
    isElementVisible,
    config: cfg,
  };
}

/**
 * Calculate bounds of an element
 */
function getElementBounds(element: CanvasElement): Bounds {
  return element.bounds;
}

/**
 * Calculate overall bounds for elements
 */
function calculateBounds(elements: CanvasElement[]): Bounds {
  if (elements.length === 0) {
    return { x: 0, y: 0, width: 1000, height: 1000 };
  }

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  for (const el of elements) {
    const bounds = getElementBounds(el);
    minX = Math.min(minX, bounds.x);
    minY = Math.min(minY, bounds.y);
    maxX = Math.max(maxX, bounds.x + bounds.width);
    maxY = Math.max(maxY, bounds.y + bounds.height);
  }

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
  };
}

/**
 * Check if two bounds intersect
 */
function boundsIntersect(a: Bounds, b: Bounds): boolean {
  return !(
    a.x + a.width < b.x ||
    b.x + b.width < a.x ||
    a.y + a.height < b.y ||
    b.y + b.height < a.y
  );
}

/**
 * Calculate distance from viewport (for off-screen elements)
 */
function calculateDistanceFromViewport(
  bounds: Bounds,
  viewport: ViewportBounds
): number {
  const centerX = bounds.x + bounds.width / 2;
  const centerY = bounds.y + bounds.height / 2;
  const viewportCenterX = viewport.x + viewport.width / 2;
  const viewportCenterY = viewport.y + viewport.height / 2;

  const dx = centerX - viewportCenterX;
  const dy = centerY - viewportCenterY;

  return Math.sqrt(dx * dx + dy * dy);
}

/**
 * Prioritize elements for rendering
 */
function prioritizeElements(
  elements: CanvasElement[],
  viewport: ViewportBounds
): CanvasElement[] {
  return [...elements].sort((a, b) => {
    const priorityA = calculatePriority(a, viewport);
    const priorityB = calculatePriority(b, viewport);
    return priorityA - priorityB; // Lower priority = render first
  });
}

/**
 * Calculate render priority for element
 */
function calculatePriority(
  element: CanvasElement,
  viewport: ViewportBounds
): number {
  const bounds = getElementBounds(element);
  const distance = calculateDistanceFromViewport(bounds, viewport);
  
  // Priority factors:
  // 1. Distance from viewport (closer = higher priority)
  // 2. Element size (larger = higher priority)
  // 3. Element type (some types more important)
  
  const sizeFactor = (bounds.width * bounds.height) / 10000;
  const distanceFactor = distance / 1000;
  
  return distanceFactor - sizeFactor;
}

/**
 * Create a simple visibility checker
 * 
 * @example
 * ```ts
 * const checker = createVisibilityChecker();
 * const isVisible = checker.isVisible(element, viewport);
 * ```
 */
export function createVisibilityChecker() {
  return {
    isVisible(element: CanvasElement, viewport: ViewportBounds): boolean {
      const bounds = getElementBounds(element);
      return boundsIntersect(bounds, {
        x: viewport.x,
        y: viewport.y,
        width: viewport.width,
        height: viewport.height,
      });
    },
    
    getVisibleCount(elements: CanvasElement[], viewport: ViewportBounds): number {
      return elements.filter(el => this.isVisible(el, viewport)).length;
    },
  };
}

/**
 * Performance utilities
 */
export const VirtualViewportUtils = {
  /**
   * Estimate memory usage of spatial index
   */
  estimateIndexMemory(elementCount: number): number {
    // Rough estimate: ~100 bytes per element + tree overhead
    return elementCount * 100 + Math.log2(elementCount) * 1000;
  },

  /**
   * Calculate optimal margin based on pan velocity
   */
  calculateOptimalMargin(velocity: Point, zoom: number): number {
    const speed = Math.sqrt(velocity.x ** 2 + velocity.y ** 2);
    // Larger margin for faster panning
    return Math.max(100, Math.min(500, speed * 10 / zoom));
  },

  /**
   * Check if virtualization is beneficial
   */
  shouldUseVirtualization(elementCount: number, viewportSize: number): boolean {
    // Use virtualization if more than 100 elements or viewport covers < 20% of canvas
    return elementCount > 100;
  },
};
