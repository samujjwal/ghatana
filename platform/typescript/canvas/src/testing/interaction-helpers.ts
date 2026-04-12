/**
 * @fileoverview Canvas interaction testing helpers.
 *
 * Provides typed simulation helpers for pointer, keyboard, and viewport
 * interactions in canvas unit and integration tests.
 *
 * @doc.type utilities
 * @doc.purpose Canvas interaction simulation for testing
 * @doc.layer testing
 */

// ============================================================================
// Pointer event simulation
// ============================================================================

export interface PointerPosition {
  readonly x: number;
  readonly y: number;
}

export interface SimulatedPointerEvent {
  readonly type: 'pointerdown' | 'pointermove' | 'pointerup' | 'click' | 'dblclick';
  readonly position: PointerPosition;
  readonly button: 0 | 1 | 2;
  readonly shiftKey: boolean;
  readonly ctrlKey: boolean;
  readonly metaKey: boolean;
  readonly altKey: boolean;
}

/** Build a typed pointer event descriptor. */
export function makePointerEvent(
  type: SimulatedPointerEvent['type'],
  position: PointerPosition,
  modifiers: Partial<Pick<SimulatedPointerEvent, 'button' | 'shiftKey' | 'ctrlKey' | 'metaKey' | 'altKey'>> = {},
): SimulatedPointerEvent {
  return {
    type,
    position,
    button: modifiers.button ?? 0,
    shiftKey: modifiers.shiftKey ?? false,
    ctrlKey: modifiers.ctrlKey ?? false,
    metaKey: modifiers.metaKey ?? false,
    altKey: modifiers.altKey ?? false,
  };
}

/** Dispatch a synthetic pointer event on an HTML element. */
export function dispatchPointerEvent(
  target: HTMLElement,
  type: SimulatedPointerEvent['type'],
  position: PointerPosition,
  modifiers: Partial<Pick<SimulatedPointerEvent, 'button' | 'shiftKey' | 'ctrlKey' | 'metaKey' | 'altKey'>> = {},
): void {
  const init: PointerEventInit = {
    bubbles: true,
    cancelable: true,
    clientX: position.x,
    clientY: position.y,
    button: modifiers.button ?? 0,
    shiftKey: modifiers.shiftKey ?? false,
    ctrlKey: modifiers.ctrlKey ?? false,
    metaKey: modifiers.metaKey ?? false,
    altKey: modifiers.altKey ?? false,
  };
  target.dispatchEvent(new PointerEvent(type, init));
}

/** Simulate a click on a canvas element. */
export function simulateClick(
  target: HTMLElement,
  position: PointerPosition = { x: 0, y: 0 },
  modifiers: Partial<Pick<SimulatedPointerEvent, 'shiftKey' | 'ctrlKey' | 'metaKey'>> = {},
): void {
  dispatchPointerEvent(target, 'pointerdown', position, modifiers);
  dispatchPointerEvent(target, 'pointerup', position, modifiers);
  dispatchPointerEvent(target, 'click', position, modifiers);
}

/** Simulate a drag gesture (pointerdown → n pointermoves → pointerup). */
export function simulateDrag(
  target: HTMLElement,
  from: PointerPosition,
  to: PointerPosition,
  steps = 5,
): void {
  dispatchPointerEvent(target, 'pointerdown', from);
  for (let i = 1; i <= steps; i++) {
    const progress = i / steps;
    dispatchPointerEvent(target, 'pointermove', {
      x: from.x + (to.x - from.x) * progress,
      y: from.y + (to.y - from.y) * progress,
    });
  }
  dispatchPointerEvent(target, 'pointerup', to);
}

// ============================================================================
// Keyboard event simulation
// ============================================================================

export interface SimulatedKeyEvent {
  readonly key: string;
  readonly code: string;
  readonly shiftKey: boolean;
  readonly ctrlKey: boolean;
  readonly metaKey: boolean;
  readonly altKey: boolean;
}

/** Dispatch a synthetic keyboard event. */
export function dispatchKeyEvent(
  target: HTMLElement | Window,
  type: 'keydown' | 'keyup' | 'keypress',
  key: string,
  modifiers: Partial<Pick<SimulatedKeyEvent, 'shiftKey' | 'ctrlKey' | 'metaKey' | 'altKey'>> = {},
): void {
  const init: KeyboardEventInit = {
    bubbles: true,
    cancelable: true,
    key,
    code: `Key${key.toUpperCase()}`,
    shiftKey: modifiers.shiftKey ?? false,
    ctrlKey: modifiers.ctrlKey ?? false,
    metaKey: modifiers.metaKey ?? false,
    altKey: modifiers.altKey ?? false,
  };
  const element = target instanceof HTMLElement ? target : (target as unknown as HTMLElement);
  element.dispatchEvent(new KeyboardEvent(type, init));
}

/** Simulate pressing a key (keydown + keyup). */
export function simulateKeyPress(
  target: HTMLElement | Window,
  key: string,
  modifiers: Partial<Pick<SimulatedKeyEvent, 'shiftKey' | 'ctrlKey' | 'metaKey' | 'altKey'>> = {},
): void {
  dispatchKeyEvent(target, 'keydown', key, modifiers);
  dispatchKeyEvent(target, 'keyup', key, modifiers);
}

// ============================================================================
// Viewport interaction helpers
// ============================================================================

export interface ViewportState {
  readonly x: number;
  readonly y: number;
  readonly zoom: number;
}

/** Create a default viewport state for tests. */
export function makeTestViewport(overrides: Partial<ViewportState> = {}): ViewportState {
  return {
    x: 0,
    y: 0,
    zoom: 1,
    ...overrides,
  };
}

/** Assert two viewport states are equal within a numeric tolerance. */
export function assertViewportEqual(
  actual: ViewportState,
  expected: ViewportState,
  tolerance = 0.001,
): void {
  const dx = Math.abs(actual.x - expected.x);
  const dy = Math.abs(actual.y - expected.y);
  const dz = Math.abs(actual.zoom - expected.zoom);
  if (dx > tolerance || dy > tolerance || dz > tolerance) {
    throw new Error(
      `ViewportState mismatch:\n  actual:   ${JSON.stringify(actual)}\n  expected: ${JSON.stringify(expected)}\n  tolerance: ${tolerance}`,
    );
  }
}

// ============================================================================
// Canvas render assertion helpers
// ============================================================================

/**
 * Assert that an element matching the given role and label is present in the
 * rendered output. Works without @testing-library/jest-dom matchers.
 */
export function assertRenderedRole(container: HTMLElement, role: string, label?: string): void {
  const elements = container.querySelectorAll(`[role="${role}"]`);
  if (elements.length === 0) {
    throw new Error(`Expected at least one element with role="${role}" but found none.`);
  }
  if (label !== undefined) {
    const matching = Array.from(elements).filter(
      (el) =>
        el.getAttribute('aria-label') === label ||
        el.textContent?.trim() === label,
    );
    if (matching.length === 0) {
      throw new Error(
        `Expected role="${role}" element with label="${label}" but found none among:\n  ${Array.from(elements)
          .map((el) => el.getAttribute('aria-label') ?? el.textContent?.trim() ?? '(no label)')
          .join('\n  ')}`,
      );
    }
  }
}

/**
 * Assert that no element with the given role is present in the container.
 */
export function assertNotRenderedRole(container: HTMLElement, role: string): void {
  const elements = container.querySelectorAll(`[role="${role}"]`);
  if (elements.length > 0) {
    throw new Error(
      `Expected no element with role="${role}" but found ${elements.length}.`,
    );
  }
}

/**
 * Assert that an element has a particular CSS class.
 */
export function assertHasClass(element: Element, className: string): void {
  if (!element.classList.contains(className)) {
    throw new Error(
      `Expected element to have class "${className}" but classList is: ${element.className}`,
    );
  }
}

/**
 * Assert that a given aria-* attribute has the expected value.
 */
export function assertAriaAttribute(
  element: Element,
  attribute: string,
  expected: string,
): void {
  const actual = element.getAttribute(attribute);
  if (actual !== expected) {
    throw new Error(
      `Expected ${attribute}="${expected}" but got ${attribute}="${actual ?? '(missing)'}".`,
    );
  }
}
