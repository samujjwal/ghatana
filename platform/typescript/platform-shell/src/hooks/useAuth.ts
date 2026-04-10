/**
 * useAuth — shared authentication hook for all Ghatana products.
 *
 * Reads auth state from the platform-shell Jotai atoms and exposes a
 * consistent, typed interface. Product hooks may wrap this hook to add
 * product-specific fields (e.g. tenantId, RBAC permissions).
 *
 * @doc.type hook
 * @doc.purpose Shared auth state accessor for Ghatana products
 * @doc.layer shared
 * @doc.pattern Custom Hook, Adapter
 */
import { useAtomValue } from 'jotai';
import {
  authTokenAtom,
  isAuthenticatedAtom,
  isTokenExpiredAtom,
  currentUserEmailAtom,
  type AuthToken,
} from '@ghatana/state';

export interface UseAuthReturn {
  /** Whether a valid, non-expired token is present */
  isAuthenticated: boolean;
  /** Whether the current token has passed its expiry time */
  isTokenExpired: boolean;
  /** The in-memory auth token, or null when unauthenticated */
  token: AuthToken | null;
  /** The authenticated user's email, or null */
  currentUserEmail: string | null;
  /**
   * Returns the Authorization header value ready for use with fetch/axios,
   * or null when unauthenticated.
   *
   * @example
   * headers: { Authorization: getAuthHeader() ?? '' }
   */
  getAuthHeader: () => string | null;
  /** Returns true if the user holds the given role string */
  hasRole: (role: string) => boolean;
  /** Returns true if the user has access to the given tenant ID */
  hasTenant: (tenantId: string) => boolean;
}

/**
 * Provides reactive auth state from the platform-shell Jotai store.
 *
 * All atoms are read-only here — mutations (login, logout, token refresh)
 * are performed by the product's auth service which writes to the atoms.
 */
export function useAuth(): UseAuthReturn {
  const token = useAtomValue(authTokenAtom);
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);
  const isTokenExpired = useAtomValue(isTokenExpiredAtom);
  const currentUserEmail = useAtomValue(currentUserEmailAtom);

  const getAuthHeader = (): string | null => {
    if (!token || isTokenExpired) return null;
    return `Bearer ${token.accessToken}`;
  };

  const hasRole = (role: string): boolean => {
    return token?.roles.includes(role) ?? false;
  };

  const hasTenant = (tenantId: string): boolean => {
    return token?.tenants.includes(tenantId) ?? false;
  };

  return {
    isAuthenticated,
    isTokenExpired,
    token,
    currentUserEmail,
    getAuthHeader,
    hasRole,
    hasTenant,
  };
}
