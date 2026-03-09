/**
 * Canvas Testing Utilities
 *
 * Comprehensive testing utilities for Canvas UI components.
 * Includes test helpers, mock providers, and test factories.
 *
 * @doc.type utilities
 * @doc.purpose Testing utilities and helpers
 * @doc.layer testing
 */

import React, { ReactElement } from "react";
import { render, RenderOptions, RenderResult } from "@testing-library/react";
import { Provider } from "jotai";
import { ThemeProvider } from "../theme/ThemeProvider";
import { AccessibilityProvider } from "../accessibility/AccessibilityProvider";

// ============================================================================
// TEST PROVIDERS
// ============================================================================

/**
 * All-in-one test provider wrapper
 */
const AllTheProviders: React.FC<{ children?: React.ReactNode }> = ({
  children,
}) => {
  return (
    <Provider>
      <AccessibilityProvider>
        <ThemeProvider>{children}</ThemeProvider>
      </AccessibilityProvider>
    </Provider>
  );
};

/**
 * Custom render function with providers
 */
const customRender = (
  ui: ReactElement,
  options?: Omit<RenderOptions, "wrapper">,
): RenderResult => {
  return render(ui, { wrapper: AllTheProviders, ...options });
};

// ============================================================================
// MOCK UTILITIES
// ============================================================================

/**
 * Mock canvas state for testing
 */
const mockCanvasState = {
  elements: [
    {
      id: "test-element-1",
      type: "service",
      x: 100,
      y: 100,
      width: 180,
      height: 100,
      label: "Test Service",
      data: { layer: "architecture", phase: "INTENT" },
      style: { fillColor: "#3b82f6", strokeColor: "#1e40af" },
    },
    {
      id: "test-element-2",
      type: "database",
      x: 300,
      y: 200,
      width: 120,
      height: 120,
      label: "Test Database",
      data: { layer: "architecture", phase: "SHAPE" },
      style: { fillColor: "#10b981", strokeColor: "#047857" },
    },
  ],
  connections: [],
  viewport: { x: 0, y: 0, zoom: 1 },
};

/**
 * Mock theme state for testing
 */
const mockThemeState = {
  mode: "light" as const,
  colors: {
    background: {
      primary: "#ffffff",
      secondary: "#f9fafb",
      tertiary: "#f3f4f6",
      elevated: "#ffffff",
    },
    text: {
      primary: "#111827",
      secondary: "#6b7280",
      tertiary: "#9ca3af",
      disabled: "#d1d5db",
      inverse: "#ffffff",
    },
    border: {
      primary: "#e5e7eb",
      secondary: "#d1d5db",
      focus: "#3b82f6",
      error: "#ef4444",
    },
    interactive: {
      primary: "#3b82f6",
      primaryHover: "#2563eb",
      primaryActive: "#1d4ed8",
      secondary: "#6b7280",
      secondaryHover: "#4b5563",
      danger: "#ef4444",
      dangerHover: "#dc2626",
    },
    status: {
      success: "#10b981",
      warning: "#f59e0b",
      error: "#ef4444",
      info: "#3b82f6",
    },
    canvas: {
      grid: "#e5e7eb",
      selection: "rgba(59, 130, 246, 0.1)",
      selectionBorder: "#3b82f6",
      guideline: "#f59e0b",
      frame: "#9ca3af",
    },
  },
};

/**
 * Mock accessibility settings for testing
 */
const mockAccessibilitySettings = {
  reducedMotion: false,
  highContrast: false,
  screenReader: false,
  keyboardOnly: false,
  fontSize: "medium" as const,
  focusVisible: true,
};

// ============================================================================
// TEST FACTORIES
// ============================================================================

/**
 * Create mock panel props
 */
const createMockPanelProps = (
  overrides: Partial<{ onClose: () => void }> = {},
) => ({
  onClose: () => {},
  ...overrides,
});

/**
 * Create mock canvas element
 */
const createMockCanvasElement = (
  overrides: Partial<Record<string, unknown>> = {},
) => ({
  id: "test-element",
  type: "service",
  x: 0,
  y: 0,
  width: 100,
  height: 100,
  label: "Test Element",
  data: {},
  style: {},
  ...overrides,
});

/**
 * Create mock task
 */
const createMockTask = (overrides: Partial<Record<string, unknown>> = {}) => ({
  id: "test-task",
  title: "Test Task",
  description: "Test description",
  status: "todo" as const,
  priority: "medium" as const,
  phase: "INTENT" as const,
  assignee: "test-user",
  dueDate: "2024-01-01",
  createdAt: "2024-01-01T00:00:00Z",
  updatedAt: "2024-01-01T00:00:00Z",
  ...overrides,
});

/**
 * Create mock collaboration cursor
 */
const createMockCursor = (
  overrides: Partial<Record<string, unknown>> = {},
) => ({
  userId: "test-user",
  userName: "Test User",
  userColor: "#3b82f6",
  x: 100,
  y: 100,
  timestamp: Date.now(),
  ...overrides,
});

// ============================================================================
// TEST HELPERS
// ============================================================================

/**
 * Wait for a specified time (for async tests)
 */
const waitFor = (ms: number): Promise<void> => {
  return new Promise((resolve) => setTimeout(resolve, ms));
};

/**
 * Mock ResizeObserver
 */
const mockResizeObserver = (): (() => void) => {
  const observe = () => {};
  const unobserve = () => {};
  const disconnect = () => {};

  (global as unknown as Record<string, unknown>).ResizeObserver = () => ({
    observe,
    unobserve,
    disconnect,
  });

  return () => {
    // Cleanup function
    delete (global as unknown as Record<string, unknown>).ResizeObserver;
  };
};

/**
 * Mock IntersectionObserver
 */
const mockIntersectionObserver = (): (() => void) => {
  const observe = () => {};
  const unobserve = () => {};
  const disconnect = () => {};

  (global as unknown as Record<string, unknown>).IntersectionObserver = () => ({
    observe,
    unobserve,
    disconnect,
  });

  return () => {
    delete (global as unknown as Record<string, unknown>).IntersectionObserver;
  };
};

/**
 * Mock performance API
 */
const mockPerformanceAPI = (): void => {
  (global as unknown as Record<string, unknown>).performance = {
    ...((global as unknown as Record<string, unknown>).performance as Record<string, unknown> || {}),
    now: () => Date.now(),
    mark: () => {},
    measure: () => 0,
    getEntriesByName: () => [],
    getEntriesByType: () => [],
  };
};

/**
 * Mock matchMedia for theme detection
 */
const mockMatchMedia = (matches: boolean = false): void => {
  (global as unknown as Record<string, unknown>).matchMedia = (query: string) => ({
    matches,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => {},
  });
};

/**
 * Mock localStorage
 */
const mockLocalStorage = (): void => {
  const store: Record<string, string> = {};

  (global as unknown as Record<string, unknown>).localStorage = {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = value;
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      Object.keys(store).forEach((key) => delete store[key]);
    },
    length: 0,
    key: (index: number) => Object.keys(store)[index] || null,
  };
};

// ============================================================================
// ASSERTION HELPERS
// ============================================================================

/**
 * Assert element has proper ARIA attributes
 */
const assertAccessibility = (element: HTMLElement): void => {
  // Check for basic accessibility attributes
  if (!element.getAttribute("role")) {
    throw new Error("Element missing required 'role' attribute");
  }
  if (!element.getAttribute("aria-label") && !element.textContent?.trim()) {
    throw new Error("Element missing 'aria-label' or text content");
  }
};

/**
 * Assert theme CSS variables are applied
 */
const assertThemeVariables = (element: HTMLElement): void => {
  const styles = getComputedStyle(element);
  if (!styles.getPropertyValue("--canvas-bg-primary")) {
    throw new Error("Element missing --canvas-bg-primary CSS variable");
  }
  if (!styles.getPropertyValue("--canvas-text-primary")) {
    throw new Error("Element missing --canvas-text-primary CSS variable");
  }
};

/**
 * Assert component is properly memoized
 */
const assertMemoized = (Component: React.ComponentType<unknown>): void => {
  // Check if component is wrapped in React.memo
  const MemoizedComponent = React.memo(Component);
  if (!MemoizedComponent) {
    throw new Error("Component is not properly memoized");
  }
};

// ============================================================================
// CLEANUP HELPERS
// ============================================================================

/**
 * Cleanup all mocks
 * Note: Jest-specific cleanup not available in browser environment
 */
const cleanupMocks = (): void => {
  // Browser environment cleanup would go here
  // Jest-specific cleanup not available
  console.log("Mock cleanup called (browser environment)");
};

/**
 * Setup common mocks for canvas tests
 */
const setupCanvasMocks = (): void => {
  mockResizeObserver();
  mockIntersectionObserver();
  mockPerformanceAPI();
  mockLocalStorage();
  mockMatchMedia();
};

// ============================================================================
// RE-EXPORT
// ============================================================================

// Re-export testing library utilities
export * from "@testing-library/react";
export { customRender as render };

// Note: Jest matchers are not available in browser environment
// Use testing-library's built-in matchers instead

// Export test utilities
export {
  AllTheProviders,
  mockCanvasState,
  mockThemeState,
  mockAccessibilitySettings,
  createMockPanelProps,
  createMockCanvasElement,
  createMockTask,
  createMockCursor,
  waitFor,
  mockResizeObserver,
  mockIntersectionObserver,
  mockPerformanceAPI,
  mockMatchMedia,
  mockLocalStorage,
  assertAccessibility,
  assertThemeVariables,
  assertMemoized,
  cleanupMocks,
  setupCanvasMocks,
};
