/**
 * Authentication Store - Jotai Atoms
 *
 * Manages authentication state including:
 * - User login/logout
 * - Token management
 * - Session state
 * - Authentication errors
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose Authentication state management
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';
import { guardianApi } from '../services/guardianApi';

/**
 * User object representing authenticated user.
 *
 * @interface User
 * @property {string} id - Unique user identifier
 * @property {string} email - User email address
 * @property {string} name - User display name
 * @property {string[]} roles - User roles (admin, parent, child)
 * @property {Date} lastLogin - Last login timestamp
 */
export interface User {
  id: string;
  email: string;
  name: string;
  roles: string[];
  tenantId: string;
  lastLogin?: Date;
}

/**
 * Login credentials for authentication.
 *
 * @interface LoginCredentials
 * @property {string} email - User email
 * @property {string} password - User password
 */
export interface LoginCredentials {
  email: string;
  password: string;
}

/**
 * Authentication state.
 *
 * @interface AuthState
 * @property {User | null} user - Currently authenticated user or null
 * @property {string | null} token - JWT or session token
 * @property {'idle' | 'loading' | 'authenticated' | 'unauthenticated'} status - Auth status
 * @property {string | null} error - Error message if authentication failed
 */
export interface AuthState {
  user: User | null;
  token: string | null;
  status: 'idle' | 'loading' | 'authenticated' | 'unauthenticated';
  error: string | null;
}

/**
 * Initial authentication state.
 *
 * GIVEN: App initialization
 * WHEN: authAtom is first accessed
 * THEN: User starts in idle state with no user/token
 */
const initialAuthState: AuthState = {
  user: null,
  token: null,
  status: 'idle',
  error: null,
};

/**
 * Core authentication atom.
 *
 * Holds complete authentication state including:
 * - Current authenticated user
 * - JWT/session token
 * - Authentication status
 * - Error information
 *
 * Usage:
 * ```typescript
 * const [authState, setAuthState] = useAtom(authAtom);
 * ```
 */
export const authAtom = atom<AuthState>(initialAuthState);

/**
 * Derived atom: Is user authenticated?
 *
 * GIVEN: authAtom has any state
 * WHEN: isAuthenticatedAtom is read
 * THEN: Returns true if status === 'authenticated'
 *
 * Usage:
 * ```typescript
 * const [isAuthenticated] = useAtom(isAuthenticatedAtom);
 * ```
 */
export const isAuthenticatedAtom = atom<boolean>((get) => {
  return get(authAtom).status === 'authenticated';
});

/**
 * Derived atom: Current authenticated user.
 *
 * GIVEN: authAtom with user data
 * WHEN: currentUserAtom is read
 * THEN: Returns user object or null if not authenticated
 *
 * Usage:
 * ```typescript
 * const [currentUser] = useAtom(currentUserAtom);
 * ```
 */
export const currentUserAtom = atom<User | null>((get) => {
  return get(authAtom).user;
});

/**
 * Derived atom: Authentication status string.
 *
 * GIVEN: authAtom with status
 * WHEN: authStatusAtom is read
 * THEN: Returns current authentication status
 *
 * Usage (in components):
 * `const [status] = useAtom(authStatusAtom);`
 * If status === 'loading', show spinner
 */
export const authStatusAtom = atom<AuthState['status']>((get) => {
  return get(authAtom).status;
});

/**
 * Derived atom: Authentication error message.
 *
 * GIVEN: authAtom with error after failed login
 * WHEN: authErrorAtom is read
 * THEN: Returns error message or null if no error
 *
 * Usage (in components):
 * `const [error] = useAtom(authErrorAtom);`
 * If error exists, show error toast
 */
export const authErrorAtom = atom<string | null>((get) => {
  return get(authAtom).error;
});

/**
 * Action atom: Login with credentials.
 *
 * GIVEN: Valid login credentials
 * WHEN: loginAtom action is called
 * THEN: Attempts authentication and updates authAtom
 *       Sets status to 'loading' during request
 *       Sets status to 'authenticated' on success
 *       Sets status to 'unauthenticated' on failure
 *
 * GIVEN: Login fails (invalid credentials, network error)
 * WHEN: Error occurs
 * THEN: Sets error message in authAtom
 *       Throws error to caller
 *
 * Usage (in components):
 * `const [, login] = useAtom(loginAtom);`
 * Call login with credentials, error stored in authAtom.error on failure
 *
 * @async
 * @param {LoginCredentials} credentials - Email and password
 * @returns {Promise<User>} Authenticated user object
 * @throws {Error} If authentication fails
 */
export const loginAtom = atom<
  null,
  [LoginCredentials],
  Promise<User>
>(
  null,
  async (get, set, credentials: LoginCredentials) => {
    // Set loading state
    const currentAuth = get(authAtom);
    set(authAtom, {
      ...currentAuth,
      status: 'loading',
      error: null,
    });

    try {
      const res = await fetch('/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(credentials),
      });

      if (!res.ok) {
        const errBody = await res.json().catch(() => ({})) as { message?: string };
        throw new Error(errBody.message ?? `HTTP ${res.status}`);
      }

      const { user, token } = await res.json() as { user: User; token: string };

      // Authenticate the shared API client for subsequent calls
      guardianApi.setToken(token);

      // Update state on success
      set(authAtom, {
        user,
        token,
        status: 'authenticated',
        error: null,
      });

      return user;
    } catch (error) {
      // Update state on error
      const errorMessage = error instanceof Error ? error.message : 'Login failed';
      set(authAtom, {
        ...get(authAtom),
        status: 'unauthenticated',
        error: errorMessage,
      });
      throw error;
    }
  }
);

/**
 * Action atom: Logout user.
 *
 * GIVEN: Authenticated user
 * WHEN: logoutAtom action is called
 * THEN: Clears all authentication state
 *       Resets authAtom to initial state
 *
 * Usage (in components):
 * `const [, logout] = useAtom(logoutAtom);` then call logout()
 * User is immediately logged out
 */
export const logoutAtom = atom<null, [], void>(null, async (get, set) => {
  const { token } = get(authAtom);
  if (token) {
    try {
      await fetch('/auth/logout', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
    } catch {
      // Best-effort: clear local state regardless of API response
    }
  }

  guardianApi.setToken(null);
  set(authAtom, initialAuthState);
});

/**
 * Action atom: Refresh authentication token.
 *
 * GIVEN: User with expired or expiring token
 * WHEN: refreshTokenAtom action is called
 * THEN: Attempts to refresh token via API
 *       Updates token in authAtom on success
 *
 * GIVEN: Refresh fails (user session expired)
 * WHEN: Error occurs
 * THEN: Logs user out automatically
 *
 * Usage (in components):
 * `const [, refreshToken] = useAtom(refreshTokenAtom);`
 * Token updated on success, user logged out on failure
 */
export const refreshTokenAtom = atom<null, [], Promise<string>>(
  null,
  async (get, set) => {
    try {
      const { token: currentToken } = get(authAtom);
      const res = await fetch('/auth/refresh', {
        method: 'POST',
        headers: { Authorization: `Bearer ${currentToken ?? ''}` },
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      const { token: newToken } = await res.json() as { token: string };

      guardianApi.setToken(newToken);

      const currentAuth = get(authAtom);
      set(authAtom, {
        ...currentAuth,
        token: newToken,
      });

      return newToken;
    } catch (error) {
      // Logout on refresh failure
      set(authAtom, initialAuthState);
      throw error;
    }
  }
);

/**
 * Clear authentication error.
 *
 * GIVEN: Authentication error is displayed
 * WHEN: User dismisses error or tries again
 * THEN: clearAuthErrorAtom clears the error message
 *
 * Usage (in components):
 * `const [, clearError] = useAtom(clearAuthErrorAtom);`
 * Then call clearError() to dismiss error
 */
export const clearAuthErrorAtom = atom<null, [], void>(null, (get, set) => {
  const currentAuth = get(authAtom);
  set(authAtom, {
    ...currentAuth,
    error: null,
  });
});
