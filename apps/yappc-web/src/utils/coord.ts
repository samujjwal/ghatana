/**
 * Coordinate transformation utilities for React Flow ↔ Konva sync
 * Centralizes viewport math to prevent drift between layers
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
  x: number;
  y: number;
  zoom: number;
}

/**
 * Convert screen coordinates to canvas coordinates
 * @param screenPoint - Point in screen space (e.g., mouse event)
 * @param viewport - Current viewport state
 * @returns Point in canvas coordinate space
 */
export function screenToCanvas(screenPoint: Point, viewport: Viewport): Point {
  return {
    x: (screenPoint.x - viewport.x) / viewport.zoom,
    y: (screenPoint.y - viewport.y) / viewport.zoom,
  };
}

/**
 * Convert canvas coordinates to screen coordinates
 * @param canvasPoint - Point in canvas space
 * @param viewport - Current viewport state
 * @returns Point in screen coordinate space
 */
export function canvasToScreen(canvasPoint: Point, viewport: Viewport): Point {
  return {
    x: canvasPoint.x * viewport.zoom + viewport.x,
    y: canvasPoint.y * viewport.zoom + viewport.y,
  };
}

/**
 * Apply viewport transform to Konva stage
 * @param viewport - Current viewport state
 * @returns Konva-compatible transform object
 */
export function viewportToKonvaTransform(viewport: Viewport) {
  return {
    x: viewport.x,
    y: viewport.y,
    scaleX: viewport.zoom,
    scaleY: viewport.zoom,
  };
}

/**
 * Round-trip test utility for coordinate conversion accuracy
 * @param point - Original point
 * @param viewport - Viewport to test with
 * @returns Error distance (should be < 1px for good accuracy)
 */
export function testCoordinateAccuracy(point: Point, viewport: Viewport): number {
  const screen = canvasToScreen(point, viewport);
  const backToCanvas = screenToCanvas(screen, viewport);
  const dx = point.x - backToCanvas.x;
  const dy = point.y - backToCanvas.y;
  return Math.sqrt(dx * dx + dy * dy);
}