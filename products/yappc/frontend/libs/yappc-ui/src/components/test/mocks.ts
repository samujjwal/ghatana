/**
 * Test Doubles (Mocks/Stubs)
 * 
 * This file provides mock implementations for common dependencies
 * used throughout the YAPPC UI library tests.
 */

import { vi } from 'vitest';

/**
 * Mock API client
 */
export const mockApiClient = {
  get: vi.fn().mockResolvedValue({ data: {} }),
  post: vi.fn().mockResolvedValue({ data: {} }),
  put: vi.fn().mockResolvedValue({ data: {} }),
  delete: vi.fn().mockResolvedValue({ data: {} }),
  patch: vi.fn().mockResolvedValue({ data: {} }),
};

/**
 * Mock GraphQL client
 */
export const mockGraphQLClient = {
  query: vi.fn().mockResolvedValue({ data: {} }),
  mutate: vi.fn().mockResolvedValue({ data: {} }),
};

/**
 * Mock theme context
 */
export const mockThemeContext = {
  mode: 'light',
  toggleTheme: vi.fn(),
  setTheme: vi.fn(),
};

/**
 * Mock toast context
 */
export const mockToastContext = {
  toasts: [],
  addToast: vi.fn(),
  removeToast: vi.fn(),
};

/**
 * Mock auth context
 */
export const mockAuthContext = {
  user: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
};

/**
 * Mock router
 */
export const mockRouter = {
  push: vi.fn(),
  replace: vi.fn(),
  back: vi.fn(),
  pathname: '/',
  query: {},
  asPath: '/',
  events: {
    on: vi.fn(),
    off: vi.fn(),
    emit: vi.fn(),
  },
};

/**
 * Mock window methods
 */
export const mockWindow = {
  open: vi.fn(),
  location: {
    assign: vi.fn(),
    replace: vi.fn(),
    reload: vi.fn(),
    href: 'http://localhost:3000',
    origin: 'http://localhost:3000',
    pathname: '/',
    search: '',
    hash: '',
  },
  scrollTo: vi.fn(),
  alert: vi.fn(),
  confirm: vi.fn(),
  prompt: vi.fn(),
};

/**
 * Mock event
 */
export const mockEvent = {
  preventDefault: vi.fn(),
  stopPropagation: vi.fn(),
  target: { value: '' },
  currentTarget: { value: '' },
};

/**
 * Mock form event
 */
export const mockFormEvent = {
  ...mockEvent,
  target: {
    elements: {
      email: { value: 'test@example.com' },
      password: { value: 'password123' },
    },
  },
};

/**
 * Mock file
 */
export const mockFile = new File(['test'], 'test.jpg', { type: 'image/jpeg' });

/**
 * Mock image file
 */
export const mockImageFile = new File(['test'], 'image.png', { type: 'image/png' });

/**
 * Mock document file
 */
export const mockDocumentFile = new File(['test'], 'document.pdf', { type: 'application/pdf' });

/**
 * Mock user data
 */
export const mockUser = {
  id: '1',
  name: 'Test User',
  email: 'test@example.com',
  avatar: 'https://example.com/avatar.jpg',
  role: 'user',
};

/**
 * Mock data factory
 */
export const mockDataFactory = {
  user: (overrides = {}) => ({ ...mockUser, ...overrides }),
  product: (overrides = {}) => ({
    id: '1',
    name: 'Test Product',
    price: 99.99,
    description: 'Test description',
    image: 'https://example.com/product.jpg',
    ...overrides,
  }),
  order: (overrides = {}) => ({
    id: '1',
    userId: '1',
    products: [{ id: '1', quantity: 1 }],
    total: 99.99,
    status: 'pending',
    createdAt: new Date().toISOString(),
    ...overrides,
  }),
};

/**
 * Mock intersection observer entry
 */
export const mockIntersectionObserverEntry = (isIntersecting = true) => ({
  isIntersecting,
  time: Date.now(),
  rootBounds: null,
  boundingClientRect: {
    width: 100,
    height: 100,
    top: 0,
    left: 0,
    right: 100,
    bottom: 100,
  },
  intersectionRect: isIntersecting
    ? {
        width: 100,
        height: 100,
        top: 0,
        left: 0,
        right: 100,
        bottom: 100,
      }
    : {
        width: 0,
        height: 0,
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
      },
  intersectionRatio: isIntersecting ? 1 : 0,
  target: document.createElement('div'),
});
