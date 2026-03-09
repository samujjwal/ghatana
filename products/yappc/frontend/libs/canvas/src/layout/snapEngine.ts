/**
 * Grid Snapping & Alignment Engine
 *
 * Provides grid-based snapping, alignment, and distribution utilities:
 * - Configurable grid sizes and snap tolerance
 * - Snap-to-grid for positions and dimensions
 * - Element distribution (horizontal, vertical, even spacing)
 * - Alignment helpers (left, center, right, top, middle, bottom)
 *
 * @module layout/snapEngine
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
export interface GridConfig {
  /** Grid cell size in pixels */
  size: number;
  /** Snap tolerance in pixels (default: half grid size) */
  tolerance?: number;
  /** Enable grid snapping */
  enabled: boolean;
}

/**
 *
 */
export interface DistributionOptions {
  /** Direction to distribute elements */
  direction: 'horizontal' | 'vertical';
  /** Spacing mode */
  mode: 'even' | 'fixed';
  /** Fixed spacing in pixels (for 'fixed' mode) */
  spacing?: number;
}

/**
 *
 */
export interface AlignmentOptions {
  /** Alignment type */
  type: 'left' | 'center' | 'right' | 'top' | 'middle' | 'bottom';
  /** Reference point for alignment (if not provided, uses selection bounds) */
  reference?: Point;
}

/**
 * Calculate nearest grid point
 */
export function snapToGrid(value: number, gridSize: number): number {
  return Math.round(value / gridSize) * gridSize;
}

/**
 * Snap a point to grid with tolerance
 *
 * If the point is within tolerance of a grid line, snap it.
 * Otherwise, return the original value.
 */
export function snapPointToGrid(point: Point, config: GridConfig): Point {
  if (!config.enabled) {
    return point;
  }

  const tolerance = config.tolerance ?? config.size / 2;
  const gridX = snapToGrid(point.x, config.size);
  const gridY = snapToGrid(point.y, config.size);

  return {
    x: Math.abs(point.x - gridX) <= tolerance ? gridX : point.x,
    y: Math.abs(point.y - gridY) <= tolerance ? gridY : point.y,
  };
}

/**
 * Get grid lines within viewport
 */
export function getGridLines(
  viewport: { x: number; y: number; width: number; height: number },
  gridSize: number
): { horizontal: number[]; vertical: number[] } {
  const startX = Math.floor(viewport.x / gridSize) * gridSize;
  const endX = Math.ceil((viewport.x + viewport.width) / gridSize) * gridSize;
  const startY = Math.floor(viewport.y / gridSize) * gridSize;
  const endY = Math.ceil((viewport.y + viewport.height) / gridSize) * gridSize;

  const vertical: number[] = [];
  for (let x = startX; x <= endX; x += gridSize) {
    vertical.push(x);
  }

  const horizontal: number[] = [];
  for (let y = startY; y <= endY; y += gridSize) {
    horizontal.push(y);
  }

  return { horizontal, vertical };
}

/**
 * Distribute elements evenly
 */
export function distributeElements<
  T extends { position: Point; data: { width?: number; height?: number } },
>(elements: T[], options: DistributionOptions): T[] {
  if (elements.length < 2) {
    return elements;
  }

  // Sort elements by position
  const sorted = [...elements].sort((a, b) => {
    if (options.direction === 'horizontal') {
      return a.position.x - b.position.x;
    }
    return a.position.y - b.position.y;
  });

  if (options.mode === 'fixed' && options.spacing !== undefined) {
    // Fixed spacing between elements
    return distributeWithFixedSpacing(
      sorted,
      options.direction,
      options.spacing
    );
  }

  // Even distribution
  return distributeEvenly(sorted, options.direction);
}

/**
 * Distribute elements with fixed spacing
 */
function distributeWithFixedSpacing<
  T extends { position: Point; data: { width?: number; height?: number } },
>(elements: T[], direction: 'horizontal' | 'vertical', spacing: number): T[] {
  const result: T[] = [];
  let currentPos =
    direction === 'horizontal'
      ? elements[0].position.x
      : elements[0].position.y;

  for (let i = 0; i < elements.length; i++) {
    const el = elements[i];

    if (direction === 'horizontal') {
      result.push({
        ...el,
        position: { ...el.position, x: currentPos },
      });
      currentPos += (el.data.width || 0) + spacing;
    } else {
      result.push({
        ...el,
        position: { ...el.position, y: currentPos },
      });
      currentPos += (el.data.height || 0) + spacing;
    }
  }

  return result;
}

/**
 * Distribute elements evenly (equal spacing between)
 */
function distributeEvenly<
  T extends { position: Point; data: { width?: number; height?: number } },
>(elements: T[], direction: 'horizontal' | 'vertical'): T[] {
  const first = elements[0];
  const last = elements[elements.length - 1];

  // Calculate total span
  const startPos =
    direction === 'horizontal' ? first.position.x : first.position.y;
  const endPos =
    direction === 'horizontal'
      ? last.position.x + (last.data.width || 0)
      : last.position.y + (last.data.height || 0);

  // Calculate total element size
  const totalElementSize = elements.reduce((sum, el) => {
    return (
      sum +
      (direction === 'horizontal' ? el.data.width || 0 : el.data.height || 0)
    );
  }, 0);

  // Calculate spacing between elements
  const totalSpan = endPos - startPos;
  const spacing = (totalSpan - totalElementSize) / (elements.length - 1);

  // Distribute
  const result: T[] = [];
  let currentPos = startPos;

  for (const el of elements) {
    if (direction === 'horizontal') {
      result.push({
        ...el,
        position: { ...el.position, x: currentPos },
      });
      currentPos += (el.data.width || 0) + spacing;
    } else {
      result.push({
        ...el,
        position: { ...el.position, y: currentPos },
      });
      currentPos += (el.data.height || 0) + spacing;
    }
  }

  return result;
}

/**
 * Align elements
 */
export function alignElements<
  T extends { position: Point; data: { width?: number; height?: number } },
>(elements: T[], options: AlignmentOptions): T[] {
  if (elements.length === 0) {
    return elements;
  }

  // Calculate reference point
  let referenceValue: number;

  if (options.reference) {
    referenceValue =
      options.type === 'left' ||
      options.type === 'center' ||
      options.type === 'right'
        ? options.reference.x
        : options.reference.y;
  } else {
    // Use bounds of selection
    referenceValue = calculateReferenceFromBounds(elements, options.type);
  }

  // Align each element
  return elements.map((el) => {
    const newPosition = { ...el.position };

    switch (options.type) {
      case 'left':
        newPosition.x = referenceValue;
        break;
      case 'center':
        newPosition.x = referenceValue - (el.data.width || 0) / 2;
        break;
      case 'right':
        newPosition.x = referenceValue - (el.data.width || 0);
        break;
      case 'top':
        newPosition.y = referenceValue;
        break;
      case 'middle':
        newPosition.y = referenceValue - (el.data.height || 0) / 2;
        break;
      case 'bottom':
        newPosition.y = referenceValue - (el.data.height || 0);
        break;
    }

    return { ...el, position: newPosition };
  });
}

/**
 * Calculate reference value from element bounds
 */
function calculateReferenceFromBounds<
  T extends { position: Point; data: { width?: number; height?: number } },
>(elements: T[], alignmentType: AlignmentOptions['type']): number {
  const bounds = {
    minX: Math.min(...elements.map((el) => el.position.x)),
    maxX: Math.max(
      ...elements.map((el) => el.position.x + (el.data.width || 0))
    ),
    minY: Math.min(...elements.map((el) => el.position.y)),
    maxY: Math.max(
      ...elements.map((el) => el.position.y + (el.data.height || 0))
    ),
  };

  switch (alignmentType) {
    case 'left':
      return bounds.minX;
    case 'center':
      return (bounds.minX + bounds.maxX) / 2;
    case 'right':
      return bounds.maxX;
    case 'top':
      return bounds.minY;
    case 'middle':
      return (bounds.minY + bounds.maxY) / 2;
    case 'bottom':
      return bounds.maxY;
  }
}

/**
 * Check if point is near grid line
 */
export function isNearGridLine(
  value: number,
  gridSize: number,
  tolerance: number
): boolean {
  const nearestGrid = snapToGrid(value, gridSize);
  return Math.abs(value - nearestGrid) <= tolerance;
}

/**
 * Get snap suggestions for a point
 */
export function getSnapSuggestions(
  point: Point,
  gridConfig: GridConfig
): { x: number | null; y: number | null } {
  if (!gridConfig.enabled) {
    return { x: null, y: null };
  }

  const tolerance = gridConfig.tolerance ?? gridConfig.size / 2;
  const gridX = snapToGrid(point.x, gridConfig.size);
  const gridY = snapToGrid(point.y, gridConfig.size);

  return {
    x: Math.abs(point.x - gridX) <= tolerance ? gridX : null,
    y: Math.abs(point.y - gridY) <= tolerance ? gridY : null,
  };
}

// ============================================================================
// Feature 2.4 Enhancements: Smart Alignment Guides
// ============================================================================

/**
 *
 */
export interface Bounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 *
 */
export interface AlignmentGuide {
  /** Type of alignment */
  type: 'vertical' | 'horizontal';
  /** Position of the guide line */
  position: number;
  /** IDs of elements this guide aligns */
  elementIds: string[];
  /** Alignment point (left, center, right, top, middle, bottom) */
  alignmentPoint: 'left' | 'center' | 'right' | 'top' | 'middle' | 'bottom';
}

/**
 *
 */
export interface SmartSnapResult {
  /** Snapped position */
  position: Point;
  /** Active alignment guides */
  guides: AlignmentGuide[];
  /** Whether snapping occurred */
  snapped: boolean;
}

/**
 * Get smart alignment guides for a dragging element
 *
 * Returns dynamic snap lines based on alignment with other elements
 */
export function getSmartAlignmentGuides(
  draggingElement: { id: string; bounds: Bounds },
  otherElements: Array<{ id: string; bounds: Bounds }>,
  tolerance: number = 5
): AlignmentGuide[] {
  const guides: AlignmentGuide[] = [];
  const { bounds: dragBounds } = draggingElement;

  // Calculate dragging element key points
  const dragPoints = {
    left: dragBounds.x,
    center: dragBounds.x + dragBounds.width / 2,
    right: dragBounds.x + dragBounds.width,
    top: dragBounds.y,
    middle: dragBounds.y + dragBounds.height / 2,
    bottom: dragBounds.y + dragBounds.height,
  };

  // Check alignment with each other element
  for (const other of otherElements) {
    const otherPoints = {
      left: other.bounds.x,
      center: other.bounds.x + other.bounds.width / 2,
      right: other.bounds.x + other.bounds.width,
      top: other.bounds.y,
      middle: other.bounds.y + other.bounds.height / 2,
      bottom: other.bounds.y + other.bounds.height,
    };

    // Check vertical alignment (left, center, right)
    for (const point of ['left', 'center', 'right'] as const) {
      if (Math.abs(dragPoints[point] - otherPoints[point]) <= tolerance) {
        guides.push({
          type: 'vertical',
          position: otherPoints[point],
          elementIds: [draggingElement.id, other.id],
          alignmentPoint: point,
        });
      }
    }

    // Check horizontal alignment (top, middle, bottom)
    for (const point of ['top', 'middle', 'bottom'] as const) {
      if (Math.abs(dragPoints[point] - otherPoints[point]) <= tolerance) {
        guides.push({
          type: 'horizontal',
          position: otherPoints[point],
          elementIds: [draggingElement.id, other.id],
          alignmentPoint: point,
        });
      }
    }
  }

  return guides;
}

/**
 * Snap to smart alignment guides
 *
 * Snaps dragging element to alignment guides and returns adjusted position
 */
export function snapToAlignmentGuides(
  draggingElement: { id: string; bounds: Bounds },
  otherElements: Array<{ id: string; bounds: Bounds }>,
  tolerance: number = 5
): SmartSnapResult {
  const guides = getSmartAlignmentGuides(
    draggingElement,
    otherElements,
    tolerance
  );

  if (guides.length === 0) {
    return {
      position: { x: draggingElement.bounds.x, y: draggingElement.bounds.y },
      guides: [],
      snapped: false,
    };
  }

  let newX = draggingElement.bounds.x;
  let newY = draggingElement.bounds.y;
  let snapped = false;

  // Find closest vertical guide
  const verticalGuides = guides.filter((g) => g.type === 'vertical');
  if (verticalGuides.length > 0) {
    const closestVertical = verticalGuides[0];
    const { bounds } = draggingElement;

    switch (closestVertical.alignmentPoint) {
      case 'left':
        newX = closestVertical.position;
        break;
      case 'center':
        newX = closestVertical.position - bounds.width / 2;
        break;
      case 'right':
        newX = closestVertical.position - bounds.width;
        break;
    }
    snapped = true;
  }

  // Find closest horizontal guide
  const horizontalGuides = guides.filter((g) => g.type === 'horizontal');
  if (horizontalGuides.length > 0) {
    const closestHorizontal = horizontalGuides[0];
    const { bounds } = draggingElement;

    switch (closestHorizontal.alignmentPoint) {
      case 'top':
        newY = closestHorizontal.position;
        break;
      case 'middle':
        newY = closestHorizontal.position - bounds.height / 2;
        break;
      case 'bottom':
        newY = closestHorizontal.position - bounds.height;
        break;
    }
    snapped = true;
  }

  return {
    position: { x: newX, y: newY },
    guides,
    snapped,
  };
}

/**
 * Calculate element spacing distribution
 *
 * Returns info about spacing between elements for even distribution
 */
export function calculateSpacingDistribution(
  elements: Array<{ bounds: Bounds }>,
  direction: 'horizontal' | 'vertical'
): {
  totalSpan: number;
  totalElementSize: number;
  avgSpacing: number;
  spacings: number[];
} {
  if (elements.length < 2) {
    return {
      totalSpan: 0,
      totalElementSize: 0,
      avgSpacing: 0,
      spacings: [],
    };
  }

  // Sort elements by position
  const sorted = [...elements].sort((a, b) => {
    return direction === 'horizontal'
      ? a.bounds.x - b.bounds.x
      : a.bounds.y - b.bounds.y;
  });

  // Calculate spacings between elements
  const spacings: number[] = [];
  for (let i = 0; i < sorted.length - 1; i++) {
    const current = sorted[i];
    const next = sorted[i + 1];

    if (direction === 'horizontal') {
      const gap =
        next.bounds.x - (current.bounds.x + current.bounds.width);
      spacings.push(gap);
    } else {
      const gap =
        next.bounds.y - (current.bounds.y + current.bounds.height);
      spacings.push(gap);
    }
  }

  // Calculate total span and element size
  const first = sorted[0];
  const last = sorted[sorted.length - 1];

  const totalSpan =
    direction === 'horizontal'
      ? last.bounds.x + last.bounds.width - first.bounds.x
      : last.bounds.y + last.bounds.height - first.bounds.y;

  const totalElementSize = sorted.reduce((sum, el) => {
    return (
      sum + (direction === 'horizontal' ? el.bounds.width : el.bounds.height)
    );
  }, 0);

  const avgSpacing =
    spacings.reduce((sum, s) => sum + s, 0) / spacings.length;

  return {
    totalSpan,
    totalElementSize,
    avgSpacing,
    spacings,
  };
}

/**
 * Align elements to selection bounds
 *
 * Aligns elements within their collective bounding box
 */
export function alignToSelectionBounds<
  T extends { position: Point; data: { width?: number; height?: number } },
>(elements: T[], alignmentType: AlignmentOptions['type']): T[] {
  if (elements.length === 0) {
    return elements;
  }

  // Calculate selection bounds
  const bounds = {
    minX: Math.min(...elements.map((el) => el.position.x)),
    maxX: Math.max(
      ...elements.map((el) => el.position.x + (el.data.width || 0))
    ),
    minY: Math.min(...elements.map((el) => el.position.y)),
    maxY: Math.max(
      ...elements.map((el) => el.position.y + (el.data.height || 0))
    ),
    width: 0,
    height: 0,
  };

  bounds.width = bounds.maxX - bounds.minX;
  bounds.height = bounds.maxY - bounds.minY;

  // Calculate reference point from bounds
  let referenceValue: number;

  switch (alignmentType) {
    case 'left':
      referenceValue = bounds.minX;
      break;
    case 'center':
      referenceValue = bounds.minX + bounds.width / 2;
      break;
    case 'right':
      referenceValue = bounds.maxX;
      break;
    case 'top':
      referenceValue = bounds.minY;
      break;
    case 'middle':
      referenceValue = bounds.minY + bounds.height / 2;
      break;
    case 'bottom':
      referenceValue = bounds.maxY;
      break;
  }

  // Align elements to reference
  return alignElements(elements, {
    type: alignmentType,
    reference:
      alignmentType === 'left' ||
      alignmentType === 'center' ||
      alignmentType === 'right'
        ? { x: referenceValue, y: 0 }
        : { x: 0, y: referenceValue },
  });
}

/**
 * Get enhanced grid rendering info
 *
 * Returns grid lines with major/minor distinction for better visual hierarchy
 */
export function getEnhancedGridLines(
  viewport: { x: number; y: number; width: number; height: number },
  gridSize: number,
  majorGridMultiplier: number = 5
): {
  major: { horizontal: number[]; vertical: number[] };
  minor: { horizontal: number[]; vertical: number[] };
} {
  const allLines = getGridLines(viewport, gridSize);

  const major = {
    horizontal: allLines.horizontal.filter(
      (y) => y % (gridSize * majorGridMultiplier) === 0
    ),
    vertical: allLines.vertical.filter(
      (x) => x % (gridSize * majorGridMultiplier) === 0
    ),
  };

  const minor = {
    horizontal: allLines.horizontal.filter(
      (y) => y % (gridSize * majorGridMultiplier) !== 0
    ),
    vertical: allLines.vertical.filter(
      (x) => x % (gridSize * majorGridMultiplier) !== 0
    ),
  };

  return { major, minor };
}
