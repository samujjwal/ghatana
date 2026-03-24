/**
 * Minimap & Viewport Controls
 *
 * Provides minimap state management and viewport control utilities:
 * - Minimap viewport synchronization
 * - Zoom-to-selection functionality
 * - Keyboard zoom controls
 * - Viewport bounds calculation for minimap rendering
 *
 * Feature 2.9: Minimap & Viewport Controls
 *
 * @module viewport/minimapState
 */

import type { Point, Viewport } from './infiniteSpace';

/**
 *
 */
export interface MinimapConfig {
  /** Width of minimap in pixels */
  width: number;
  /** Height of minimap in pixels */
  height: number;
  /** Padding around minimap content */
  padding: number;
  /** Background color */
  backgroundColor: string;
  /** Viewport indicator color */
  viewportColor: string;
  /** Node indicator color */
  nodeColor: string;
}

/**
 *
 */
export interface MinimapNode {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 *
 */
export interface MinimapViewport {
  /** Viewport rectangle in minimap coordinates */
  x: number;
  y: number;
  width: number;
  height: number;
  /** Main viewport zoom level */
  zoom: number;
}

/**
 *
 */
export interface CanvasBounds {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
  width: number;
  height: number;
}

/**
 *
 */
export interface ZoomConfig {
  min: number;
  max: number;
  step: number;
  /** Zoom increment for keyboard shortcuts */
  keyboardStep: number;
}

/**
 * Calculate canvas bounds from nodes
 *
 * Determines the bounding box containing all nodes,
 * used for minimap scaling and fit-to-view calculations.
 *
 * @param nodes - Array of nodes with position and size
 * @param padding - Additional padding around bounds
 * @returns Bounding box containing all nodes
 */
export function calculateCanvasBounds(
  nodes: MinimapNode[],
  padding = 50
): CanvasBounds {
  if (nodes.length === 0) {
    return {
      minX: -padding,
      minY: -padding,
      maxX: padding,
      maxY: padding,
      width: padding * 2,
      height: padding * 2,
    };
  }

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  for (const node of nodes) {
    minX = Math.min(minX, node.x);
    minY = Math.min(minY, node.y);
    maxX = Math.max(maxX, node.x + node.width);
    maxY = Math.max(maxY, node.y + node.height);
  }

  return {
    minX: minX - padding,
    minY: minY - padding,
    maxX: maxX + padding,
    maxY: maxY + padding,
    width: maxX - minX + padding * 2,
    height: maxY - minY + padding * 2,
  };
}

/**
 * Convert world coordinates to minimap coordinates
 *
 * Transforms positions from canvas world space to minimap display space.
 *
 * @param worldPoint - Point in world coordinates
 * @param canvasBounds - Canvas bounding box
 * @param minimapConfig - Minimap dimensions
 * @returns Point in minimap coordinates
 */
export function worldToMinimapCoordinates(
  worldPoint: Point,
  canvasBounds: CanvasBounds,
  minimapConfig: MinimapConfig
): Point {
  const { padding, width, height } = minimapConfig;
  const displayWidth = width - padding * 2;
  const displayHeight = height - padding * 2;

  const scaleX = displayWidth / canvasBounds.width;
  const scaleY = displayHeight / canvasBounds.height;
  const scale = Math.min(scaleX, scaleY);

  const offsetX = (width - canvasBounds.width * scale) / 2;
  const offsetY = (height - canvasBounds.height * scale) / 2;

  return {
    x: (worldPoint.x - canvasBounds.minX) * scale + offsetX,
    y: (worldPoint.y - canvasBounds.minY) * scale + offsetY,
  };
}

/**
 * Convert minimap coordinates to world coordinates
 *
 * Inverse transformation from minimap space to world space.
 *
 * @param minimapPoint - Point in minimap coordinates
 * @param canvasBounds - Canvas bounding box
 * @param minimapConfig - Minimap dimensions
 * @returns Point in world coordinates
 */
export function minimapToWorldCoordinates(
  minimapPoint: Point,
  canvasBounds: CanvasBounds,
  minimapConfig: MinimapConfig
): Point {
  const { padding, width, height } = minimapConfig;
  const displayWidth = width - padding * 2;
  const displayHeight = height - padding * 2;

  const scaleX = displayWidth / canvasBounds.width;
  const scaleY = displayHeight / canvasBounds.height;
  const scale = Math.min(scaleX, scaleY);

  const offsetX = (width - canvasBounds.width * scale) / 2;
  const offsetY = (height - canvasBounds.height * scale) / 2;

  return {
    x: (minimapPoint.x - offsetX) / scale + canvasBounds.minX,
    y: (minimapPoint.y - offsetY) / scale + canvasBounds.minY,
  };
}

/**
 * Calculate minimap viewport indicator position
 *
 * Determines where to draw the viewport indicator on the minimap.
 *
 * @param viewport - Current main viewport state
 * @param canvasBounds - Canvas bounding box
 * @param minimapConfig - Minimap dimensions
 * @returns Minimap viewport rectangle
 */
export function calculateMinimapViewport(
  viewport: Viewport,
  canvasBounds: CanvasBounds,
  minimapConfig: MinimapConfig
): MinimapViewport {
  // Calculate viewport corners in world coordinates
  const halfWidth = viewport.width / (2 * viewport.zoom);
  const halfHeight = viewport.height / (2 * viewport.zoom);

  const topLeft = {
    x: viewport.center.x - halfWidth,
    y: viewport.center.y - halfHeight,
  };

  const bottomRight = {
    x: viewport.center.x + halfWidth,
    y: viewport.center.y + halfHeight,
  };

  // Convert to minimap coordinates
  const minimapTopLeft = worldToMinimapCoordinates(
    topLeft,
    canvasBounds,
    minimapConfig
  );

  const minimapBottomRight = worldToMinimapCoordinates(
    bottomRight,
    canvasBounds,
    minimapConfig
  );

  return {
    x: minimapTopLeft.x,
    y: minimapTopLeft.y,
    width: minimapBottomRight.x - minimapTopLeft.x,
    height: minimapBottomRight.y - minimapTopLeft.y,
    zoom: viewport.zoom,
  };
}

/**
 * Calculate zoom level to fit selection
 *
 * Determines zoom and center point to fit selected nodes in viewport.
 *
 * @param selectedNodes - Nodes to fit in view
 * @param viewportSize - Viewport dimensions
 * @param padding - Padding around selection
 * @param zoomConfig - Zoom constraints
 * @returns New viewport state
 */
export function zoomToSelection(
  selectedNodes: MinimapNode[],
  viewportSize: { width: number; height: number },
  padding = 50,
  zoomConfig: ZoomConfig = { min: 0.1, max: 2, step: 0.1, keyboardStep: 0.1 }
): Viewport {
  if (selectedNodes.length === 0) {
    return {
      center: { x: 0, y: 0 },
      zoom: 1,
      width: viewportSize.width,
      height: viewportSize.height,
    };
  }

  const bounds = calculateCanvasBounds(selectedNodes, padding);

  // Calculate zoom to fit selection
  const zoomX = viewportSize.width / bounds.width;
  const zoomY = viewportSize.height / bounds.height;
  const targetZoom = Math.min(zoomX, zoomY);

  // Clamp zoom to config limits
  const zoom = Math.max(zoomConfig.min, Math.min(zoomConfig.max, targetZoom));

  // Calculate center point
  const center = {
    x: (bounds.minX + bounds.maxX) / 2,
    y: (bounds.minY + bounds.maxY) / 2,
  };

  return {
    center,
    zoom,
    width: viewportSize.width,
    height: viewportSize.height,
  };
}

/**
 * Apply keyboard zoom
 *
 * Handles zoom in/out via keyboard shortcuts (+/- keys).
 *
 * @param currentZoom - Current zoom level
 * @param direction - 'in' or 'out'
 * @param zoomConfig - Zoom constraints
 * @returns New zoom level
 */
export function applyKeyboardZoom(
  currentZoom: number,
  direction: 'in' | 'out',
  zoomConfig: ZoomConfig = { min: 0.1, max: 2, step: 0.1, keyboardStep: 0.1 }
): number {
  const delta =
    direction === 'in' ? zoomConfig.keyboardStep : -zoomConfig.keyboardStep;
  const newZoom = currentZoom + delta;

  return Math.max(zoomConfig.min, Math.min(zoomConfig.max, newZoom));
}

/**
 * Apply smooth zoom with animation
 *
 * Calculates intermediate zoom values for smooth transitions.
 *
 * @param startZoom - Starting zoom level
 * @param targetZoom - Target zoom level
 * @param progress - Animation progress (0-1)
 * @returns Interpolated zoom level
 */
export function interpolateZoom(
  startZoom: number,
  targetZoom: number,
  progress: number
): number {
  // Ensure progress is clamped to [0, 1]
  const t = Math.max(0, Math.min(1, progress));

  // Use ease-out cubic for smooth deceleration
  const eased = 1 - Math.pow(1 - t, 3);

  return startZoom + (targetZoom - startZoom) * eased;
}

/**
 * Handle minimap click to pan viewport
 *
 * Converts minimap click to new viewport center.
 *
 * @param minimapClickPoint - Click position in minimap coordinates
 * @param canvasBounds - Canvas bounding box
 * @param minimapConfig - Minimap dimensions
 * @param currentViewport - Current viewport state
 * @returns New viewport center
 */
export function handleMinimapClick(
  minimapClickPoint: Point,
  canvasBounds: CanvasBounds,
  minimapConfig: MinimapConfig,
  currentViewport: Viewport
): Point {
  return minimapToWorldCoordinates(
    minimapClickPoint,
    canvasBounds,
    minimapConfig
  );
}

/**
 * Check if point is inside minimap viewport indicator
 *
 * Used for detecting viewport drag operations.
 *
 * @param point - Point to test in minimap coordinates
 * @param minimapViewport - Minimap viewport rectangle
 * @returns true if point is inside viewport indicator
 */
export function isPointInMinimapViewport(
  point: Point,
  minimapViewport: MinimapViewport
): boolean {
  return (
    point.x >= minimapViewport.x &&
    point.x <= minimapViewport.x + minimapViewport.width &&
    point.y >= minimapViewport.y &&
    point.y <= minimapViewport.y + minimapViewport.height
  );
}

/**
 * Create default minimap configuration
 *
 * @param overrides - Partial config to override defaults
 * @returns Complete minimap configuration
 */
export function createMinimapConfig(
  overrides: Partial<MinimapConfig> = {}
): MinimapConfig {
  return {
    width: 200,
    height: 150,
    padding: 10,
    backgroundColor: '#f5f5f5',
    viewportColor: 'rgba(33, 150, 243, 0.3)',
    nodeColor: '#90a4ae',
    ...overrides,
  };
}

/**
 * Create default zoom configuration
 *
 * @param overrides - Partial config to override defaults
 * @returns Complete zoom configuration
 */
export function createZoomConfig(
  overrides: Partial<ZoomConfig> = {}
): ZoomConfig {
  return {
    min: 0.1,
    max: 2.0,
    step: 0.1,
    keyboardStep: 0.1,
    ...overrides,
  };
}
