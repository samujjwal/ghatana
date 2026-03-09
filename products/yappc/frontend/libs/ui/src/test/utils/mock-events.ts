/**
 * Mock event utilities for testing
 * @module test/utils/mock-events
 */

import { fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

/**
 * Simulates a form submission
 *
 * @param formElement - Form element to submit
 *
 * @example
 * ```typescript
 * const form = getByTestId('login-form');
 * submitForm(form);
 * ```
 */
export function submitForm(formElement: HTMLFormElement) {
  fireEvent.submit(formElement);
}

/**
 * Simulates user typing into an input element
 *
 * @param input - Input element to type into
 * @param text - Text to type
 *
 * @example
 * ```typescript
 * await typeIntoInput(emailInput, 'user@example.com');
 * ```
 */
export async function typeIntoInput(input: HTMLElement, text: string) {
  await userEvent.type(input, text);
}

/**
 * Simulates user clicking an element
 *
 * @param element - Element to click
 *
 * @example
 * ```typescript
 * await clickElement(submitButton);
 * ```
 */
export async function clickElement(element: HTMLElement) {
  await userEvent.click(element);
}

/**
 * Waits for a specified time in milliseconds
 *
 * @param ms - Milliseconds to wait
 * @returns Promise that resolves after the delay
 *
 * @example
 * ```typescript
 * await wait(500); // Wait 500ms
 * ```
 */
export function wait(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Creates a mock generic event with prevention methods
 *
 * @param overrides - Event property overrides
 * @returns Mock event object
 *
 * @example
 * ```typescript
 * const event = createMockEvent({ target: { value: 'test' } });
 * ```
 */
export function createMockEvent(overrides = {}) {
  return {
    preventDefault: vi.fn(),
    stopPropagation: vi.fn(),
    target: { value: '' },
    currentTarget: { value: '' },
    ...overrides,
  };
}

/**
 * Creates a mock form event with form data
 *
 * @param overrides - Event property overrides
 * @returns Mock form event with form elements
 *
 * @example
 * ```typescript
 * const event = createMockFormEvent({ target: { elements: {...} } });
 * ```
 */
export function createMockFormEvent(overrides = {}) {
  return {
    preventDefault: vi.fn(),
    stopPropagation: vi.fn(),
    target: {
      elements: {
        email: { value: 'test@example.com' },
        password: { value: 'password123' },
      },
    },
    ...overrides,
  };
}

/**
 * Creates a mock keyboard event
 *
 * @param key - Key value (e.g., 'Enter', 'Escape')
 * @param overrides - Event property overrides
 * @returns Mock keyboard event
 *
 * @example
 * ```typescript
 * const event = createMockKeyboardEvent('Enter');
 * element.dispatchEvent(event);
 * ```
 */
export function createMockKeyboardEvent(key: string, overrides = {}) {
  return {
    key,
    preventDefault: vi.fn(),
    stopPropagation: vi.fn(),
    ...overrides,
  };
}

/**
 * Creates a mock mouse event
 *
 * @param overrides - Event property overrides
 * @returns Mock mouse event with coordinates
 *
 * @example
 * ```typescript
 * const event = createMockMouseEvent({ clientX: 100, clientY: 200 });
 * ```
 */
export function createMockMouseEvent(overrides = {}) {
  return {
    preventDefault: vi.fn(),
    stopPropagation: vi.fn(),
    clientX: 0,
    clientY: 0,
    ...overrides,
  };
}

/**
 * Creates a mock touch event
 *
 * @param overrides - Event property overrides
 * @returns Mock touch event with touch points
 *
 * @example
 * ```typescript
 * const event = createMockTouchEvent({ touches: [{clientX: 0, clientY: 0}] });
 * ```
 */
export function createMockTouchEvent(overrides = {}) {
  return {
    preventDefault: vi.fn(),
    stopPropagation: vi.fn(),
    touches: [{ clientX: 0, clientY: 0 }],
    ...overrides,
  };
}
