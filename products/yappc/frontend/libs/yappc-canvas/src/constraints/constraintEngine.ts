/**
 * Feature 2.8: Grouping & Constraints
 * 
 * Implements a flexible constraint system for canvas elements including:
 * - Container constraints (children must stay within parent bounds)
 * - Sticky notes (elements attached to anchors)
 * - Swimlane constraints (elements constrained to lanes)
 * - Size constraints (min/max width/height)
 * - Position constraints (grid snapping, alignment)
 * - Custom constraint rules
 * 
 * @module constraints/constraintEngine
 */

/**
 * Represents a 2D point
 */
export interface Point {
  x: number;
  y: number;
}

/**
 * Represents a rectangle with position and dimensions
 */
export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * Types of constraints supported
 */
export enum ConstraintType {
  CONTAINER = 'container',
  STICKY = 'sticky',
  SWIMLANE = 'swimlane',
  SIZE = 'size',
  POSITION = 'position',
  CUSTOM = 'custom',
}

/**
 * Severity levels for constraint violations
 */
export enum ViolationSeverity {
  ERROR = 'error',
  WARNING = 'warning',
  INFO = 'info',
}

/**
 * Represents an element on the canvas
 */
export interface CanvasElement {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  parentId?: string;
  locked?: boolean;
  [key: string]: unknown;
}

/**
 * Base constraint interface
 */
export interface Constraint {
  id: string;
  type: ConstraintType;
  elementId: string;
  enabled: boolean;
  priority: number; // Higher priority constraints are enforced first
  description?: string;
}

/**
 * Container constraint - keeps children within parent bounds
 */
export interface ContainerConstraint extends Constraint {
  type: ConstraintType.CONTAINER;
  containerId: string;
  padding?: number; // Minimum distance from container edges
  allowPartialOverlap?: boolean; // Allow children to partially exceed bounds
}

/**
 * Sticky constraint - keeps element attached to anchor point
 */
export interface StickyConstraint extends Constraint {
  type: ConstraintType.STICKY;
  anchorElementId: string;
  offset: Point; // Offset from anchor element
  maintainRelativePosition?: boolean; // Keep relative position when anchor moves
}

/**
 * Swimlane constraint - constrains element to a lane
 */
export interface SwimlaneConstraint extends Constraint {
  type: ConstraintType.SWIMLANE;
  laneId: string;
  allowCrossLane?: boolean; // Allow dragging to adjacent lanes
}

/**
 * Size constraint - enforces min/max dimensions
 */
export interface SizeConstraint extends Constraint {
  type: ConstraintType.SIZE;
  minWidth?: number;
  maxWidth?: number;
  minHeight?: number;
  maxHeight?: number;
  aspectRatio?: number; // Maintain aspect ratio (width/height)
}

/**
 * Position constraint - enforces position rules
 */
export interface PositionConstraint extends Constraint {
  type: ConstraintType.POSITION;
  gridSize?: number; // Snap to grid
  alignTo?: string; // Align to another element
  bounds?: Rect; // Constrain to specific bounds
}

/**
 * Custom constraint with validation function
 */
export interface CustomConstraint extends Constraint {
  type: ConstraintType.CUSTOM;
  validate: (element: CanvasElement, elements: Map<string, CanvasElement>) => boolean;
  fix?: (element: CanvasElement, elements: Map<string, CanvasElement>) => CanvasElement;
  message?: string;
}

/**
 * Union type of all constraint types
 */
export type AnyConstraint =
  | ContainerConstraint
  | StickyConstraint
  | SwimlaneConstraint
  | SizeConstraint
  | PositionConstraint
  | CustomConstraint;

/**
 * Represents a constraint violation
 */
export interface ConstraintViolation {
  constraintId: string;
  elementId: string;
  type: ConstraintType;
  severity: ViolationSeverity;
  message: string;
  suggestedFix?: Partial<CanvasElement>;
}

/**
 * State for the constraint engine
 */
export interface ConstraintEngineState {
  constraints: Map<string, AnyConstraint>;
  elements: Map<string, CanvasElement>;
  violations: ConstraintViolation[];
  autoFix: boolean;
  validateOnMove: boolean;
}

/**
 * Result of applying constraints
 */
export interface ConstraintResult {
  element: CanvasElement;
  violations: ConstraintViolation[];
  fixed: boolean;
}

/**
 * Create a new constraint engine state
 */
export function createConstraintEngine(
  options: {
    autoFix?: boolean;
    validateOnMove?: boolean;
  } = {}
): ConstraintEngineState {
  return {
    constraints: new Map(),
    elements: new Map(),
    violations: [],
    autoFix: options.autoFix ?? true,
    validateOnMove: options.validateOnMove ?? true,
  };
}

/**
 * Add a constraint to the engine
 */
export function addConstraint(
  state: ConstraintEngineState,
  constraint: AnyConstraint
): ConstraintEngineState {
  state.constraints.set(constraint.id, constraint);
  return state;
}

/**
 * Remove a constraint from the engine
 */
export function removeConstraint(
  state: ConstraintEngineState,
  constraintId: string
): ConstraintEngineState {
  state.constraints.delete(constraintId);
  // Remove violations for this constraint
  state.violations = state.violations.filter((v) => v.constraintId !== constraintId);
  return state;
}

/**
 * Update a constraint
 */
export function updateConstraint(
  state: ConstraintEngineState,
  constraintId: string,
  updates: Partial<Omit<AnyConstraint, 'type'>>
): ConstraintEngineState {
  const constraint = state.constraints.get(constraintId);
  if (constraint) {
    state.constraints.set(constraintId, { ...constraint, ...updates } as AnyConstraint);
  }
  return state;
}

/**
 * Get constraints for a specific element
 */
export function getConstraintsForElement(
  state: ConstraintEngineState,
  elementId: string
): AnyConstraint[] {
  return Array.from(state.constraints.values())
    .filter((c) => c.elementId === elementId && c.enabled)
    .sort((a, b) => b.priority - a.priority); // Higher priority first
}

/**
 * Add or update an element in the engine
 */
export function setElement(
  state: ConstraintEngineState,
  element: CanvasElement
): ConstraintEngineState {
  state.elements.set(element.id, element);
  return state;
}

/**
 * Remove an element from the engine
 */
export function removeElement(
  state: ConstraintEngineState,
  elementId: string
): ConstraintEngineState {
  state.elements.delete(elementId);
  // Remove constraints for this element
  state.constraints.forEach((constraint, id) => {
    if (constraint.elementId === elementId) {
      state.constraints.delete(id);
    }
  });
  // Remove violations for this element
  state.violations = state.violations.filter((v) => v.elementId !== elementId);
  return state;
}

/**
 * Check if a point is within a rectangle
 */
export function isPointInRect(point: Point, rect: Rect): boolean {
  return (
    point.x >= rect.x &&
    point.x <= rect.x + rect.width &&
    point.y >= rect.y &&
    point.y <= rect.y + rect.height
  );
}

/**
 * Check if a rectangle is fully contained within another
 */
export function isRectContained(inner: Rect, outer: Rect, padding: number = 0): boolean {
  return (
    inner.x >= outer.x + padding &&
    inner.y >= outer.y + padding &&
    inner.x + inner.width <= outer.x + outer.width - padding &&
    inner.y + inner.height <= outer.y + outer.height - padding
  );
}

/**
 * Check if two rectangles overlap
 */
export function rectsOverlap(rect1: Rect, rect2: Rect): boolean {
  return !(
    rect1.x + rect1.width < rect2.x ||
    rect2.x + rect2.width < rect1.x ||
    rect1.y + rect1.height < rect2.y ||
    rect2.y + rect2.height < rect1.y
  );
}

/**
 * Clamp a rectangle within bounds
 */
export function clampRectToBounds(rect: Rect, bounds: Rect, padding: number = 0): Rect {
  const minX = bounds.x + padding;
  const minY = bounds.y + padding;
  const maxX = bounds.x + bounds.width - rect.width - padding;
  const maxY = bounds.y + bounds.height - rect.height - padding;

  return {
    x: Math.max(minX, Math.min(maxX, rect.x)),
    y: Math.max(minY, Math.min(maxY, rect.y)),
    width: rect.width,
    height: rect.height,
  };
}

/**
 * Validate a container constraint
 */
export function validateContainerConstraint(
  constraint: ContainerConstraint,
  element: CanvasElement,
  elements: Map<string, CanvasElement>
): ConstraintViolation | null {
  const container = elements.get(constraint.containerId);
  if (!container) {
    return {
      constraintId: constraint.id,
      elementId: element.id,
      type: ConstraintType.CONTAINER,
      severity: ViolationSeverity.ERROR,
      message: `Container element ${constraint.containerId} not found`,
    };
  }

  const elementRect: Rect = {
    x: element.x,
    y: element.y,
    width: element.width,
    height: element.height,
  };

  const containerRect: Rect = {
    x: container.x,
    y: container.y,
    width: container.width,
    height: container.height,
  };

  const padding = constraint.padding ?? 0;
  const isContained = isRectContained(elementRect, containerRect, padding);

  if (!isContained && !constraint.allowPartialOverlap) {
    const clampedRect = clampRectToBounds(elementRect, containerRect, padding);
    return {
      constraintId: constraint.id,
      elementId: element.id,
      type: ConstraintType.CONTAINER,
      severity: ViolationSeverity.ERROR,
      message: `Element exceeds container bounds`,
      suggestedFix: { x: clampedRect.x, y: clampedRect.y },
    };
  }

  if (!isContained && constraint.allowPartialOverlap) {
    // Check if at least partially overlapping
    if (!rectsOverlap(elementRect, containerRect)) {
      const clampedRect = clampRectToBounds(elementRect, containerRect, padding);
      return {
        constraintId: constraint.id,
        elementId: element.id,
        type: ConstraintType.CONTAINER,
        severity: ViolationSeverity.WARNING,
        message: `Element not overlapping container`,
        suggestedFix: { x: clampedRect.x, y: clampedRect.y },
      };
    }
  }

  return null;
}

/**
 * Validate a sticky constraint
 */
export function validateStickyConstraint(
  constraint: StickyConstraint,
  element: CanvasElement,
  elements: Map<string, CanvasElement>
): ConstraintViolation | null {
  const anchor = elements.get(constraint.anchorElementId);
  if (!anchor) {
    return {
      constraintId: constraint.id,
      elementId: element.id,
      type: ConstraintType.STICKY,
      severity: ViolationSeverity.ERROR,
      message: `Anchor element ${constraint.anchorElementId} not found`,
    };
  }

  const expectedX = anchor.x + constraint.offset.x;
  const expectedY = anchor.y + constraint.offset.y;

  const tolerance = 1; // Allow 1px tolerance for floating point errors
  const isInPosition =
    Math.abs(element.x - expectedX) <= tolerance &&
    Math.abs(element.y - expectedY) <= tolerance;

  if (!isInPosition) {
    return {
      constraintId: constraint.id,
      elementId: element.id,
      type: ConstraintType.STICKY,
      severity: ViolationSeverity.WARNING,
      message: `Element not attached to anchor`,
      suggestedFix: { x: expectedX, y: expectedY },
    };
  }

  return null;
}

/**
 * Validate a size constraint
 */
export function validateSizeConstraint(
  constraint: SizeConstraint,
  element: CanvasElement
): ConstraintViolation | null {
  const violations: string[] = [];
  const fix: Partial<CanvasElement> = {};

  if (constraint.minWidth !== undefined && element.width < constraint.minWidth) {
    violations.push(`width < ${constraint.minWidth}`);
    fix.width = constraint.minWidth;
  }

  if (constraint.maxWidth !== undefined && element.width > constraint.maxWidth) {
    violations.push(`width > ${constraint.maxWidth}`);
    fix.width = constraint.maxWidth;
  }

  if (constraint.minHeight !== undefined && element.height < constraint.minHeight) {
    violations.push(`height < ${constraint.minHeight}`);
    fix.height = constraint.minHeight;
  }

  if (constraint.maxHeight !== undefined && element.height > constraint.maxHeight) {
    violations.push(`height > ${constraint.maxHeight}`);
    fix.height = constraint.maxHeight;
  }

  if (constraint.aspectRatio !== undefined) {
    const currentRatio = element.width / element.height;
    const tolerance = 0.01;
    if (Math.abs(currentRatio - constraint.aspectRatio) > tolerance) {
      violations.push(`aspect ratio != ${constraint.aspectRatio}`);
      // Fix by adjusting height to maintain width
      fix.height = element.width / constraint.aspectRatio;
    }
  }

  if (violations.length > 0) {
    return {
      constraintId: constraint.id,
      elementId: element.id,
      type: ConstraintType.SIZE,
      severity: ViolationSeverity.ERROR,
      message: `Size constraint violated: ${violations.join(', ')}`,
      suggestedFix: fix,
    };
  }

  return null;
}

/**
 * Validate a position constraint
 */
export function validatePositionConstraint(
  constraint: PositionConstraint,
  element: CanvasElement,
  elements: Map<string, CanvasElement>
): ConstraintViolation | null {
  const fix: Partial<CanvasElement> = {};
  const violations: string[] = [];

  // Grid snapping
  if (constraint.gridSize !== undefined && constraint.gridSize > 0) {
    const snappedX = Math.round(element.x / constraint.gridSize) * constraint.gridSize;
    const snappedY = Math.round(element.y / constraint.gridSize) * constraint.gridSize;

    if (element.x !== snappedX || element.y !== snappedY) {
      violations.push('not snapped to grid');
      fix.x = snappedX;
      fix.y = snappedY;
    }
  }

  // Bounds constraint
  if (constraint.bounds) {
    const elementRect: Rect = {
      x: element.x,
      y: element.y,
      width: element.width,
      height: element.height,
    };

    if (!isRectContained(elementRect, constraint.bounds)) {
      violations.push('outside bounds');
      const clamped = clampRectToBounds(elementRect, constraint.bounds);
      fix.x = clamped.x;
      fix.y = clamped.y;
    }
  }

  // Alignment constraint
  if (constraint.alignTo) {
    const alignTarget = elements.get(constraint.alignTo);
    if (alignTarget && element.x !== alignTarget.x) {
      violations.push('not aligned');
      fix.x = alignTarget.x;
    }
  }

  if (violations.length > 0) {
    return {
      constraintId: constraint.id,
      elementId: element.id,
      type: ConstraintType.POSITION,
      severity: ViolationSeverity.WARNING,
      message: `Position constraint violated: ${violations.join(', ')}`,
      suggestedFix: fix,
    };
  }

  return null;
}

/**
 * Validate a custom constraint
 */
export function validateCustomConstraint(
  constraint: CustomConstraint,
  element: CanvasElement,
  elements: Map<string, CanvasElement>
): ConstraintViolation | null {
  const isValid = constraint.validate(element, elements);

  if (!isValid) {
    let suggestedFix: Partial<CanvasElement> | undefined;
    if (constraint.fix) {
      const fixed = constraint.fix(element, elements);
      suggestedFix = {
        x: fixed.x,
        y: fixed.y,
        width: fixed.width,
        height: fixed.height,
      };
    }

    return {
      constraintId: constraint.id,
      elementId: element.id,
      type: ConstraintType.CUSTOM,
      severity: ViolationSeverity.ERROR,
      message: constraint.message || 'Custom constraint violated',
      suggestedFix,
    };
  }

  return null;
}

/**
 * Validate a single constraint
 */
export function validateConstraint(
  constraint: AnyConstraint,
  element: CanvasElement,
  elements: Map<string, CanvasElement>
): ConstraintViolation | null {
  if (!constraint.enabled) {
    return null;
  }

  switch (constraint.type) {
    case ConstraintType.CONTAINER:
      return validateContainerConstraint(constraint, element, elements);
    case ConstraintType.STICKY:
      return validateStickyConstraint(constraint, element, elements);
    case ConstraintType.SIZE:
      return validateSizeConstraint(constraint, element);
    case ConstraintType.POSITION:
      return validatePositionConstraint(constraint, element, elements);
    case ConstraintType.CUSTOM:
      return validateCustomConstraint(constraint, element, elements);
    default:
      return null;
  }
}

/**
 * Validate all constraints for an element
 */
export function validateElement(
  state: ConstraintEngineState,
  elementId: string
): ConstraintViolation[] {
  const element = state.elements.get(elementId);
  if (!element) {
    return [];
  }

  const constraints = getConstraintsForElement(state, elementId);
  const violations: ConstraintViolation[] = [];

  for (const constraint of constraints) {
    const violation = validateConstraint(constraint, element, state.elements);
    if (violation) {
      violations.push(violation);
    }
  }

  return violations;
}

/**
 * Apply constraint fixes to an element
 */
export function applyConstraints(
  state: ConstraintEngineState,
  element: CanvasElement
): ConstraintResult {
  const constraints = getConstraintsForElement(state, element.id);
  let currentElement = { ...element };
  const violations: ConstraintViolation[] = [];
  let fixed = false;

  for (const constraint of constraints) {
    const violation = validateConstraint(constraint, currentElement, state.elements);

    if (violation) {
      violations.push(violation);

      if (state.autoFix && violation.suggestedFix) {
        currentElement = { ...currentElement, ...violation.suggestedFix };
        fixed = true;
      }
    }
  }

  return {
    element: currentElement,
    violations,
    fixed,
  };
}

/**
 * Validate all elements in the engine
 */
export function validateAll(state: ConstraintEngineState): ConstraintEngineState {
  state.violations = [];

  state.elements.forEach((element) => {
    const violations = validateElement(state, element.id);
    state.violations.push(...violations);
  });

  return state;
}

/**
 * Get violations for a specific element
 */
export function getViolationsForElement(
  state: ConstraintEngineState,
  elementId: string
): ConstraintViolation[] {
  return state.violations.filter((v) => v.elementId === elementId);
}

/**
 * Get violations by severity
 */
export function getViolationsBySeverity(
  state: ConstraintEngineState,
  severity: ViolationSeverity
): ConstraintViolation[] {
  return state.violations.filter((v) => v.severity === severity);
}

/**
 * Clear all violations
 */
export function clearViolations(state: ConstraintEngineState): ConstraintEngineState {
  state.violations = [];
  return state;
}

/**
 * Create a container constraint
 */
export function createContainerConstraint(
  id: string,
  elementId: string,
  containerId: string,
  options: {
    padding?: number;
    allowPartialOverlap?: boolean;
    priority?: number;
  } = {}
): ContainerConstraint {
  return {
    id,
    type: ConstraintType.CONTAINER,
    elementId,
    containerId,
    enabled: true,
    priority: options.priority ?? 100,
    padding: options.padding,
    allowPartialOverlap: options.allowPartialOverlap,
  };
}

/**
 * Create a sticky constraint
 */
export function createStickyConstraint(
  id: string,
  elementId: string,
  anchorElementId: string,
  offset: Point,
  options: {
    maintainRelativePosition?: boolean;
    priority?: number;
  } = {}
): StickyConstraint {
  return {
    id,
    type: ConstraintType.STICKY,
    elementId,
    anchorElementId,
    offset,
    enabled: true,
    priority: options.priority ?? 90,
    maintainRelativePosition: options.maintainRelativePosition,
  };
}

/**
 * Create a size constraint
 */
export function createSizeConstraint(
  id: string,
  elementId: string,
  options: {
    minWidth?: number;
    maxWidth?: number;
    minHeight?: number;
    maxHeight?: number;
    aspectRatio?: number;
    priority?: number;
  } = {}
): SizeConstraint {
  return {
    id,
    type: ConstraintType.SIZE,
    elementId,
    enabled: true,
    priority: options.priority ?? 80,
    minWidth: options.minWidth,
    maxWidth: options.maxWidth,
    minHeight: options.minHeight,
    maxHeight: options.maxHeight,
    aspectRatio: options.aspectRatio,
  };
}

/**
 * Create a position constraint
 */
export function createPositionConstraint(
  id: string,
  elementId: string,
  options: {
    gridSize?: number;
    alignTo?: string;
    bounds?: Rect;
    priority?: number;
  } = {}
): PositionConstraint {
  return {
    id,
    type: ConstraintType.POSITION,
    elementId,
    enabled: true,
    priority: options.priority ?? 70,
    gridSize: options.gridSize,
    alignTo: options.alignTo,
    bounds: options.bounds,
  };
}

/**
 * Update sticky constraint when anchor moves
 */
export function updateStickyForAnchorMove(
  state: ConstraintEngineState,
  anchorId: string,
  deltaX: number,
  deltaY: number
): ConstraintEngineState {
  // Find all sticky constraints pointing to this anchor
  const stickyConstraints = Array.from(state.constraints.values()).filter(
    (c): c is StickyConstraint =>
      c.type === ConstraintType.STICKY &&
      c.anchorElementId === anchorId &&
      c.enabled
  );

  // Update attached elements
  stickyConstraints.forEach((constraint) => {
    const element = state.elements.get(constraint.elementId);
    if (element && constraint.maintainRelativePosition) {
      element.x += deltaX;
      element.y += deltaY;
    }
  });

  return state;
}

/**
 * Get statistics about constraints
 */
export function getConstraintStatistics(state: ConstraintEngineState): {
  totalConstraints: number;
  enabledConstraints: number;
  constraintsByType: Record<ConstraintType, number>;
  totalViolations: number;
  violationsBySeverity: Record<ViolationSeverity, number>;
} {
  const constraintsByType: Record<ConstraintType, number> = {
    [ConstraintType.CONTAINER]: 0,
    [ConstraintType.STICKY]: 0,
    [ConstraintType.SWIMLANE]: 0,
    [ConstraintType.SIZE]: 0,
    [ConstraintType.POSITION]: 0,
    [ConstraintType.CUSTOM]: 0,
  };

  const violationsBySeverity: Record<ViolationSeverity, number> = {
    [ViolationSeverity.ERROR]: 0,
    [ViolationSeverity.WARNING]: 0,
    [ViolationSeverity.INFO]: 0,
  };

  let enabledCount = 0;

  state.constraints.forEach((constraint) => {
    constraintsByType[constraint.type]++;
    if (constraint.enabled) {
      enabledCount++;
    }
  });

  state.violations.forEach((violation) => {
    violationsBySeverity[violation.severity]++;
  });

  return {
    totalConstraints: state.constraints.size,
    enabledConstraints: enabledCount,
    constraintsByType,
    totalViolations: state.violations.length,
    violationsBySeverity,
  };
}
