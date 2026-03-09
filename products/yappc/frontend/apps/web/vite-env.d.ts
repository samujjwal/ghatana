/// <reference types="vite/client" />
/// <reference types="@testing-library/jest-dom" />

// Add type definitions for Vitest
declare const vi: {
  fn: (implementation?: (...args: unknown[]) => any) => any;
  spyOn: (object: unknown, method: string) => any;
  clearAllMocks: () => void;
  mock: (path: string, factory?: () => any) => void;
  mockReset: () => void;
  mockImplementation: (fn: (...args: unknown[]) => any) => void;
  mockReturnValue: (value: unknown) => void;
  mockResolvedValue: (value: unknown) => void;
  mockRejectedValue: (value: unknown) => void;
};

// Add type definitions for styled-components
declare module 'styled-components' {
  /**
   *
   */
  export interface DefaultTheme {
    colors: {
      primary: string;
      secondary: string;
      background: string;
      text: string;
    };
  }
}

// Add type definitions for MSW
/**
 *
 */
interface Window {
  msw: {
    worker: {
      start: (options?: { onUnhandledRequest: string }) => void;
    };
  };
}

// Add type definitions for ResizeObserver
/**
 *
 */
declare class ResizeObserver {
  /**
   *
   */
  constructor(callback: ResizeObserverCallback);
  /**
   *
   */
  observe(target: Element): void;
  /**
   *
   */
  unobserve(target: Element): void;
  /**
   *
   */
  disconnect(): void;
}

/**
 *
 */
interface ResizeObserverCallback {
  (entries: ResizeObserverEntry[], observer: ResizeObserver): void;
}

/**
 *
 */
interface ResizeObserverEntry {
  readonly target: Element;
  readonly contentRect: DOMRectReadOnly;
  readonly borderBoxSize?: ResizeObserverSize;
  readonly contentBoxSize?: ResizeObserverSize;
  readonly devicePixelContentBoxSize?: ResizeObserverSize;
}

/**
 *
 */
interface ResizeObserverSize {
  readonly inlineSize: number;
  readonly blockSize: number;
}

// Add type definitions for IntersectionObserver
/**
 *
 */
declare class IntersectionObserver {
  /**
   *
   */
  constructor(callback: IntersectionObserverCallback, options?: IntersectionObserverInit);
  readonly root: Element | null;
  readonly rootMargin: string;
  readonly thresholds: ReadonlyArray<number>;
  /**
   *
   */
  disconnect(): void;
  /**
   *
   */
  observe(target: Element): void;
  /**
   *
   */
  takeRecords(): IntersectionObserverEntry[];
  /**
   *
   */
  unobserve(target: Element): void;
}

/**
 *
 */
type IntersectionObserverCallback = (
  entries: IntersectionObserverEntry[],
  observer: IntersectionObserver
) => void;

/**
 *
 */
interface IntersectionObserverEntry {
  readonly boundingClientRect: DOMRectReadOnly;
  readonly intersectionRatio: number;
  readonly intersectionRect: DOMRectReadOnly;
  readonly isIntersecting: boolean;
  readonly rootBounds: DOMRectReadOnly | null;
  readonly target: Element;
  readonly time: number;
}

/**
 *
 */
interface IntersectionObserverInit {
  root?: Element | null;
  rootMargin?: string;
  threshold?: number | number[];
}

// Add type definitions for testing utilities
declare namespace jest {
  /**
   *
   */
  interface Mock<T = unknown> {
    (...args: unknown[]): unknown;
    mockImplementation: (fn: (...args: unknown[]) => any) => Mock;
    mockReturnValue: (value: unknown) => Mock;
    mockResolvedValue: (value: unknown) => Mock;
    mockRejectedValue: (value: unknown) => Mock;
    mockClear: () => void;
    mockReset: () => void;
    mockRestore: () => void;
    mock: {
      calls: unknown[][];
      instances: unknown[];
      results: Array<{ type: 'return' | 'throw'; value: any }>;
    };
  }
}

// Add type definitions for Vitest matchers
declare namespace Vi {
  /**
   *
   */
  interface JestAssertion<T = unknown> {
    toBeInTheDocument(): void;
    toHaveClass(...classNames: string[]): void;
    toHaveAttribute(attr: string, value?: unknown): void;
    toHaveTextContent(text: string | RegExp, options?: { normalizeWhitespace: boolean }): void;
    toBeVisible(): void;
    toBeDisabled(): void;
    toBeEnabled(): void;
    toBeEmptyDOMElement(): void;
    toContainElement(element: HTMLElement | null): void;
    toHaveFocus(): void;
    toHaveFormValues(expectedValues: Record<string, unknown>): void;
    toHaveStyle(css: string | Record<string, string>): void;
    toHaveValue(value: unknown): void;
    toBeChecked(): void;
    toBePartiallyChecked(): void;
    toHaveDisplayValue(value: string | string[] | RegExp): void;
    toBeRequired(): void;
    toBeInvalid(): void;
    toBeValid(): void;
    toHaveErrorMessage(text: string | RegExp): void;
  }
}
