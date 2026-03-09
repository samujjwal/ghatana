/**
 * Infinite Canvas Utilities
 *
 * Provides viewport management for unbounded pan/zoom:
 * - Origin shift detection to prevent float precision errors
 * - Coordinate transformation utilities
 * - Viewport bounds calculation
 * - Tiled background positioning
 *
 * Feature 2.5: Infinite Canvas
 *
 * @module viewport/infiniteSpace
 */

/**
 *
 */
export interface Point {
  x: number;
  y: number;
}

/**
 *
 */
export interface Viewport {
  /** Center position in world coordinates */
  center: Point;
  /** Zoom level (1 = 100%, 0.5 = 50%, 2 = 200%) */
  zoom: number;
  /** Viewport dimensions in screen pixels */
  width: number;
  height: number;
}

/**
 *
 */
export interface ViewportTransform {
  /** Translation in screen pixels */
  translation: Point;
  /** Scale factor */
  scale: number;
}

/**
 *
 */
export interface OriginShiftConfig {
  /** Distance threshold in screen pixels before triggering origin shift */
  threshold: number;
  /** Minimum threshold (safety guard) */
  minThreshold?: number;
  /** Maximum threshold (safety guard) */
  maxThreshold?: number;
}

/**
 * Check if viewport translation exceeds threshold and needs origin shift
 *
 * Origin shift prevents floating-point precision errors by re-centering
 * the canvas when the viewport translation becomes too large.
 *
 * @param translation - Current viewport translation in screen pixels
 * @param threshold - Shift threshold in screen pixels
 * @returns true if origin shift should be performed
 */
export function shouldShiftOrigin(
  translation: Point,
  threshold: number
): boolean {
  if (!Number.isFinite(threshold) || threshold <= 0) {
    return false;
  }

  return (
    Math.abs(translation.x) >= threshold || Math.abs(translation.y) >= threshold
  );
}

/**
 * Compute world-space delta for origin shift
 *
 * Converts screen-space translation to world-space coordinates
 * for repositioning elements during origin shift.
 *
 * @param translation - Viewport translation in screen pixels
 * @param scale - Current zoom scale
 * @returns Delta in world coordinates
 */
export function computeOriginShiftDelta(
  translation: Point,
  scale: number
): Point {
  if (!Number.isFinite(scale) || scale === 0) {
    return { x: 0, y: 0 };
  }

  return {
    x: translation.x / scale,
    y: translation.y / scale,
  };
}

/**
 * Calculate viewport bounds in world coordinates
 *
 * Determines the visible area of the canvas in world space,
 * useful for viewport culling and rendering optimization.
 *
 * @param viewport - Current viewport state
 * @returns Bounding box in world coordinates
 */
export function getViewportBounds(viewport: Viewport): {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
  width: number;
  height: number;
} {
  const halfWidth = viewport.width / (2 * viewport.zoom);
  const halfHeight = viewport.height / (2 * viewport.zoom);

  const minX = viewport.center.x - halfWidth;
  const maxX = viewport.center.x + halfWidth;
  const minY = viewport.center.y - halfHeight;
  const maxY = viewport.center.y + halfHeight;

  return {
    minX,
    minY,
    maxX,
    maxY,
    width: maxX - minX,
    height: maxY - minY,
  };
}

/**
 * Convert screen coordinates to world coordinates
 *
 * @param screenPoint - Point in screen space (e.g., mouse event coordinates)
 * @param viewport - Current viewport state
 * @returns Point in world coordinate space
 */
export function screenToWorld(screenPoint: Point, viewport: Viewport): Point {
  const bounds = getViewportBounds(viewport);

  return {
    x: bounds.minX + (screenPoint.x / viewport.width) * bounds.width,
    y: bounds.minY + (screenPoint.y / viewport.height) * bounds.height,
  };
}

/**
 * Convert world coordinates to screen coordinates
 *
 * @param worldPoint - Point in world space
 * @param viewport - Current viewport state
 * @returns Point in screen coordinate space
 */
export function worldToScreen(worldPoint: Point, viewport: Viewport): Point {
  const bounds = getViewportBounds(viewport);

  return {
    x: ((worldPoint.x - bounds.minX) / bounds.width) * viewport.width,
    y: ((worldPoint.y - bounds.minY) / bounds.height) * viewport.height,
  };
}

/**
 * Check if a point is visible in the viewport
 *
 * @param point - Point in world coordinates
 * @param viewport - Current viewport state
 * @param margin - Optional margin in world units (for culling optimization)
 * @returns true if point is within viewport bounds
 */
export function isPointVisible(
  point: Point,
  viewport: Viewport,
  margin: number = 0
): boolean {
  const bounds = getViewportBounds(viewport);

  return (
    point.x >= bounds.minX - margin &&
    point.x <= bounds.maxX + margin &&
    point.y >= bounds.minY - margin &&
    point.y <= bounds.maxY + margin
  );
}

/**
 * Check if a rectangular region is visible in the viewport
 *
 * @param rect - Rectangle in world coordinates
 * @param viewport - Current viewport state
 * @param margin - Optional margin in world units
 * @returns true if rectangle intersects viewport
 */
export function isRectVisible(
  rect: { x: number; y: number; width: number; height: number },
  viewport: Viewport,
  margin: number = 0
): boolean {
  const bounds = getViewportBounds(viewport);

  return !(
    rect.x + rect.width < bounds.minX - margin ||
    rect.x > bounds.maxX + margin ||
    rect.y + rect.height < bounds.minY - margin ||
    rect.y > bounds.maxY + margin
  );
}

/**
 * Calculate tiled background offset for infinite canvas
 *
 * Computes the offset for seamlessly tiling background patterns
 * as the viewport pans, creating an infinite grid effect.
 *
 * @param viewport - Current viewport state
 * @param tileSize - Size of tile pattern in world units
 * @returns Background offset in screen pixels
 */
export function getTiledBackgroundOffset(
  viewport: Viewport,
  tileSize: number
): Point {
  const bounds = getViewportBounds(viewport);

  // Calculate how many tiles offset from origin
  const tilesX = Math.floor(bounds.minX / tileSize);
  const tilesY = Math.floor(bounds.minY / tileSize);

  // Calculate world offset (modulo tile size)
  const worldOffsetX = bounds.minX - tilesX * tileSize;
  const worldOffsetY = bounds.minY - tilesY * tileSize;

  // Convert to screen pixels
  return {
    x: worldOffsetX * viewport.zoom,
    y: worldOffsetY * viewport.zoom,
  };
}

/**
 * Clamp viewport zoom to safe range
 *
 * Prevents extreme zoom levels that cause rendering or precision issues.
 *
 * @param zoom - Desired zoom level
 * @param minZoom - Minimum allowed zoom (default: 0.1 = 10%)
 * @param maxZoom - Maximum allowed zoom (default: 5.0 = 500%)
 * @returns Clamped zoom value
 */
export function clampZoom(
  zoom: number,
  minZoom: number = 0.1,
  maxZoom: number = 5.0
): number {
  if (!Number.isFinite(zoom)) {
    return 1.0;
  }

  return Math.max(minZoom, Math.min(maxZoom, zoom));
}

/**
 * Calculate zoom to fit all elements in viewport
 *
 * Computes optimal zoom and center to show all elements with padding.
 *
 * @param elements - Array of elements with bounds
 * @param viewport - Current viewport dimensions
 * @param padding - Padding in screen pixels
 * @returns Optimal viewport state or null if no elements
 */
export function fitElementsInView(
  elements: Array<{ x: number; y: number; width: number; height: number }>,
  viewport: { width: number; height: number },
  padding: number = 40
): { center: Point; zoom: number } | null {
  if (elements.length === 0) {
    return null;
  }

  let minX = Number.POSITIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;

  elements.forEach((el) => {
    minX = Math.min(minX, el.x);
    minY = Math.min(minY, el.y);
    maxX = Math.max(maxX, el.x + el.width);
    maxY = Math.max(maxY, el.y + el.height);
  });

  if (
    !Number.isFinite(minX) ||
    !Number.isFinite(minY) ||
    !Number.isFinite(maxX) ||
    !Number.isFinite(maxY)
  ) {
    return null;
  }

  const contentWidth = Math.max(1, maxX - minX);
  const contentHeight = Math.max(1, maxY - minY);

  // Calculate zoom to fit content with padding
  const availableWidth = viewport.width - padding * 2;
  const availableHeight = viewport.height - padding * 2;

  const zoomX = availableWidth / contentWidth;
  const zoomY = availableHeight / contentHeight;

  const zoom = clampZoom(Math.min(zoomX, zoomY));

  // Center on content
  const center = {
    x: minX + contentWidth / 2,
    y: minY + contentHeight / 2,
  };

  return { center, zoom };
}

/**
 * Apply zoom at a specific point (zoom-to-cursor behavior)
 *
 * @param viewport - Current viewport state
 * @param zoomDelta - Zoom change (positive = zoom in, negative = zoom out)
 * @param zoomPoint - Point to zoom toward (in screen coordinates)
 * @returns Updated viewport state
 */
export function zoomAtPoint(
  viewport: Viewport,
  zoomDelta: number,
  zoomPoint: Point
): Viewport {
  const newZoom = clampZoom(viewport.zoom * Math.exp(zoomDelta));

  if (newZoom === viewport.zoom) {
    return viewport;
  }

  // Convert zoom point to world coordinates at current zoom
  const worldPoint = screenToWorld(zoomPoint, viewport);

  // After zoom, we want worldPoint to still map to zoomPoint (screen)
  // screenToWorld: screen -> world
  // We need to adjust center so that:
  //   worldPoint = center + (screen - screenCenter) / newZoom
  // Solving for center:
  //   center = worldPoint - (screen - screenCenter) / newZoom

  const screenCenter = {
    x: viewport.width / 2,
    y: viewport.height / 2,
  };

  const newCenter = {
    x: worldPoint.x - (zoomPoint.x - screenCenter.x) / newZoom,
    y: worldPoint.y - (zoomPoint.y - screenCenter.y) / newZoom,
  };

  return {
    ...viewport,
    zoom: newZoom,
    center: newCenter,
  };
}

/**
 * Test coordinate conversion accuracy (for debugging/validation)
 *
 * Performs round-trip conversion to detect precision loss.
 *
 * @param point - Point to test
 * @param viewport - Viewport to test with
 * @returns Error distance in world units (should be < 1px for good accuracy)
 */
export function testCoordinateAccuracy(
  point: Point,
  viewport: Viewport
): number {
  const screen = worldToScreen(point, viewport);
  const backToWorld = screenToWorld(screen, viewport);

  const dx = point.x - backToWorld.x;
  const dy = point.y - backToWorld.y;

  return Math.sqrt(dx * dx + dy * dy);
}

/**
 * Validate origin shift configuration
 *
 * @param config - Origin shift configuration to validate
 * @returns Validated configuration with safe defaults
 */
export function validateOriginShiftConfig(
  config: Partial<OriginShiftConfig>
): OriginShiftConfig {
  const minThreshold = config.minThreshold ?? 400;
  const maxThreshold = config.maxThreshold ?? 5000;

  let threshold = config.threshold ?? 1800;

  // Clamp threshold to safe range
  threshold = Math.max(minThreshold, Math.min(maxThreshold, threshold));

  return {
    threshold,
    minThreshold,
    maxThreshold,
  };
}
