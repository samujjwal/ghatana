/**
 * Element Transformation Utilities
 *
 * Provides low-level transformation operations for canvas elements:
 * - Multi-select dragging with snap lines
 * - Rotation with angle snapping
 * - Layer (z-index) ordering
 *
 * These utilities are framework-agnostic and work with any element structure
 * that conforms to the BaseElement interface.
 *
 * @module elements/transformations
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
export interface BaseElement {
  id: string;
  position: Point;
  data: {
    rotation?: number;
    width?: number;
    height?: number;
  };
  layerIndex: number;
}

/**
 *
 */
export interface SnapLine {
  position: number;
  orientation: 'horizontal' | 'vertical';
  elementIds: string[];
}

/**
 * Snap a value to the nearest snap point within tolerance
 */
export function snapValue(
  value: number,
  snapPoints: number[],
  tolerance: number
): { snapped: boolean; value: number } {
  for (const snapPoint of snapPoints) {
    if (Math.abs(value - snapPoint) <= tolerance) {
      return { snapped: true, value: snapPoint };
    }
  }
  return { snapped: false, value };
}

/**
 * Calculate snap lines for multi-select dragging
 *
 * Returns horizontal and vertical snap lines when elements align
 * within the tolerance threshold.
 */
export function calculateSnapLines(
  movingElements: BaseElement[],
  staticElements: BaseElement[],
  tolerance: number = 10
): SnapLine[] {
  const snapLines: SnapLine[] = [];

  // Extract positions from static elements
  const staticPositions = staticElements.map((el) => ({
    id: el.id,
    x: el.position.x,
    y: el.position.y,
    centerX: el.position.x + (el.data.width || 0) / 2,
    centerY: el.position.y + (el.data.height || 0) / 2,
    right: el.position.x + (el.data.width || 0),
    bottom: el.position.y + (el.data.height || 0),
  }));

  // Check each moving element against static positions
  for (const moving of movingElements) {
    const movingX = moving.position.x;
    const movingY = moving.position.y;
    const movingCenterX = movingX + (moving.data.width || 0) / 2;
    const movingCenterY = movingY + (moving.data.height || 0) / 2;

    for (const stat of staticPositions) {
      // Vertical snap lines (x alignment)
      if (Math.abs(movingX - stat.x) <= tolerance) {
        snapLines.push({
          position: stat.x,
          orientation: 'vertical',
          elementIds: [moving.id, stat.id],
        });
      }
      if (Math.abs(movingCenterX - stat.centerX) <= tolerance) {
        snapLines.push({
          position: stat.centerX,
          orientation: 'vertical',
          elementIds: [moving.id, stat.id],
        });
      }

      // Horizontal snap lines (y alignment)
      if (Math.abs(movingY - stat.y) <= tolerance) {
        snapLines.push({
          position: stat.y,
          orientation: 'horizontal',
          elementIds: [moving.id, stat.id],
        });
      }
      if (Math.abs(movingCenterY - stat.centerY) <= tolerance) {
        snapLines.push({
          position: stat.centerY,
          orientation: 'horizontal',
          elementIds: [moving.id, stat.id],
        });
      }
    }
  }

  return snapLines;
}

/**
 * Snap rotation angle to nearest snap point
 *
 * @param angle - Current angle in degrees
 * @param snapAngle - Snap increment in degrees (e.g., 15 for 15° snapping)
 * @returns Snapped angle in degrees
 */
export function snapRotation(angle: number, snapAngle: number): number {
  if (snapAngle <= 0) return angle;

  // Normalize angle to 0-360 range
  const normalized = ((angle % 360) + 360) % 360;

  // Find nearest snap point
  const snaps = Math.round(normalized / snapAngle);
  return snaps * snapAngle;
}

/**
 * Calculate rotation delta from mouse movement
 *
 * @param center - Center point of rotation
 * @param startPoint - Initial mouse position
 * @param currentPoint - Current mouse position
 * @param initialRotation - Initial rotation angle in degrees
 * @returns Rotation delta in degrees
 */
export function calculateRotationDelta(
  center: Point,
  startPoint: Point,
  currentPoint: Point,
  initialRotation: number = 0
): number {
  const startAngle = Math.atan2(
    startPoint.y - center.y,
    startPoint.x - center.x
  );
  const currentAngle = Math.atan2(
    currentPoint.y - center.y,
    currentPoint.x - center.x
  );

  const delta = ((currentAngle - startAngle) * 180) / Math.PI;
  return initialRotation + delta;
}

/**
 * Apply rotation to an element
 *
 * @param element - Element to rotate
 * @param rotation - New rotation angle in degrees
 * @param snapAngle - Optional snap angle (0 = no snapping)
 * @returns Updated element
 */
export function applyRotation<T extends BaseElement>(
  element: T,
  rotation: number,
  snapAngle: number = 0
): T {
  const snappedRotation =
    snapAngle > 0 ? snapRotation(rotation, snapAngle) : rotation;

  return {
    ...element,
    data: {
      ...element.data,
      rotation: snappedRotation,
    },
  };
}

/**
 * Update layer indices for z-order management
 *
 * @param elements - All elements
 * @param elementId - ID of element to move
 * @param direction - Direction to move ('forward' | 'backward' | 'front' | 'back')
 * @returns Updated elements array
 */
export function updateLayerOrder<T extends BaseElement>(
  elements: T[],
  elementId: string,
  direction: 'forward' | 'backward' | 'front' | 'back'
): T[] {
  const index = elements.findIndex((el) => el.id === elementId);
  if (index === -1) return elements;

  const element = elements[index];
  const sorted = [...elements].sort((a, b) => a.layerIndex - b.layerIndex);
  const currentLayerIndex = element.layerIndex;

  let newLayerIndex: number;

  switch (direction) {
    case 'forward':
      // Move one step forward
      const nextHigher = sorted.find((el) => el.layerIndex > currentLayerIndex);
      newLayerIndex = nextHigher
        ? nextHigher.layerIndex + 1
        : currentLayerIndex + 1;
      break;

    case 'backward':
      // Move one step backward
      const nextLower = sorted
        .reverse()
        .find((el) => el.layerIndex < currentLayerIndex);
      newLayerIndex =
        nextLower && nextLower.layerIndex > 0
          ? nextLower.layerIndex - 1
          : Math.max(0, currentLayerIndex - 1);
      break;

    case 'front':
      // Move to front (highest layer index + 1)
      newLayerIndex = Math.max(...elements.map((el) => el.layerIndex)) + 1;
      break;

    case 'back':
      // Move to back (lowest layer index - 1, minimum 0)
      const minIndex = Math.min(...elements.map((el) => el.layerIndex));
      newLayerIndex = Math.max(0, minIndex - 1);
      // Shift all others up to make room
      return elements.map((el) =>
        el.id === elementId
          ? { ...el, layerIndex: 0 }
          : { ...el, layerIndex: el.layerIndex + 1 }
      );

    default:
      return elements;
  }

  return elements.map((el) =>
    el.id === elementId ? { ...el, layerIndex: newLayerIndex } : el
  );
}

/**
 * Batch update positions for multi-select drag
 *
 * @param elements - All elements
 * @param selectedIds - IDs of selected elements to move
 * @param delta - Position delta {dx, dy}
 * @returns Updated elements array
 */
export function batchUpdatePositions<T extends BaseElement>(
  elements: T[],
  selectedIds: string[],
  delta: { dx: number; dy: number }
): T[] {
  const selectedSet = new Set(selectedIds);

  return elements.map((el) => {
    if (!selectedSet.has(el.id)) return el;

    return {
      ...el,
      position: {
        x: el.position.x + delta.dx,
        y: el.position.y + delta.dy,
      },
    };
  });
}

/**
 * Get bounding box for a set of elements
 */
export function getBoundingBox(elements: BaseElement[]): {
  x: number;
  y: number;
  width: number;
  height: number;
} | null {
  if (elements.length === 0) return null;

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  for (const el of elements) {
    const x = el.position.x;
    const y = el.position.y;
    const width = el.data.width || 0;
    const height = el.data.height || 0;

    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    maxX = Math.max(maxX, x + width);
    maxY = Math.max(maxY, y + height);
  }

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
  };
}
