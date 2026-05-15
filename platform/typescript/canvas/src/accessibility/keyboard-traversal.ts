/**
 * @fileoverview Keyboard Traversal Model for Canvas Accessibility
 *
 * Provides keyboard navigation patterns for canvas elements, including
 * arrow key navigation, tab order management, and focus path tracking.
 *
 * @doc.type module
 * @doc.purpose Keyboard traversal and navigation for canvas
 * @doc.layer platform
 */

import type { FocusPath, FocusPathSegment } from "../core/semantic-zoom.js";

// ============================================================================
// TRAVERSAL DIRECTION
// ============================================================================

/** Direction of keyboard traversal. */
export type TraversalDirection =
  | "up"
  | "down"
  | "left"
  | "right"
  | "forward"
  | "backward"
  | "home"
  | "end";

/** Navigation mode for traversal. */
export type NavigationMode =
  | "geographic" // Navigate by spatial position
  | "hierarchical" // Navigate by parent-child relationships
  | "sequential" // Navigate by creation/tab order
  | "semantic"; // Navigate by semantic meaning (e.g., flowchart flow)

// ============================================================================
// TRAVERSABLE ELEMENT
// ============================================================================

/** An element that can be traversed via keyboard. */
export interface TraversableElement {
  readonly id: string;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
  readonly tabIndex?: number;
  readonly parentId?: string;
  readonly children?: readonly string[];
  readonly semanticRole?: string;
  readonly semanticFlow?: {
    readonly incoming: readonly string[];
    readonly outgoing: readonly string[];
  };
  readonly disabled?: boolean;
  readonly hidden?: boolean;
  readonly metadata?: Record<string, unknown>;
}

/** Registry of traversable elements. */
export class TraversableRegistry {
  private elements: Map<string, TraversableElement> = new Map();
  private rootIds: Set<string> = new Set();
  private focusOrder: string[] = [];

  /**
   * Register a traversable element.
   */
  register(element: TraversableElement): void {
    if (this.elements.has(element.id)) {
      throw new Error(`Element with ID ${element.id} already registered`);
    }

    this.elements.set(element.id, element);

    // Add to root if no parent
    if (!element.parentId) {
      this.rootIds.add(element.id);
    }

    // Update focus order
    this.updateFocusOrder();
  }

  /**
   * Unregister an element.
   */
  unregister(id: string): void {
    this.elements.delete(id);
    this.rootIds.delete(id);
    this.focusOrder = this.focusOrder.filter((fid) => fid !== id);
  }

  /**
   * Get an element by ID.
   */
  get(id: string): TraversableElement | undefined {
    return this.elements.get(id);
  }

  /**
   * Get all registered elements.
   */
  getAll(): readonly TraversableElement[] {
    return Array.from(this.elements.values());
  }

  /**
   * Get root elements (no parent).
   */
  getRoots(): readonly TraversableElement[] {
    return Array.from(this.rootIds)
      .map((id) => this.elements.get(id))
      .filter((e): e is TraversableElement => e !== undefined);
  }

  /**
   * Get children of an element.
   */
  getChildren(parentId: string): readonly TraversableElement[] {
    const parent = this.elements.get(parentId);
    if (!parent?.children) return [];

    return parent.children
      .map((id) => this.elements.get(id))
      .filter((e): e is TraversableElement => e !== undefined);
  }

  /**
   * Update the focus order based on tab indices.
   */
  private updateFocusOrder(): void {
    const sorted = Array.from(this.elements.values())
      .filter((e) => !e.disabled && !e.hidden)
      .sort((a, b) => (a.tabIndex ?? Infinity) - (b.tabIndex ?? Infinity));

    this.focusOrder = sorted.map((e) => e.id);
  }

  /**
   * Get the focus order array.
   */
  getFocusOrder(): readonly string[] {
    return this.focusOrder;
  }

  /**
   * Clear all elements.
   */
  clear(): void {
    this.elements.clear();
    this.rootIds.clear();
    this.focusOrder = [];
  }
}

// ============================================================================
// TRAVERSAL ENGINE
// ============================================================================

/** Result of a traversal operation. */
export interface TraversalResult {
  readonly success: boolean;
  readonly targetId: string | null;
  readonly direction: TraversalDirection;
  readonly mode: NavigationMode;
}

/** Engine for computing keyboard traversal. */
export class TraversalEngine {
  private registry: TraversableRegistry;

  constructor(registry: TraversableRegistry) {
    this.registry = registry;
  }

  /**
   * Traverse from a starting element in a direction.
   */
  traverse(
    fromId: string | null,
    direction: TraversalDirection,
    mode: NavigationMode = "geographic",
  ): TraversalResult {
    if (!fromId) {
      // Start from first available element
      const first = this.registry.getFocusOrder()[0];
      return {
        success: first !== undefined,
        targetId: first ?? null,
        direction,
        mode,
      };
    }

    const fromElement = this.registry.get(fromId);
    if (!fromElement) {
      return { success: false, targetId: null, direction, mode };
    }

    switch (mode) {
      case "geographic":
        return this.geographicTraverse(fromElement, direction);
      case "hierarchical":
        return this.hierarchicalTraverse(fromElement, direction);
      case "sequential":
        return this.sequentialTraverse(fromElement, direction);
      case "semantic":
        return this.semanticTraverse(fromElement, direction);
      default:
        return { success: false, targetId: null, direction, mode };
    }
  }

  /**
   * Geographic traversal based on spatial position.
   */
  private geographicTraverse(
    from: TraversableElement,
    direction: TraversalDirection,
  ): TraversalResult {
    const candidates = this.registry
      .getAll()
      .filter((e) => e.id !== from.id && !e.disabled && !e.hidden);

    let target: TraversableElement | null = null;

    switch (direction) {
      case "left":
        target = this.findNearestLeft(from, candidates);
        break;
      case "right":
        target = this.findNearestRight(from, candidates);
        break;
      case "up":
        target = this.findNearestAbove(from, candidates);
        break;
      case "down":
        target = this.findNearestBelow(from, candidates);
        break;
      case "home":
        target = this.findLeftmost(candidates);
        break;
      case "end":
        target = this.findRightmost(candidates);
        break;
    }

    return {
      success: target !== null,
      targetId: target?.id ?? null,
      direction,
      mode: "geographic",
    };
  }

  private findNearestLeft(
    from: TraversableElement,
    candidates: readonly TraversableElement[],
  ): TraversableElement | null {
    const leftOf = candidates.filter((e) => e.x + e.width < from.x);
    if (leftOf.length === 0) return null;

    return leftOf.reduce((closest, current) => {
      const closestDist = this.geographicDistance(from, closest, "left");
      const currentDist = this.geographicDistance(from, current, "left");
      return currentDist < closestDist ? current : closest;
    });
  }

  private findNearestRight(
    from: TraversableElement,
    candidates: readonly TraversableElement[],
  ): TraversableElement | null {
    const rightOf = candidates.filter((e) => e.x > from.x + from.width);
    if (rightOf.length === 0) return null;

    return rightOf.reduce((closest, current) => {
      const closestDist = this.geographicDistance(from, closest, "right");
      const currentDist = this.geographicDistance(from, current, "right");
      return currentDist < closestDist ? current : closest;
    });
  }

  private findNearestAbove(
    from: TraversableElement,
    candidates: readonly TraversableElement[],
  ): TraversableElement | null {
    const above = candidates.filter((e) => e.y + e.height < from.y);
    if (above.length === 0) return null;

    return above.reduce((closest, current) => {
      const closestDist = this.geographicDistance(from, closest, "up");
      const currentDist = this.geographicDistance(from, current, "up");
      return currentDist < closestDist ? current : closest;
    });
  }

  private findNearestBelow(
    from: TraversableElement,
    candidates: readonly TraversableElement[],
  ): TraversableElement | null {
    const below = candidates.filter((e) => e.y > from.y + from.height);
    if (below.length === 0) return null;

    return below.reduce((closest, current) => {
      const closestDist = this.geographicDistance(from, closest, "down");
      const currentDist = this.geographicDistance(from, current, "down");
      return currentDist < closestDist ? current : closest;
    });
  }

  private findLeftmost(
    candidates: readonly TraversableElement[],
  ): TraversableElement | null {
    if (candidates.length === 0) return null;
    return candidates.reduce((leftmost, current) =>
      current.x < leftmost.x ? current : leftmost,
    );
  }

  private findRightmost(
    candidates: readonly TraversableElement[],
  ): TraversableElement | null {
    if (candidates.length === 0) return null;
    return candidates.reduce((rightmost, current) =>
      current.x > rightmost.x ? current : rightmost,
    );
  }

  private geographicDistance(
    from: TraversableElement,
    to: TraversableElement,
    _direction: TraversalDirection,
  ): number {
    const dx = to.x + to.width / 2 - (from.x + from.width / 2);
    const dy = to.y + to.height / 2 - (from.y + from.height / 2);
    return Math.sqrt(dx * dx + dy * dy);
  }

  /**
   * Hierarchical traversal based on parent-child relationships.
   */
  private hierarchicalTraverse(
    from: TraversableElement,
    direction: TraversalDirection,
  ): TraversalResult {
    let targetId: string | null = null;

    switch (direction) {
      case "forward":
      case "right":
      case "down": {
        // Try children first, then next sibling
        const children = this.registry.getChildren(from.id);
        if (children.length > 0) {
          targetId = children[0].id;
        } else {
          const siblings = from.parentId
            ? this.registry.getChildren(from.parentId)
            : this.registry.getRoots();
          const index = siblings.findIndex((s) => s.id === from.id);
          if (index >= 0 && index < siblings.length - 1) {
            targetId = siblings[index + 1].id;
          }
        }
        break;
      }
      case "backward":
      case "left":
      case "up": {
        // Try previous sibling, then parent
        const siblings = from.parentId
          ? this.registry.getChildren(from.parentId)
          : this.registry.getRoots();
        const index = siblings.findIndex((s) => s.id === from.id);
        if (index > 0) {
          // Get previous sibling and dive to its deepest descendant
          const prevSibling = siblings[index - 1];
          targetId = this.getDeepestDescendant(prevSibling)?.id ?? null;
          if (!targetId) {
            targetId = prevSibling.id;
          }
        } else if (from.parentId) {
          targetId = from.parentId;
        }
        break;
      }
      case "home":
        targetId = this.registry.getRoots()[0]?.id ?? null;
        break;
      case "end":
        targetId = this.getDeepestDescendant(this.registry.getRoots().slice(-1)[0])?.id ?? null;
        break;
    }

    return {
      success: targetId !== null,
      targetId,
      direction,
      mode: "hierarchical",
    };
  }

  private getDeepestDescendant(
    element: TraversableElement | undefined,
  ): TraversableElement | undefined {
    if (!element) return undefined;

    const children = this.registry.getChildren(element.id);
    if (children.length === 0) return element;

    return this.getDeepestDescendant(children[children.length - 1]);
  }

  /**
   * Sequential traversal based on tab order.
   */
  private sequentialTraverse(
    from: TraversableElement,
    direction: TraversalDirection,
  ): TraversalResult {
    const order = this.registry.getFocusOrder();
    const index = order.indexOf(from.id);

    let targetId: string | null = null;

    switch (direction) {
      case "forward":
      case "right":
      case "down":
        targetId = order[index + 1] ?? null;
        break;
      case "backward":
      case "left":
      case "up":
        targetId = order[index - 1] ?? null;
        break;
      case "home":
        targetId = order[0] ?? null;
        break;
      case "end":
        targetId = order[order.length - 1] ?? null;
        break;
    }

    return {
      success: targetId !== null,
      targetId,
      direction,
      mode: "sequential",
    };
  }

  /**
   * Semantic traversal based on flowchart or semantic flow.
   */
  private semanticTraverse(
    from: TraversableElement,
    direction: TraversalDirection,
  ): TraversalResult {
    const flow = from.semanticFlow;
    if (!flow) {
      // Fall back to geographic
      return this.geographicTraverse(from, direction);
    }

    let targetId: string | null = null;

    switch (direction) {
      case "forward":
      case "right":
      case "down":
        targetId = flow.outgoing[0] ?? null;
        break;
      case "backward":
      case "left":
      case "up":
        targetId = flow.incoming[0] ?? null;
        break;
    }

    return {
      success: targetId !== null,
      targetId,
      direction,
      mode: "semantic",
    };
  }
}

// ============================================================================
// ARIA LABEL GENERATION
// ============================================================================

/** Generator for screen reader labels. */
export class AriaLabelGenerator {
  /**
   * Generate an accessible label for a canvas element.
   */
  generateLabel(
    element: TraversableElement,
    context?: {
      readonly index?: number;
      readonly total?: number;
      readonly role?: string;
    },
  ): string {
    const parts: string[] = [];

    // Add role
    if (context?.role) {
      parts.push(context.role);
    } else if (element.semanticRole) {
      parts.push(element.semanticRole);
    }

    // Add position
    if (context?.total !== undefined && context?.index !== undefined) {
      parts.push(`${context.index + 1} of ${context.total}`);
    }

    // Add coordinates for spatial awareness
    parts.push(`at position ${Math.round(element.x)}, ${Math.round(element.y)}`);

    return parts.join(", ");
  }

  /**
   * Generate a description for a traversal action.
   */
  generateTraversalDescription(
    fromElement: TraversableElement,
    toElement: TraversableElement,
    direction: TraversalDirection,
  ): string {
    const directionText = this.directionToText(direction);
    return `Navigated ${directionText} from ${fromElement.semanticRole || "element"} to ${toElement.semanticRole || "element"}`;
  }

  private directionToText(direction: TraversalDirection): string {
    const mapping: Record<TraversalDirection, string> = {
      up: "up",
      down: "down",
      left: "left",
      right: "right",
      forward: "forward",
      backward: "backward",
      home: "to beginning",
      end: "to end",
    };
    return mapping[direction] || direction;
  }
}

// ============================================================================
// FOCUS VISIBLE STATE
// ============================================================================

/** State for focus visibility tracking. */
export interface FocusVisibleState {
  readonly currentId: string | null;
  readonly previousId: string | null;
  readonly focusOrigin: "keyboard" | "mouse" | "programmatic";
  readonly timestamp: number;
}

/** Manager for focus visible state. */
export class FocusVisibleManager {
  private state: FocusVisibleState = {
    currentId: null,
    previousId: null,
    focusOrigin: "programmatic",
    timestamp: Date.now(),
  };

  private listeners: Set<(state: FocusVisibleState) => void> = new Set();

  /**
   * Set focus to an element.
   */
  setFocus(
    id: string | null,
    origin: "keyboard" | "mouse" | "programmatic" = "programmatic",
  ): void {
    this.state = {
      currentId: id,
      previousId: this.state.currentId,
      focusOrigin: origin,
      timestamp: Date.now(),
    };
    this.notifyListeners();
  }

  /**
   * Get current focus state.
   */
  getState(): FocusVisibleState {
    return this.state;
  }

  /**
   * Check if focus is visible (keyboard origin).
   */
  isFocusVisible(): boolean {
    return this.state.focusOrigin === "keyboard" && this.state.currentId !== null;
  }

  /**
   * Subscribe to focus changes.
   */
  subscribe(listener: (state: FocusVisibleState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notifyListeners(): void {
    this.listeners.forEach((listener) => listener(this.state));
  }
}

// ============================================================================
// NON-COLOR STATUS SIGNALS
// ============================================================================

/** Status types that need non-color indicators. */
export type NonColorStatus =
  | "info"
  | "success"
  | "warning"
  | "error"
  | "neutral"
  | "selected"
  | "focused"
  | "disabled";

/** Pattern for non-color status indication. */
export type NonColorPattern =
  | "solid"
  | "dashed"
  | "dotted"
  | "striped"
  | "checkered"
  | "hatched"
  | "outlined"
  | "filled";

/** Non-color status signal configuration. */
export interface NonColorStatusSignal {
  readonly status: NonColorStatus;
  readonly pattern: NonColorPattern;
  readonly icon?: string;
  readonly label?: string;
  readonly borderStyle?: "solid" | "dashed" | "dotted" | "double";
  readonly borderWidth?: number;
}

/** Registry of non-color status signals. */
export const NON_COLOR_STATUS_SIGNALS: ReadonlyMap<NonColorStatus, NonColorStatusSignal> =
  new Map([
    [
      "info",
      {
        status: "info",
        pattern: "solid",
        icon: "ⓘ",
        label: "Information",
        borderStyle: "solid",
        borderWidth: 1,
      },
    ],
    [
      "success",
      {
        status: "success",
        pattern: "solid",
        icon: "✓",
        label: "Success",
        borderStyle: "solid",
        borderWidth: 2,
      },
    ],
    [
      "warning",
      {
        status: "warning",
        pattern: "striped",
        icon: "⚠",
        label: "Warning",
        borderStyle: "dashed",
        borderWidth: 2,
      },
    ],
    [
      "error",
      {
        status: "error",
        pattern: "hatched",
        icon: "✕",
        label: "Error",
        borderStyle: "dotted",
        borderWidth: 3,
      },
    ],
    [
      "neutral",
      {
        status: "neutral",
        pattern: "outlined",
        borderStyle: "solid",
        borderWidth: 1,
      },
    ],
    [
      "selected",
      {
        status: "selected",
        pattern: "solid",
        icon: "◉",
        label: "Selected",
        borderStyle: "double",
        borderWidth: 2,
      },
    ],
    [
      "focused",
      {
        status: "focused",
        pattern: "solid",
        label: "Focused",
        borderStyle: "solid",
        borderWidth: 3,
      },
    ],
    [
      "disabled",
      {
        status: "disabled",
        pattern: "dotted",
        icon: "⊘",
        label: "Disabled",
        borderStyle: "dotted",
        borderWidth: 1,
      },
    ],
  ]);

/**
 * Get non-color status signal for a status.
 */
export function getNonColorStatusSignal(status: NonColorStatus): NonColorStatusSignal {
  return (
    NON_COLOR_STATUS_SIGNALS.get(status) || {
      status: "neutral",
      pattern: "outlined",
      borderStyle: "solid",
      borderWidth: 1,
    }
  );
}

/**
 * Apply non-color status styles to an element.
 */
export function applyNonColorStatus(
  element: HTMLElement,
  status: NonColorStatus,
): void {
  const signal = getNonColorStatusSignal(status);
  element.setAttribute("data-status", status);
  element.setAttribute("data-pattern", signal.pattern);
  if (signal.icon) {
    element.setAttribute("data-status-icon", signal.icon);
  }
  if (signal.label) {
    element.setAttribute("aria-label", signal.label);
  }
  element.style.borderStyle = signal.borderStyle || "solid";
  element.style.borderWidth = `${signal.borderWidth || 1}px`;
}

// ============================================================================
// FOCUS PATH STABILITY
// ============================================================================

/**
 * Ensure focus path stability during canvas mutations.
 * Reconciles focus path when elements are added/removed/moved.
 */
export function reconcileFocusPath(
  path: FocusPath,
  validIds: Set<string>,
): FocusPath {
  const validSegments = path.segments.filter((s) => validIds.has(s.id));
  return {
    segments: validSegments,
    depth: validSegments.length,
  };
}

/**
 * Check if a focus path is valid given current element state.
 */
export function isFocusPathValid(
  path: FocusPath,
  validIds: Set<string>,
): boolean {
  return path.segments.every((s) => validIds.has(s.id));
}
