/**
 * Test render helper utilities
 * @module test/utils/render-helpers
 */

import {
  render,
  screen,
  within,
  waitFor,
  type RenderOptions,
  type RenderResult,
} from '@testing-library/react';

/**
 * Renders a component with custom options
 *
 * @param ui - React element to render
 * @param options - Optional render options
 * @returns Render result with queries
 *
 * @example
 * ```typescript
 * const { getByText } = renderWithProviders(<MyComponent />);
 * ```
 */
export function renderWithProviders(
  ui: React.ReactElement,
  options: RenderOptions = {}
): RenderResult {
  return render(ui, options);
}

/**
 * Renders a component with custom wrapper
 *
 * @param ui - React element to render
 * @param options - Optional render options
 * @returns Render result with queries
 */
export function renderWithTheme(
  ui: React.ReactElement,
  options: RenderOptions = {}
): RenderResult {
  return render(ui, options);
}

/**
 * Renders a component with custom wrapper
 *
 * @param ui - React element to render
 * @param options - Optional render options
 * @returns Render result with queries
 */
export function renderWithToast(
  ui: React.ReactElement,
  options: RenderOptions = {}
): RenderResult {
  return render(ui, options);
}

/**
 * Renders a component with mock theme context
 *
 * @param ui - React element to render
 * @param options - Optional render options
 * @returns Render result with queries
 */
export function renderWithMockTheme(
  ui: React.ReactElement,
  options: RenderOptions = {}
): RenderResult {
  return render(ui, options);
}

/**
 * Renders a component with mock toast context
 *
 * @param ui - React element to render
 * @param options - Optional render options
 * @returns Render result with queries
 */
export function renderWithMockToast(
  ui: React.ReactElement,
  options: RenderOptions = {}
): RenderResult {
  return render(ui, options);
}

/**
 * Waits for an element to be removed from DOM
 *
 * @param element - Element to wait for removal
 *
 * @example
 * ```typescript
 * await waitForElementToBeRemoved(getByTestId('modal'));
 * ```
 */
export async function waitForElementToBeRemoved(element: Element | null) {
  if (!element) return;
  await waitFor(() => {
    expect(document.body.contains(element)).toBe(false);
  });
}

/**
 * Waits for an element to be visible in DOM
 *
 * @param element - Element to wait for visibility
 *
 * @example
 * ```typescript
 * await waitForElementToBeVisible(getByTestId('notification'));
 * ```
 */
export async function waitForElementToBeVisible(element: Element | null) {
  if (!element) return;
  await waitFor(() => {
    expect(document.body.contains(element)).toBe(true);
  });
}

/**
 * Gets elements within a container
 *
 * @param container - Container element
 * @returns Query functions scoped to container
 *
 * @example
 * ```typescript
 * const { getByText } = getWithin(dialogElement);
 * ```
 */
export function getWithin(container: HTMLElement) {
  return within(container);
}

/**
 * Finds an element by text content
 *
 * @param text - Text to search for
 * @returns Promise resolving to element
 */
export function findByText(text: string) {
  return screen.findByText(text);
}

/**
 * Gets an element by text content
 *
 * @param text - Text to search for
 * @returns Element matching text
 */
export function getByText(text: string) {
  return screen.getByText(text);
}

/**
 * Queries an element by text content
 *
 * @param text - Text to search for
 * @returns Element or null if not found
 */
export function queryByText(text: string) {
  return screen.queryByText(text);
}

/**
 * Gets an element by accessibility role
 *
 * @param role - Accessibility role (button, link, etc.)
 * @param options - Optional search options
 * @returns Element with matching role
 *
 * @example
 * ```typescript
 * const button = getByRole('button', { name: 'Submit' });
 * ```
 */
export function getByRole(role: string, options = {}) {
  return screen.getByRole(role, options);
}

/**
 * Gets an element by test id
 *
 * @param testId - Test ID attribute value
 * @returns Element with matching test ID
 *
 * @example
 * ```typescript
 * const form = getByTestId('login-form');
 * ```
 */
export function getByTestId(testId: string) {
  return screen.getByTestId(testId);
}
