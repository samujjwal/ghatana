/**
 * @ghatana/canvas Coordinate Utilities
 *
 * Coordinate transformation utilities between screen, canvas, and graph spaces.
 *
 * @doc.type module
 * @doc.purpose Coordinate transformations
 * @doc.layer core
 * @doc.pattern Utility
 */

import type { Point, Rect, ViewportState, CoordinateSystem } from "./types";

/**
 * Convert screen coordinates to canvas coordinates
 *
 * @param point - Point in screen coordinates
 * @param viewport - Current viewport state
 * @returns Point in canvas coordinates
 */
export function screenToCanvas(point: Point, viewport: ViewportState): Point {
  return {
    x: (point.x - viewport.x) / viewport.zoom,
    y: (point.y - viewport.y) / viewport.zoom,
  };
}

/**
 * Convert canvas coordinates to screen coordinates
 *
 * @param point - Point in canvas coordinates
 * @param viewport - Current viewport state
 * @returns Point in screen coordinates
 */
export function canvasToScreen(point: Point, viewport: ViewportState): Point {
  return {
    x: point.x * viewport.zoom + viewport.x,
    y: point.y * viewport.zoom + viewport.y,
  };
}

/**
 * Convert screen coordinates to canvas world coordinates.
 *
 * Alias for {@link screenToCanvas} using industry-standard naming.
 * Prefer this name in all new code.
 *
 * @param point - Point in screen coordinates
 * @param viewport - Current viewport state
 * @returns Point in world (canvas) coordinates
 */
export function screenToWorld(point: Point, viewport: ViewportState): Point {
  return screenToCanvas(point, viewport);
}

/**
 * Convert canvas world coordinates to screen coordinates.
 *
 * Alias for {@link canvasToScreen} using industry-standard naming.
 * Prefer this name in all new code.
 *
 * @param point - Point in world (canvas) coordinates
 * @param viewport - Current viewport state
 * @returns Point in screen coordinates
 */
export function worldToScreen(point: Point, viewport: ViewportState): Point {
  return canvasToScreen(point, viewport);
}

/**
 * Convert canvas coordinates to ReactFlow graph coordinates.
 *
 * @deprecated ReactFlow's "graph" coordinate space is identical to "canvas"
 * space. This function is an identity transform — call sites should use the
 * canvas-space value directly. Will be removed in the next major version.
 *
 * @param point - Point in canvas coordinates
 * @returns The same point unchanged
 */
export function canvasToGraph(point: Point): Point {
  return point;
}

/**
 * Convert ReactFlow graph coordinates to canvas coordinates.
 *
 * @deprecated ReactFlow's "graph" coordinate space is identical to "canvas"
 * space. This function is an identity transform — call sites should use the
 * graph-space value directly. Will be removed in the next major version.
 *
 * @param point - Point in graph coordinates
 * @returns The same point unchanged
 */
export function graphToCanvas(point: Point): Point {
  return point;
}

/**
 * Normalize coordinates between any two coordinate systems
 *
 * @param point - Input point
 * @param from - Source coordinate system
 * @param to - Target coordinate system
 * @param viewport - Current viewport state
 * @returns Point in target coordinate system
 */
export function normalizeCoordinates(
  point: Point,
  from: CoordinateSystem,
  to: CoordinateSystem,
  viewport: ViewportState,
): Point {
  if (from === to) return point;

  // Convert to canvas/world space first (common intermediate space).
  // 'graph' is identical to 'canvas' in ReactFlow's model — both map to
  // world coordinates. No conversion needed between them.
  let canvasPoint: Point;
  switch (from) {
    case "screen":
      canvasPoint = screenToCanvas(point, viewport);
      break;
    case "graph":
    case "canvas":
    default:
      canvasPoint = point;
  }

  // Convert from canvas/world space to the target space.
  switch (to) {
    case "screen":
      return canvasToScreen(canvasPoint, viewport);
    case "graph":
    case "canvas":
    default:
      return canvasPoint;
  }
}

/**
 * Convert a rectangle from one coordinate system to another
 *
 * @param rect - Input rectangle
 * @param from - Source coordinate system
 * @param to - Target coordinate system
 * @param viewport - Current viewport state
 * @returns Rectangle in target coordinate system
 */
export function normalizeRect(
  rect: Rect,
  from: CoordinateSystem,
  to: CoordinateSystem,
  viewport: ViewportState,
): Rect {
  const topLeft = normalizeCoordinates(
    { x: rect.x, y: rect.y },
    from,
    to,
    viewport,
  );
  const bottomRight = normalizeCoordinates(
    { x: rect.x + rect.width, y: rect.y + rect.height },
    from,
    to,
    viewport,
  );

  return {
    x: topLeft.x,
    y: topLeft.y,
    width: bottomRight.x - topLeft.x,
    height: bottomRight.y - topLeft.y,
  };
}

/**
 * Apply viewport transformation to a point
 *
 * @param point - Point to transform
 * @param viewport - Viewport state
 * @returns Transformed point
 */
export function applyViewportTransform(
  point: Point,
  viewport: ViewportState,
): Point {
  return {
    x: point.x * viewport.zoom + viewport.x,
    y: point.y * viewport.zoom + viewport.y,
  };
}

/**
 * Remove viewport transformation from a point
 *
 * @param point - Transformed point
 * @param viewport - Viewport state
 * @returns Original point
 */
export function removeViewportTransform(
  point: Point,
  viewport: ViewportState,
): Point {
  return {
    x: (point.x - viewport.x) / viewport.zoom,
    y: (point.y - viewport.y) / viewport.zoom,
  };
}

/**
 * Snap a point to grid
 *
 * @param point - Point to snap
 * @param gridSize - Grid cell size
 * @returns Snapped point
 */
export function snapToGrid(point: Point, gridSize: number): Point {
  return {
    x: Math.round(point.x / gridSize) * gridSize,
    y: Math.round(point.y / gridSize) * gridSize,
  };
}

/**
 * Calculate distance between two points
 *
 * @param p1 - First point
 * @param p2 - Second point
 * @returns Distance
 */
export function distance(p1: Point, p2: Point): number {
  const dx = p2.x - p1.x;
  const dy = p2.y - p1.y;
  return Math.sqrt(dx * dx + dy * dy);
}

/**
 * Check if a point is inside a rectangle
 *
 * @param point - Point to check
 * @param rect - Rectangle to check against
 * @returns True if point is inside rectangle
 */
export function pointInRect(point: Point, rect: Rect): boolean {
  return (
    point.x >= rect.x &&
    point.x <= rect.x + rect.width &&
    point.y >= rect.y &&
    point.y <= rect.y + rect.height
  );
}

/**
 * Check if two rectangles intersect
 *
 * @param r1 - First rectangle
 * @param r2 - Second rectangle
 * @returns True if rectangles intersect
 */
export function rectsIntersect(r1: Rect, r2: Rect): boolean {
  return !(
    r1.x + r1.width < r2.x ||
    r2.x + r2.width < r1.x ||
    r1.y + r1.height < r2.y ||
    r2.y + r2.height < r1.y
  );
}

/**
 * Get the bounding box of multiple rectangles
 *
 * @param rects - Array of rectangles
 * @returns Bounding rectangle
 */
export function getBoundingRect(rects: Rect[]): Rect | null {
  if (rects.length === 0) return null;

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  for (const rect of rects) {
    minX = Math.min(minX, rect.x);
    minY = Math.min(minY, rect.y);
    maxX = Math.max(maxX, rect.x + rect.width);
    maxY = Math.max(maxY, rect.y + rect.height);
  }

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
  };
}

/**
 * Get the center point of a rectangle
 *
 * @param rect - Rectangle
 * @returns Center point
 */
export function getRectCenter(rect: Rect): Point {
  return {
    x: rect.x + rect.width / 2,
    y: rect.y + rect.height / 2,
  };
}

/**
 * Expand a rectangle by a given amount
 *
 * @param rect - Rectangle to expand
 * @param amount - Amount to expand by
 * @returns Expanded rectangle
 */
export function expandRect(rect: Rect, amount: number): Rect {
  return {
    x: rect.x - amount,
    y: rect.y - amount,
    width: rect.width + amount * 2,
    height: rect.height + amount * 2,
  };
}

/**
 * Calculate the viewport bounds in canvas coordinates
 *
 * @param containerWidth - Container width in pixels
 * @param containerHeight - Container height in pixels
 * @param viewport - Viewport state
 * @returns Viewport bounds in canvas coordinates
 */
export function getViewportBounds(
  containerWidth: number,
  containerHeight: number,
  viewport: ViewportState,
): Rect {
  const topLeft = screenToCanvas({ x: 0, y: 0 }, viewport);
  const bottomRight = screenToCanvas(
    { x: containerWidth, y: containerHeight },
    viewport,
  );

  return {
    x: topLeft.x,
    y: topLeft.y,
    width: bottomRight.x - topLeft.x,
    height: bottomRight.y - topLeft.y,
  };
}

/**
 * Calculate zoom to fit a rectangle in the viewport
 *
 * @param rect - Rectangle to fit
 * @param containerWidth - Container width
 * @param containerHeight - Container height
 * @param padding - Padding around the content
 * @returns Viewport state to fit the rectangle
 */
export function calculateZoomToFit(
  rect: Rect,
  containerWidth: number,
  containerHeight: number,
  padding = 50,
): ViewportState {
  const availableWidth = containerWidth - padding * 2;
  const availableHeight = containerHeight - padding * 2;

  const zoomX = availableWidth / rect.width;
  const zoomY = availableHeight / rect.height;
  const zoom = Math.min(zoomX, zoomY, 1);

  const center = getRectCenter(rect);

  return {
    x: containerWidth / 2 - center.x * zoom,
    y: containerHeight / 2 - center.y * zoom,
    zoom,
    minZoom: 0.1,
    maxZoom: 5,
  };
}
