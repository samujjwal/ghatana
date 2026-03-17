/**
 * Authentication Test Utilities
 * 
 * Reusable test helpers for authentication testing including:
 * - Wrapper components with auth providers
 * - Mock auth state helpers
 * - Auth-specific render functions
 * - Token management helpers
 * - User factory functions
 * 
 * @doc.type test-utility
 * @doc.purpose Authentication test helpers and wrappers
 * @doc.layer testing
 */

import { render, type RenderOptions } from '@testing-library/react';
import { Provider as JotaiProvider } from 'jotai';
import { useHydrateAtoms } from 'jotai/utils';
import { ReactElement, ReactNode } from 'react';

import { ToastProvider } from '../components/Toast';
import { ThemeProvider } from '../theme/ThemeContext';
import {
  authStateAtom,
  authUserAtom,
  authTokenAtom,
  authLoadingAtom,
  authErrorAtom,
} from '@ghatana/yappc-canvas';

import type { User } from '@ghatana/yappc-types';

// ============================================================================
// Types
// ============================================================================

/**
 * Auth state for testing
 */
export interface MockAuthState {
  /** Authenticated user */
  user: User | null;
  
  /** Authentication token */
  token: string | null;
  
  /** Is authenticated */
  isAuthenticated: boolean;
  
  /** Is loading */
  isLoading: boolean;
  
  /** Error message */
  error: string | null;
  
  /** User roles */
  roles?: string[];
  
  /** User permissions */
  permissions?: string[];
}

/**
 * Auth provider props for testing
 */
export interface MockAuthProviderProps {
  children: ReactNode;
  initialAuthState?: Partial<MockAuthState>;
}

/**
 * Render options with auth state
 */
export interface RenderWithAuthOptions extends Omit<RenderOptions, 'wrapper'> {
  initialAuthState?: Partial<MockAuthState>;
}

// ============================================================================
// Mock Data Factories
// ============================================================================

/**
 * Create mock user for testing
 * 
 * @param overrides - User property overrides
 * @returns Mock user object
 */
export function createMockUser(overrides: Partial<User> = {}): User {
  return {
    id: overrides.id || 'mock-user-1',
    email: overrides.email || 'test@example.com',
    name: overrides.name || 'Test User',
    createdAt: overrides.createdAt || new Date().toISOString(),
    updatedAt: overrides.updatedAt || new Date().toISOString(),
    ...overrides,
  } as User;
}

/**
 * Create mock admin user for testing
 * 
 * @param overrides - User property overrides
 * @returns Mock admin user object
 */
export function createMockAdminUser(overrides: Partial<User> = {}): User {
  return createMockUser({
    id: 'mock-admin-1',
    email: 'admin@example.com',
    name: 'Admin User',
    ...overrides,
  });
}

/**
 * Create mock auth token
 * 
 * @param expiresIn - Token expiration in seconds (default: 3600)
 * @returns Mock JWT token string
 */
export function createMockAuthToken(expiresIn: number = 3600): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(
    JSON.stringify({
      sub: 'mock-user-1',
      email: 'test@example.com',
      exp: Math.floor(Date.now() / 1000) + expiresIn,
      iat: Math.floor(Date.now() / 1000),
    })
  );
  const signature = 'mock-signature';
  
  return `${header}.${payload}.${signature}`;
}

/**
 * Create expired mock auth token
 * 
 * @returns Expired mock JWT token string
 */
export function createExpiredMockAuthToken(): string {
  return createMockAuthToken(-3600); // Expired 1 hour ago
}

// ============================================================================
// Mock Auth State Helpers
// ============================================================================

/**
 * Create authenticated state for testing
 * 
 * @param user - User object (optional)
 * @returns Mock authenticated state
 */
export function createAuthenticatedState(user?: User): MockAuthState {
  const mockUser = user || createMockUser();
  
  return {
    user: mockUser,
    token: createMockAuthToken(),
    isAuthenticated: true,
    isLoading: false,
    error: null,
    roles: (mockUser as unknown).roles || ['user'],
    permissions: (mockUser as unknown).permissions || ['read', 'write'],
  };
}

/**
 * Create unauthenticated state for testing
 * 
 * @returns Mock unauthenticated state
 */
export function createUnauthenticatedState(): MockAuthState {
  return {
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    error: null,
  };
}

/**
 * Create loading state for testing
 * 
 * @returns Mock loading state
 */
export function createLoadingState(): MockAuthState {
  return {
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: true,
    error: null,
  };
}

/**
 * Create error state for testing
 * 
 * @param error - Error message
 * @returns Mock error state
 */
export function createErrorState(error: string = 'Authentication failed'): MockAuthState {
  return {
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    error,
  };
}

/**
 * Create admin authenticated state for testing
 * 
 * @param user - Admin user object (optional)
 * @returns Mock admin authenticated state
 */
export function createAdminAuthenticatedState(user?: User): MockAuthState {
  const adminUser = user || createMockAdminUser();
  
  return {
    user: adminUser,
    token: createMockAuthToken(),
    isAuthenticated: true,
    isLoading: false,
    error: null,
    roles: (adminUser as unknown).roles || ['admin', 'user'],
    permissions: (adminUser as unknown).permissions || ['read', 'write', 'delete', 'admin'],
  };
}

// ============================================================================
// Auth Provider Wrapper Component
// ============================================================================

/**
 * Hydrate atoms component for testing
 */
function HydrateAtoms({ initialValues, children }: {
  initialValues: Array<[any, any]>;
  children: ReactNode;
}) {
  useHydrateAtoms(initialValues);
  return <>{children}</>;
}

/**
 * Mock authentication provider wrapper for testing
 * Provides Jotai store with initial auth state
 * 
 * @example
 * const { getByText } = render(
 *   <MockAuthProvider initialAuthState={{ user: mockUser, isAuthenticated: true }}>
 *     <ProtectedComponent />
 *   </MockAuthProvider>
 * );
 */
export function MockAuthProvider({ children, initialAuthState = {} }: MockAuthProviderProps) {
  const defaultState = createUnauthenticatedState();
  const authState = { ...defaultState, ...initialAuthState };
  
  const initialValues: Array<[any, any]> = [
    [authUserAtom, authState.user],
    [authTokenAtom, authState.token],
    [authLoadingAtom, authState.isLoading],
    [authErrorAtom, authState.error],
    [authStateAtom, {
      user: authState.user,
      token: authState.token,
      isAuthenticated: authState.isAuthenticated,
      isLoading: authState.isLoading,
      error: authState.error,
    }],
  ];
  
  return (
    <JotaiProvider>
      <HydrateAtoms initialValues={initialValues}>
        <ThemeProvider>
          <ToastProvider>
            {children}
          </ToastProvider>
        </ThemeProvider>
      </HydrateAtoms>
    </JotaiProvider>
  );
}

// ============================================================================
// Render Functions with Auth
// ============================================================================

/**
 * Render component with authenticated user
 * 
 * @param ui - Component to render
 * @param options - Render options with optional auth state
 * @returns Render result
 * 
 * @example
 * const { getByText } = renderWithAuth(<Dashboard />, {
 *   initialAuthState: { user: createMockUser({ name: 'John' }) }
 * });
 */
export function renderWithAuth(
  ui: ReactElement,
  options: RenderWithAuthOptions = {}
): ReturnType<typeof render> {
  const { initialAuthState, ...renderOptions } = options;
  
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <MockAuthProvider initialAuthState={initialAuthState}>
      {children}
    </MockAuthProvider>
  );
  
  return render(ui, { wrapper: Wrapper, ...renderOptions });
}

/**
 * Render component with authenticated user
 * 
 * @param ui - Component to render
 * @param user - User object (optional)
 * @param options - Render options
 * @returns Render result
 * 
 * @example
 * const { getByText } = renderAuthenticated(<Dashboard />, createMockUser({ name: 'John' }));
 */
export function renderAuthenticated(
  ui: ReactElement,
  user?: User,
  options: Omit<RenderOptions, 'wrapper'> = {}
): ReturnType<typeof render> {
  const authState = createAuthenticatedState(user);
  return renderWithAuth(ui, { initialAuthState: authState, ...options });
}

/**
 * Render component with unauthenticated state
 * 
 * @param ui - Component to render
 * @param options - Render options
 * @returns Render result
 * 
 * @example
 * const { getByText } = renderUnauthenticated(<LoginPage />);
 */
export function renderUnauthenticated(
  ui: ReactElement,
  options: Omit<RenderOptions, 'wrapper'> = {}
): ReturnType<typeof render> {
  const authState = createUnauthenticatedState();
  return renderWithAuth(ui, { initialAuthState: authState, ...options });
}

/**
 * Render component with admin user
 * 
 * @param ui - Component to render
 * @param user - Admin user object (optional)
 * @param options - Render options
 * @returns Render result
 * 
 * @example
 * const { getByText } = renderAsAdmin(<AdminPanel />);
 */
export function renderAsAdmin(
  ui: ReactElement,
  user?: User,
  options: Omit<RenderOptions, 'wrapper'> = {}
): ReturnType<typeof render> {
  const authState = createAdminAuthenticatedState(user);
  return renderWithAuth(ui, { initialAuthState: authState, ...options });
}

/**
 * Render component with loading state
 * 
 * @param ui - Component to render
 * @param options - Render options
 * @returns Render result
 * 
 * @example
 * const { getByTestId } = renderLoading(<LoginForm />);
 */
export function renderLoading(
  ui: ReactElement,
  options: Omit<RenderOptions, 'wrapper'> = {}
): ReturnType<typeof render> {
  const authState = createLoadingState();
  return renderWithAuth(ui, { initialAuthState: authState, ...options });
}

/**
 * Render component with error state
 * 
 * @param ui - Component to render
 * @param error - Error message
 * @param options - Render options
 * @returns Render result
 * 
 * @example
 * const { getByText } = renderWithError(<LoginForm />, 'Invalid credentials');
 */
export function renderWithError(
  ui: ReactElement,
  error: string = 'Authentication failed',
  options: Omit<RenderOptions, 'wrapper'> = {}
): ReturnType<typeof render> {
  const authState = createErrorState(error);
  return renderWithAuth(ui, { initialAuthState: authState, ...options });
}

// ============================================================================
// Storage Helpers
// ============================================================================

/**
 * Mock localStorage with auth token
 * 
 * @param token - Auth token (optional, creates new if not provided)
 */
export function mockLocalStorageWithToken(token?: string): void {
  const authToken = token || createMockAuthToken();
  localStorage.setItem('authToken', authToken);
}

/**
 * Mock sessionStorage with auth token
 * 
 * @param token - Auth token (optional, creates new if not provided)
 */
export function mockSessionStorageWithToken(token?: string): void {
  const authToken = token || createMockAuthToken();
  sessionStorage.setItem('authToken', authToken);
}

/**
 * Clear auth tokens from storage
 */
export function clearAuthTokens(): void {
  localStorage.removeItem('authToken');
  localStorage.removeItem('refreshToken');
  sessionStorage.removeItem('authToken');
  sessionStorage.removeItem('refreshToken');
}

/**
 * Mock auth tokens in storage
 * 
 * @param accessToken - Access token
 * @param refreshToken - Refresh token
 * @param storage - Storage type ('local' | 'session')
 */
export function mockAuthTokensInStorage(
  accessToken?: string,
  refreshToken?: string,
  storage: 'local' | 'session' = 'local'
): void {
  const storageObj = storage === 'local' ? localStorage : sessionStorage;
  
  if (accessToken) {
    storageObj.setItem('authToken', accessToken);
  }
  if (refreshToken) {
    storageObj.setItem('refreshToken', refreshToken);
  }
}

// ============================================================================
// Assertion Helpers
// ============================================================================

/**
 * Assert user is authenticated in the UI
 * 
 * @param container - Render container
 * @param userName - Expected user name
 */
export function expectUserAuthenticated(container: HTMLElement, userName: string): void {
  const userElement = container.querySelector(`[data-testid="user-name"]`);
  expect(userElement).toHaveTextContent(userName);
}

/**
 * Assert user is not authenticated in the UI
 * 
 * @param container - Render container
 */
export function expectUserNotAuthenticated(container: HTMLElement): void {
  const loginButton = container.querySelector(`[data-testid="login-button"]`);
  expect(loginButton).toBeInTheDocument();
}

// ============================================================================
// Test Utilities Export
// ============================================================================

/**
 * All auth test helpers in one object
 */
export const authTestHelpers = {
  // Factories
  createMockUser,
  createMockAdminUser,
  createMockAuthToken,
  createExpiredMockAuthToken,
  
  // State creators
  createAuthenticatedState,
  createUnauthenticatedState,
  createLoadingState,
  createErrorState,
  createAdminAuthenticatedState,
  
  // Render functions
  renderWithAuth,
  renderAuthenticated,
  renderUnauthenticated,
  renderAsAdmin,
  renderLoading,
  renderWithError,
  
  // Storage helpers
  mockLocalStorageWithToken,
  mockSessionStorageWithToken,
  clearAuthTokens,
  mockAuthTokensInStorage,
  
  // Assertions
  expectUserAuthenticated,
  expectUserNotAuthenticated,
};

export default authTestHelpers;
