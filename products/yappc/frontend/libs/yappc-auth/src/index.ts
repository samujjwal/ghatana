/**
 * yappc-auth — DEPRECATED thin re-export.
 *
 * @deprecated This package is superseded by `@ghatana/sso-client`.
 * All OAuth, RBAC, and security utilities have been migrated to
 * `@ghatana/sso-client` (oauth, rbac, and security sub-modules).
 *
 * **Migration guide:**
 * ```ts
 * // Before
 * import { useOAuth, OAuthProvider } from '@yappc/auth';
 * import { WorkspaceRole, hasPermission } from '@yappc/auth';
 * import { helmetConfig } from '@yappc/auth';
 *
 * // After
 * import { useOAuth } from '@ghatana/sso-client';
 * import type { OAuthProvider } from '@ghatana/sso-client';
 * import { WorkspaceRole, hasPermission } from '@ghatana/sso-client';
 * import { helmetConfig } from '@ghatana/sso-client';
 *
 * // For server-side CSP (Fastify):
 * import { securityHeadersPlugin } from '@ghatana/sso-client/security/fastify';
 * ```
 *
 * @doc.type module
 * @doc.purpose Deprecated — re-exports @ghatana/sso-client for backward compatibility
 * @doc.layer product
 * @doc.pattern Deprecated Barrel
 */

export * from '@ghatana/sso-client';
