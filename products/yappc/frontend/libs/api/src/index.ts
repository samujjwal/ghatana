/**
 * @yappc/api — DEPRECATED thin re-export.
 *
 * @deprecated This package has been consolidated into `@yappc/core`.
 * All GraphQL clients, operations, AI client, AuthService, and hooks
 * are now exported from `@yappc/core` (or `@yappc/core/api` sub-path).
 *
 * **Migration guide:**
 * ```ts
 * // Before
 * import { AuthService, createGraphQLClient } from '@yappc/api';
 * import { aiClient } from '@yappc/api';
 * import { useBootstrapping } from '@yappc/api';
 *
 * // After
 * import { AuthService, createGraphQLClient } from '@yappc/core';
 * import { aiClient } from '@yappc/core';
 * import { useBootstrapping } from '@yappc/core';
 * ```
 *
 * @doc.type module
 * @doc.purpose Deprecated — re-exports @yappc/core/api for backward compatibility
 * @doc.layer product
 * @doc.pattern Deprecated Barrel
 */

export * from '@yappc/core/api';

