import { render, type RenderOptions } from '@testing-library/react';
import { vi } from 'vitest';

import { ToastProvider } from '../components/Toast';
import { ThemeProvider } from '../theme/ThemeContext';

import type { ReactElement } from 'react';


/**
 * Test utilities for component testing
 */

/**
 *
 */
interface AllTheProvidersProps {
  children: React.ReactNode;
}

/**
 * Wrapper with all providers for testing
 */
function AllTheProviders({ children }: AllTheProvidersProps) {
  return (
    <ThemeProvider>
      <ToastProvider>
        {children}
      </ToastProvider>
    </ThemeProvider>
  );
}

/**
 * Custom render function with providers
 */
export function renderWithProviders(
  ui: ReactElement,
  options?: Omit<RenderOptions, 'wrapper'>
): ReturnType<typeof render> {
  return render(ui, { wrapper: AllTheProviders, ...options });
}

/**
 * Mock data factories
 */
export const mockUser = (overrides = {}) => ({
  id: '1',
  email: 'test@example.com',
  name: 'Test User',
  avatar: 'https://via.placeholder.com/150',
  role: 'user',
  permissions: ['read'],
  ...overrides,
});

export const mockEvent = (overrides = {}) => ({
  preventDefault: vi.fn(),
  stopPropagation: vi.fn(),
  target: { value: '' },
  currentTarget: { value: '' },
  ...overrides,
});

/**
 * Wait utilities
 */
export const waitFor = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

/**
 * Mock intersection observer
 */
export const mockIntersectionObserver = () => {
  global.IntersectionObserver = class IntersectionObserver {
    /**
     *
     */
    constructor() { }
    /**
     *
     */
    disconnect() { }
    /**
     *
     */
    observe() { }
    /**
     *
     */
    takeRecords() {
      return [];
    }
    /**
     *
     */
    unobserve() { }
  } as unknown;
};

/**
 * Mock resize observer
 */
export const mockResizeObserver = () => {
  global.ResizeObserver = class ResizeObserver {
    /**
     *
     */
    constructor() { }
    /**
     *
     */
    disconnect() { }
    /**
     *
     */
    observe() { }
    /**
     *
     */
    unobserve() { }
  } as unknown;
};

/**
 * Mock matchMedia
 */
export const mockMatchMedia = (matches = false) => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
};

/**
 * Mock localStorage
 */
export const mockLocalStorage = () => {
  const store: Record<string, string> = {};

  const localStorageMock = {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      Object.keys(store).forEach(key => delete store[key]);
    }),
  };

  Object.defineProperty(window, 'localStorage', {
    value: localStorageMock,
  });

  return localStorageMock;
};

/**
 * Create mock file
 */
export const createMockFile = (name = 'test.txt', size = 1024, type = 'text/plain') => {
  const file = new File(['test content'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
};

/**
 * Accessibility test helpers
 */
export const axeTest = async (container: HTMLElement) => {
  const axe = (await import('axe-core')).default as unknown;
  const results = await axe.run(container);
  return results.violations;
};

// Re-export everything from testing library
export * from '@testing-library/react';
export { default as userEvent } from '@testing-library/user-event';

// Re-export authentication test utilities
export * from './auth-helpers';
export { default as authTestHelpers } from './auth-helpers';
