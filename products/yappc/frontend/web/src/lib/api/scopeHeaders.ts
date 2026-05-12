/**
 * Scope and Header Utilities
 *
 * Utilities for adding scope and headers to HTTP requests.
 * Implements the backend extraction policy: path > query > header.
 *
 * @doc.type module
 * @doc.purpose Scope and header utilities
 * @doc.layer product
 * @doc.pattern Utility
 */

/**
 * Scope location preference for passing scope to backend.
 * Backend extraction policy: path > query > header
 */
export type ScopeLocation = 'query' | 'header';

/**
 * Options for scoped requests.
 */
export interface ScopedRequestOptions {
  /** The scope to pass (e.g., 'project:read', 'workspace:write') */
  scope?: string;
  /** Where to pass the scope (default: query) */
  scopeLocation?: ScopeLocation;
  /** Additional headers to include */
  headers?: Readonly<Record<string, string>>;
}

/**
 * Adds scope to a path or headers based on the specified location.
 * This implements the backend extraction policy: path > query > header
 */
export function addScopeToRequest(
  path: string,
  options: ScopedRequestOptions,
): { path: string; headers: Readonly<Record<string, string>> } {
  const { scope, scopeLocation = 'query', headers = {} } = options;

  if (!scope) {
    return { path, headers };
  }

  const resultHeaders = { ...headers };

  switch (scopeLocation) {
    case 'query':
      // Add scope as query parameter
      const url = new URL(path, window.location.origin);
      url.searchParams.set('scope', scope);
      return { path: url.pathname + url.search, headers: resultHeaders };
    case 'header':
      // Add scope as header
      resultHeaders['X-Scope'] = scope;
      return { path, headers: resultHeaders };
    default:
      // Default to query for unknown location
      const defaultUrl = new URL(path, window.location.origin);
      defaultUrl.searchParams.set('scope', scope);
      return { path: defaultUrl.pathname + defaultUrl.search, headers: resultHeaders };
  }
}
